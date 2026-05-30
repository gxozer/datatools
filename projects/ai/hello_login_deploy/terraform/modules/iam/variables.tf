# Inputs this module accepts from the root main.tf.

# AWS region — used to build ECR repository ARNs in the GitHub Actions policy.
variable "aws_region" {
  type = string
}

# AWS account ID — used to build ARNs for IAM policies and the GitHub Actions
# OIDC provider. Comes from data.aws_caller_identity in root main.tf.
variable "aws_account_id" {
  type = string
}

# EKS cluster name — used to name IAM roles and to scope the GitHub Actions
# EKS permission to this specific cluster.
variable "cluster_name" {
  type = string
}

# ARN of the EKS OIDC (OpenID Connect — a federated identity standard) identity provider.
# Used as the "Federated" principal in the trust policies for ALB controller and ESO roles.
# This tells IAM: "only allow role assumptions from this specific EKS cluster's OIDC issuer."
variable "oidc_provider_arn" {
  type = string
}

# URL of the EKS OIDC issuer — used to build the IAM condition key:
#   "<oidc_host>:sub" = "system:serviceaccount:<namespace>:<serviceaccount>"
# The condition pins each role to exactly one Kubernetes service account.
variable "oidc_provider_url" {
  type = string
}

# ARN of the Secrets Manager secret — the ESO role policy is scoped to this
# specific secret so ESO can only read the secret for its own environment.
variable "secret_arn" {
  type = string
}

# GitHub organisation or user — used in the GitHub Actions role trust policy
# condition to restrict which GitHub account can assume the role.
variable "github_org" {
  type = string
}

# GitHub repository name — combined with github_org to form the subject claim:
#   "repo:<org>/<repo>:*"
# Only Actions workflows from this repository can assume the role.
variable "github_repo" {
  type = string
}

# Controls whether this module creates the GitHub Actions OIDC provider.
# The provider is account-global — running both staging and production
# states with this set to true will fail on the second apply with
# "provider already exists". Set to true for the first environment applied
# (staging) and false for all others (production), which reference the
# already-created provider by ARN.
variable "create_github_oidc_provider" {
  type    = bool
  default = true
}

# ARN of the RDS-managed Secrets Manager secret (rds!db-<instance-id>-admin).
# RDS creates this secret automatically when manage_master_user_password = true
# and rotates its password automatically. The ESO role needs read access to it
# so ExternalSecret can compose DATABASE_URL from the individual fields without
# relying on the manually-populated hello-login/<env> secret for the password.
# Null before the RDS instance exists (e.g. in terraform test with mock providers).
variable "rds_master_user_secret_arn" {
  type    = string
  default = null
}
