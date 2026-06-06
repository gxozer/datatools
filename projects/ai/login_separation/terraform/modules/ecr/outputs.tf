# Outputs exposed to the root main.tf.
# These URLs are used in CI/CD to push images and in Kubernetes manifests
# to pull them. Format: <account>.dkr.ecr.<region>.amazonaws.com/<repo-name>
#
# When create_repos = true  (staging):    read from aws_ecr_repository.this
# When create_repos = false (production): read from data.aws_ecr_repository.existing

# Full ECR URL for the backend image.
# Used in docker push commands and in k8s/overlays/*/kustomization.yaml.
output "backend_repo_url" {
  value = var.create_repos ? aws_ecr_repository.this["hello-login-login"].repository_url : data.aws_ecr_repository.existing["hello-login-login"].repository_url
}

output "hello_repo_url" {
  value = var.create_repos ? aws_ecr_repository.this["hello-login-hello"].repository_url : data.aws_ecr_repository.existing["hello-login-hello"].repository_url
}

# Full ECR URL for the frontend image.
output "frontend_repo_url" {
  value = var.create_repos ? aws_ecr_repository.this["hello-login-frontend"].repository_url : data.aws_ecr_repository.existing["hello-login-frontend"].repository_url
}
