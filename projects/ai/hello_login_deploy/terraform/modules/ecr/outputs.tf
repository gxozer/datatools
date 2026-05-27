# Outputs exposed to the root main.tf.
# These URLs are used in CI/CD to push images and in Kubernetes manifests
# to pull them. Format: <account>.dkr.ecr.<region>.amazonaws.com/<repo-name>

# Full ECR URL for the backend image.
# Used in docker push commands and in k8s/overlays/*/kustomization.yaml.
output "backend_repo_url" {
  value = aws_ecr_repository.this["hello-login-backend"].repository_url
}

# Full ECR URL for the frontend image.
output "frontend_repo_url" {
  value = aws_ecr_repository.this["hello-login-frontend"].repository_url
}
