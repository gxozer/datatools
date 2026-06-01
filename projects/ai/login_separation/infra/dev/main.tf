# infra/dev/main.tf
#
# Provisions a single EC2 (Elastic Compute Cloud) instance running the full
# hello-login stack via Docker Compose. ~$15-20/month vs ~$150/month for EKS.
#
# What this file creates (in dependency order):
#   1. VPC + IGW + subnet + route table   — isolated network for the dev instance
#   2. IAM role + policies + profile      — lets the EC2 instance pull images from ECR
#   3. Security group                     — firewall: HTTP(80), HTTPS(443), SSH(22)
#   4. EC2 instance                       — the server; user_data installs Docker on boot
#   5. Elastic IP                         — static public IP so DNS doesn't break on reboot
#
# Acronym glossary:
#   AMI   Amazon Machine Image — pre-built OS image used to launch EC2 instances
#   EC2   Elastic Compute Cloud — AWS virtual machines
#   ECR   Elastic Container Registry — private Docker image registry
#   EIP   Elastic IP — a static public IPv4 address in AWS
#   IAM   Identity and Access Management — controls what AWS resources can do
#   IGW   Internet Gateway — VPC component that enables internet access
#   IMDSv2 Instance Metadata Service v2 — secure way for EC2 to read its own metadata
#   SG    Security Group — AWS virtual firewall
#   SSM   Systems Manager — allows terminal access via AWS console without SSH keys
#   VPC   Virtual Private Cloud — an isolated private network in AWS

# ── AMI ───────────────────────────────────────────────────────────────────────

# Looks up the latest Amazon Linux 2023 (AL2023) AMI for x86_64 automatically.
# Using a data source instead of hardcoding an AMI ID means we always get the
# most recent image with the latest security patches without manual updates.
# AL2023 is the successor to Amazon Linux 2 — it uses dnf instead of yum.
data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]  # only trust AMIs published by AWS, not third parties

  # The name glob matches all AL2023 AMIs, e.g. "al2023-ami-2023.5.20240701.0-kernel-6.1-x86_64"
  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }

  # HVM (Hardware Virtual Machine) is the modern virtualisation type.
  # The alternative is "paravirtual" (PV) which is older and not recommended.
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# ── Local Values ──────────────────────────────────────────────────────────────

locals {
  # Prefix used in every resource name. Keeps all dev resources clearly identified
  # in the AWS console and avoids name collisions with staging/production resources.
  name = "hello-login-dev"
}

# ── VPC ───────────────────────────────────────────────────────────────────────

# A dedicated VPC isolates dev infrastructure from staging and production.
# Resources in different VPCs cannot talk to each other unless explicitly peered.
# 10.99.0.0/16 was chosen to avoid overlapping with staging (10.0.x.x) and
# production (10.1.x.x) in case the VPCs are ever peered.
resource "aws_vpc" "dev" {
  cidr_block = "10.99.0.0/16"

  # enable_dns_support: allows EC2 instances to resolve AWS service hostnames
  # (e.g. ecr.us-west-2.amazonaws.com → IP). Required for Docker to pull ECR images.
  enable_dns_support = true

  # enable_dns_hostnames: gives EC2 instances a public DNS hostname
  # (e.g. ec2-54-x-x-x.us-west-2.compute.amazonaws.com). Not strictly required
  # here, but good practice and makes it easier to reference the instance by name.
  enable_dns_hostnames = true

  tags = { Name = "${local.name}-vpc" }
}

# ── Internet Gateway ──────────────────────────────────────────────────────────

# An Internet Gateway (IGW) is the VPC component that allows resources in public
# subnets to send and receive traffic from the internet. Without it, the VPC is
# completely isolated — no inbound connections and no outbound internet access.
# One IGW per VPC; attached directly to the VPC.
resource "aws_internet_gateway" "dev" {
  vpc_id = aws_vpc.dev.id  # attach to our dev VPC
  tags   = { Name = "${local.name}-igw" }
}

# ── Subnet ────────────────────────────────────────────────────────────────────

# A single public subnet — the EC2 instance lives here.
# Public subnet = a subnet whose route table sends 0.0.0.0/0 to the IGW.
# map_public_ip_on_launch = true: every instance launched here automatically gets
# a public IP in addition to its private IP. Required for internet connectivity.
# 10.99.1.0/24 gives 254 usable addresses — more than enough for one EC2 instance.
resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.dev.id
  cidr_block              = "10.99.1.0/24"
  map_public_ip_on_launch = true
  tags                    = { Name = "${local.name}-public" }
}

# ── Route Table ───────────────────────────────────────────────────────────────

# A route table contains rules (routes) that determine where network traffic goes.
# This route table has one rule: send all traffic not destined for the VPC itself
# (0.0.0.0/0 = the entire internet) through the Internet Gateway.
# Without this route, the subnet would be private — no internet access.
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.dev.id

  route {
    cidr_block = "0.0.0.0/0"               # match all destinations not in the VPC
    gateway_id = aws_internet_gateway.dev.id # send them through the Internet Gateway
  }

  tags = { Name = "${local.name}-rt" }
}

# Associates the public subnet with the public route table.
# A subnet is private by default (no internet route). This association is what
# makes it "public" — traffic follows the route table rules defined above.
resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

# ── IAM Role ─────────────────────────────────────────────────────────────────

# An IAM role gives the EC2 instance an AWS identity so it can make AWS API
# calls without storing credentials on the instance. The role has two policies:
#   1. ECR read access — lets Docker pull images from the private registry
#   2. SSM core — enables terminal access via AWS console (no SSH key needed)
#
# The assume_role_policy is a trust policy — it answers "who can use this role?"
# type="Service" with "ec2.amazonaws.com" means only EC2 instances can assume it.
resource "aws_iam_role" "ec2" {
  name = "${local.name}-ec2"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }  # only EC2 instances can assume this role
      Action    = "sts:AssumeRole"                   # sts:AssumeRole is how roles are assumed
    }]
  })
}

# Grants the EC2 instance read-only access to ECR.
# Required for `docker compose pull` to authenticate and download images from
# the private ECR registry (277070500859.dkr.ecr.us-west-2.amazonaws.com).
# Read-only is sufficient — the instance only needs to pull, never push.
resource "aws_iam_role_policy_attachment" "ecr" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

# SSM (Systems Manager) Session Manager enables browser-based and CLI terminal
# access to the instance without opening port 22 or using SSH keys.
# Useful as a fallback if SSH stops working (e.g. key issues, SG misconfiguration).
# Access via: AWS Console → Systems Manager → Session Manager → Start Session
#         or: aws ssm start-session --target <instance-id>
resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# An instance profile is the container that holds the IAM role and is attached
# to the EC2 instance. You cannot attach a role directly to an EC2 instance —
# it must be wrapped in an instance profile first. One role per profile.
resource "aws_iam_instance_profile" "ec2" {
  name = "${local.name}-ec2"
  role = aws_iam_role.ec2.name
}

# ── Security Group ────────────────────────────────────────────────────────────

# A security group is a stateful virtual firewall applied to EC2 instances.
# "Stateful" means if you allow inbound traffic on port 80, the response is
# automatically allowed back out without needing an explicit egress rule.
#
# Inbound (ingress) rules:
#   Port 80  (HTTP)  — open to everyone; Caddy immediately redirects to HTTPS
#   Port 443 (HTTPS) — open to everyone; Caddy serves the application over TLS
#   Port 22  (SSH)   — restricted to var.ssh_cidr_blocks (your IP only)
#
# Outbound (egress) rule:
#   All traffic — the instance needs to reach ECR (to pull images), AWS APIs,
#   Let's Encrypt (for Caddy's TLS certificate), and package repositories.
resource "aws_security_group" "dev" {
  name        = "${local.name}-sg"
  description = "hello-login dev - HTTP and SSH inbound"
  vpc_id      = aws_vpc.dev.id

  # HTTP: Caddy listens on port 80 and immediately issues a 301 redirect to HTTPS.
  # Must be open to everyone so Let's Encrypt's ACME HTTP-01 challenge can reach
  # Caddy during certificate issuance (the challenge comes from Let's Encrypt servers).
  ingress {
    description = "HTTP (Caddy redirects to HTTPS)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # HTTPS: the application is served here after Caddy terminates TLS.
  # Open to everyone — this is the public-facing port.
  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # SSH: restricted to the IP(s) set in dev.tfvars (typically your laptop IP).
  # Never set to 0.0.0.0/0 — port 22 open to the internet is constantly probed
  # by automated scanners looking for weak credentials.
  # Find your IP: curl -4 ifconfig.me
  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.ssh_cidr_blocks  # set in dev.tfvars
  }

  # Allow all outbound traffic. protocol="-1" means all protocols (TCP, UDP, ICMP).
  # The instance needs to reach ECR, Let's Encrypt, dnf repos, and AWS APIs.
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${local.name}-sg" }
}

# ── EC2 Instance ──────────────────────────────────────────────────────────────

resource "aws_instance" "dev" {
  # The latest AL2023 AMI looked up by the data source above.
  ami = data.aws_ami.al2023.id

  # t3.small: 2 vCPU, 2 GB RAM. Enough to run backend + frontend + MySQL + Caddy
  # concurrently. Upgrade to t3.medium (4 GB) if memory pressure is an issue.
  instance_type = var.instance_type

  # Place the instance in the public subnet so it gets a public IP.
  subnet_id = aws_subnet.public.id

  # Apply the security group defined above (controls inbound/outbound traffic).
  vpc_security_group_ids = [aws_security_group.dev.id]

  # Attach the IAM instance profile so the instance can call AWS APIs (ECR, SSM).
  iam_instance_profile = aws_iam_instance_profile.ec2.name

  # The SSH key pair name. The .pem file must already exist at ~/.ssh/<key_name>.pem.
  # Create it with: aws ec2 create-key-pair --key-name hello-login-dev --region us-west-2 \
  #                   --query 'KeyMaterial' --output text > ~/.ssh/hello-login-dev.pem
  key_name = var.key_name

  # user_data runs as root on the first boot after the instance launches.
  # It is a shell script that installs everything Docker needs.
  # set -e: exit immediately if any command fails (prevents silent partial installs).
  user_data = <<-EOF
    #!/bin/bash
    set -e
    dnf update -y                          # apply all OS security patches
    dnf install -y docker                  # install Docker Engine
    systemctl enable --now docker          # start Docker and enable it on reboot
    usermod -aG docker ec2-user            # allow ec2-user to run docker without sudo
    mkdir -p /usr/local/lib/docker/cli-plugins
    curl -SL https://github.com/docker/compose/releases/download/v2.27.0/docker-compose-linux-x86_64 \
      -o /usr/local/lib/docker/cli-plugins/docker-compose
    chmod +x /usr/local/lib/docker/cli-plugins/docker-compose   # install Docker Compose v2 plugin
    mkdir -p /opt/hello-login              # create the app directory
    chown ec2-user:ec2-user /opt/hello-login  # ec2-user owns it (make dev-deploy SCPs files here)
  EOF

  # When user_data changes (e.g. new Docker version), replace the instance instead
  # of trying to re-run the script in place. Triggers a new instance with a fresh boot.
  user_data_replace_on_change = true

  root_block_device {
    volume_size = 20         # 20 GB is enough for the OS, Docker images, and MySQL data
    volume_type = "gp3"      # gp3: latest-generation general purpose SSD, cheaper than gp2
    encrypted   = true       # encrypt the root volume at rest (AES-256, zero performance cost)
  }

  metadata_options {
    # IMDSv2 (Instance Metadata Service v2) requires a session token for every metadata
    # request. This blocks SSRF (Server-Side Request Forgery) attacks that try to steal
    # the instance credentials by hitting http://169.254.169.254/latest/meta-data/iam/
    # from inside the application. "required" disables the old IMDSv1 token-less access.
    http_tokens = "required"
  }

  tags = { Name = local.name }
}

# ── Elastic IP ────────────────────────────────────────────────────────────────

# An Elastic IP (EIP) is a static public IPv4 address that stays the same even
# if the EC2 instance is stopped, restarted, or replaced.
# Without an EIP, the instance gets a different public IP on every start — this
# would break the DNS A record pointing your domain to the server.
# Cost: free while attached to a running instance; ~$0.005/hr if unused.
resource "aws_eip" "dev" {
  instance = aws_instance.dev.id  # attach to our dev instance
  domain   = "vpc"                # required for EIPs in a VPC (as opposed to EC2-Classic, which is retired)

  tags = { Name = "${local.name}-eip" }
}
