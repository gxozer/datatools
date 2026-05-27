# modules/iam/main.tf
#
# Creates three IAM (Identity and Access Management) roles using
# IRSA (IAM Roles for Service Accounts).
#
# IRSA background:
#   Normally, all pods on an EC2 (Elastic Compute Cloud) node share the node's
#   IAM role — too broad, violates least-privilege principle.
#   IRSA lets individual pods assume a specific, scoped IAM role instead.
#   It works through the EKS OIDC (OpenID Connect) provider registered in the eks module:
#     1. A pod's Kubernetes service account is annotated with a role ARN (Amazon Resource Name).
#     2. The pod requests a JWT (JSON Web Token) from the EKS OIDC issuer.
#     3. AWS STS (Security Token Service) validates the token and checks the role's trust policy.
#     4. The trust policy condition verifies the token is for the correct
#        namespace and service account — if it matches, the role is assumed
#        and STS issues temporary AWS credentials to the pod.
#
# The three roles created here:
#   1. ALB (Application Load Balancer) Controller — creates AWS load balancers from Kubernetes Ingress objects
#   2. ESO (External Secrets Operator)            — reads secrets from Secrets Manager into Kubernetes Secrets
#   3. GitHub Actions                             — allows CI/CD (Continuous Integration/Delivery) to push images to ECR and deploy to EKS

locals {
  # Strip "https://" from the OIDC URL to build the IAM condition key.
  # The key format is: "<host>:sub" and "<host>:aud"
  # e.g. "oidc.eks.us-west-2.amazonaws.com/id/ABC123:sub"
  oidc_host = replace(var.oidc_provider_url, "https://", "")
}

# ── ALB Controller ────────────────────────────────────────────────────────────
#
# The AWS Load Balancer Controller watches for Kubernetes Ingress objects and
# creates/manages Application Load Balancers in AWS automatically.
# It needs IAM permissions to create, describe, and delete load balancers,
# target groups, listeners, and related security groups.

# Trust policy — who is allowed to assume this role?
# Answer: only the service account "aws-load-balancer-controller"
#         in namespace "kube-system" on THIS EKS cluster.
data "aws_iam_policy_document" "alb_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    effect  = "Allow"

    # The OIDC provider registered for this EKS cluster.
    principals {
      type        = "Federated"
      identifiers = [var.oidc_provider_arn]
    }

    # "sub" (subject) claim — must match the exact service account.
    # Format: system:serviceaccount:<namespace>:<serviceaccount-name>
    condition {
      test     = "StringEquals"
      variable = "${local.oidc_host}:sub"
      values   = ["system:serviceaccount:kube-system:aws-load-balancer-controller"]
    }

    # "aud" (audience) claim — must be the AWS STS service.
    condition {
      test     = "StringEquals"
      variable = "${local.oidc_host}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "alb_controller" {
  name               = "${var.cluster_name}-alb-controller"
  assume_role_policy = data.aws_iam_policy_document.alb_assume.json
}

# The permissions policy — what can this role do?
# The full policy JSON is in policies/alb-controller.json (from AWS docs).
# It grants the minimum permissions needed to manage ALBs: describe/create/delete
# load balancers, target groups, listeners, security groups, and related tags.
resource "aws_iam_policy" "alb_controller" {
  name   = "${var.cluster_name}-alb-controller"
  policy = file("${path.module}/policies/alb-controller.json")
}

resource "aws_iam_role_policy_attachment" "alb_controller" {
  role       = aws_iam_role.alb_controller.name
  policy_arn = aws_iam_policy.alb_controller.arn
}

# ── External Secrets Operator ─────────────────────────────────────────────────
#
# The External Secrets Operator (ESO) watches for ExternalSecret objects in
# Kubernetes and syncs values from AWS Secrets Manager into Kubernetes Secrets.
# It needs permission to read the application secret for its environment.

# Trust policy — scoped to the "external-secrets" service account
#                in namespace "external-secrets".
data "aws_iam_policy_document" "eso_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    effect  = "Allow"

    principals {
      type        = "Federated"
      identifiers = [var.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_host}:sub"
      values   = ["system:serviceaccount:external-secrets:external-secrets"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_host}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

# Permissions policy — what can ESO do?
# GetSecretValue: read the secret value (the actual DATABASE_URL, JWT_SECRET, etc.)
# DescribeSecret: read metadata about the secret (required by ESO to check for updates)
# Resource scoped to "${var.secret_arn}*" — the * covers the version suffix that
# AWS appends to the ARN (e.g. hello-login/staging-ABCDEF123).
data "aws_iam_policy_document" "eso" {
  statement {
    actions   = ["secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret"]
    resources = ["${var.secret_arn}*"]  # * matches the version suffix in the ARN
  }
}

resource "aws_iam_role" "eso" {
  name               = "${var.cluster_name}-eso"
  assume_role_policy = data.aws_iam_policy_document.eso_assume.json
}

resource "aws_iam_policy" "eso" {
  name   = "${var.cluster_name}-eso"
  policy = data.aws_iam_policy_document.eso.json
}

resource "aws_iam_role_policy_attachment" "eso" {
  role       = aws_iam_role.eso.name
  policy_arn = aws_iam_policy.eso.arn
}

# ── GitHub Actions OIDC ───────────────────────────────────────────────────────
#
# This role allows GitHub Actions CI/CD workflows to:
#   - Push Docker images to ECR (for deployments)
#   - Describe and update the EKS cluster (for kubectl access)
#
# GitHub Actions uses its own OIDC (OpenID Connect) provider at
# token.actions.githubusercontent.com. When a workflow runs, GitHub issues a
# short-lived JWT (JSON Web Token). The workflow exchanges this token for
# temporary AWS credentials via STS (Security Token Service) — no long-lived
# AWS credentials stored in GitHub secrets.

# Trust policy — who can assume this role?
# Only GitHub Actions workflows from the specific org/repo defined in variables.
data "aws_iam_policy_document" "github_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    effect  = "Allow"

    principals {
      type        = "Federated"
      # The GitHub Actions OIDC provider registered below.
      identifiers = ["arn:aws:iam::${var.aws_account_id}:oidc-provider/token.actions.githubusercontent.com"]
    }

    # "sub" claim from GitHub's token: "repo:<org>/<repo>:<ref>"
    # StringLike with * allows any branch, tag, or environment within this repo.
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_org}/${var.github_repo}:*"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

# Permissions policy — what can GitHub Actions do?
data "aws_iam_policy_document" "github_actions" {
  # ECRAuth: get a Docker login token. Required before any docker push/pull.
  # Must be *, cannot be scoped to a specific repo.
  statement {
    sid       = "ECRAuth"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  # ECRPush: push and pull images to/from the two hello-login repos.
  # Scoped to exactly these two repositories — not the entire ECR registry.
  statement {
    sid = "ECRPush"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:CompleteLayerUpload",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
    ]
    resources = [
      "arn:aws:ecr:${var.aws_region}:${var.aws_account_id}:repository/hello-login-backend",
      "arn:aws:ecr:${var.aws_region}:${var.aws_account_id}:repository/hello-login-frontend",
    ]
  }

  # EKS: describe the cluster (to configure kubectl) and update add-ons.
  # Scoped to this specific cluster only.
  statement {
    sid = "EKS"
    actions = [
      "eks:DescribeCluster",
      "eks:UpdateAddon",
    ]
    resources = ["arn:aws:eks:${var.aws_region}:${var.aws_account_id}:cluster/${var.cluster_name}"]
  }
}

resource "aws_iam_role" "github_actions" {
  name               = "${var.cluster_name}-github-actions"
  assume_role_policy = data.aws_iam_policy_document.github_assume.json
}

resource "aws_iam_policy" "github_actions" {
  name   = "${var.cluster_name}-github-actions"
  policy = data.aws_iam_policy_document.github_actions.json
}

resource "aws_iam_role_policy_attachment" "github_actions" {
  role       = aws_iam_role.github_actions.name
  policy_arn = aws_iam_policy.github_actions.arn
}

# ── GitHub Actions OIDC Provider ─────────────────────────────────────────────
#
# Registers GitHub's OIDC issuer as a trusted identity provider in this AWS account.
# This is a GLOBAL, PER-ACCOUNT resource — only one should exist per account.
#
# The thumbprint is a fixed value published by GitHub — it is the fingerprint
# of the root CA certificate for token.actions.githubusercontent.com.
resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}
