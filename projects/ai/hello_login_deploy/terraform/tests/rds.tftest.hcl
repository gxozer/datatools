mock_provider "aws" {}
mock_provider "random" {}

variables {
  environment        = "staging"
  vpc_id             = "vpc-mock"
  private_subnet_ids = ["subnet-mock-a", "subnet-mock-b", "subnet-mock-c"]
  rds_sg_id          = "sg-mock"
  instance_class     = "db.t3.micro"
  prevent_destroy    = false
}

run "db_identifier_includes_environment" {
  module {
    source = "./modules/rds"
  }

  assert {
    condition     = aws_db_instance.this.identifier == "hello-login-staging"
    error_message = "DB identifier should be hello-login-{environment}"
  }
}

run "db_engine_is_mysql_8" {
  module {
    source = "./modules/rds"
  }

  assert {
    condition     = aws_db_instance.this.engine == "mysql"
    error_message = "DB engine should be mysql"
  }

  assert {
    condition     = aws_db_instance.this.engine_version == "8.0"
    error_message = "DB engine version should be 8.0"
  }
}

run "db_storage_is_encrypted" {
  module {
    source = "./modules/rds"
  }

  assert {
    condition     = aws_db_instance.this.storage_encrypted == true
    error_message = "RDS storage should be encrypted"
  }
}

run "staging_deletion_protection_off" {
  module {
    source = "./modules/rds"
  }

  assert {
    condition     = aws_db_instance.this.deletion_protection == false
    error_message = "staging should have deletion_protection=false"
  }
}

run "production_deletion_protection_on" {
  variables {
    environment     = "production"
    prevent_destroy = true
  }

  module {
    source = "./modules/rds"
  }

  assert {
    condition     = aws_db_instance.this.deletion_protection == true
    error_message = "production should have deletion_protection=true"
  }
}

run "backup_retention_is_7_days" {
  module {
    source = "./modules/rds"
  }

  assert {
    condition     = aws_db_instance.this.backup_retention_period == 7
    error_message = "Backup retention should be 7 days"
  }
}
