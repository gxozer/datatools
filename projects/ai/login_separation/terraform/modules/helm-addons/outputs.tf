# Outputs exposed to the root main.tf.
# These are informational — the status values confirm the Helm releases
# completed successfully. They are not consumed by any other module.

# Status of the ALB controller Helm release after apply.
# A value of "deployed" means the chart was installed successfully.
output "alb_controller_status" {
  value = helm_release.alb_controller.status
}

# Status of the External Secrets Operator Helm release after apply.
output "eso_status" {
  value = helm_release.external_secrets.status
}
