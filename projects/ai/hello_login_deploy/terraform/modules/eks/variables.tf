# Inputs this module accepts from the root main.tf.

# Used to name every resource: cluster, node group, IAM roles.
# e.g. environment="staging" → cluster name "hello-login-staging"
variable "environment" {
  type = string
}

# Subnets where worker nodes are placed. Nodes go in private subnets so they
# do not have public IP addresses — traffic reaches them only via load balancers
# or the cluster control plane.
variable "private_subnet_ids" {
  type = list(string)
}

# Public subnets passed to the EKS vpc_config alongside private subnets.
# EKS associates both so the control plane can place its elastic network
# interfaces in the correct subnets for cross-AZ communication.
variable "public_subnet_ids" {
  type = list(string)
}

# Security group attached to the node group. Defined in the networking module
# and passed here so the cluster can reference it in its vpc_config.
variable "eks_node_sg_id" {
  type = string
}

# EC2 instance type for worker nodes, e.g. "t3.small".
variable "node_type" {
  type = string
}

# Minimum number of worker nodes. The node group never scales below this,
# ensuring there is always at least one node available per AZ.
variable "min_nodes" {
  type = number
}

# Maximum number of worker nodes. The Cluster Autoscaler can add nodes up to
# this limit when pods cannot be scheduled due to resource constraints.
variable "max_nodes" {
  type = number
}

# CIDR blocks that can reach the public Kubernetes API endpoint.
# ["0.0.0.0/0"] leaves it open to the internet; restrict to office/VPN
# CIDRs to reduce attack surface on the control plane.
variable "public_access_cidrs" {
  type    = list(string)
  default = ["0.0.0.0/0"]
}
