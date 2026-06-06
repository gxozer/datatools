# modules/rds/main.tf
#
# Creates a MySQL 8.0 database on AWS RDS (Relational Database Service — AWS managed database):
#   - DB subnet group  (tells RDS which subnets it may place the instance in)
#   - DB parameter group  (MySQL configuration — using defaults)
#   - DB instance  (the actual database; password managed by AWS Secrets Manager via manage_master_user_password)

# ── DB Subnet Group ───────────────────────────────────────────────────────────
#
# A subnet group is a required RDS construct — it tells RDS which subnets
# it is allowed to place the database instance in. We use the private subnets
# so the database has no public endpoint and is only reachable from inside the VPC.
# If Multi-AZ is enabled later, RDS will automatically place the standby replica
# in a different subnet (different AZ) from this group.
resource "aws_db_subnet_group" "this" {
  name       = "hello-login-${var.environment}"
  subnet_ids = var.private_subnet_ids

  tags = {
    Name = "hello-login-${var.environment}"
  }
}

# ── Parameter Group ───────────────────────────────────────────────────────────
#
# A parameter group holds MySQL server configuration (like my.cnf).
# We use the default MySQL 8.0 settings. A custom parameter group is created
# anyway because it allows us to change settings in the future without
# recreating the DB instance (changing the default group would require recreation).
resource "aws_db_parameter_group" "this" {
  name   = "hello-login-${var.environment}-mysql8"
  family = "mysql8.0" # must match the engine version

  tags = {
    Name = "hello-login-${var.environment}-mysql8"
  }
}

# ── DB Instance ───────────────────────────────────────────────────────────────

resource "aws_db_instance" "this" {
  identifier = "hello-login-${var.environment}" # name shown in the AWS console

  engine         = "mysql"
  engine_version = "8.0"
  instance_class = var.instance_class # e.g. "db.t3.micro"

  # Storage: 20 GB gp2 (General Purpose SSD v2 — balanced price/performance EBS volume). AWS auto-scales this upward
  # if needed (with autoscaling enabled, which is the default on RDS).
  allocated_storage = 20
  storage_type      = "gp2"

  # The database name created inside MySQL on first boot.
  # Application code connects with: mysql+pymysql://admin:<pw>@<endpoint>/hello_login
  db_name  = "hello_login"
  username = "admin"

  # manage_master_user_password delegates password generation and rotation to AWS.
  # RDS creates a Secrets Manager secret automatically (rds!db-<id>-admin).
  # The plaintext password never appears in Terraform state.
  manage_master_user_password = true

  parameter_group_name   = aws_db_parameter_group.this.name
  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [var.rds_sg_id] # only allows MySQL from EKS nodes

  # Keep 7 days of automated daily snapshots. Enables point-in-time recovery
  # to any second within the retention window.
  backup_retention_period = 7

  # skip_final_snapshot: when false, RDS takes a final snapshot before deleting.
  # For staging (prevent_destroy=false) we skip it to allow clean terraform destroy.
  # For production (prevent_destroy=true) we take a final snapshot as a safety net.
  skip_final_snapshot = var.prevent_destroy ? false : true

  # deletion_protection: when true, AWS refuses to delete this instance even
  # if terraform destroy is run. Guards against accidental production data loss.
  deletion_protection = var.prevent_destroy

  # Multi-AZ creates a synchronous standby replica in a different AZ.
  # Disabled for cost — can be enabled for production HA if needed.
  multi_az = false

  # No public endpoint — the DB is only reachable from within the VPC.
  publicly_accessible = false

  # Encrypt the database storage at rest. Zero performance impact on MySQL 8.0.
  # Required for compliance and a security best practice regardless.
  storage_encrypted = true

  # Propagate resource tags to automated snapshots.
  copy_tags_to_snapshot = true

  # Automatically apply minor engine version patches (e.g. 8.0.33 -> 8.0.34).
  # Major version upgrades are never applied automatically.
  auto_minor_version_upgrade = true

  tags = {
    Name = "hello-login-${var.environment}"
  }

}
