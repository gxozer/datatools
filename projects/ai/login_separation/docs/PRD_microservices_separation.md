# PRD: Separate Login and Hello into Independent Microservices

**Version:** 1.0
**Date:** 2026-06-01
**Status:** Draft
**Jira:** PR-124
**Epic:** PR-123
**Related:** TDS ticket (PR-125), TDS_microservices_separation.md

---

## 1. Purpose

Split the current Flask monolith (`backend/`) into two independently deployable, independently scalable microservices — a **login-service** and a **hello-service** — so that authentication infrastructure and application features can evolve, deploy, and scale on separate tracks.

---

## 2. Background

The hello-login application currently runs as a single Flask process (`backend/`) serving all routes under `/api`:

- **Authentication routes**: `/api/signup`, `/api/login`, `/api/logout`, `/api/password-reset/*`
- **Application routes**: `/api/hello`
- **Infrastructure**: `/api/health`

These two concerns have fundamentally different characteristics:

| Concern | Authentication | Hello feature |
|---------|---------------|---------------|
| Volatility | Low — auth changes rarely | High — features evolve often |
| Scaling pressure | Spiky (login bursts) | Steady (active sessions) |
| External dependencies | SMTP (email), MySQL | JWT validation only |
| State | Stateful (DB: users, tokens) | Stateless (reads JWT payload) |
| Risk surface | High (credentials, tokens) | Low (read-only greeting) |

Running both in a single process creates unnecessary coupling: a crash in the mail subsystem can take down the greeting endpoint; a spike in login traffic competes with feature traffic for the same CPU/memory budget; security patches to auth code require redeploying the feature, and vice versa.

---

## 3. Goals

- Authentication and application features are deployed as separate services with independent versioning and rollout.
- Either service can be scaled horizontally without scaling the other.
- A bug or crash in one service does not take down the other.
- The split is transparent to end users — the same URLs work before and after.
- No new external dependencies are introduced (no service mesh, no API gateway product).
- The frontend codebase requires no changes.

---

## 4. Scope

### In scope

| Item | Notes |
|------|-------|
| New `hello-service` Python/Flask service | Contains `/api/hello` and `/api/health`; stateless |
| Rename `backend/` → `login-service/` | Retains all auth routes, DB, migrations, SMTP |
| Remove `/api/hello` from login-service | Clean service boundary |
| Frontend nginx routing update | Route `/api/hello` to hello-service; all other `/api/*` to login-service |
| docker-compose.yml update | Add hello-service container for local development |
| docker-compose.ec2.yml update | Add hello-service for EC2 single-instance deployment |
| Kubernetes manifests update | New hello-service Deployment + Service + Ingress rule |
| ECR repository addition | New `hello-login-hello` image repository |
| GitHub Actions IAM policy update | Allow push to the new ECR repo |
| Unit and integration tests for hello-service | Mirror login-service test structure |

### Out of scope

- Shared authentication library / SDK (services share JWT_SECRET by convention; no shared package)
- API gateway product or service mesh (nginx routing is sufficient)
- Token introspection endpoint (hello-service validates JWT signature only; see Section 6)
- Changes to the frontend React application
- Changes to Kubernetes namespace, Secrets Manager secrets, or SMTP configuration
- Split into more than two services

---

## 5. User and Operator Impact

### End users

- No visible change. The same URLs (`/login`, `/signup`, `/hello`) continue to work.
- If login-service is down, users cannot authenticate but the hello page continues to serve already-authenticated requests normally (JWT validation is local, no login-service call).
- If hello-service is down, users can still log in and out; only the `/hello` page is unavailable.

### Operators / developers

- Two Docker images to build and push on each release (login and hello) instead of one.
- Two health checks to monitor (`/api/health` on each service).
- `hello-service` deploys are smaller, faster, and lower-risk (no DB migrations, no mail config).
- `login-service` retains all database migrations; schema management is unchanged.
- Local development: `docker compose up` starts three service containers (login, hello, frontend).

---

## 6. Security Requirements

- Both services validate JWTs using the same `JWT_SECRET`. The secret is injected via environment variable in both deployments; it is never hardcoded.
- hello-service validates JWT signature and expiry only. It does **not** check the denied-tokens blacklist (requires DB access to login-service's database). Consequence: after a user logs out, their token remains valid for `/api/hello` until it expires (maximum 24 hours). This is an accepted trade-off — token revocation is an authentication concern, and the short expiry window limits exposure.
- hello-service runs with no database credentials. Its attack surface is limited to JWT parsing and a single HTTP handler.
- Network policy (future): hello-service should only be reachable from the frontend nginx, not directly from the internet. This is enforced at the Ingress layer today (no direct hello-service Ingress rule) and can be tightened with Kubernetes NetworkPolicy later.

---

## 7. Non-Functional Requirements

- **Zero downtime migration**: existing deployments remain functional during the cutover; the migration is additive (add hello-service, then remove `/api/hello` from login-service, update nginx last).
- **Independent scaling**: hello-service and login-service have separate HPA configurations.
- **Lightweight hello-service**: no database driver, no mail library, no migration runner — cold-start time and image size are significantly smaller than the login-service.
- **Consistent health endpoints**: both services expose `GET /api/health` returning `{"status": "ok"}` for ALB and Kubernetes health checks.

---

## 8. Success Criteria

- [ ] `GET /api/hello` with a valid JWT returns `{"message": "Hello, <name>!", "status": "ok"}` from hello-service.
- [ ] `POST /api/login` returns a JWT from login-service; that JWT works immediately on hello-service.
- [ ] Stopping hello-service does not affect login/signup/logout flows.
- [ ] Stopping login-service does not affect `/api/hello` for users who already hold a valid JWT.
- [ ] `docker compose up` starts all three services; `make test-e2e-docker` passes end-to-end.
- [ ] Both services have passing unit and integration test suites.
- [ ] Both services have independent Docker images in ECR.
- [ ] No frontend code changes required.
