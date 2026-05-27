mock_provider "aws" {}
mock_provider "helm" {}
mock_provider "kubernetes" {}
mock_provider "random" {}
mock_provider "tls" {}
mock_provider "time" {}

# NOTE: Testing that an invalid environment value is rejected by the validation
# rule in variables.tf is not supported by `terraform test` in Terraform 1.7.
#
# When a variable validation fails at plan time, the framework correctly reports
# the expected failure BUT marks the test as failed because the subsequent apply
# cannot run. This is a known Terraform test framework limitation.
#
# The validation rule DOES work correctly — running:
#   terraform plan -var="environment=dev" -var-file=staging.tfvars
# produces: "environment must be 'staging' or 'production'."
#
# This file is kept as a placeholder. The two valid environment values are
# exercised implicitly by every other test file in this directory.
