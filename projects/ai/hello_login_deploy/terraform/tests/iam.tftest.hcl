mock_provider "aws" {
  # aws_iam_policy_document returns null json with default mock behavior.
  # Providing a minimal valid JSON string prevents "invalid JSON policy" errors.
  mock_data "aws_iam_policy_document" {
    defaults = {
      json          = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
      minified_json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }

  # Mock IAM policies with a valid ARN. Without this, aws_iam_role_policy_attachment
  # receives a random string (e.g. "kfkml5jm") for policy_arn which fails ARN validation.
  mock_resource "aws_iam_policy" {
    defaults = {
      arn = "arn:aws:iam::123456789012:policy/mock-policy"
    }
  }
}

variables {
  aws_region        = "us-west-2"
  aws_account_id    = "277070500859"
  cluster_name      = "hello-login-staging"
  oidc_provider_arn = "arn:aws:iam::277070500859:oidc-provider/oidc.eks.us-west-2.amazonaws.com/id/MOCK"
  oidc_provider_url = "https://oidc.eks.us-west-2.amazonaws.com/id/MOCK"
  secret_arn        = "arn:aws:secretsmanager:us-west-2:277070500859:secret:hello-login/staging-MOCK"
  github_org        = "gxozer"
  github_repo       = "hello_login_deploy"
}

run "alb_role_name_includes_cluster" {
  module {
    source = "./modules/iam"
  }

  assert {
    condition     = aws_iam_role.alb_controller.name == "hello-login-staging-alb-controller"
    error_message = "ALB controller role name should include cluster name"
  }
}

run "eso_role_name_includes_cluster" {
  module {
    source = "./modules/iam"
  }

  assert {
    condition     = aws_iam_role.eso.name == "hello-login-staging-eso"
    error_message = "ESO role name should include cluster name"
  }
}

run "github_actions_role_name_includes_cluster" {
  module {
    source = "./modules/iam"
  }

  assert {
    condition     = aws_iam_role.github_actions.name == "hello-login-staging-github-actions"
    error_message = "GitHub Actions role name should include cluster name"
  }
}
