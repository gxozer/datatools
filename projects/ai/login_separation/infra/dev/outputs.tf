# infra/dev/outputs.tf
#
# Values printed to the terminal after `terraform apply` completes.
# Read them anytime with: terraform -chdir=infra/dev output
# Read a single value: terraform -chdir=infra/dev output -raw public_ip

# The static public IPv4 address of the dev instance.
# This is the Elastic IP — it stays the same even if the instance is restarted.
# Use this value in two places:
#   1. DNS A record: point your domain (DEV_DOMAIN) to this IP
#   2. .env.ec2: set FRONTEND_URL=https://<DEV_DOMAIN> and CORS_ORIGINS=https://<DEV_DOMAIN>
# Verify DNS propagation: dig +short <DEV_DOMAIN>   (should return this IP)
output "public_ip" {
  value       = aws_eip.dev.public_ip
  description = "Elastic IP of the dev instance — use this in .env.ec2 for FRONTEND_URL/CORS_ORIGINS"
}

# The EC2 instance ID, e.g. "i-0abc123def456789".
# Use this for:
#   AWS console: EC2 → Instances → search by this ID
#   SSM access:  aws ssm start-session --target <instance_id>
#   Force replace: terraform -chdir=infra/dev apply -replace=aws_instance.dev -var-file=dev.tfvars
output "instance_id" {
  value       = aws_instance.dev.id
  description = "EC2 instance ID"
}

# A ready-to-run SSH command. Copy and paste it into your terminal to connect.
# Requires: the .pem file at ~/.ssh/<key_name>.pem with chmod 400 permissions.
# If you get "Permission denied", check: ls -l ~/.ssh/<key_name>.pem
output "ssh_command" {
  value       = "ssh -i ~/.ssh/${var.key_name}.pem ec2-user@${aws_eip.dev.public_ip}"
  description = "SSH command to connect to the dev instance"
}

# The HTTP URL (not HTTPS) using the raw IP. Useful for quick checks before
# DNS is configured. HTTPS requires a domain name for Caddy's Let's Encrypt cert.
# Once DNS is set up, use https://<DEV_DOMAIN> instead.
output "frontend_url" {
  value       = "http://${aws_eip.dev.public_ip}"
  description = "URL to reach the dev frontend (HTTP only — use https://<DEV_DOMAIN> once DNS is configured)"
}

# ECR login command to run on the EC2 instance before `docker compose pull`.
# The `aws ecr get-login-password` command returns a temporary Docker auth token.
# Piping it to `docker login` stores the credentials so Docker can pull from ECR.
# This works without a stored AWS secret key because the instance has an IAM role
# (aws_iam_instance_profile.ec2) with ECR read access.
# Note: make dev-deploy runs this automatically as part of the deploy sequence.
output "ecr_login_command" {
  value       = "aws ecr get-login-password --region ${var.aws_region} | docker login --username AWS --password-stdin ${var.ecr_account_id}.dkr.ecr.${var.aws_region}.amazonaws.com"
  description = "Run this on the EC2 instance before docker compose pull"
}
