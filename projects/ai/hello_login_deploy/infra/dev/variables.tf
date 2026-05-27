variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-west-2"
}

variable "instance_type" {
  description = "EC2 instance type for the dev server"
  type        = string
  default     = "t3.small"
}

variable "key_name" {
  description = "EC2 key pair name for SSH access (must already exist in AWS)"
  type        = string
}

variable "ssh_cidr_blocks" {
  description = "CIDR blocks allowed SSH access (port 22) — restrict to your IP for security"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "ecr_account_id" {
  description = "AWS account ID where ECR repositories live"
  type        = string
  default     = "277070500859"
}
