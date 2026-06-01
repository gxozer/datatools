# TDS: Terraform IaC for hello-login Infrastructure

**Version:** 1.0
**Date:** 2026-05-13
**Status:** Draft
**Jira:** PR-90
**Epic:** PR-88
**Related:** PRD ticket (PR-89), PRD_eks_deployment.md, TDS_eks_deployment.md

---

## 1. Overview

This document describes the technical implementation of Terraform Infrastructure as Code for the hello-login application. It covers directory layout, module breakdown, remote state configuration, per-environment variable files, and the specific resources managed in each module.

---

## 2. Directory Layout

```
terraform/
+-- main.tf
+-- variables.tf
+-- outputs.tf
+-- versions.tf
+-- backend.tf
+-- staging.tfvars           (staging environment values, no secrets)
+-- production.tfvars        (production environment values, no secrets)
+-- modules/
    +-- networking/
    +-- eks/
    +-- ecr/
    +-- rds/
    +-- secrets/
    +-- iam/
    +-- helm-addons/
```

---

## 3. Remote State Backend

The S3 backend is **commented out by default** in `backend.tf`. Local state is used until the backend is explicitly enabled.

**When to enable it:** when more than one person will run `terraform apply`, or when a CI/CD pipeline needs to apply infrastructure.

**Bootstrap (one-time, manual — only needed when enabling S3 backend):**

```bash
aws s3 mb s3://hello-login-tfstate --region us-west-2
aws s3api put-bucket-versioning \
  --bucket hello-login-tfstate \
  --versioning-configuration Status=Enabled
```

No DynamoDB table needed — state locking uses S3 native locking (`use_lockfile = true`), available in AWS provider v5+.

**backend.tf (uncomment to enable):**

```hcl
# terraform {
#   backend "s3" {
#     bucket       = "hello-login-tfstate"
#     key          = "hello-login/terraform.tfstate"
#     region       = "us-west-2"
#     use_lockfile = true   # S3 native locking — no DynamoDB needed
#     encrypt      = true   # state can contain sensitive values
#   }
# }
```

After uncommenting and creating the bucket, run `terraform init` — it will offer to migrate existing local state into S3 automatically.

---

## 4. Provider Configuration

```hcl
terraform {
  required_version = ">= 1.7"
  required_providers {
    aws        = { source = "hashicorp/aws",        version = "~> 5.0" }
    helm       = { source = "hashicorp/helm",       version = "~> 2.13" }
    kubernetes = { source = "hashicorp/kubernetes", version = "~> 2.30" }
    random     = { source = "hashicorp/random",     version = "~> 3.6" }
    tls        = { source = "hashicorp/tls",        version = "~> 4.0" }
  }
}

provider "aws" {
  region = var.aws_region
  default_tags {
    tags = {
      Project     = "hello-login"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
```

---

## 5. Module Specifications

### 5.1 networking

**Resources:**

- `aws_vpc` — dedicated VPC per environment (10.0.0.0/16 for staging, 10.1.0.0/16 for production)
- `aws_subnet` x3 public + x3 private (one per AZ in us-west-2: a, b, c) — subnets computed via `cidrsubnet`
- `aws_internet_gateway`
- `aws_nat_gateway` x1 (single NAT to save cost; can add per-AZ for production HA later)
- `aws_route_table` (public + private) + associations
- `aws_security_group` — EKS nodes (egress all, ingress from control plane)
- `aws_security_group` — RDS (ingress 3306 from EKS node SG)

**Key outputs:** `vpc_id`, `private_subnet_ids`, `public_subnet_ids`, `eks_node_sg_id`, `rds_sg_id`

### 5.2 eks

**Resources:**

- `aws_eks_cluster` — Kubernetes 1.29, private endpoint enabled
- `aws_eks_node_group` — managed, t3.small, min 2 / max 6, AL2 AMI
- `aws_iam_role` + `aws_iam_role_policy_attachment` — cluster role + node role
- `aws_eks_addon` — coredns, kube-proxy, vpc-cni, aws-ebs-csi-driver
- `aws_iam_openid_connect_provider` — OIDC provider from cluster issuer URL
- `data.tls_certificate` — used to fetch the OIDC thumbprint

**Key outputs:** `cluster_name`, `cluster_endpoint`, `cluster_ca`, `oidc_provider_arn`, `oidc_provider_url`, `node_role_arn`

### 5.3 ecr

**Resources:**

- `aws_ecr_repository` x2: hello-login-backend, hello-login-frontend (using `for_each`)
- `aws_ecr_lifecycle_policy` — expire untagged after 1 day; keep last 10 tagged images

**Key outputs:** `backend_repo_url`, `frontend_repo_url`

### 5.4 rds

**Resources:**

- `aws_db_subnet_group` — uses private subnets from networking module
- `aws_db_instance` — MySQL 8.0, db.t3.micro, 20 GB gp2, 7-day backup retention, `deletion_protection` driven by `var.prevent_destroy`
- `aws_db_parameter_group` — default MySQL 8.0 params
- `random_password` — generates initial DB password (stored in state; rotated out-of-band)

**Key outputs:** `db_endpoint`, `db_port`, `db_name`

### 5.5 secrets

**Resources:**

- `aws_secretsmanager_secret` — `hello-login/${var.environment}`
- Secret value is **not** managed by Terraform (populated out-of-band to avoid secrets in state)

**Key outputs:** `secret_arn`, `secret_name`

### 5.6 iam

**Three IRSA roles:**

1. **ALB Controller** — `AWSLoadBalancerControllerIAMPolicy` (policy JSON at `modules/iam/policies/alb-controller.json`)
2. **External Secrets Operator** — `secretsmanager:GetSecretValue` + `secretsmanager:DescribeSecret` on `hello-login/${var.environment}/*`
3. **GitHub Actions** — OIDC trust for `token.actions.githubusercontent.com`, allows `sts:AssumeRoleWithWebIdentity`; permissions: ECR push, `eks:DescribeCluster`, `eks:UpdateAddon`

Also creates the GitHub Actions OIDC provider (`aws_iam_openid_connect_provider.github`) — this is a global per-account resource; import it if it already exists.

**Resources per role:** `aws_iam_role`, `aws_iam_policy`, `aws_iam_role_policy_attachment`

**Key outputs:** `alb_controller_role_arn`, `eso_role_arn`, `github_actions_role_arn`

### 5.7 helm-addons

**Resources (using `helm_release`):**

- AWS Load Balancer Controller v1.7.2 — `eks/aws-load-balancer-controller`, namespace `kube-system`
- External Secrets Operator v0.9.13 — `external-secrets/external-secrets`, namespace `external-secrets`

Both releases configured with IRSA role ARNs from the iam module.

**Depends on:** eks module (cluster must exist), iam module (role ARNs)

---

## 6. Variable Files

**staging.tfvars:**

```hcl
environment         = "staging"
aws_region          = "us-west-2"
vpc_cidr            = "10.0.0.0/16"
eks_node_type       = "t3.small"
eks_min_nodes       = 2
eks_max_nodes       = 6
rds_instance_class  = "db.t3.micro"
rds_prevent_destroy = false
github_org          = "gxozer"
github_repo         = "hello_login_deploy"
```

**production.tfvars:**

```hcl
environment         = "production"
aws_region          = "us-west-2"
vpc_cidr            = "10.1.0.0/16"
eks_node_type       = "t3.small"
eks_min_nodes       = 2
eks_max_nodes       = 6
rds_instance_class  = "db.t3.micro"
rds_prevent_destroy = true
github_org          = "gxozer"
github_repo         = "hello_login_deploy"
```

---

## 7. Apply Workflow

```bash
# Bootstrap state backend (one-time) -- see Section 3

# Staging
cd terraform
terraform init
terraform workspace new staging  # or: terraform workspace select staging
terraform plan -var-file=staging.tfvars -out=staging.plan
terraform apply staging.plan

# Production
terraform workspace new production
terraform plan -var-file=production.tfvars -out=production.plan
terraform apply production.plan
```

**Destroy order — IMPORTANT:**

The ALB (Application Load Balancer) is created by the Kubernetes ALB controller
in response to Ingress objects, not by Terraform directly. If `terraform destroy`
is run while the ALB still exists, AWS refuses to delete the public subnets and
VPC that the ALB depends on.

Always delete Kubernetes resources first:

```bash
# Step 1 -- remove all k8s resources (ALB controller deletes the ALB)
kubectl delete -k k8s/overlays/staging/

# Wait ~60 seconds for the ALB to be fully removed from AWS, then:

# Step 2 -- destroy Terraform infrastructure
terraform -chdir=terraform destroy -var-file=staging.tfvars
```

---

## 8. Import Strategy (Existing Resources)

Current infrastructure was provisioned manually. Options:

1. **Import into Terraform state** (`terraform import`) — preserves resources, avoids downtime.
2. **Recreate** — destroy and apply from scratch, requires a maintenance window.

**Recommendation:** Import for production (avoid downtime); recreate for staging (simpler, acceptable downtime for a non-prod env).

Key import commands:

```bash
terraform import module.eks.aws_eks_cluster.this hello-login
terraform import module.rds.aws_db_instance.this hello-login
terraform import module.ecr.aws_ecr_repository.this[\"hello-login-backend\"] hello-login-backend
terraform import module.ecr.aws_ecr_repository.this[\"hello-login-frontend\"] hello-login-frontend
# GitHub Actions OIDC provider (global, once per account):
terraform import module.iam.aws_iam_openid_connect_provider.github <arn>
```

---

## 9. Key Design Decisions

### 9.1 Workspaces vs separate state files

**Decision:** Use Terraform workspaces with per-environment `.tfvars` files.

**Why:** A single module tree with variable overrides keeps staging and production in sync. Workspaces isolate state. Separate directories per env risk divergence and duplication.

### 9.2 Helm provider in Terraform vs standalone Helm CLI

**Decision:** Use the Terraform `helm` provider for ALB controller and ESO.

**Why:** Keeps the full infra lifecycle in one tool. Both add-ons are cluster-wide, version-pinned — good candidates for Terraform management. Application-level Helm charts stay in CI/CD.

### 9.3 Secrets values not in Terraform state

**Decision:** Create Secrets Manager secret shells in Terraform; populate values out-of-band.

**Why:** Terraform state is not a secrets store. Out-of-band population keeps plaintext out of state entirely.

### 9.4 RDS password handling

**Decision:** Generate initial password with `random_password`; ignore future changes via `lifecycle { ignore_changes = [password] }`.

**Why:** Avoids storing the password in tfvars. The password lands in state (state is encrypted at rest in S3). Rotation should be done out-of-band via Secrets Manager.

---

## 10. Files Created

| File | Purpose |
| --- | --- |
| `terraform/versions.tf` | Provider version constraints |
| `terraform/backend.tf` | S3 + DynamoDB remote state |
| `terraform/variables.tf` | Input variable declarations |
| `terraform/outputs.tf` | Root-level outputs |
| `terraform/main.tf` | Module composition |
| `terraform/staging.tfvars` | Staging variable values |
| `terraform/production.tfvars` | Production variable values |
| `terraform/modules/networking/` | VPC, subnets, SGs |
| `terraform/modules/eks/` | EKS cluster + node group + OIDC |
| `terraform/modules/ecr/` | ECR repos + lifecycle |
| `terraform/modules/rds/` | RDS + subnet group |
| `terraform/modules/secrets/` | Secrets Manager secret shells |
| `terraform/modules/iam/` | IRSA roles + ALB policy JSON |
| `terraform/modules/helm-addons/` | ALB controller + ESO helm releases |
