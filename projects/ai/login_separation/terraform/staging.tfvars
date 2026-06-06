# staging.tfvars — variable values for the staging environment.
#
# Pass this file to Terraform with:
#   terraform plan    -var-file=staging.tfvars
#   terraform apply   -var-file=staging.tfvars
#   terraform destroy -var-file=staging.tfvars
#
# This file contains no secrets — only infrastructure shape.
# Secrets (DATABASE_URL, JWT_SECRET, etc.) live in AWS Secrets Manager
# and are never written to any .tfvars file.

# Selects the staging environment. Controls resource names (hello-login-staging),
# Secrets Manager path (hello-login/staging), and safety settings.
environment = "staging"

# AWS region where all resources are created.
aws_region = "us-west-2"

# VPC IP range for staging. /16 gives 65,536 addresses.
# Subnets are carved from this automatically by cidrsubnet().
# Uses 10.0.x.x so it does not overlap with production (10.1.x.x).
vpc_cidr = "10.0.0.0/16"

# EC2 instance type for EKS worker nodes.
eks_node_type = "t3.small"

# Minimum worker nodes — cluster never scales below this.
eks_min_nodes = 2

# Maximum worker nodes — cluster autoscaler upper bound.
eks_max_nodes = 6

# RDS instance size. db.t3.micro is sufficient for staging workloads.
rds_instance_class = "db.t3.micro"

# false = staging RDS can be deleted by terraform destroy.
# This allows cheap overnight teardown of the staging environment.
rds_prevent_destroy = false

# GitHub org/user — used to scope the GitHub Actions IAM role.
github_org  = "gxozer"
github_repo = "hello_login_deploy"

# CIDRs allowed to reach the public Kubernetes API endpoint.
# 0.0.0.0/0 is acceptable for staging. For production, restrict to your
# VPN/office CIDR(s) — e.g. ["203.0.113.42/32"] — to reduce attack surface.
eks_public_access_cidrs = ["0.0.0.0/0"]
