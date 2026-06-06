# Outputs exposed to the root main.tf.
# All three role ARNs are passed to module.helm_addons so they can be
# annotated onto the Kubernetes service accounts of the respective controllers.
# The annotation format is:
#   eks.amazonaws.com/role-arn: <role_arn>

# ARN of the ALB controller IRSA role.
# Annotated onto the "aws-load-balancer-controller" service account in the
# aws-load-balancer-controller Helm chart via helm_release set block.
output "alb_controller_role_arn" {
  value = aws_iam_role.alb_controller.arn
}

# ARN of the External Secrets Operator IRSA role.
# Annotated onto the "external-secrets" service account in the ESO Helm chart.
output "eso_role_arn" {
  value = aws_iam_role.eso.arn
}

# ARN of the GitHub Actions IAM role.
# Added to the GitHub Actions workflow file as the role to assume, e.g.:
#   role-to-assume: arn:aws:iam::<account>:role/hello-login-staging-github-actions
output "github_actions_role_arn" {
  value = aws_iam_role.github_actions.arn
}
