mock_provider "aws" {
  # aws_iam_policy_document returns null json with default mock behavior.
  # Providing a minimal valid JSON string prevents "invalid JSON policy" errors.
  mock_data "aws_iam_policy_document" {
    defaults = {
      json          = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
      minified_json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }

  # Mock IAM roles with a valid ARN format. Without this the mock generates a
  # random string (e.g. "26zcz840") which fails aws_eks_cluster's ARN validation
  # on the role_arn field.
  mock_resource "aws_iam_role" {
    defaults = {
      arn = "arn:aws:iam::123456789012:role/mock-role"
    }
  }

  # Mock IAM policy attachments similarly to avoid cascading ARN errors.
  mock_resource "aws_iam_role_policy_attachment" {
    defaults = {
      id = "mock-attachment"
    }
  }

  # Mock the EKS cluster with populated identity and certificate_authority blocks.
  # These are computed by AWS after cluster creation and empty in the default mock,
  # which causes index errors on identity[0].oidc[0].issuer and
  # certificate_authority[0].data used by the OIDC provider and outputs.
  mock_resource "aws_eks_cluster" {
    defaults = {
      endpoint = "https://MOCK.gr7.us-west-2.eks.amazonaws.com"
      identity = [
        {
          oidc = [{ issuer = "https://oidc.eks.us-west-2.amazonaws.com/id/MOCK" }]
        }
      ]
      certificate_authority = [{ data = "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0t" }]
    }
  }
}

mock_provider "tls" {
  # tls_certificate returns an empty certificates list with default mock behavior.
  # Providing a mock sha1_fingerprint prevents the index error on
  # data.tls_certificate.eks_oidc.certificates[0].sha1_fingerprint
  # used when creating the OIDC identity provider.
  mock_data "tls_certificate" {
    defaults = {
      certificates = [
        {
          sha1_fingerprint     = "0000000000000000000000000000000000000000"
          cert_pem             = "mock"
          is_ca                = true
          issuer               = "mock-issuer"
          not_after            = "2030-01-01T00:00:00Z"
          not_before           = "2020-01-01T00:00:00Z"
          public_key_algorithm = "RSA"
          serial_number        = "1"
          signature_algorithm  = "SHA256WithRSA"
          subject              = "mock-subject"
          version              = 3
        }
      ]
    }
  }
}

variables {
  environment        = "staging"
  private_subnet_ids = ["subnet-mock-a", "subnet-mock-b", "subnet-mock-c"]
  public_subnet_ids  = ["subnet-mock-pub-a", "subnet-mock-pub-b", "subnet-mock-pub-c"]
  eks_node_sg_id     = "sg-mock"
  node_type          = "t3.small"
  min_nodes          = 2
  max_nodes          = 6
}

run "cluster_name_includes_environment" {
  module {
    source = "./modules/eks"
  }

  assert {
    condition     = aws_eks_cluster.this.name == "hello-login-staging"
    error_message = "Cluster name should be hello-login-{environment}"
  }
}

run "cluster_version_is_1_29" {
  module {
    source = "./modules/eks"
  }

  assert {
    condition     = aws_eks_cluster.this.version == "1.29"
    error_message = "Cluster version should be 1.29"
  }
}

run "node_group_uses_al2_ami" {
  module {
    source = "./modules/eks"
  }

  assert {
    condition     = aws_eks_node_group.this.ami_type == "AL2_x86_64"
    error_message = "Node group should use AL2_x86_64 AMI"
  }
}

run "node_group_min_max_nodes" {
  module {
    source = "./modules/eks"
  }

  assert {
    condition     = aws_eks_node_group.this.scaling_config[0].min_size == 2
    error_message = "min_nodes should be 2"
  }

  assert {
    condition     = aws_eks_node_group.this.scaling_config[0].max_size == 6
    error_message = "max_nodes should be 6"
  }
}

run "node_group_uses_private_subnets" {
  module {
    source = "./modules/eks"
  }

  assert {
    condition     = length(aws_eks_node_group.this.subnet_ids) == 3
    error_message = "Node group should use 3 private subnets"
  }
}
