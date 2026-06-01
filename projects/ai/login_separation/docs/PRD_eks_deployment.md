# Product Requirements Document — Amazon EKS Deployment

**Version:** 1.0
**Date:** 2026-05-10
**Status:** Draft
**Jira:** PR-56
**Related:** [TDS.md](TDS.md), [README.md](../README.md), [PRD_mysql_migration.md](PRD_mysql_migration.md)

---

## 1. Purpose

Deploy the hello-login stack to Amazon EKS (Elastic Kubernetes Service) to enable production-grade hosting with zero-downtime deployments, horizontal auto-scaling, self-healing, and multi-environment support (staging, production).

---

## 2. Background

The stack currently runs locally via `docker compose up`. It consists of two containers — a Flask backend and a React/nginx frontend — backed by MySQL on Amazon RDS (PR-34). The MySQL migration removed the last local-filesystem dependency, making the stack ready for cloud deployment.

Kubernetes was chosen over Elastic Beanstalk for long-term flexibility (multi-service, multi-environment, rolling deployments, namespace isolation) and over ECS Fargate for ecosystem breadth (Helm, Argo CD, native HPA). See PR-11 for the full evaluation.

---

## 3. Scope

### In scope
- EKS cluster provisioning (managed node group, single region)
- Kubernetes manifests for backend and frontend (Deployment, Service, HorizontalPodAutoscaler)
- AWS ALB Ingress Controller for HTTP/HTTPS routing
- TLS termination via AWS Certificate Manager
- RDS MySQL connection from EKS pods via Secrets Manager
- Alembic migration job (Kubernetes Job, runs on deploy)
- Staging and production namespaces
- CI/CD pipeline extension: build → push to ECR → deploy to EKS

### Out of scope
- Multi-region deployment
- Service mesh (Istio, Linkerd)
- GitOps (Argo CD, Flux) — can be added later
- Database read replicas

---

## 4. Requirements

### 4.1 Infrastructure

- **EKS cluster**: Kubernetes 1.29+, managed node group, `t3.small` instances (2 nodes minimum)
- **Region**: `us-west-2` (matches existing RDS)
- **ECR repositories**: one per service (`hello-login-backend`, `hello-login-frontend`)
- **RDS**: existing MySQL instance; EKS pods connect via VPC peering or same VPC

### 4.2 Kubernetes Resources

| Resource | Purpose |
|----------|---------|
| `Deployment` (backend) | Runs Flask; 2 replicas minimum |
| `Deployment` (frontend) | Runs nginx/React; 2 replicas minimum |
| `Service` (backend) | ClusterIP — internal only |
| `Service` (frontend) | ClusterIP — exposed via Ingress |
| `Ingress` | ALB routes `/api/*` to backend, `/*` to frontend |
| `HorizontalPodAutoscaler` | Scale on CPU >70%, max 6 replicas |
| `Job` (migration) | Runs `alembic upgrade head` on each deploy |
| `Secret` | DATABASE_URL, JWT_SECRET, MAIL_* from Secrets Manager |
| `ConfigMap` | CORS_ORIGINS, FRONTEND_URL, non-sensitive env vars |

### 4.3 Namespaces

- `hello-login-staging` — staging environment
- `hello-login-production` — production environment

### 4.4 Networking

- ALB Ingress Controller (AWS Load Balancer Controller)
- TLS via ACM certificate on the ALB
- Backend pods not publicly reachable — only via Ingress
- RDS Security Group allows inbound 3306 from EKS node security group

### 4.5 Secrets Management

- All secrets stored in AWS Secrets Manager
- Injected into pods via External Secrets Operator (or manual `kubectl create secret` for initial setup)
- `DATABASE_URL`, `JWT_SECRET`, `MAIL_PASSWORD` must never appear in manifests or CI logs

### 4.6 CI/CD

Extend the existing GitHub Actions pipeline:

1. Build and push Docker images to ECR on merge to `master`
2. Run `alembic upgrade head` via a Kubernetes Job
3. Roll out new Deployment images (`kubectl set image` or `helm upgrade`)
4. Verify rollout (`kubectl rollout status`)

### 4.7 Rollback

- `kubectl rollout undo deployment/backend` restores the previous image
- RDS point-in-time recovery for database rollback (automated backups enabled)

---

## 5. Non-Functional Requirements

### 5.1 Availability
- 2 replicas per service; pods spread across availability zones via `topologySpreadConstraints`
- ALB health checks on `/api/health` (backend) and `/` (frontend)

### 5.2 Zero-downtime Deploys
- `RollingUpdate` strategy: `maxSurge: 1`, `maxUnavailable: 0`
- Migration Job completes before Deployment rollout begins

### 5.3 Security
- No `latest` image tags in production — always use the Git SHA digest
- RBAC: CI service account has `update deployments` only, not cluster-admin
- Pods run as non-root (`runAsNonRoot: true`)
- Network Policy restricts backend-to-RDS traffic

### 5.4 Cost
- `t3.small` nodes: ~$30/month for 2 nodes
- EKS control plane: $0.10/hr (~$73/month)
- RDS `db.t3.micro`: ~$15/month
- ALB: ~$20/month

---

## 6. Out of Scope

- Multi-region failover
- Kubernetes service mesh
- GitOps controllers (Argo CD, Flux)
- Monitoring/alerting stack (Prometheus, Grafana) — tracked separately

---

## 7. Success Criteria

- [ ] `docker compose up` continues to work unchanged for local development
- [ ] `kubectl apply` deploys both services to staging with no manual steps beyond initial cluster setup
- [ ] ALB serves the app over HTTPS at the staging domain
- [ ] All unit, integration, and E2E tests pass in CI after deploy
- [ ] A new push to `master` triggers an automatic rolling deploy — zero downtime
- [ ] Rollback to previous version completes in under 2 minutes
- [ ] No secrets appear in manifests, CI logs, or container env dumps
