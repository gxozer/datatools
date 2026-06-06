mock_provider "aws" {
  # Mock aws_availability_zones so slice(names, 0, 3) doesn't fail on an empty list.
  # The real provider returns AZ names from AWS; the mock returns none by default.
  mock_data "aws_availability_zones" {
    defaults = {
      names = ["us-west-2a", "us-west-2b", "us-west-2c"]
    }
  }
}

variables {
  environment = "staging"
  vpc_cidr    = "10.0.0.0/16"
}

run "vpc_cidr_is_correct" {
  module {
    source = "./modules/networking"
  }

  assert {
    condition     = aws_vpc.this.cidr_block == "10.0.0.0/16"
    error_message = "VPC CIDR should match var.vpc_cidr"
  }
}

run "creates_three_public_subnets" {
  module {
    source = "./modules/networking"
  }

  assert {
    condition     = length(aws_subnet.public) == 3
    error_message = "Expected 3 public subnets"
  }
}

run "creates_three_private_subnets" {
  module {
    source = "./modules/networking"
  }

  assert {
    condition     = length(aws_subnet.private) == 3
    error_message = "Expected 3 private subnets"
  }
}

run "public_subnets_have_elb_tag" {
  module {
    source = "./modules/networking"
  }

  assert {
    condition     = aws_subnet.public[0].tags["kubernetes.io/role/elb"] == "1"
    error_message = "Public subnets should have kubernetes.io/role/elb=1 tag"
  }
}

run "private_subnets_have_internal_elb_tag" {
  module {
    source = "./modules/networking"
  }

  assert {
    condition     = aws_subnet.private[0].tags["kubernetes.io/role/internal-elb"] == "1"
    error_message = "Private subnets should have kubernetes.io/role/internal-elb=1 tag"
  }
}

run "rds_sg_exists" {
  module {
    source = "./modules/networking"
  }

  # The RDS SG has no inline ingress rules — the port 3306 rule is added in the
  # root main.tf as aws_security_group_rule.rds_from_eks_cluster (separate from
  # this module) to avoid mixing inline ingress blocks with aws_security_group_rule
  # on the same SG, which causes AWS provider conflicts. Verify the SG is created.
  assert {
    condition     = aws_security_group.rds.name == "hello-login-staging-rds"
    error_message = "RDS security group should be created with the correct name"
  }
}
