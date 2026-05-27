terraform {
  # Minimum Terraform version required. 1.7 introduced mock provider support
  # for `terraform test`, which our unit test suite relies on.
  required_version = ">= 1.7"

  required_providers {
    # aws — the core provider. Used by every module to create and manage all
    # AWS resources: VPC, EKS, RDS, ECR, IAM, Secrets Manager, etc.
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }

    # helm — installs Helm charts into the EKS cluster. Used by the
    # helm-addons module to deploy the AWS Load Balancer Controller and
    # the External Secrets Operator.
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.13"
    }

    # kubernetes — communicates with the Kubernetes API. Required by the
    # helm provider internally and available for managing Kubernetes
    # resources (namespaces, config maps) directly if needed in the future.
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.30"
    }

    # random — generates random values. Used by the rds module to create
    # a secure initial database password without storing it in tfvars or
    # committing it to git.
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }

    # tls — reads TLS certificates over HTTPS. Used by the eks module to
    # fetch the OIDC (OpenID Connect — a standard for federated identity) issuer
    # certificate thumbprint from the EKS cluster, which is required when
    # registering the OIDC identity provider in IAM
    # (enables IRSA — pod-level IAM roles).
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }

    # time — provides time_sleep, used to add a delay between the ALB controller
    # Helm release completing and the ESO Helm release starting. Without this delay,
    # the ALB controller webhook has not fully registered its endpoints yet, causing
    # ESO installation to fail with "no endpoints available for service
    # aws-load-balancer-webhook-service".
    time = {
      source  = "hashicorp/time"
      version = "~> 0.11"
    }
  }
}

# Configure the AWS provider with the target region and a set of default tags.
# default_tags applies these tags to every AWS resource Terraform creates,
# so we never forget to tag something. Tags are used for cost tracking
# (filter by Project in AWS Cost Explorer) and to identify Terraform-managed
# resources vs. anything created manually.
provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "hello-login"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

# Configure the Helm provider to talk to the EKS cluster we create.
# It needs three things to authenticate:
#   host                   — the Kubernetes API server URL
#   cluster_ca_certificate — the cluster's TLS certificate (to verify we're
#                            talking to the real cluster, not an impostor)
#   token                  — a short-lived auth token (expires after 15 min;
#                            Terraform fetches a fresh one on each run)
provider "helm" {
  kubernetes {
    host                   = module.eks.cluster_endpoint
    cluster_ca_certificate = base64decode(module.eks.cluster_ca)
    token                  = data.aws_eks_cluster_auth.this.token
  }
}

# Configure the Kubernetes provider with the same credentials as Helm above.
# Both providers need to reach the same cluster API.
provider "kubernetes" {
  host                   = module.eks.cluster_endpoint
  cluster_ca_certificate = base64decode(module.eks.cluster_ca)
  token                  = data.aws_eks_cluster_auth.this.token
}

# Fetches a temporary authentication token for the EKS cluster from AWS.
# This is a data source (read-only) — it does not create anything.
# The token is used by the helm and kubernetes providers above.
data "aws_eks_cluster_auth" "this" {
  name = module.eks.cluster_name
}
