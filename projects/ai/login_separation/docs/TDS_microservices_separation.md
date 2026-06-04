# TDS: Separate Login and Hello into Independent Microservices

**Version:** 1.0
**Date:** 2026-06-01
**Status:** Draft
**Jira:** PR-125
**Epic:** PR-123
**Related:** PRD ticket (PR-124), PRD_microservices_separation.md

---

## 1. Overview

This document describes the technical implementation of splitting the `backend/` Flask monolith into two microservices: `login-service` (authentication) and `hello-service` (application feature). It covers the service directory layout, JWT validation strategy, frontend routing changes, docker-compose updates, Kubernetes manifest changes, ECR and IAM changes, and test reorganisation.

---

## 2. Architecture

### Current (monolith)

```
Browser
  └── nginx:8080 (frontend)
        └── /api/* → backend:5001
                        ├── POST /api/login
                        ├── POST /api/signup
                        ├── POST /api/logout
                        ├── POST /api/password-reset/*
                        ├── GET  /api/hello
                        └── GET  /api/health
```

### After split

```
Browser
  └── nginx:8080 (frontend)
        ├── /api/hello  → hello-service:5002
        │                     ├── GET /api/hello   (JWT validated locally)
        │                     └── GET /api/health
        └── /api/*      → login-service:5001
                              ├── POST /api/login
                              ├── POST /api/signup
                              ├── POST /api/logout
                              ├── POST /api/password-reset/request
                              ├── POST /api/password-reset/confirm
                              └── GET  /api/health
```

**No direct service-to-service calls.** Frontend nginx is the sole router. Both services are ClusterIP (internal only); external traffic enters only via the ALB Ingress.

---

## 3. Repository Layout Changes

### Before

```
backend/
  app/
    auth_controllers.py   # login, signup, logout, password-reset
    controllers.py        # hello, health
    auth.py               # JWT generation + require_auth decorator
    models.py             # User, LoginAttempt, DeniedToken, PasswordResetToken
    database.py           # SQLAlchemy wrapper
    factory.py            # Flask app factory (DB + mail + CORS)
    routes.py             # all routes in one blueprint
  Dockerfile
  entrypoint.sh           # waits for MySQL, runs alembic, starts app
  requirements.txt
  run.py
  migrations/
```

### After

```
login-service/            (renamed from backend/)
  app/
    auth_controllers.py   # unchanged
    controllers.py        # HealthController only (HelloController removed)
    auth.py               # unchanged (JWT generation + full require_auth with DB checks)
    models.py             # unchanged
    database.py           # unchanged
    factory.py            # unchanged
    routes.py             # remove /api/hello binding
  Dockerfile              # unchanged, port 5001
  entrypoint.sh           # unchanged
  requirements.txt        # unchanged
  run.py                  # unchanged
  migrations/             # unchanged

hello-service/            (new)
  app/
    __init__.py
    auth.py               # slim JWT validator (signature + expiry + role, no DB)
    controllers.py        # HelloController + HealthController
    factory.py            # minimal Flask factory (no DB, no mail)
    routes.py             # /api/hello and /api/health only
  Dockerfile              # port 5002, no alembic step in entrypoint
  entrypoint.sh           # simple: exec python run.py (no DB wait, no migrations)
  requirements.txt        # flask, flask-cors, pyjwt, python-dotenv, cryptography
  run.py                  # PORT defaults to 5002
  .env.example
```

---

## 4. hello-service Implementation

### 4.1 `hello-service/app/auth.py`

Slim version of the login-service `Auth` class. Removes all database calls:

```python
class Auth:
    @staticmethod
    def generate_token(user):
        # NOT present in hello-service — token issuance is login-service only
        raise NotImplementedError

    @staticmethod
    def require_auth(f, allowed_roles=None):
        # Validates: Authorization header, JWT signature, expiry, role
        # Does NOT check: denied_tokens table, tokens_invalidated_at
        # Rationale: no DB in hello-service; tokens expire in 24h (acceptable window)
        ...
```

The `require_auth` decorator injects `flask.g.current_user` (decoded JWT payload) identically to the login-service version, so `HelloController` code is copy-paste identical.

### 4.2 `hello-service/app/factory.py`

```python
class AppFactory:
    @staticmethod
    def create(config=None):
        app = Flask(__name__)
        load_dotenv()
        app.config["JWT_SECRET"] = os.environ["JWT_SECRET"]
        CORS(app, origins=os.environ.get("CORS_ORIGINS", "").split(","))
        Router.register(app)
        return app
```

No `SQLAlchemy`, no `Flask-Mail`, no `alembic`.

### 4.3 `hello-service/Dockerfile`

Identical to `login-service/Dockerfile` except:
- `EXPOSE 5002`
- Healthcheck hits port 5002
- No `alembic upgrade head` in entrypoint

### 4.4 `hello-service/requirements.txt`

```
flask==3.1.0
flask-cors==5.0.0
pyjwt==2.10.1
python-dotenv==1.0.1
cryptography==44.0.3
```

No `flask-sqlalchemy`, `flask-mail`, `alembic`, `pymysql`, `bcrypt`.

---

## 5. login-service Changes

Minimal — only two changes:

1. **`login-service/app/routes.py`**: Remove the `/api/hello` route binding.
2. **`login-service/app/controllers.py`**: Remove `HelloController` class (keep `HealthController`).

All other files (auth.py, auth_controllers.py, models.py, migrations/, etc.) are **unchanged**.

---

## 6. JWT Strategy

| Concern | Decision |
|---------|----------|
| Secret sharing | Both services read `JWT_SECRET` from their own environment. Same value injected into both deployments via Kubernetes Secret / docker-compose env. |
| Token issuance | login-service only (`Auth.generate_token`). |
| Token validation | Both services validate signature, expiry, and role independently. |
| Revocation (denied_tokens) | login-service checks the blacklist. hello-service does not (no DB). Tokens expire in 24h. |
| Password reset invalidation | login-service checks `tokens_invalidated_at`. hello-service does not. After a password reset, old tokens remain valid for hello-service until expiry (max 24h). |

**Future option**: Add a `/api/token/verify` introspection endpoint to login-service. hello-service would call it on each request to check revocation. Not implemented in this iteration — adds latency and coupling.

---

## 7. Frontend Nginx Changes

**File:** `frontend/nginx.conf.template`

Add a specific location block for `/api/hello` **before** the catch-all `/api/` block:

```nginx
# hello-service: handles the application greeting endpoint
location = /api/hello {
    proxy_pass         http://${HELLO_BACKEND_HOST}:5002/api/hello;
    proxy_set_header   Host              $host;
    proxy_set_header   X-Real-IP         $remote_addr;
    proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header   Authorization     $http_authorization;
}

# login-service: all other /api/* requests
location /api/ {
    proxy_pass         http://${BACKEND_HOST}:5001/api/;
    ...  # existing headers unchanged
}
```

**New env var**: `HELLO_BACKEND_HOST` — the DNS name of the hello-service container / Kubernetes Service.

---

## 8. docker-compose.yml Changes

```yaml
services:
  login-service:          # renamed from "backend"
    build: ./login-service
    ports: ["5001:5001"]
    environment:
      JWT_SECRET: ${JWT_SECRET:-dev-only-secret-change-for-production-32c}
      DATABASE_URL: ${DATABASE_URL:-mysql+pymysql://hello:hello@mysql:3306/hello_login}
      CORS_ORIGINS: ${CORS_ORIGINS:-http://localhost:5173,http://localhost:3000}
      # ... other existing vars unchanged

  hello-service:          # new
    build: ./hello-service
    ports: ["5002:5002"]
    environment:
      JWT_SECRET: ${JWT_SECRET:-dev-only-secret-change-for-production-32c}
      PORT: 5002
    depends_on:
      login-service:
        condition: service_healthy  # not strictly needed, but avoids early startup races

  frontend:
    environment:
      BACKEND_HOST: login-service        # renamed from "backend"
      HELLO_BACKEND_HOST: hello-service  # new
```

---

## 9. Kubernetes Manifest Changes

### 9.1 New files

| File | Description |
|------|-------------|
| `k8s/base/hello-deployment.yaml` | hello-service Deployment: 2 replicas, port 5002, no initContainer (no DB migrations), same resource limits pattern as login-service |
| *(in services.yaml)* | Add `hello` ClusterIP Service, port 5002, selector `app: hello` |

### 9.2 Modified files

**`k8s/base/backend-deployment.yaml`** → rename to **`login-deployment.yaml`**:
- Update `metadata.name`, `spec.selector.matchLabels`, pod template labels from `app: backend` → `app: login`
- Service name `backend` → `login`

**`k8s/base/services.yaml`**:
- Rename backend Service: `name: backend` → `name: login`, selector `app: login`
- Add hello Service: `name: hello`, port 5002, selector `app: hello`

**`k8s/base/ingress.yaml`**:

Add a new `/api/hello` path rule (group.order 1), push existing `/api` to order 2, `/` stays at order 3:

```yaml
# Backend ingress — hello endpoint (order 1, most specific)
- path: /api/hello
  pathType: Exact
  backend:
    service:
      name: hello
      port:
        number: 5002

# Backend ingress — login/auth endpoints (order 2)
- path: /api
  pathType: Prefix
  backend:
    service:
      name: login
      port:
        number: 5001
```

**`k8s/base/configmap.yaml`**: Add `HELLO_BACKEND_HOST: hello` for the frontend.

**`k8s/base/kustomization.yaml`**: Add `hello-deployment.yaml` to the resources list.

### 9.3 Overlay changes (staging and production)

Both `k8s/overlays/staging/` and `k8s/overlays/production/`:
- `deployment-patch.yaml`: add image patch for hello-service (`hello-login-hello:latest` / `:REPLACE_WITH_SHA`)
- `kustomization.yaml`: reference the new patch

---

## 10. Infrastructure Changes

### 10.1 ECR

**`terraform/modules/ecr/main.tf`** — update the `repos` local:

```hcl
repos = ["hello-login-login", "hello-login-hello", "hello-login-frontend"]
```

Note: `hello-login-backend` is renamed to `hello-login-login`. Because ECR repo names are immutable, this requires creating a new repo and decommissioning the old one. Staging can recreate; production should:
1. Create `hello-login-login` (new)
2. Push existing images under the new name
3. Update CI to push to `hello-login-login`
4. Delete `hello-login-backend` once CI is confirmed working

### 10.2 GitHub Actions IAM Policy

**`terraform/modules/iam/main.tf`** — the `ECRPush` statement's `resources` list:

```hcl
resources = [
  "arn:aws:ecr:${var.aws_region}:${var.aws_account_id}:repository/hello-login-login",
  "arn:aws:ecr:${var.aws_region}:${var.aws_account_id}:repository/hello-login-hello",
  "arn:aws:ecr:${var.aws_region}:${var.aws_account_id}:repository/hello-login-frontend",
]
```

---

## 11. Test Changes

### 11.1 hello-service tests (new)

Create `hello-service/tests/` mirroring login-service test structure:

```
hello-service/tests/
  conftest.py          # Flask test client with JWT_SECRET=test-secret (no DB)
  unit/
    test_controllers.py   # HelloController, HealthController
    test_auth.py          # require_auth decorator (slim version)
  integration/
    test_api.py           # GET /api/hello: 401 without JWT, 200 with valid JWT, 403 wrong role
```

`conftest.py` fixture — no database, no migrations:

```python
@pytest.fixture
def app():
    os.environ["JWT_SECRET"] = "test-secret"
    return create_app({"TESTING": True})
```

### 11.2 login-service tests (minimal changes)

- Remove `TestHelloController` from `tests/unit/test_controllers.py`
- Remove `/api/hello` tests from `tests/integration/test_api.py`
- All other tests unchanged

### 11.3 Root-level test config

**`pytest.ini`**: Update `pythonpath` to cover both services:

```ini
pythonpath = login-service hello-service
```

**`Makefile`**: Add `test-hello-unit` target:

```makefile
test-hello-unit: build-hello
	docker run --rm \
		-e JWT_SECRET=test-secret \
		-e TESTING=true \
		hello-login-hello \
		sh -c "pip install -q pytest && pytest tests/unit tests/integration --tb=short -q"
```

### 11.4 GitHub Actions CI job (independent per service)

**File:** `.github/workflows/hello-login-ci.yml`

Add a dedicated `test-hello-service` job that runs only when `hello-service/**` changes, independent of the existing `test-backend` job:

```yaml
test-hello-service:
  runs-on: ubuntu-latest
  if: |
    github.event_name == 'push' ||
    contains(github.event.pull_request.changed_files, 'hello-service/')
  steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-python@v5
      with:
        python-version: "3.11"
    - name: Install dependencies
      run: pip install -r hello-service/requirements.txt -r hello-service/requirements-dev.txt
    - name: Run hello-service tests
      run: pytest hello-service/tests/ --tb=short -q
      env:
        JWT_SECRET: test-secret
        TESTING: "true"
```

The existing `test-backend` job gains a path filter to run only when `login-service/**` or `tests/**` changes:

```yaml
test-backend:
  ...
  # add under 'on: pull_request / push':
  paths:
    - 'login-service/**'
    - 'tests/**'
```

This ensures each service's tests run independently on PRs that touch only that service, while both jobs run on changes that affect shared config (`pytest.ini`, `Makefile`, etc.).

---

## 12. Migration Path (Zero Downtime)

Perform the split in this order to avoid any service interruption:

1. **Create hello-service** alongside the unchanged monolith. Deploy it. Verify `/api/health` on port 5002 returns 200.
2. **Update frontend nginx** to route `/api/hello` to hello-service. Deploy frontend. Verify `/hello` page still works.
3. **Remove `/api/hello`** from login-service routes.py and controllers.py. Deploy login-service. Verify login/signup still work.
4. **Update ECR + IAM** (Terraform apply). Verify CI can push to the new repo names.
5. **Update Kubernetes Ingress** to route `/api/hello` to the hello Service. Apply manifests.

Each step is independently reversible. Step 2 is the only user-visible change; if it fails, reverting the nginx config restores the previous behaviour.

---

## 13. Key Design Decisions

### 13.1 Nginx routing vs dedicated API gateway

**Decision:** Update the existing frontend nginx config to route to two upstreams.

**Why:** nginx is already the entry point for all API traffic (both in docker-compose and Kubernetes). Adding one `location` block is a two-line change with zero new infrastructure. A dedicated gateway product (Kong, Traefik, etc.) would add operational overhead for a two-service system.

### 13.2 Stateless hello-service (no DB)

**Decision:** hello-service reads all user data from the JWT payload (`full_name`, `role`). It makes no database calls.

**Why:** The JWT already contains `full_name` (embedded at login time by login-service). Querying the DB for a display name on every `/api/hello` request would add latency, require the hello-service to have DB credentials, and create coupling to the users table schema. Stateless services are simpler to scale and operate.

### 13.3 Accepted revocation gap

**Decision:** hello-service does not check the denied-tokens blacklist.

**Why:** Checking revocation requires either a shared database (tight coupling) or a service-to-service call (latency + availability coupling). Tokens expire in 24 hours. The risk window is bounded and accepted. This is documented explicitly in the code (`hello-service/app/auth.py`) so future developers understand the trade-off.

### 13.4 Monorepo

**Decision:** Both services live in the same git repository.

**Why:** They share a deployment pipeline, Kubernetes overlays, Terraform configuration, and E2E tests. Splitting into separate repos would add complexity (cross-repo coordination for changes that span both services) with no clear benefit at this scale.

---

## 14. Files Created / Modified Summary

| File | Action |
|------|--------|
| `hello-service/` (whole directory) | Create |
| `backend/` | Rename to `login-service/` |
| `login-service/app/routes.py` | Remove `/api/hello` binding |
| `login-service/app/controllers.py` | Remove `HelloController` |
| `frontend/nginx.conf.template` | Add hello-service upstream location |
| `docker-compose.yml` | Add hello-service; rename backend → login-service |
| `docker-compose.ec2.yml` | Same as above |
| `k8s/base/backend-deployment.yaml` | Rename → `login-deployment.yaml`; update labels |
| `k8s/base/hello-deployment.yaml` | Create |
| `k8s/base/services.yaml` | Rename backend Service; add hello Service |
| `k8s/base/ingress.yaml` | Add `/api/hello` path to hello Service |
| `k8s/base/configmap.yaml` | Add `HELLO_BACKEND_HOST` |
| `k8s/base/kustomization.yaml` | Add hello-deployment.yaml |
| `k8s/overlays/staging/deployment-patch.yaml` | Add hello-service image patch |
| `k8s/overlays/production/deployment-patch.yaml` | Add hello-service image patch |
| `terraform/modules/ecr/main.tf` | Update repos list |
| `terraform/modules/iam/main.tf` | Add hello-login-hello to ECR push policy |
| `pytest.ini` | Update pythonpath |
| `Makefile` | Add test-hello-unit target |
| `.github/workflows/hello-login-ci.yml` | Add test-hello-service job; add path filters to test-backend job |
