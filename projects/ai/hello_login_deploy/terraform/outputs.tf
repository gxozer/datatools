# outputs.tf — values printed to the terminal after terraform apply completes.
#
# Outputs serve two purposes:
#   1. Human convenience — you can read the cluster endpoint, DB endpoint, and
#      ECR URLs directly from the terminal instead of hunting in the AWS console.
#   2. Scripting — other tools (CI pipelines, shell scripts) can read output
#      values with: terraform output -raw <output_name>
#
# All values here are collected from module outputs. The modules themselves
# collect them from the AWS resources they create.

# The VPC ID — useful for verifying which VPC was created and for importing
# other resources into this VPC in the future.
output "vpc_id" {
  description = "VPC ID"
  value       = module.networking.vpc_id
}

# The EKS cluster name — used in kubectl and aws CLI commands, e.g.:
#   aws eks update-kubeconfig --name <cluster_name> --region us-west-2
output "cluster_name" {
  description = "EKS cluster name"
  value       = module.eks.cluster_name
}

# The Kubernetes API server URL — used by kubectl and the helm/kubernetes providers.
output "cluster_endpoint" {
  description = "EKS cluster API endpoint"
  value       = module.eks.cluster_endpoint
}

# ECR URL for the backend image — used in CI/CD to push and pull images, e.g.:
#   docker push <backend_repo_url>:sha-abc123
output "backend_repo_url" {
  description = "ECR URL for backend image"
  value       = module.ecr.backend_repo_url
}

# ECR URL for the frontend image.
output "frontend_repo_url" {
  description = "ECR URL for frontend image"
  value       = module.ecr.frontend_repo_url
}

# The RDS connection endpoint — used to build the DATABASE_URL secret value, e.g.:
#   mysql+pymysql://admin:<password>@<db_endpoint>/hello_login
output "db_endpoint" {
  description = "RDS endpoint"
  value       = module.rds.db_endpoint
}

# The ARN of the Secrets Manager secret — used when granting other services
# read access to the secret, or when referencing it in IAM policies.
output "secret_arn" {
  description = "Secrets Manager secret ARN"
  value       = module.secrets.secret_arn
}

# IAM role ARN for the ALB controller — annotated onto the controller's
# Kubernetes service account to enable IRSA. Printed here for verification.
output "alb_controller_role_arn" {
  description = "IRSA role ARN for ALB controller"
  value       = module.iam.alb_controller_role_arn
}

# IAM role ARN for the External Secrets Operator — annotated onto the ESO
# service account so it can read from Secrets Manager.
output "eso_role_arn" {
  description = "IRSA role ARN for External Secrets Operator"
  value       = module.iam.eso_role_arn
}

# IAM role ARN for GitHub Actions — added to the GitHub Actions workflow as
# the role to assume when pushing images and deploying to EKS.
output "github_actions_role_arn" {
  description = "IAM role ARN for GitHub Actions OIDC (OpenID Connect) — assumed by CI/CD workflows without long-lived credentials"
  value       = module.iam.github_actions_role_arn
}
