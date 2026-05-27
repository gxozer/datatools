# Outputs exposed to the root main.tf.
# Every other module that needs network information receives it through these outputs.
# The syntax module.networking.<output_name> is used to reference them in main.tf.

# The VPC ID — passed to EKS, RDS, IAM, and helm-addons modules so they place
# their resources inside this VPC.
output "vpc_id" {
  value = aws_vpc.this.id
}

# IDs of the 3 public subnets — passed to the EKS module so the cluster
# control plane can be associated with them. Also used by the ALB controller
# to place load balancers.
output "public_subnet_ids" {
  value = aws_subnet.public[*].id  # [*] collects all 3 IDs into a list
}

# IDs of the 3 private subnets — passed to the EKS module (worker nodes go here)
# and to the RDS module (database goes here).
output "private_subnet_ids" {
  value = aws_subnet.private[*].id
}

# Security group ID for EKS nodes — passed to the EKS module (attached to the
# node group) and to the RDS security group ingress rule (so the DB allows
# connections only from nodes with this SG).
output "eks_node_sg_id" {
  value = aws_security_group.eks_nodes.id
}

# Security group ID for RDS — passed to the RDS module and attached to the
# database instance.
output "rds_sg_id" {
  value = aws_security_group.rds.id
}
