# Which environment to provision. Controls resource names (e.g. "hello-login-staging"),
# the Secrets Manager path (hello-login/staging), VPC (Virtual Private Cloud) CIDR
# (Classless Inter-Domain Routing — the IP range for the network), and deletion protection.
# The validation block accepts "staging", "production", or any name beginning with "tt"
# (reserved for Terratest integration test runs, which use names like "tt3a7b2c").
variable "environment" {
  description = "Environment name: 'staging', 'production', or 'tt<id>' for Terratest runs"
  type        = string

  validation {
    condition     = contains(["staging", "production"], var.environment) || startswith(var.environment, "tt")
    error_message = "environment must be 'staging', 'production', or start with 'tt' (Terratest)."
  }
}

# AWS region where all resources are created. Defaults to us-west-2 because that
# is where the existing EKS cluster and RDS instance live.
variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-west-2"
}

# IP address range for the entire VPC. Must be a /16 CIDR block.
# staging uses 10.0.0.0/16 and production uses 10.1.0.0/16 — keeping them
# different means the two networks can be peered in future without conflicts.
# Subnets are carved out of this range automatically using cidrsubnet().
variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
}

# EC2 instance type for EKS worker nodes. t3.small matches the existing cluster.
# Increase to t3.medium or t3.large if workloads need more CPU or memory.
variable "eks_node_type" {
  description = "EC2 instance type for EKS nodes"
  type        = string
  default     = "t3.small"
}

# Minimum number of EKS worker nodes. The cluster will never scale below this.
# Set to 2 so there is always a node available in a second AZ for pod scheduling
# if one node is replaced or unavailable.
variable "eks_min_nodes" {
  description = "Minimum number of EKS nodes"
  type        = number
  default     = 2
}

# Maximum number of EKS worker nodes. The Cluster Autoscaler can scale up to
# this limit when pods cannot be scheduled due to insufficient resources.
variable "eks_max_nodes" {
  description = "Maximum number of EKS nodes"
  type        = number
  default     = 6
}

# RDS instance class — controls CPU and memory for the database.
# db.t3.micro matches the existing instance and is sufficient for low traffic.
# Upgrade to db.t3.small or db.t3.medium if query performance becomes a concern.
variable "rds_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.micro"
}

# When true, the RDS instance refuses to be deleted even by terraform destroy.
# Set to false for staging (allows clean teardown to save cost overnight).
# Set to true for production (guards against accidental data loss).
# Note: Terraform cannot toggle lifecycle { prevent_destroy } dynamically, so
# this variable drives deletion_protection on the RDS instance instead, which
# achieves the same protection at the AWS level.
variable "rds_prevent_destroy" {
  description = "Enable lifecycle prevent_destroy on RDS (set true for production)"
  type        = bool
  default     = false
}

# GitHub organisation or user that owns the repository. Used in the GitHub Actions
# IAM (Identity and Access Management) role trust policy. GitHub Actions workflows
# produce OIDC (OpenID Connect) tokens containing a "sub" claim that identifies
# the repo — the trust policy checks this so only tokens from this org can assume the role.
variable "github_org" {
  description = "GitHub organisation or user that owns the repo"
  type        = string
  default     = "gxozer"
}

# GitHub repository name. Combined with github_org to scope the GitHub Actions
# IAM role to exactly this repository — no other repo can assume the role.
variable "github_repo" {
  description = "GitHub repository name"
  type        = string
  default     = "hello_login_deploy"
}

# ECR repos are account-global. Only one state should create them (staging).
# Set to false in production so it looks them up instead of trying to recreate them.
variable "ecr_create_repos" {
  description = "Create ECR repositories (true for staging, false for production which references staging's repos)"
  type        = bool
  default     = true
}

# CIDR blocks that can reach the public Kubernetes API endpoint.
# Default is open; restrict to your office/VPN CIDR(s) to reduce attack surface.
# Example: ["203.0.113.0/24", "198.51.100.5/32"]
variable "eks_public_access_cidrs" {
  description = "CIDRs allowed to reach the EKS public API endpoint"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

# The GitHub Actions OIDC provider is account-global — it can only be created
# once per AWS account. Set to true for staging (creates it) and false for
# production (provider already exists; staging owns it).
variable "create_github_oidc_provider" {
  description = "Create the GitHub Actions OIDC provider (account-global; set false if already exists)"
  type        = bool
  default     = true
}
