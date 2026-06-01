# Outputs exposed to the root main.tf.

# The ARN of the Secrets Manager secret.
# Passed to module.iam so the ESO IAM role policy can be scoped to this
# specific secret -- the role can only read THIS secret, nothing else.
# Format: arn:aws:secretsmanager:<region>:<account>:secret:hello-login/<env>-<suffix>
output "secret_arn" {
  value = aws_secretsmanager_secret.this.arn
}

# The secret name/path -- hello-login/staging or hello-login/production.
# Matches the key field in k8s/overlays/*/external-secret.yaml.
output "secret_name" {
  value = aws_secretsmanager_secret.this.name
}
