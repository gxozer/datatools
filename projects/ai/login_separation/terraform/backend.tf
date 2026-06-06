# Remote state backend — OPTIONAL for solo use, REQUIRED for teams and CI/CD (Continuous Integration/Delivery).
#
# When this block is commented out (the default), Terraform stores state in a
# local file: terraform/terraform.tfstate. That is fine if you are the only
# person applying infrastructure from your laptop.
#
# Uncomment this block when:
#   - More than one person will run terraform apply
#   - A CI/CD pipeline (e.g. GitHub Actions) needs to apply infrastructure
#   - You want state backed up and versioned in S3
#
# Before uncommenting, create the S3 bucket once:
#   aws s3 mb s3://hello-login-tfstate --region us-west-2
#   aws s3api put-bucket-versioning \
#     --bucket hello-login-tfstate \
#     --versioning-configuration Status=Enabled
#
# Then run terraform init — it will offer to migrate your local state into S3.
#
# terraform {
#   backend "s3" {
#     bucket       = "hello-login-tfstate"
#     key          = "hello-login/terraform.tfstate"
#     region       = "us-west-2"
#     use_lockfile = true   # S3 native locking prevents concurrent applies corrupting state — no DynamoDB (NoSQL database) table needed
#     encrypt      = true   # AES-256 encryption at rest — state can contain sensitive values like RDS passwords
#   }
# }
