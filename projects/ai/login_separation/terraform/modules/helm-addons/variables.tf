# Inputs this module accepts from the root main.tf.

# The EKS cluster name — passed to the ALB controller chart so it knows
# which cluster it is managing load balancers for.
variable "cluster_name" {
  type = string
}

# AWS region — passed to the ALB controller so it knows which region to create
# load balancers in.
variable "aws_region" {
  type = string
}

# VPC ID — passed to the ALB controller so it knows which VPC to create
# load balancers in.
variable "vpc_id" {
  type = string
}

# IAM role ARN for the ALB controller — annotated onto the controller's
# Kubernetes service account to enable IRSA (pod-level AWS permissions).
variable "alb_controller_role_arn" {
  type = string
}

# IAM role ARN for the External Secrets Operator — annotated onto the ESO
# service account so it can read from Secrets Manager.
variable "eso_role_arn" {
  type = string
}
