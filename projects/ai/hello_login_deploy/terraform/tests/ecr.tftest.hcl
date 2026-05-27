mock_provider "aws" {}

variables {
  environment = "staging"
}

run "creates_backend_repo" {
  module {
    source = "./modules/ecr"
  }

  assert {
    condition     = aws_ecr_repository.this["hello-login-backend"].name == "hello-login-backend"
    error_message = "Backend ECR repo should be named hello-login-backend"
  }
}

run "creates_frontend_repo" {
  module {
    source = "./modules/ecr"
  }

  assert {
    condition     = aws_ecr_repository.this["hello-login-frontend"].name == "hello-login-frontend"
    error_message = "Frontend ECR repo should be named hello-login-frontend"
  }
}

run "scan_on_push_enabled" {
  module {
    source = "./modules/ecr"
  }

  assert {
    condition     = aws_ecr_repository.this["hello-login-backend"].image_scanning_configuration[0].scan_on_push == true
    error_message = "Scan on push should be enabled"
  }
}

run "lifecycle_policies_exist" {
  module {
    source = "./modules/ecr"
  }

  assert {
    condition     = length(aws_ecr_lifecycle_policy.this) == 2
    error_message = "Expected lifecycle policies for both repos"
  }
}
