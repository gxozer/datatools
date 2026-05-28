# Inputs this module accepts from the root main.tf.
# ECR repositories are shared across environments — both staging and production
# pull from the same repos, distinguished by image tag.

# When true, this state creates and owns the ECR repositories.
# When false, this state uses data sources to reference repos created elsewhere.
# Only ONE state should set this to true — use staging by convention.
variable "create_repos" {
  type    = bool
  default = true
}
