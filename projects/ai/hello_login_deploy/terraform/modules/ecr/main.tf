# modules/ecr/main.tf
#
# Creates two private Docker image registries in ECR (Elastic Container Registry):
#   - hello-login-backend
#   - hello-login-frontend
#
# ECR is AWS's private container registry. Images are pulled over the private
# network (no internet egress cost), authentication is handled via IAM, and
# images stay within your AWS account.
#
# Note: ECR repositories are shared across environments. Both staging and
# production pull from the same repos — environments are distinguished by
# image tag (e.g. "latest" for staging, "sha-abc123" for production).

locals {
  # List of repository names to create. Using a list + for_each means we
  # write the repository configuration once and it applies to both repos.
  repos = ["hello-login-backend", "hello-login-frontend"]

  # The lifecycle policy is defined once here and applied to both repositories.
  # jsonencode() converts the HCL map into the JSON string that the ECR API expects.
  #
  # Two rules:
  #   Rule 1 (priority 1 — checked first): delete untagged images after 1 day.
  #     Every CI build produces an image. Many are never tagged for release.
  #     Without this rule, the registry fills up with abandoned build artifacts.
  #
  #   Rule 2 (priority 2): keep only the last 10 tagged images.
  #     "Tagged" means images whose tag starts with "v" or "sha-".
  #     Keeps recent releases available for rollback while discarding old ones.
  lifecycle_policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images after 1 day"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 1
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 2
        description  = "Keep last 10 tagged images"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["v", "sha-"]   # only count images tagged with these prefixes
          countType     = "imageCountMoreThan"
          countNumber   = 10
        }
        action = { type = "expire" }
      }
    ]
  })
}

# Creates one ECR repository for each name in the repos list.
# Only the first environment to apply should create ECR repos (staging by default)
# because repos are account-global and cannot be managed by multiple states.
# Set create_repos = false in production.tfvars; production will use data sources.
# for_each iterates over a set (toset converts the list to a set to remove
# duplicates and give each item a stable key).
# each.key is the repository name (e.g. "hello-login-backend").
resource "aws_ecr_repository" "this" {
  for_each = var.create_repos ? toset(local.repos) : toset([])

  name = each.key

  # MUTABLE means image tags can be overwritten (e.g. "latest" can be pushed
  # again). Set to IMMUTABLE if you want to enforce that tags are write-once.
  image_tag_mutability = "MUTABLE"

  # force_delete allows terraform destroy to delete the repo even when it
  # contains images. Without this, destroy fails if any images have been pushed.
  force_delete = true

  image_scanning_configuration {
    # Automatically scan each image for known CVEs (Common Vulnerabilities and Exposures — publicly disclosed security flaws) when it is pushed.
    # Results appear in the ECR console. No extra cost.
    scan_on_push = true
  }

  lifecycle {
    # Repos are shared across environments — never allow terraform destroy to
    # delete them accidentally. Remove this protection manually only when
    # decommissioning the entire project.
    prevent_destroy = true
  }
}

# When create_repos = false (production), look up the repos that staging created.
data "aws_ecr_repository" "existing" {
  for_each = var.create_repos ? toset([]) : toset(local.repos)
  name     = each.key
}

# Attaches the lifecycle policy to each repository we own (create_repos = true).
# When create_repos = false (production), staging owns the lifecycle policy.
resource "aws_ecr_lifecycle_policy" "this" {
  for_each = aws_ecr_repository.this

  repository = each.value.name   # each.value is the aws_ecr_repository resource
  policy     = local.lifecycle_policy
}
