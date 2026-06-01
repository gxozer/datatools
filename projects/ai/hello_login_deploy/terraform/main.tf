# main.tf — the root composition file.
#
# ── Acronym glossary ──────────────────────────────────────────────────────────
# ALB   Application Load Balancer — AWS load balancer that routes HTTP/HTTPS traffic
# AMI   Amazon Machine Image — pre-built OS image used to launch EC2 instances
# API   Application Programming Interface — a contract for how software talks to software
# ARN   Amazon Resource Name — a unique identifier for any AWS resource
#         format: arn:aws:service:region:account-id:resource
# AZ    Availability Zone — an isolated data centre within an AWS region
#         us-west-2 has zones: us-west-2a, us-west-2b, us-west-2c, us-west-2d
# CIDR  Classless Inter-Domain Routing — a notation for IP address ranges
#         e.g. 10.0.0.0/16 means the range 10.0.0.0 – 10.0.255.255 (65 536 addresses)
# CI/CD Continuous Integration / Continuous Delivery — automated build, test, deploy pipeline
# CNI   Container Network Interface — a plugin standard for pod networking in Kubernetes
# CSI   Container Storage Interface — a plugin standard for persistent storage in Kubernetes
# CVE   Common Vulnerabilities and Exposures — a public database of known security flaws
# DNS   Domain Name System — translates hostnames (e.g. example.com) to IP addresses
# EBS   Elastic Block Store — AWS persistent SSD volumes, mountable by EC2/EKS
# ECR   Elastic Container Registry — AWS private Docker image registry
# EIP   Elastic IP — a static public IPv4 address in AWS
# EKS   Elastic Kubernetes Service — AWS managed Kubernetes control plane
# ESO   External Secrets Operator — Kubernetes controller that syncs AWS Secrets Manager
#         values into Kubernetes Secrets
# gp2   General Purpose SSD v2 — AWS EBS storage type, balanced price/performance
# HA    High Availability — designing systems to continue working during partial failures
# HCL   HashiCorp Configuration Language — the language Terraform files are written in
# HTTP  HyperText Transfer Protocol — the protocol for web traffic (unencrypted)
# HTTPS HTTP Secure — HTTP encrypted with TLS
# IAM   Identity and Access Management — AWS service for controlling who can do what
# IGW   Internet Gateway — VPC component that enables communication with the internet
# IRSA  IAM Roles for Service Accounts — mechanism that gives individual Kubernetes pods
#         their own AWS IAM role instead of sharing the node's role
# JSON  JavaScript Object Notation — a text format for structured data
# JWT   JSON Web Token — a signed, self-contained token used for authentication
# NAT   Network Address Translation — allows private resources to make outbound internet
#         connections without having a public IP (via a NAT Gateway)
# OIDC  OpenID Connect — an identity layer on top of OAuth 2.0; used here for
#         federated authentication between EKS/GitHub and AWS IAM
# RDS   Relational Database Service — AWS managed database (MySQL, Postgres, etc.)
# SG    Security Group — AWS virtual firewall that controls inbound/outbound traffic
# STS   Security Token Service — AWS service that issues temporary credentials when
#         an IAM role is assumed
# TLS   Transport Layer Security — the encryption protocol used in HTTPS connections
# URL   Uniform Resource Locator — a web address
# VPC   Virtual Private Cloud — an isolated private network in AWS
#
# This file does not create any resources directly. Its job is to call each
# module, pass in the inputs they need, and wire their outputs together.
# Think of it as the conductor: it knows about every module and connects them.
#
# Execution order (Terraform derives this from the references below):
#   1. data.aws_caller_identity  — read-only AWS lookup, no dependencies
#   2. module.networking         — must run first; every other module needs its outputs
#   3. module.eks / module.rds / module.ecr / module.secrets — run in parallel after networking
#   4. module.iam                — runs after eks (needs the OIDC provider ARN)
#   5. module.helm_addons        — runs last; installs software into the cluster

# Fetches the AWS account ID of whoever is running Terraform.
# Used in module.iam to build IAM ARNs like:
#   arn:aws:iam::<account_id>:role/...
# Using a data source instead of hardcoding the account ID means this code
# works in any AWS account without changes.
data "aws_caller_identity" "current" {}

# ── Networking ────────────────────────────────────────────────────────────────
# Creates the VPC, subnets, NAT gateway, internet gateway, route tables,
# and security groups. Everything else runs inside this network, so this
# module must complete before any other module can start.
module "networking" {
  source = "./modules/networking"

  environment = var.environment  # used in resource names, e.g. "hello-login-staging"
  vpc_cidr    = var.vpc_cidr     # e.g. "10.0.0.0/16" for staging
}

# ── EKS ───────────────────────────────────────────────────────────────────────
# Creates the Kubernetes cluster, worker nodes, IAM roles, cluster add-ons,
# and the OIDC provider (required for IRSA — pod-level IAM roles).
# Runs in parallel with module.rds, module.ecr, and module.secrets.
module "eks" {
  source = "./modules/eks"

  environment         = var.environment
  private_subnet_ids  = module.networking.private_subnet_ids  # nodes go in private subnets
  public_subnet_ids   = module.networking.public_subnet_ids   # passed to vpc_config so the control plane can communicate
  eks_node_sg_id      = module.networking.eks_node_sg_id      # security group assigned to worker nodes
  node_type           = var.eks_node_type                     # e.g. "t3.small"
  min_nodes           = var.eks_min_nodes                     # cluster autoscaler lower bound
  max_nodes           = var.eks_max_nodes                     # cluster autoscaler upper bound
  public_access_cidrs = var.eks_public_access_cidrs           # restrict Kubernetes API endpoint access
}

# ── ECR ───────────────────────────────────────────────────────────────────────
# Creates the Docker image registries for backend and frontend.
# Runs in parallel with module.eks, module.rds, and module.secrets.
# ECR repos are shared across environments — the same repo holds images for
# both staging and production, distinguished by image tag.
module "ecr" {
  source = "./modules/ecr"

  create_repos = var.ecr_create_repos  # false for production — repos owned by staging state
}

# ── RDS ───────────────────────────────────────────────────────────────────────
# Creates the MySQL database, subnet group, and parameter group.
# Runs in parallel with module.eks, module.ecr, and module.secrets.
module "rds" {
  source = "./modules/rds"

  environment        = var.environment
  private_subnet_ids = module.networking.private_subnet_ids # DB is placed in private subnets
  rds_sg_id          = module.networking.rds_sg_id          # only allows MySQL from EKS node SG
  instance_class     = var.rds_instance_class               # e.g. "db.t3.micro"
  prevent_destroy    = var.rds_prevent_destroy              # true for production — blocks deletion
}

# ── Secrets Manager ───────────────────────────────────────────────────────────
# Creates the Secrets Manager secret shell at hello-login/<environment>.
# The secret value (DATABASE_URL, JWT_SECRET, etc.) is NOT set here —
# it is populated out-of-band to keep plaintext out of Terraform state.
# Has no dependencies on other modules, so it runs in parallel with eks/rds/ecr.
module "secrets" {
  source = "./modules/secrets"

  environment = var.environment
}

# ── IAM ───────────────────────────────────────────────────────────────────────
# Creates three IAM roles using IRSA (IAM Roles for Service Accounts):
#   1. ALB Controller   — creates/manages AWS load balancers from Kubernetes Ingress objects
#   2. ESO              — reads from Secrets Manager and injects secrets into pods
#   3. GitHub Actions   — allows CI/CD to push Docker images and deploy to EKS
# Runs after module.eks because it needs the OIDC provider ARN and cluster name.
module "iam" {
  source = "./modules/iam"

  aws_region        = var.aws_region
  aws_account_id    = data.aws_caller_identity.current.account_id # from the data source above
  cluster_name      = module.eks.cluster_name                     # used in role names
  oidc_provider_arn = module.eks.oidc_provider_arn                # required for IRSA trust policies
  oidc_provider_url = module.eks.oidc_provider_url                # used to build OIDC condition keys
  secret_arn                 = module.secrets.secret_arn                   # ESO role is scoped to this secret only
  rds_master_user_secret_arn = module.rds.master_user_secret_arn           # ESO also reads the RDS-managed secret for DATABASE_URL
  github_org                  = var.github_org                              # GitHub Actions role is scoped to this org
  github_repo                 = var.github_repo                             # and this repo
  create_github_oidc_provider = var.create_github_oidc_provider             # account-global; only create once (staging)
}

# ── Helm Add-ons ─────────────────────────────────────────────────────────────
# Installs two Helm charts into the EKS cluster:
#   1. AWS Load Balancer Controller — watches for Kubernetes Ingress objects
#      and creates ALBs in AWS automatically
#   2. External Secrets Operator   — watches for ExternalSecret objects and
#      syncs values from Secrets Manager into Kubernetes Secrets
#
# depends_on is explicit here because Helm charts are installed INTO the cluster
# and configured WITH the IAM role ARNs. Neither dependency appears as a direct
# resource reference, so Terraform cannot infer it automatically.
# Allow MySQL from the EKS cluster security group to the RDS security group.
#
# Why this is here and not inside modules/networking/main.tf:
#   The networking module runs BEFORE the EKS module (EKS needs the VPC/subnets).
#   The EKS cluster security group ID is only known AFTER the cluster is created.
#   Adding this rule here in the root module avoids a circular dependency — it
#   references outputs from both networking and eks modules after both have run.
#
# Note: modules/networking/main.tf also has a rule allowing from the custom
# eks_nodes SG (sg-0f709f83ced812f1e), but EKS managed node groups use the
# auto-created cluster SG instead. This rule covers the actual traffic path.
resource "aws_security_group_rule" "rds_from_eks_cluster" {
  type                     = "ingress"
  from_port                = 3306
  to_port                  = 3306
  protocol                 = "tcp"
  security_group_id        = module.networking.rds_sg_id           # the RDS security group
  source_security_group_id = module.eks.cluster_security_group_id  # EKS auto-created cluster SG

  depends_on = [module.networking, module.eks]
}

module "helm_addons" {
  source = "./modules/helm-addons"

  cluster_name            = module.eks.cluster_name            # tells the ALB controller which cluster it's in
  aws_region              = var.aws_region                     # ALB controller needs to know its region
  vpc_id                  = module.networking.vpc_id           # ALB controller needs VPC to create load balancers
  alb_controller_role_arn = module.iam.alb_controller_role_arn # annotated onto the ALB controller service account
  eso_role_arn            = module.iam.eso_role_arn            # annotated onto the ESO service account

  depends_on = [module.eks, module.iam]
}
