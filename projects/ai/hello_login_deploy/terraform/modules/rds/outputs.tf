# Outputs exposed to the root main.tf.

# The DB connection endpoint — the hostname used in the DATABASE_URL secret.
# Format: hello-login-staging.<hash>.<region>.rds.amazonaws.com:3306
# Used to construct: mysql+pymysql://admin:<pw>@<endpoint>/hello_login
output "db_endpoint" {
  value = aws_db_instance.this.endpoint
}

# The MySQL port (always 3306 for MySQL).
output "db_port" {
  value = aws_db_instance.this.port
}

# The database name created on first boot — "hello_login".
output "db_name" {
  value = aws_db_instance.this.db_name
}

# ARN of the Secrets Manager secret that RDS automatically creates for the master
# password (rds!db-<instance-id>-admin). Use this to grant application roles
# read access to the DB credential.
output "master_user_secret_arn" {
  value = aws_db_instance.this.master_user_secret[0].secret_arn
}
