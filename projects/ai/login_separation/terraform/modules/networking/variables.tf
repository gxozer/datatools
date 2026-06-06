# Inputs this module accepts from the root main.tf.
# All three are required — there are no defaults because the values differ
# between staging and production.

# Used to name every resource in this module, e.g. "hello-login-staging".
# Also embedded in Kubernetes subnet tags so the ALB controller can find
# the right subnets for a given cluster.
variable "environment" {
  type = string
}

# The CIDR block for the entire VPC, e.g. "10.0.0.0/16".
# All subnet CIDRs are computed from this value using cidrsubnet(), so
# changing this value changes the IP ranges of all subnets.
variable "vpc_cidr" {
  type = string
}
