# Inputs this module accepts from the root main.tf.

# Determines the Secrets Manager secret path: hello-login/<environment>
# e.g. "staging"    -> hello-login/staging
#      "production" -> hello-login/production
variable "environment" {
  type = string
}
