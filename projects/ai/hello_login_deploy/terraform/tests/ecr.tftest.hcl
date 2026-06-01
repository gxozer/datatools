mock_provider "aws" {}

variables {
  environment = "staging"
}

# All ECR runs use command = plan to avoid apply/teardown.
# prevent_destroy = true on aws_ecr_repository blocks teardown even with mock
# providers, causing the test suite to fail. Since all assertions check values
# that are fully known at plan time (names, settings), plan is sufficient.

run "creates_backend_repo" {
  command = plan

  module {
    source = "./modules/ecr"
  }

  assert {
    condition     = aws_ecr_repository.this["hello-login-backend"].name == "hello-login-backend"
    error_message = "Backend ECR repo should be named hello-login-backend"
  }
}

run "creates_frontend_repo" {
  command = plan

  module {
    source = "./modules/ecr"
  }

  assert {
    condition     = aws_ecr_repository.this["hello-login-frontend"].name == "hello-login-frontend"
    error_message = "Frontend ECR repo should be named hello-login-frontend"
  }
}

run "scan_on_push_enabled" {
  command = plan

  module {
    source = "./modules/ecr"
  }

  assert {
    condition     = aws_ecr_repository.this["hello-login-backend"].image_scanning_configuration[0].scan_on_push == true
    error_message = "Scan on push should be enabled"
  }
}

run "lifecycle_policies_exist" {
  command = plan

  module {
    source = "./modules/ecr"
  }

  assert {
    condition     = length(aws_ecr_lifecycle_policy.this) == 2
    error_message = "Expected lifecycle policies for both repos"
  }
}
