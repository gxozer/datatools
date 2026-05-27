# modules/secrets/main.tf
#
# Creates a Secrets Manager secret SHELL for this environment.
#
# What "shell" means: the secret container (name, ARN, metadata) is created
# here, but the secret VALUE is NOT set by Terraform. The values
# (DATABASE_URL, JWT_SECRET, MAIL_PASSWORD, etc.) are populated manually
# or by a separate secrets rotation process after the infrastructure exists.
#
# Why not set the values here?
#   If Terraform managed the secret values, they would be stored in Terraform
#   state in plaintext. Even with S3 encryption, state access = secret access.
#   Keeping values out of Terraform state reduces the blast radius if state
#   is ever accessed by an unauthorised party.
#
# How the values are consumed:
#   The ESO (External Secrets Operator — a Kubernetes controller) running in EKS
#   watches for ExternalSecret objects (defined in k8s/overlays/<env>/external-secret.yaml).
#   ESO reads the secret from AWS Secrets Manager at path hello-login/<environment>
#   and injects the values into a Kubernetes Secret that pods can mount as environment variables.

resource "aws_secretsmanager_secret" "this" {
  # Path follows the convention used in k8s/overlays/*/external-secret.yaml.
  # e.g. "hello-login/staging" or "hello-login/production"
  name        = "hello-login/${var.environment}"
  description = "Application secrets for hello-login ${var.environment}"

  tags = {
    Name = "hello-login/${var.environment}"
  }
}
