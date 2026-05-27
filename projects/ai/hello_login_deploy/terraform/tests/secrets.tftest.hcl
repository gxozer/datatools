mock_provider "aws" {}

variables {
  environment = "staging"
}

run "secret_path_includes_environment" {
  module {
    source = "./modules/secrets"
  }

  assert {
    condition     = aws_secretsmanager_secret.this.name == "hello-login/staging"
    error_message = "Secret name should be hello-login/{environment}"
  }
}

run "production_secret_path" {
  variables {
    environment = "production"
  }

  module {
    source = "./modules/secrets"
  }

  assert {
    condition     = aws_secretsmanager_secret.this.name == "hello-login/production"
    error_message = "Production secret name should be hello-login/production"
  }
}
