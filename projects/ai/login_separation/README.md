# Hello Login Web App

A full-stack web application with JWT authentication. Users log in to receive a personalised greeting fetched from a protected REST API.

**Stack:** Python + Flask (backend) · TypeScript + React (frontend)

---

## Architecture

```
Browser (React/Vite)
        │
        │  POST /api/login  →  JWT token
        │
        │  GET /api/hello
        │  Authorization: Bearer <token>
        ▼
Flask Backend
        │
        │  Auth.require_auth validates JWT, checks role
        │
        │  JSON: { "message": "Hello, Alice!", "status": "ok" }
        ▼
HelloController → Response
```

- The React frontend calls `POST /api/login` to obtain a JWT, stored in `localStorage`
- `GET /api/hello` requires a valid Bearer token with role `user` or `admin`
- Flask serves the personalised greeting from `HelloController`
- In development, Vite proxies `/api/*` requests to `localhost:5001`

---

## Prerequisites

**To run with Docker (recommended):**
- Docker Desktop 4.x+

**To run locally:**
- Python 3.11+
- Node.js 18+
- npm 9+

---

## Running with Docker

The fastest way to get the full stack running is with Docker Compose.

### 1. Clone the repository

```bash
git clone https://github.com/gxozer/datatools.git
cd datatools/projects/ai/hello_login_deploy
```

### 2. Start the stack

```bash
docker compose up -d
```

This will:
- Pull/build the Flask backend image (runs Alembic migrations on startup)
- Pull/build the React/nginx frontend image
- Start both services and wire them together

Open [http://localhost:3000](http://localhost:3000) in your browser.

### 3. Stop the stack

```bash
docker compose down       # keep the database volume
docker compose down -v    # also delete the database
```

### Environment variables (optional)

To use a custom JWT secret or enable real email sending, copy the example file and fill in your values:

```bash
cp backend/.env.example backend/.env
```

Then edit `backend/.env`:

```bash
JWT_SECRET=your-32-char-secret-here
MAIL_SERVER=smtp-relay.brevo.com
MAIL_PORT=587
MAIL_USE_TLS=true
MAIL_USERNAME=your-brevo-email@example.com
MAIL_PASSWORD=your-brevo-smtp-key
MAIL_DEFAULT_SENDER=your-brevo-email@example.com
```

By default `JWT_SECRET` uses a built-in development value and email sending is suppressed. The `backend/.env` file is optional — the stack starts without it.

---

## Setup (local development)

### 1. Clone the repository

```bash
git clone https://github.com/gxozer/datatools.git
cd datatools/projects/ai/beads/hello_login
```

### 2. Backend

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate       # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

Copy the example environment file and fill in your values:

```bash
cp .env.example .env
```

**Email (Brevo):** Password reset emails are sent via [Brevo](https://brevo.com) SMTP. Sign up for a free account (300 emails/day, no expiry), then:

1. Go to **Settings → SMTP & API → SMTP** and generate an SMTP key
2. Go to **Senders, Domains & Dedicated IPs** and verify your sender address
3. Fill in the `MAIL_*` variables in `.env`:

```
MAIL_SERVER=smtp-relay.brevo.com
MAIL_PORT=587
MAIL_USE_TLS=true
MAIL_USERNAME=your-brevo-login-email@example.com
MAIL_PASSWORD=your-brevo-smtp-key
MAIL_DEFAULT_SENDER=your-brevo-login-email@example.com
```

To skip email sending in development, set `MAIL_SUPPRESS_SEND=1` in `.env` instead.

Initialise the database:

```bash
alembic upgrade head
```

### 3. Frontend

```bash
cd ../frontend
npm install
```

---

## Running Locally

Open two terminals from the `hello_login/` directory:

**Terminal 1 — Backend:**

```bash
cd backend
source .venv/bin/activate
python run.py
# Flask running on http://localhost:5001
```

**Terminal 2 — Frontend:**

```bash
cd frontend
npm run dev
# Vite running on http://localhost:5173
```

Open [http://localhost:5173](http://localhost:5173) in your browser. The app will prompt you to log in. Once authenticated you will see a personalised greeting, e.g. **"Hello, Alice!"**.

---

## API Reference

| Method | Endpoint | Auth | Response |
|--------|----------|------|----------|
| POST | `/api/login` | None | `{"token": "<jwt>", "status": "ok"}` |
| GET | `/api/hello` | Bearer JWT (role: `user` or `admin`) | `{"message": "Hello, {name}!", "status": "ok"}` |
| GET | `/api/health` | None | `{"status": "ok"}` |

---

## Testing

See [TESTING.md](TESTING.md) for full instructions. Quick start:

```bash
# Backend tests (unit + integration)
backend/.venv/bin/python -m pytest tests/unit/ tests/integration/ -v

# Frontend tests (also run inside docker build automatically)
cd frontend && npm test

# Container structure tests (requires images to be built first)
make test-containers

# E2E tests against the containerized stack
make test-e2e-docker
```

### Container test infrastructure

Container specs live in `tests/container/` and use [container-structure-test](https://github.com/GoogleContainerTools/container-structure-test):

```bash
brew install container-structure-test   # one-time install
make test-backend                        # backend image specs
make test-frontend                       # frontend image specs
```

To verify the container test infrastructure is set up correctly:

```bash
bash tests/container/verify_setup.sh
```

### CI (GitHub Actions)

All tests run automatically on every push or pull request that touches files under `projects/ai/hello_login_deploy/`. The pipeline is defined in `.github/workflows/hello-login-ci.yml`.

#### Pipeline jobs

| Job | Trigger | What it runs |
|-----|---------|-------------|
| Backend unit + integration | every push/PR | `pytest tests/unit/ tests/integration/` |
| Backend container | every push/PR | `docker build` → container-structure-test |
| Frontend container | every push/PR | `docker build` (includes Vitest) → container-structure-test |
| E2E tests | after both container jobs pass | `docker compose up` → Playwright against the live stack |

#### Viewing results

1. Go to [github.com/gxozer/datatools/actions](https://github.com/gxozer/datatools/actions)
2. Click the workflow run to see all jobs
3. Click any job to see step-by-step logs
4. Failed steps are highlighted in red with the full error output

On a pull request, results appear directly on the PR page — all jobs must pass before merging.

#### Adding secrets

If the workflow needs credentials (e.g. for future AWS deployment steps), add them as GitHub secrets:

1. Go to **Settings → Secrets and variables → Actions**
2. Click **New repository secret**
3. Reference in the workflow as `${{ secrets.MY_SECRET }}`

Never hardcode credentials in the workflow file.

#### Testing the pipeline locally

Use [`act`](https://github.com/nektos/act) to run the workflow on your machine without pushing to GitHub:

```bash
brew install act   # one-time install
cd /path/to/datatools
act push --workflows .github/workflows/hello-login-ci.yml
```

`act` runs jobs inside Docker containers that mirror GitHub's runners. Some features (e.g. caching) behave slightly differently locally — treat it as a fast smoke test before pushing.

---

## Debugging

### Backend — PyCharm

1. Open `backend/` in PyCharm and set the interpreter to `backend/.venv`
2. Open `run.py`
3. Click in the gutter next to any line to set a breakpoint
4. Click **🐛 Debug** (or press Shift+F9) to start the server in debug mode
5. Trigger the endpoint from the browser — PyCharm pauses at your breakpoint with the full debugger UI (variables, call stack, step controls)

### Backend — Docker (remote-pdb)

`remote-pdb` is a step-through debugger that runs inside the container and accepts connections over TCP on port 4444.

**1. Add a breakpoint in code**

```python
import remote_pdb; remote_pdb.set_trace()
```

**2. Start the stack with the debug overlay**

```bash
docker compose -f docker-compose.yml -f docker-compose.override.yml up -d --build
```

**3. Trigger the request** (e.g. submit the signup form). The process will freeze waiting for a connection.

**4. Connect from a terminal**

```bash
nc localhost 4444
```

You land in a pdb prompt with the full call stack:

```
> /app/app/auth_controllers.py(45)signup()
-> data = request.get_json()
(Pdb)
```

**Useful commands**

| Command | Action |
|---------|--------|
| `n` | Next line (stay in current function) |
| `s` | Step into function call |
| `r` | Run until current function returns |
| `c` | Continue to next breakpoint |
| `p expr` | Print expression — `p data`, `p request.method` |
| `pp expr` | Pretty-print (dicts, lists) |
| `l` | Show surrounding source |
| `ll` | Show full current function |
| `w` | Call stack |
| `u` / `d` | Move up/down the call stack |
| `b 72` | Set breakpoint at line 72 |
| `q` | Quit (request fails with 500, server keeps running) |

> Port 4444 is only exposed when using the override file. Remove `set_trace()` calls before committing — they will block requests indefinitely.

### Backend — terminal (pdb)

Add `breakpoint()` anywhere in the code, then run the server normally:

```bash
python run.py
```

When execution reaches that line the terminal drops into a `pdb` prompt:

```
(Pdb) n       # next line
(Pdb) s       # step into
(Pdb) p expr  # print expression
(Pdb) c       # continue
(Pdb) q       # quit
```

---

## Inspecting the Database

### MySQL (Docker)

Connect to the MySQL container with an interactive shell:

```bash
docker compose exec mysql mysql -u hello -phello hello_login
```

Or run a one-liner without an interactive session:

```bash
docker compose exec mysql mysql -u hello -phello hello_login -e "SELECT * FROM users LIMIT 10;"
```

Useful SQL commands once connected:

```sql
SHOW TABLES;
DESCRIBE users;
SELECT * FROM users;
SELECT * FROM login_attempts;
SELECT * FROM password_reset_tokens;
EXIT;
```


---

## Deploying to Dev EC2 (cheap, ~$15-20/month)

A single EC2 t3.small running Docker Compose — backend, frontend, MySQL, and Caddy (HTTPS). No EKS, no RDS, no NAT Gateway. Good for iterating on the app without paying for the full EKS stack.

**How it compares to EKS staging:**

| | Dev EC2 | EKS staging |
|---|---|---|
| Cost | ~$15-20/month | ~$150/month |
| Startup time | ~3 min | ~20 min |
| Kubernetes | No (Docker Compose) | Yes |
| TLS | Caddy + Let's Encrypt | ACM + ALB |
| Database | MySQL in container | RDS |
| HA / autoscaling | No | Yes |

---

### Prerequisites

- AWS CLI configured and authenticated
- Terraform >= 1.7 — `brew install hashicorp/tap/terraform`
- Docker Desktop
- An EC2 key pair in us-west-2 — create it with the CLI (key pairs are region-specific):
  ```bash
  aws ec2 create-key-pair \
    --key-name hello-login-dev \
    --region us-west-2 \
    --query 'KeyMaterial' \
    --output text > ~/.ssh/hello-login-dev.pem
  chmod 400 ~/.ssh/hello-login-dev.pem
  ```
- A domain name with DNS you can edit

---

### Step 1 — Provision the EC2 instance

```bash
cp infra/dev/dev.tfvars.example infra/dev/dev.tfvars
```

Edit `infra/dev/dev.tfvars` — set your key pair name and restrict SSH to your IP:

```hcl
key_name        = "your-key-pair-name"
ssh_cidr_blocks = ["YOUR_IP/32"]   # find your IPv4: curl -4 ifconfig.me
```

Apply:

```bash
make dev-init
! terraform -chdir=infra/dev apply -var-file=dev.tfvars
```

Get the public IP:

```bash
! terraform -chdir=infra/dev output public_ip
```

---

### Step 2 — Create a DNS A record

Log in to your domain registrar (e.g. Namecheap, GoDaddy, Cloudflare, Route 53) and create an **A record**:

| Field | Value |
|---|---|
| Type | A |
| Name / Host | `dev` (or whatever subdomain you want — this becomes `dev.yourdomain.com`) |
| Value / Points to | `54.201.132.10` (the IP from Step 1) |
| TTL | 300 (or the lowest available) |

The exact UI varies by registrar but every DNS provider supports A records. If you're on Cloudflare, turn **proxy off** (grey cloud, DNS only) — Caddy needs a direct connection to obtain the Let's Encrypt certificate.

Verify propagation before continuing:

```bash
dig +short dev.yourdomain.com
# should return: 54.201.132.10
```

DNS typically propagates within 1–5 minutes with a low TTL. Caddy will automatically obtain the TLS certificate on first startup once the DNS record resolves correctly.

---

### Step 3 — Configure secrets

```bash
cp .env.ec2.example .env.ec2
```

Edit `.env.ec2` — at minimum, set these three values:

```bash
DEV_DOMAIN=dev.yourdomain.com
FRONTEND_URL=https://dev.yourdomain.com
CORS_ORIGINS=https://dev.yourdomain.com
JWT_SECRET=<run: python3 -c "import secrets; print(secrets.token_hex(32))">
```

**To enable real email sending** (signup verification, password reset), fill in your Brevo SMTP credentials in `.env.ec2`:

```bash
MAIL_SUPPRESS_SEND=0
MAIL_USERNAME=your-brevo-login@example.com
MAIL_PASSWORD=your-brevo-smtp-key
MAIL_DEFAULT_SENDER=your-brevo-login@example.com
```

To get Brevo credentials: log in to [brevo.com](https://brevo.com) → **Settings → SMTP & API → SMTP** → generate an SMTP key. Also verify your sender address under **Senders, Domains & Dedicated IPs**.

If you don't have Brevo set up yet, leave `MAIL_SUPPRESS_SEND=1` for now — signup and login still work, emails are just silently dropped.

---

### Step 4 — Build and push Docker images

First, ensure the ECR repositories exist (they are deleted when the EKS staging environment is torn down):

```bash
aws ecr create-repository --repository-name hello-login-backend --region us-west-2
aws ecr create-repository --repository-name hello-login-frontend --region us-west-2
```

These commands are safe to run even if the repositories already exist — they will return an error that can be ignored.

```bash
# Authenticate Docker to ECR
aws ecr get-login-password --region us-west-2 | \
  docker login --username AWS --password-stdin \
  277070500859.dkr.ecr.us-west-2.amazonaws.com

# Build for x86_64 (EC2 is amd64, not Apple Silicon arm64)
docker build --platform linux/amd64 -t hello-login-backend ./backend
docker tag hello-login-backend:latest \
  277070500859.dkr.ecr.us-west-2.amazonaws.com/hello-login-backend:latest
docker push 277070500859.dkr.ecr.us-west-2.amazonaws.com/hello-login-backend:latest

docker build --platform linux/amd64 -t hello-login-frontend ./frontend
docker tag hello-login-frontend:latest \
  277070500859.dkr.ecr.us-west-2.amazonaws.com/hello-login-frontend:latest
docker push 277070500859.dkr.ecr.us-west-2.amazonaws.com/hello-login-frontend:latest
```

---

### Step 5 — Deploy

```bash
make dev-deploy
```

This copies `docker-compose.ec2.yml`, `Caddyfile`, and `.env.ec2` to the EC2 instance, logs Docker into ECR, pulls the images, and starts the stack.

Visit `https://dev.yourdomain.com` — Caddy obtains the TLS certificate automatically on first request (takes a few seconds).

---

### Updating the app

After pushing new images to ECR:

```bash
make dev-deploy
```

The command re-pulls images and restarts containers. The MySQL data volume persists across restarts.

---

### Useful commands

```bash
make dev-ssh      # open an SSH session on the EC2 instance
make dev-logs     # tail Docker Compose logs (all services)
make dev-down     # stop the stack (instance keeps running, no cost saved)
```

To save cost when not using dev, stop the instance from the AWS console (EC2 → Instances → Stop). The Elastic IP stays assigned so DNS doesn't break.

---

### Teardown

```bash
make dev-down
! terraform -chdir=infra/dev destroy -var-file=dev.tfvars
```

This deletes the EC2 instance, Elastic IP, security group, and IAM role. The ECR images and MySQL data (stored in an EBS volume on the instance) are also deleted.

---

### Alternative: k3s on the same EC2

If you want to test the Kubernetes manifests on the dev instance (without paying for EKS), install k3s and use the dev Kustomize overlay:

```bash
# On the EC2 instance
curl -sfL https://get.k3s.io | sh -
# Copy /etc/rancher/k3s/k3s.yaml to ~/.kube/config locally (update server IP)

# Locally — populate secrets file
cp k8s/overlays/dev/.env.dev.example k8s/overlays/dev/.env.dev
# edit .env.dev: set JWT_SECRET, update MAIL_* if needed

# Update the EC2 IP in the dev overlay configmap patch
# Edit k8s/overlays/dev/configmap-patch.yaml: replace YOUR_EC2_IP

make dev-k8s-apply
```

This uses Traefik (bundled with k3s) instead of the ALB, and runs MySQL as a pod instead of RDS.

---

## Deploying to Amazon EKS

Infrastructure is managed with Terraform. Full technical details are in the `docs/` folder:

- [docs/PRD_terraform_iac.md](docs/PRD_terraform_iac.md) — what to build and why
- [docs/TDS_terraform_iac.md](docs/TDS_terraform_iac.md) — technical design and module breakdown
- [docs/GUIDE_terraform_iac.md](docs/GUIDE_terraform_iac.md) — line-by-line explanation of every Terraform file
- [docs/TEST_PLAN_terraform_iac.md](docs/TEST_PLAN_terraform_iac.md) — test cases and verification steps

---

### Prerequisites

- AWS CLI configured and authenticated
- Terraform >= 1.7 — `brew install hashicorp/tap/terraform`
- kubectl — `brew install kubectl`
- Docker Desktop

---

### Phase 1 — Provision infrastructure with Terraform

```bash
cd terraform
terraform init
terraform workspace new staging
terraform plan -var-file=staging.tfvars
terraform apply -var-file=staging.tfvars
```

This creates (in ~20 minutes): VPC, EKS cluster, RDS MySQL, ECR repos, Secrets Manager secret, IAM roles, ALB controller, External Secrets Operator.

---

### Phase 2 — Configure kubectl

```bash
aws eks update-kubeconfig --name hello-login-staging --region us-west-2
```

---

### Phase 3 — Build and push Docker images

```bash
# Authenticate Docker to ECR
aws ecr get-login-password --region us-west-2 | \
  docker login --username AWS --password-stdin \
  277070500859.dkr.ecr.us-west-2.amazonaws.com

# Build for x86_64 (EKS nodes run on amd64, not Apple Silicon arm64)
docker build --platform linux/amd64 -t hello-login-backend ./backend
docker tag hello-login-backend:latest \
  277070500859.dkr.ecr.us-west-2.amazonaws.com/hello-login-backend:latest
docker push 277070500859.dkr.ecr.us-west-2.amazonaws.com/hello-login-backend:latest

docker build --platform linux/amd64 -t hello-login-frontend ./frontend
docker tag hello-login-frontend:latest \
  277070500859.dkr.ecr.us-west-2.amazonaws.com/hello-login-frontend:latest
docker push 277070500859.dkr.ecr.us-west-2.amazonaws.com/hello-login-frontend:latest
```

---

### Phase 4 — Populate secrets

Get the RDS password from Terraform state, then populate the Secrets Manager secret:

```bash
# Get the generated RDS password
terraform -chdir=terraform state pull | \
  python3 -c "import sys,json; s=json.load(sys.stdin); [print(r['instances'][0]['attributes']['result']) for r in s['resources'] if r.get('module')=='module.rds' and r['type']=='random_password']"

# Get the RDS endpoint
terraform -chdir=terraform output db_endpoint

# Write secrets (substitute real values)
aws secretsmanager put-secret-value \
  --secret-id hello-login/staging \
  --region us-west-2 \
  --secret-string '{
    "DATABASE_URL": "mysql+pymysql://admin:<password>@<db-endpoint>/hello_login",
    "JWT_SECRET": "<run: python3 -c \"import secrets; print(secrets.token_hex(32))\">",
    "MAIL_PASSWORD": "<brevo-smtp-key>",
    "MAIL_USERNAME": "<brevo-login-email>",
    "MAIL_DEFAULT_SENDER": "<sender-email>"
  }'
```

---

### Phase 5 — Deploy the app

```bash
kubectl create namespace hello-login-staging
kubectl apply -k k8s/overlays/staging/
```

Verify:

```bash
kubectl get pods -n hello-login-staging        # all pods Running
kubectl get externalsecret -n hello-login-staging  # SecretSynced = True
kubectl get ingress -n hello-login-staging     # shows ALB address
```

The ALB address in the ingress output is the public URL. It takes 2–3 minutes to become active after first deploy.

---

### Teardown (cost saving)

**Always delete Kubernetes resources first** — the ALB controller must remove the Application Load Balancer before Terraform can delete the VPC. If you skip this step the VPC deletion will fail.

```bash
# Step 1 — remove all k8s resources (deletes the ALB)
kubectl delete -k k8s/overlays/staging/

# Wait ~60 seconds for the ALB to be fully removed, then:

# Step 2 — destroy all AWS infrastructure
terraform -chdir=terraform destroy -var-file=staging.tfvars
```

---

### Terraform CI

Four checks run automatically on every pull request that touches `terraform/**`:

| Check | Tool | What it does |
|-------|------|-------------|
| Validate | `terraform validate` | Syntax and schema |
| Lint | tflint | Style and deprecated patterns |
| Security | checkov | CIS benchmark compliance |
| Unit tests | `terraform test` | Mock-based module tests |

Pipeline: `.github/workflows/terraform-ci.yml`

---

### Terratest integration tests

Terratest provisions **real** AWS infrastructure, asserts on it, then destroys it. Run manually — each run costs ~$5–10 and takes ~30 minutes.

```bash
# Prerequisites: Go 1.21+, AWS credentials, staging environment destroyed
cd terraform/test
go mod tidy          # first time only
go test -v -timeout 60m -run TestTerraformIntegration
```

The test creates a randomly-named workspace (`tt<id>`), applies the full stack, runs one assertion sub-test per module (networking, ECR, RDS, EKS, secrets), then destroys everything on exit.

**Constraint:** Destroy the staging environment first — the GitHub OIDC provider is a global IAM resource (one per AWS account).

GitHub Actions workflow (manual trigger): `.github/workflows/terratest.yml`
Requires GitHub secret: `TERRATEST_AWS_ROLE_ARN`

---

### Full reference

See [docs/GUIDE_terraform_iac.md](docs/GUIDE_terraform_iac.md) for:
- Explanation of every Terraform file and resource
- How the dependency graph and parallel execution work
- Common commands and troubleshooting

---

## Project Structure

```
hello_login_deploy/
├── backend/
│   ├── app/
│   │   ├── __init__.py           # Package entry point
│   │   ├── factory.py            # Flask app factory (create_app)
│   │   ├── auth.py               # Auth class: generate_token, require_auth
│   │   ├── auth_controllers.py   # LoginController, SignupController, etc.
│   │   ├── controllers.py        # HelloController, HealthController
│   │   ├── models.py             # User, LoginAttempt, PasswordResetToken
│   │   └── routes.py             # API Blueprint and URL rules
│   ├── Dockerfile            # Backend container image
│   ├── entrypoint.sh         # Runs migrations then starts Flask
│   ├── run.py                # Entry point
│   ├── requirements.txt      # Runtime dependencies
│   └── requirements-dev.txt  # Dev/test dependencies
├── frontend/
│   ├── src/
│   │   ├── api/
│   │   │   └── ApiClient.ts      # HTTP client class
│   │   ├── components/
│   │   │   └── HelloMessage.tsx  # Presentational component
│   │   ├── test/                 # Vitest unit tests
│   │   └── App.tsx               # Root component
│   ├── Dockerfile            # Multi-stage: build (Vitest) + nginx serve
│   ├── nginx.conf.template   # nginx config with /api proxy + SPA routing
│   └── vite.config.ts        # Vite config with /api proxy (dev only)
├── tests/
│   ├── conftest.py           # Shared pytest fixtures
│   ├── unit/                 # Controller unit tests
│   ├── integration/          # API integration tests
│   ├── e2e/                  # Playwright end-to-end tests
│   └── container/            # container-structure-test specs
│       ├── backend.yaml
│       ├── frontend.yaml
│       └── verify_setup.sh
│
│   # Two separate Terraform roots — kept apart because they manage completely
│   # different infrastructure with different lifecycles and no shared state:
│   #
│   #   terraform/   — EKS stack (staging + production). Full cloud setup:
│   #                  VPC, EKS, RDS, ECR, IAM, ALB, Secrets Manager.
│   #                  Long-lived; uses workspaces for staging vs production.
│   #
│   #   infra/dev/   — Dev EC2 stack (single instance + Docker Compose).
│   #                  Throwaway; destroyed when not in use to save cost.
│   #                  Mixing them would couple dev teardown to prod infrastructure.
│
├── terraform/
│   ├── modules/              # networking, eks, rds, ecr, iam, secrets, helm-addons
│   ├── test/                 # Terratest integration tests (Go)
│   │   ├── go.mod
│   │   ├── integration_test.go   # apply → assert all modules → destroy
│   │   ├── networking_test.go
│   │   ├── ecr_test.go
│   │   ├── rds_test.go
│   │   ├── eks_test.go
│   │   └── secrets_test.go
│   ├── tests/                # terraform test mock-based unit tests
│   ├── staging.tfvars
│   └── production.tfvars
├── infra/
│   └── dev/                  # Terraform for dev EC2 (single instance, throwaway)
│       ├── main.tf           # EC2 instance, security group, EIP, IAM role
│       ├── variables.tf
│       ├── outputs.tf
│       ├── versions.tf
│       └── dev.tfvars.example
├── k8s/
│   ├── base/                 # Shared Kubernetes manifests
│   └── overlays/
│       ├── staging/          # EKS staging (ECR images, ESO secrets, ALB ingress)
│       ├── production/       # EKS production
│       └── dev/              # k3s on dev EC2 (MySQL pod, Traefik ingress, .env.dev secrets)
├── docker-compose.yml        # Local development (builds from source)
├── docker-compose.override.yml  # Debug overlay: exposes remote-pdb on port 4444
├── docker-compose.ec2.yml    # Dev EC2 deployment (pulls from ECR, Caddy TLS)
├── Caddyfile                 # Caddy reverse proxy config (HTTPS via Let's Encrypt)
├── .env.ec2.example          # Secret template for dev EC2 deployment
├── Makefile                  # Build, test, compose, and dev EC2 targets
├── pytest.ini                # Pytest configuration
├── TESTING.md                # Full testing instructions
└── README.md                 # This file

# CI pipelines live at the repo root (not inside this directory):
# .github/workflows/hello-login-ci.yml   — app CI (every push/PR)
# .github/workflows/terraform-ci.yml     — terraform checks (every PR touching terraform/)
# .github/workflows/terratest.yml        — Terratest integration (manual only)
```
