# infra/dev/variables.tf
#
# Input variables for the dev EC2 Terraform root.
# Values are provided by infra/dev/dev.tfvars (gitignored — never commit it).
# Copy infra/dev/dev.tfvars.example to infra/dev/dev.tfvars and fill in your values.

# AWS region where the EC2 instance and all related resources are created.
# Key pairs are region-specific — the key named in var.key_name must exist
# in this same region, not in a different one.
variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-west-2"
}

# EC2 instance type controls the CPU and memory of the dev server.
# t3.small (2 vCPU, 2 GB RAM) is sufficient for running backend + frontend +
# MySQL + Caddy concurrently at low traffic. Upgrade to t3.medium (4 GB) if
# Docker containers are killed due to out-of-memory conditions.
variable "instance_type" {
  description = "EC2 instance type for the dev server"
  type        = string
  default     = "t3.small"
}

# Name of the EC2 key pair used for SSH access. The key pair must already exist
# in AWS in the same region as var.aws_region — key pairs are region-specific.
# Create one with:
#   aws ec2 create-key-pair \
#     --key-name hello-login-dev \
#     --region us-west-2 \
#     --query 'KeyMaterial' \
#     --output text > ~/.ssh/hello-login-dev.pem
#   chmod 400 ~/.ssh/hello-login-dev.pem
variable "key_name" {
  description = "EC2 key pair name for SSH access (must already exist in AWS)"
  type        = string
}

# CIDR blocks (IP address ranges) allowed to connect on port 22 (SSH).
# This variable is required — there is no default because leaving SSH open to
# the internet (0.0.0.0/0) is a security risk: port 22 is continuously probed
# by automated scanners.
# Set to your own IP:
#   ssh_cidr_blocks = ["203.0.113.42/32"]   # /32 means exactly this one IP address
# Find your IPv4 address: curl -4 ifconfig.me
variable "ssh_cidr_blocks" {
  description = "CIDR blocks allowed SSH access (port 22). Required — set to your IP: [\"$(curl -4 -s ifconfig.me)/32\"]"
  type        = list(string)
}

# AWS account ID where the ECR (Elastic Container Registry) repositories live.
# Used in the ecr_login_command output to construct the full registry URL:
#   <ecr_account_id>.dkr.ecr.<aws_region>.amazonaws.com
# This is the same account that owns the hello-login-backend and hello-login-frontend repos.
variable "ecr_account_id" {
  description = "AWS account ID where ECR repositories live"
  type        = string
  default     = "277070500859"
}
