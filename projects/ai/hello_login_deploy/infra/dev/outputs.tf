output "public_ip" {
  value       = aws_eip.dev.public_ip
  description = "Elastic IP of the dev instance — use this in .env.ec2 for FRONTEND_URL/CORS_ORIGINS"
}

output "instance_id" {
  value       = aws_instance.dev.id
  description = "EC2 instance ID"
}

output "ssh_command" {
  value       = "ssh -i ~/.ssh/${var.key_name}.pem ec2-user@${aws_eip.dev.public_ip}"
  description = "SSH command to connect to the dev instance"
}

output "frontend_url" {
  value       = "http://${aws_eip.dev.public_ip}"
  description = "URL to reach the dev frontend"
}

output "ecr_login_command" {
  value       = "aws ecr get-login-password --region ${var.aws_region} | docker login --username AWS --password-stdin ${var.ecr_account_id}.dkr.ecr.${var.aws_region}.amazonaws.com"
  description = "Run this on the EC2 instance before docker compose pull"
}
