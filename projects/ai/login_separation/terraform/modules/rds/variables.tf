# Inputs this module accepts from the root main.tf.

# Used to name the DB instance and subnet group, e.g. "hello-login-staging".
# Also determines the Secrets Manager path: hello-login/<environment>.
variable "environment" {
  type = string
}

# The private subnet IDs where the DB subnet group is created.
# RDS places the database in one of these subnets. Using private subnets
# means the database has no public endpoint.
variable "private_subnet_ids" {
  type = list(string)
}

# Security group ID from the networking module. This SG allows MySQL (3306)
# only from EKS nodes — the database is not reachable from anywhere else.
variable "rds_sg_id" {
  type = string
}

# RDS instance class, e.g. "db.t3.micro". Controls CPU and memory.
variable "instance_class" {
  type = string
}

# When true: deletion_protection=true on the DB instance (production).
#   AWS refuses to delete the instance even with terraform destroy.
# When false: deletion_protection=false (staging).
#   terraform destroy completes cleanly — useful for overnight teardown.
variable "prevent_destroy" {
  type = bool
}
