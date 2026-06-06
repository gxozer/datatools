# modules/eks/main.tf
#
# Creates the EKS (Elastic Kubernetes Service) cluster and everything it needs:
#   - IAM (Identity and Access Management) role for the cluster control plane
#   - The EKS cluster itself
#   - IAM role for worker nodes (with 4 required policy attachments)
#   - Managed node group (the EC2 worker nodes)
#   - 4 EKS add-ons (coredns, kube-proxy, vpc-cni, ebs-csi-driver)
#   - OIDC (OpenID Connect) identity provider — enables IRSA (IAM Roles for Service Accounts)

locals {
  # All resources in this module are named after this, e.g. "hello-login-staging".
  cluster_name = "hello-login-${var.environment}"
}

# ── Cluster IAM Role ──────────────────────────────────────────────────────────
#
# EKS needs an IAM role to make AWS API calls on your behalf — for example,
# creating load balancers and describing EC2 instances.
#
# This is a trust policy — it answers the question: "who is allowed to assume
# this IAM role?" Every IAM role has exactly one trust policy.
#
# The JSON this produces looks like:
#   {
#     "Version": "2012-10-17",
#     "Statement": [{
#       "Effect": "Allow",
#       "Action": "sts:AssumeRole",
#       "Principal": { "Service": "eks.amazonaws.com" }
#     }]
#   }
data "aws_iam_policy_document" "eks_assume_role" {

  # A statement is one rule inside the policy. A policy can have multiple
  # statements. Each statement is either Allow or Deny (default is Allow).
  statement {

    # The action being permitted. "sts:AssumeRole" is the specific AWS API
    # call that lets an entity "become" this role and inherit its permissions.
    # STS stands for Security Token Service — it issues temporary credentials
    # when a role is assumed. Without this action, nothing can use the role.
    actions = ["sts:AssumeRole"]

    # The principal is WHO is allowed to perform the action above.
    principals {
      # type = "Service" means the principal is an AWS service (not a user or role).
      # Other valid types are:
      #   "AWS"       — an IAM user, role, or account
      #   "Federated" — a web identity provider (used for IRSA and GitHub Actions)
      type = "Service"

      # The specific AWS service that is allowed to assume this role.
      # "eks.amazonaws.com" is the identifier for the EKS control plane service.
      # This means ONLY the EKS service can assume this role — no IAM user,
      # no EC2 instance, no Lambda, nothing else.
      identifiers = ["eks.amazonaws.com"]
    }
  }
}

# The IAM role for the cluster control plane.
resource "aws_iam_role" "cluster" {
  name               = "${local.cluster_name}-cluster"
  assume_role_policy = data.aws_iam_policy_document.eks_assume_role.json
}

# AmazonEKSClusterPolicy grants EKS the minimum permissions it needs to run
# the control plane: describing EC2 resources, creating network interfaces, etc.
resource "aws_iam_role_policy_attachment" "cluster_policy" {
  role       = aws_iam_role.cluster.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}

# ── EKS Cluster ───────────────────────────────────────────────────────────────

resource "aws_eks_cluster" "this" {
  name     = local.cluster_name
  role_arn = aws_iam_role.cluster.arn
  version  = "1.29" # Kubernetes version — upgrade this periodically

  vpc_config {
    # Both private and public subnet IDs are provided so EKS can place its
    # control plane elastic network interfaces across all AZs.
    subnet_ids         = concat(var.private_subnet_ids, var.public_subnet_ids)
    security_group_ids = [var.eks_node_sg_id]

    # endpoint_private_access=true: pods and nodes reach the API server over
    # the private network — faster and free.
    endpoint_private_access = true

    # endpoint_public_access=true: kubectl works from your laptop without a VPN.
    # Set to false for stricter environments that require a VPN for cluster access.
    endpoint_public_access = true

    # Restrict which IPs can reach the public Kubernetes API endpoint.
    # Default is ["0.0.0.0/0"] (open to internet). Override in tfvars with your
    # office/VPN CIDR(s) — e.g. ["203.0.113.0/24"] — to reduce attack surface.
    public_access_cidrs = var.public_access_cidrs
  }

  # The cluster role must exist and have its policy attached before the cluster
  # can be created. Without this, EKS would fail to initialise its control plane.
  depends_on = [aws_iam_role_policy_attachment.cluster_policy]
}

# ── Node IAM Role ─────────────────────────────────────────────────────────────
#
# Worker nodes (EC2 instances) also need an IAM role so they can:
#   - Join the cluster (EKSWorkerNodePolicy)
#   - Configure pod networking (EKS_CNI_Policy)
#   - Pull Docker images from ECR (EC2ContainerRegistryReadOnly)
#   - Mount EBS volumes (EBSCSIDriverPolicy)
#
# This trust policy allows EC2 instances (the nodes) to assume this role.
data "aws_iam_policy_document" "node_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "node" {
  name               = "${local.cluster_name}-node"
  assume_role_policy = data.aws_iam_policy_document.node_assume_role.json
}

# Allows nodes to communicate with the EKS control plane and register themselves.
resource "aws_iam_role_policy_attachment" "node_worker_policy" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
}

# Allows the vpc-cni plugin to configure network interfaces on the node so
# each pod gets a real VPC IP address (no overlay network).
resource "aws_iam_role_policy_attachment" "node_cni_policy" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
}

# Allows nodes to pull Docker images from ECR (read-only).
resource "aws_iam_role_policy_attachment" "node_ecr_policy" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

# Allows the EBS CSI driver to create, attach, and detach EBS volumes for
# Kubernetes PersistentVolumes.
resource "aws_iam_role_policy_attachment" "node_ebs_csi_policy" {
  role       = aws_iam_role.node.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy"
}

# ── Node Group ────────────────────────────────────────────────────────────────

resource "aws_eks_node_group" "this" {
  cluster_name    = aws_eks_cluster.this.name
  node_group_name = "${local.cluster_name}-nodes"
  node_role_arn   = aws_iam_role.node.arn

  # Nodes go in private subnets — no public IP, not directly reachable from internet.
  subnet_ids     = var.private_subnet_ids
  instance_types = [var.node_type] # e.g. ["t3.small"]
  ami_type       = "AL2_x86_64"    # AL2 = Amazon Linux 2; AMI = Amazon Machine Image — the OS image used to launch nodes

  scaling_config {
    desired_size = var.min_nodes # start at the minimum; autoscaler adjusts from here
    min_size     = var.min_nodes
    max_size     = var.max_nodes
  }

  update_config {
    # During a rolling update (e.g. AMI upgrade), allow 1 node to be unavailable
    # at a time. This keeps the cluster running while nodes are replaced one by one.
    max_unavailable = 1
  }

  # All four node policy attachments must exist before the node group is created,
  # otherwise the nodes will fail to join the cluster.
  depends_on = [
    aws_iam_role_policy_attachment.node_worker_policy,
    aws_iam_role_policy_attachment.node_cni_policy,
    aws_iam_role_policy_attachment.node_ecr_policy,
    aws_iam_role_policy_attachment.node_ebs_csi_policy,
  ]
}

# ── EKS Add-ons ───────────────────────────────────────────────────────────────
#
# Add-ons are AWS-managed components that run on every cluster. AWS keeps them
# updated and patches security vulnerabilities.

# CoreDNS: provides DNS (Domain Name System) resolution inside the cluster.
# Without it, pods cannot look up services by name
# (e.g. "backend-service.hello-login-staging.svc").
# Requires nodes to be running first (pods need somewhere to be scheduled).
resource "aws_eks_addon" "coredns" {
  cluster_name = aws_eks_cluster.this.name
  addon_name   = "coredns"

  depends_on = [aws_eks_node_group.this]
}

# kube-proxy: runs on every node and maintains network rules that route traffic
# to the correct pod when a Service is accessed. Required for Service networking.
resource "aws_eks_addon" "kube_proxy" {
  cluster_name = aws_eks_cluster.this.name
  addon_name   = "kube-proxy"
}

# vpc-cni: the AWS VPC CNI (Container Network Interface) plugin.
# Gives each pod a real VPC IP address from the subnet CIDR (Classless Inter-Domain Routing).
# This means pods are directly routable within the VPC —
# no overlay network or IP translation needed.
resource "aws_eks_addon" "vpc_cni" {
  cluster_name = aws_eks_cluster.this.name
  addon_name   = "vpc-cni"
}

# aws-ebs-csi-driver: the EBS (Elastic Block Store) CSI (Container Storage Interface) driver.
# Allows pods to mount EBS volumes as Kubernetes PersistentVolumes.
# Required if any workload needs persistent disk storage that survives pod restarts.
# Requires nodes to be running first.
resource "aws_eks_addon" "ebs_csi" {
  cluster_name = aws_eks_cluster.this.name
  addon_name   = "aws-ebs-csi-driver"

  depends_on = [aws_eks_node_group.this]
}

# ── OIDC (OpenID Connect) Provider ───────────────────────────────────────────
#
# IRSA (IAM Roles for Service Accounts) lets individual pods assume specific
# IAM roles instead of inheriting the broad permissions of the EC2 node they
# run on. This is how the ALB controller, ESO, and GitHub Actions get their
# AWS permissions.
#
# How it works:
#   1. EKS has a built-in OIDC issuer URL (a public HTTPS endpoint).
#   2. We register that URL as a trusted OIDC identity provider in IAM.
#   3. IAM roles can then include a trust policy condition that says:
#      "only allow this if the token comes from this OIDC issuer AND
#       the service account is X in namespace Y."
#
# The thumbprint is a SHA-1 fingerprint of the root CA (Certificate Authority)
# TLS certificate that signed the OIDC server's certificate.
# IAM uses it to verify it is talking to the genuine EKS OIDC endpoint.
# We fetch it dynamically so it stays correct if AWS rotates the certificate
# rather than hardcoding a value that could go stale.

# data "tls_certificate" is a read-only data source — it makes an HTTPS
# request to the OIDC issuer URL and reads back the TLS certificate chain.
# It does not create anything in AWS.
data "tls_certificate" "eks_oidc" {
  # aws_eks_cluster.this         — the EKS cluster resource created above
  # .identity                    — the cluster's identity configuration block
  # [0]                          — identity is a list; [0] takes the first (and only) item
  # .oidc                        — the OIDC configuration nested inside identity
  # [0]                          — oidc is also a list; [0] takes the first item
  # .issuer                      — the OIDC issuer URL, e.g.:
  #                                https://oidc.eks.us-west-2.amazonaws.com/id/ABC123DEF456
  # This URL is the public HTTPS endpoint EKS uses as its OIDC identity provider.
  url = aws_eks_cluster.this.identity[0].oidc[0].issuer
}

# Registers the EKS cluster's OIDC issuer as a trusted identity provider in IAM.
# After this resource is created, IAM role trust policies in the iam module can
# reference it to allow pods from THIS cluster to assume specific roles.
resource "aws_iam_openid_connect_provider" "this" {
  # The OIDC issuer URL — the same value used above in data "tls_certificate".
  # It appears twice because each resource has a different purpose:
  #   data "tls_certificate"              — uses the URL to FETCH the TLS certificate
  #   aws_iam_openid_connect_provider     — uses the URL to REGISTER the identity provider in IAM
  # IAM uses this URL as the unique identifier for this provider. The URL itself
  # is the address EKS pods contact to get their OIDC tokens.
  url = aws_eks_cluster.this.identity[0].oidc[0].issuer

  # client_id_list — the "audience" values that tokens from this provider
  # are allowed to have. "sts.amazonaws.com" means only tokens intended for
  # the AWS STS (Security Token Service) are accepted. When a pod requests
  # a token, Kubernetes sets the audience to "sts.amazonaws.com" automatically.
  # Tokens with any other audience are rejected.
  client_id_list = ["sts.amazonaws.com"]

  # thumbprint_list — a list of SHA-1 fingerprints of the root CA certificates
  # that are trusted for this OIDC provider. IAM checks this fingerprint when
  # validating tokens to confirm the token genuinely came from the EKS OIDC endpoint.
  # .certificates    — the full TLS certificate chain fetched from the issuer URL
  # [0]              — the first certificate in the chain, which is the root CA
  # .sha1_fingerprint — the SHA-1 hash of that certificate's DER (binary) encoding
  thumbprint_list = [data.tls_certificate.eks_oidc.certificates[0].sha1_fingerprint]
}
