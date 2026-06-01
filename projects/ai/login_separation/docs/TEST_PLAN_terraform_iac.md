# Test Plan: Terraform IaC for hello-login Infrastructure

**Version:** 1.0
**Date:** 2026-05-14
**Jira:** PR-93
**Epic:** PR-88
**Related:** PRD_terraform_iac.md, TDS_terraform_iac.md

---

## 1. Objectives

- Verify all Terraform modules provision the expected AWS resources
- Confirm staging can be created and destroyed without errors
- Confirm production safety guards (deletion protection) are in place
- Confirm no secrets or credentials are committed to the repository

---

## 2. Prerequisites

| Prerequisite | Notes |
| --- | --- |
| AWS credentials configured | `aws configure` or environment variables; must have sufficient IAM permissions |
| Terraform >= 1.7 installed | `terraform version` |
| tflint installed | `tflint --version` |
| checkov installed | `pip install checkov` |
| kubectl configured | Needed for post-apply EKS verification |
| S3 backend (optional) | Only needed for TC-05 onwards. For TC-01 to TC-04, local state is fine. To enable: uncomment `backend.tf` and create the S3 bucket (see TDS Section 3) |
| `staging.tfvars` values confirmed | VPC CIDR, node type, etc. |

---

## 3. Test Cases

### TC-01: Static validation (no AWS required)

**Command:**
```bash
cd terraform
terraform init      # backend is commented out by default — no S3 bucket needed
terraform validate
```

**Pass criteria:** exits 0, no errors output.

---

### TC-02: Linting

**Command:**
```bash
tflint --init && tflint --recursive
```

**Pass criteria:** exits 0, no errors (warnings acceptable).

---

### TC-03: Security scan

**Command:**
```bash
checkov -d terraform --framework terraform
```

**Pass criteria:** no FAILED checks other than documented skips (CKV_AWS_7, CKV_AWS_144, CKV_AWS_338).

---

### TC-04: Unit tests (mock providers)

**Command:**
```bash
cd terraform
terraform init -backend=false
terraform test
```

**Pass criteria:** all 23 test cases pass, 0 failures.

---

### TC-05: Terraform plan — staging

**Command:**
```bash
cd terraform
terraform init
terraform workspace new staging   # or: terraform workspace select staging
terraform plan -var-file=staging.tfvars -out=staging.plan
```

**Pass criteria:**
- Plan completes with no errors
- Resource count matches expected (review output for VPC, 6 subnets, NAT GW, EKS cluster, node group, 4 addons, OIDC provider, 2 ECR repos, RDS instance, SM secret, 3 IAM roles, 2 Helm releases)
- No unexpected destroy or replace actions on a fresh workspace

---

### TC-06: Full apply — staging

**Command:**
```bash
terraform apply staging.plan
```

**Post-apply checks:**

| Check | Command | Expected |
| --- | --- | --- |
| EKS cluster active | `aws eks describe-cluster --name hello-login-staging --query 'cluster.status'` | `"ACTIVE"` |
| Nodes ready | `kubectl get nodes` | All nodes in `Ready` state |
| ECR repos exist | `aws ecr describe-repositories --query 'repositories[].repositoryName'` | `hello-login-backend`, `hello-login-frontend` |
| RDS available | `aws rds describe-db-instances --db-instance-identifier hello-login-staging --query 'DBInstances[0].DBInstanceStatus'` | `"available"` |
| RDS deletion protection | `aws rds describe-db-instances --db-instance-identifier hello-login-staging --query 'DBInstances[0].DeletionProtection'` | `false` (staging) |
| Secret exists | `aws secretsmanager describe-secret --secret-id hello-login/staging --query 'Name'` | `"hello-login/staging"` |
| ALB controller running | `kubectl get pods -n kube-system -l app.kubernetes.io/name=aws-load-balancer-controller` | Pod(s) in `Running` state |
| ESO running | `kubectl get pods -n external-secrets -l app.kubernetes.io/name=external-secrets` | Pod(s) in `Running` state |

**Pass criteria:** all post-apply checks pass.

---

### TC-07: App deploy on staging

Populate the Secrets Manager secret with test values, then deploy via Kustomize:

```bash
aws secretsmanager put-secret-value \
  --secret-id hello-login/staging \
  --secret-string '{"DATABASE_URL":"mysql+pymysql://admin:<pass>@<rds-endpoint>/hello_login","JWT_SECRET":"test-secret","MAIL_PASSWORD":"","MAIL_USERNAME":"","MAIL_DEFAULT_SENDER":""}'

kubectl apply -k k8s/overlays/staging
kubectl rollout status deployment/backend -n hello-login-staging
kubectl rollout status deployment/frontend -n hello-login-staging
```

**Pass criteria:** both deployments reach `Available`, ExternalSecret syncs successfully (`kubectl get externalsecret -n hello-login-staging`).

---

### TC-08: Idempotency check

With no changes to `.tfvars` or module files:

```bash
terraform plan -var-file=staging.tfvars
```

**Pass criteria:** `No changes. Your infrastructure matches the configuration.`

---

### TC-09: Destroy — staging

**IMPORTANT — delete Kubernetes resources first.**
The ALB (Application Load Balancer) created by the Kubernetes Ingress is managed
by the ALB controller running inside EKS. If `terraform destroy` is run while the
ALB still exists, AWS will refuse to delete the public subnets and Internet Gateway
that the ALB depends on, causing the destroy to fail.

```bash
# Step 1 — delete all Kubernetes resources so the ALB controller removes the ALB
kubectl delete -k k8s/overlays/staging/

# Wait ~60 seconds for the ALB to be fully deleted in AWS, then:

# Step 2 — destroy all Terraform-managed infrastructure
terraform -chdir=terraform destroy -var-file=staging.tfvars
```

If destroy was already run without Step 1 and the ALB is orphaned, delete it manually:

```bash
# Find the orphaned ALB
aws elbv2 describe-load-balancers --region us-west-2 \
  --query 'LoadBalancers[].{Name:LoadBalancerName,ARN:LoadBalancerArn}' \
  --output table

# Delete it
aws elbv2 delete-load-balancer \
  --region us-west-2 \
  --load-balancer-arn <arn-from-above>

# Wait ~30s, then re-run destroy
terraform -chdir=terraform destroy -var-file=staging.tfvars
```

**Pass criteria:**
- Exits 0 with no errors
- All resources removed (EKS cluster, RDS instance, VPC, ECR repos, IAM roles, SM secret)
- `aws eks list-clusters` no longer shows `hello-login-staging`

---

### TC-10: Production safety check (plan only — do not apply)

```bash
terraform workspace new production
terraform plan -var-file=production.tfvars
```

**Pass criteria:**
- Plan shows `deletion_protection = true` on the RDS instance
- Plan shows `rds_prevent_destroy = true` reflected in resource config
- No accidental staging resources included

---

## 4. Import test (existing resources only)

If importing existing manually-provisioned resources instead of a fresh apply:

```bash
terraform import module.eks.aws_eks_cluster.this hello-login
terraform import module.rds.aws_db_instance.this hello-login
terraform import module.ecr.aws_ecr_repository.this[\"hello-login-backend\"] hello-login-backend
terraform import module.ecr.aws_ecr_repository.this[\"hello-login-frontend\"] hello-login-frontend
```

After import, run `terraform plan` — **pass criteria:** `No changes.`

---

## 5. Pass / Fail Summary

| TC | Description | Result | Notes |
| --- | --- | --- | --- |
| TC-01 | terraform validate | | |
| TC-02 | tflint | | |
| TC-03 | checkov | | |
| TC-04 | terraform test (unit) | | |
| TC-05 | terraform plan — staging | | |
| TC-06 | terraform apply — staging | | |
| TC-07 | App deploy on staging | | |
| TC-08 | Idempotency | | |
| TC-09 | terraform destroy — staging | | |
| TC-10 | Production safety check | | |

---

## 6. Terratest Integration Tests (PR-94)

Terratest provisions real AWS infrastructure, asserts on it, and destroys it.

### Location

`terraform/test/` — one assertion file per module, one master test in `integration_test.go`.

### Running

```bash
# Install Go 1.21+ first, then:
cd terraform/test
go mod tidy          # download dependencies (first time only)
go test -v -timeout 60m -run TestTerraformIntegration
```

### Cost and timing

- ~$5–10 per run (EKS cluster is the main driver)
- ~30 minutes end-to-end

### Constraint — GitHub OIDC provider

The IAM module creates an account-wide GitHub OIDC provider. Only one can exist per AWS account. Destroy the staging environment before running Terratest, or the test will fail on this resource.

### GitHub Actions

The workflow `.github/workflows/terratest.yml` triggers manually via `workflow_dispatch`. It requires a GitHub secret `TERRATEST_AWS_ROLE_ARN` with an IAM role ARN that has permissions to provision the full stack.

### What is asserted

| Module | Assertion |
| --- | --- |
| Networking | `vpc_id` starts with `vpc-` |
| ECR | `backend_repo_url` and `frontend_repo_url` contain `.dkr.ecr.amazonaws.com` |
| RDS | `db_endpoint` contains `rds.amazonaws.com:3306` |
| EKS | `cluster_name` matches `hello-login-<env>`, `cluster_endpoint` starts with `https://` |
| Secrets | `secret_arn` contains `hello-login/<env>` |

---

## 7. Rollback Procedure

If apply fails mid-way:

1. Run `terraform destroy -var-file=staging.tfvars` to clean up partial state.
2. If destroy also fails, use the AWS console to manually delete resources in this order: EKS node group → EKS cluster → RDS instance → NAT gateway → subnets → VPC.
3. Remove the corrupted workspace state: `terraform workspace select default && terraform workspace delete staging`.

---

## 7. Sign-off

| Role | Name | Date | Signature |
| --- | --- | --- | --- |
| Engineer | | | |
| Reviewer | | | |
