# infra/dev/versions.tf
#
# Declares the minimum Terraform version and the providers this root module needs.
# Run `terraform init` once after cloning to download these providers locally.
# The downloaded files go into infra/dev/.terraform/ (gitignored).

terraform {
  # Require Terraform 1.7 or newer. 1.7 introduced native S3 state locking
  # (use_lockfile = true) without needing a DynamoDB table.
  required_version = ">= 1.7"

  required_providers {
    # aws — the HashiCorp AWS provider. Manages all AWS resources in this file:
    # VPC, subnets, IAM roles, security groups, EC2 instance, Elastic IP, etc.
    # ~> 5.0 means "5.x but not 6.0" — allows patch and minor upgrades automatically.
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# Configure the AWS provider with the target region.
# The region is read from var.aws_region (set in dev.tfvars).
# All resources created by this module will be placed in this region.
# Credentials are read from the environment automatically:
#   - AWS_PROFILE / AWS_DEFAULT_REGION environment variables
#   - ~/.aws/credentials and ~/.aws/config files
#   - EC2 instance profile (when running inside AWS)
provider "aws" {
  region = var.aws_region
}
