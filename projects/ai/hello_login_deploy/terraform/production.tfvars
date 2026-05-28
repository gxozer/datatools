# production.tfvars — variable values for the production environment.
#
# Pass this file to Terraform with:
#   terraform plan    -var-file=production.tfvars
#   terraform apply   -var-file=production.tfvars
#
# WARNING: terraform destroy with these values will attempt to delete production
# infrastructure. The RDS instance has deletion_protection=true (from
# rds_prevent_destroy below), which will cause destroy to fail on the database
# — this is intentional. Remove deletion protection manually in the AWS console
# only when you are certain you want to delete the database.
#
# This file contains no secrets.

# Selects the production environment. Controls resource names (hello-login-production),
# Secrets Manager path (hello-login/production), and safety settings.
environment = "production"

# AWS region where all resources are created.
aws_region = "us-west-2"

# VPC IP range for production. Uses 10.1.x.x so it does not overlap with
# staging (10.0.x.x) — important if the two VPCs are ever peered together.
vpc_cidr = "10.1.0.0/16"

# EC2 instance type for EKS worker nodes.
eks_node_type = "t3.small"

# Minimum worker nodes.
eks_min_nodes = 2

# Maximum worker nodes.
eks_max_nodes = 6

# RDS instance size.
rds_instance_class = "db.t3.micro"

# true = production RDS has deletion_protection enabled.
# AWS will refuse to delete the RDS instance even if terraform destroy is run.
# This is a safety guard against accidental data loss in production.
rds_prevent_destroy = true

# GitHub org/user — scopes the GitHub Actions IAM role to this repository only.
github_org  = "gxozer"
github_repo = "hello_login_deploy"

# false — ECR repos are account-global. They are created and owned by the
# staging state. Production looks them up via data sources.
ecr_create_repos = false

# false — the GitHub Actions OIDC provider is account-global and was already
# created by the staging state. Creating it again here would fail with
# "EntityAlreadyExists". Production references it by ARN without owning it.
create_github_oidc_provider = false
