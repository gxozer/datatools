# PRD: Terraform IaC for hello-login Infrastructure

**Version:** 1.0
**Date:** 2026-05-13
**Status:** Draft
**Jira:** PR-89
**Epic:** PR-88
**Related:** TDS ticket (PR-90), PRD_eks_deployment.md, TDS_eks_deployment.md

---

## 1. Purpose

Codify all AWS infrastructure for the hello-login application in Terraform so that any environment can be created or destroyed with a single `terraform apply` / `terraform destroy`, without manual AWS CLI or eksctl steps.

---

## 2. Background

The hello-login stack (Flask backend + React/nginx frontend + MySQL on RDS) currently runs on Amazon EKS. All infrastructure was provisioned manually:

- EKS cluster via `eksctl create cluster`
- RDS instance via `aws rds create-db-instance`
- ECR repos via `aws ecr create-repository`
- Security groups, subnet groups, Secrets Manager secrets via raw `aws` CLI
- ALB controller and External Secrets Operator installed via `helm install`

There is no Terraform in the repository. Every operation is documented in the README as a sequence of shell commands. This approach has three problems:

1. **Not reproducible** — recreating the environment requires following every step correctly by hand.
2. **Not auditable** — no version history of infra changes; drift is undetectable.
3. **Environment parity risk** — staging and production can diverge silently.

---

## 3. Goals

- All AWS resources for hello-login are defined in Terraform and checked into the repo.
- A new environment (staging or production) can be bootstrapped from scratch with `terraform init && terraform apply -var-file=<env>.tfvars`.
- Infra changes are reviewed via pull request before they are applied.
- Staging environment can be torn down and recreated cheaply (e.g., overnight destroy to save cost).
- State is stored remotely in S3 with DynamoDB locking so multiple operators can collaborate safely.

---

## 4. Scope

### In scope

| Resource | Notes |
| --- | --- |
| VPC + subnets + NAT gateway | Dedicated VPC per environment |
| EKS cluster + managed node group | t3.small, 2–6 nodes, same spec as current |
| OIDC provider | Enables IRSA for pod-level IAM |
| ECR repositories | hello-login-backend, hello-login-frontend |
| RDS MySQL 8.0 | db.t3.micro, same spec as current |
| AWS Secrets Manager secrets | hello-login/staging, hello-login/production |
| IAM roles (IRSA) | ALB controller, External Secrets Operator, GitHub Actions OIDC |
| Helm add-ons | AWS Load Balancer Controller, External Secrets Operator |
| Terraform remote state | S3 bucket + DynamoDB lock table |

### Out of scope

- Kubernetes manifests (already in `k8s/` via Kustomize — not changing)
- Application code or CI/CD pipeline changes
- DNS / Route 53 records (manual for now)
- ACM certificate provisioning (manual for now)
- Monitoring / alerting stack

---

## 5. Environment Requirements

Two environments must be supported from the same Terraform code, differing only in variable values:

| Parameter | staging | production |
| --- | --- | --- |
| Environment name | `staging` | `production` |
| EKS node type | t3.small | t3.small |
| EKS min nodes | 2 | 2 |
| EKS max nodes | 6 | 6 |
| RDS instance class | db.t3.micro | db.t3.micro |
| Multi-AZ RDS | No | No (can upgrade later) |
| Deletion protection (RDS) | Off | On |
| Secrets Manager path | hello-login/staging | hello-login/production |

---

## 6. Non-Functional Requirements

- **Idempotent:** repeated `terraform apply` with no changes produces no changes.
- **Least-privilege IAM:** each IRSA role has only the permissions it needs.
- **No secrets in code:** Terraform variables for sensitive values; secrets populated out-of-band (not in tfvars committed to git).
- **Cost visibility:** cost estimates provided in TDS before production apply.
- **Destroy safety:** production resources have `prevent_destroy = true` where appropriate (RDS).

---

## 7. Success Criteria

- [ ] A fresh staging environment can be created end-to-end with `terraform apply` — no manual AWS CLI steps.
- [ ] A fresh production environment can be created end-to-end with `terraform apply`.
- [ ] `terraform destroy` on staging completes cleanly (useful for cost management).
- [ ] All existing EKS / RDS / ECR resources match what Terraform would produce (import or recreate verified).
- [ ] State stored in S3; DynamoDB lock prevents concurrent applies.
- [ ] No AWS credentials or secret values committed to the repository.
