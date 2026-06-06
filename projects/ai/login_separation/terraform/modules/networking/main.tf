# modules/networking/main.tf
#
# Creates all network infrastructure for one environment:
#   - VPC  (Virtual Private Cloud)     — the isolated private network for this environment
#   - 3 public subnets + 3 private subnets (one per AZ — Availability Zone)
#   - IGW  (Internet Gateway)          — enables inbound/outbound internet for public subnets
#   - NAT  (Network Address Translation) Gateway — enables outbound-only internet for private subnets
#   - Route tables                     — rules that direct traffic to IGW or NAT
#   - SGs  (Security Groups)           — virtual firewalls for EKS nodes and RDS

# Fetches the list of Availability Zones that are currently available in the
# region. Using a data source instead of hardcoding AZ names means this code
# works in any region without changes.
data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  # Take the first 3 AZs from the list. us-west-2 has: a, b, c, d — we use a, b, c.
  azs = slice(data.aws_availability_zones.available.names, 0, 3)

  # cidrsubnet(base, newbits, netnum) carves a smaller subnet from the VPC CIDR
  # (Classless Inter-Domain Routing — the notation "10.0.0.0/16" where /16 is the prefix length).
  # newbits=8 means add 8 bits to the prefix, turning a /16 into /24 subnets.
  # netnum is the subnet index. Results for vpc_cidr="10.0.0.0/16":
  #   public_subnets[0]  = 10.0.0.0/24  (netnum 0)
  #   public_subnets[1]  = 10.0.1.0/24  (netnum 1)
  #   public_subnets[2]  = 10.0.2.0/24  (netnum 2)
  #   private_subnets[0] = 10.0.10.0/24 (netnum 10)
  #   private_subnets[1] = 10.0.11.0/24 (netnum 11)
  #   private_subnets[2] = 10.0.12.0/24 (netnum 12)
  public_subnets  = [for i in range(3) : cidrsubnet(var.vpc_cidr, 8, i)]
  private_subnets = [for i in range(3) : cidrsubnet(var.vpc_cidr, 8, i + 10)]

  # Prefix used in every resource name, e.g. "hello-login-staging".
  name_prefix = "hello-login-${var.environment}"
}

# ── VPC ───────────────────────────────────────────────────────────────────────

# A dedicated VPC isolates this environment's resources from everything else
# in the AWS account. Resources in separate VPCs cannot communicate unless
# you explicitly set up VPC peering.
resource "aws_vpc" "this" {
  cidr_block = var.vpc_cidr

  # These two flags are required for EKS. The cluster's internal DNS
  # (CoreDNS) needs DNS support, and nodes need to resolve AWS service
  # hostnames (e.g. ecr.us-west-2.amazonaws.com).
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = local.name_prefix
  }
}

# ── Subnets ───────────────────────────────────────────────────────────────────

# Public subnets — one per AZ.
# Resources here get a public IP by default (map_public_ip_on_launch = true).
# Used for: load balancers (ALBs need to be publicly reachable).
# NOT used for: EKS nodes or the database (those go in private subnets).
#
# The Kubernetes tags are read by the AWS Load Balancer Controller:
#   kubernetes.io/role/elb = "1"          — marks this subnet for public ALBs
#   kubernetes.io/cluster/<name> = shared — tells the controller this subnet
#                                           belongs to this cluster
resource "aws_subnet" "public" {
  count = 3

  vpc_id                  = aws_vpc.this.id
  cidr_block              = local.public_subnets[count.index]
  availability_zone       = local.azs[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name                                         = "${local.name_prefix}-public-${local.azs[count.index]}"
    "kubernetes.io/role/elb"                     = "1"
    "kubernetes.io/cluster/${local.name_prefix}" = "shared"
  }
}

# Private subnets — one per AZ.
# Resources here have NO public IP. Outbound traffic goes via the NAT gateway.
# Used for: EKS worker nodes, RDS database.
# Inbound traffic reaches them only from within the VPC (e.g. from a load balancer).
#
# kubernetes.io/role/internal-elb = "1" marks this subnet for internal ALBs
# (load balancers that are only reachable from within the VPC).
resource "aws_subnet" "private" {
  count = 3

  vpc_id            = aws_vpc.this.id
  cidr_block        = local.private_subnets[count.index]
  availability_zone = local.azs[count.index]

  tags = {
    Name                                         = "${local.name_prefix}-private-${local.azs[count.index]}"
    "kubernetes.io/role/internal-elb"            = "1"
    "kubernetes.io/cluster/${local.name_prefix}" = "shared"
  }
}

# ── Internet Gateway ─────────────────────────────────────────────────────────

# The Internet Gateway (IGW — Internet Gateway) is the door between the VPC and the public internet.
# Without it, nothing in the VPC can reach or be reached from the internet.
# The public route table sends 0.0.0.0/0 traffic here.
resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = {
    Name = local.name_prefix
  }
}

# ── NAT Gateway ───────────────────────────────────────────────────────────────

# An EIP (Elastic IP — a static public IPv4 address) is required for the NAT gateway.
# NAT gateway traffic appears to come from this IP address when it leaves the VPC.
resource "aws_eip" "nat" {
  domain = "vpc"

  tags = {
    Name = "${local.name_prefix}-nat"
  }
}

# The NAT Gateway lives in a public subnet and allows resources in private
# subnets to make outbound internet connections (e.g. EKS nodes pulling Docker
# images from ECR, or calling AWS APIs) without having a public IP themselves.
#
# We use a single NAT gateway (in the first public subnet) to save cost.
# The trade-off: if us-west-2a has an outage, nodes in other AZs lose
# outbound internet access. For production HA, add one NAT gateway per AZ.
#
# depends_on ensures the IGW exists before the NAT gateway is created,
# because the NAT gateway's subnet needs an internet route to function.
resource "aws_nat_gateway" "this" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[0].id # placed in the first public subnet (us-west-2a)

  tags = {
    Name = local.name_prefix
  }

  depends_on = [aws_internet_gateway.this]
}

# ── Route Tables ─────────────────────────────────────────────────────────────

# Public route table — routes all traffic (0.0.0.0/0) to the Internet Gateway.
# Associated with the 3 public subnets. This is what makes them "public".
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block = "0.0.0.0/0" # all traffic not destined for the VPC itself
    gateway_id = aws_internet_gateway.this.id
  }

  tags = {
    Name = "${local.name_prefix}-public"
  }
}

# Associates each public subnet with the public route table.
# count.index cycles through 0, 1, 2 — one association per subnet.
resource "aws_route_table_association" "public" {
  count = 3

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# Private route table — routes all outbound traffic through the NAT gateway.
# Associated with the 3 private subnets. This gives private resources outbound
# internet access (to pull images, call AWS APIs) without making them reachable
# from the internet.
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.this.id
  }

  tags = {
    Name = "${local.name_prefix}-private"
  }
}

# Associates each private subnet with the private route table.
resource "aws_route_table_association" "private" {
  count = 3

  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

# ── Security Groups ───────────────────────────────────────────────────────────

# Security group (SG — virtual firewall) for EKS worker nodes.
# Egress (outbound): all traffic allowed — nodes need to pull images, call AWS APIs, etc.
# Ingress (inbound): not defined here — EKS manages the node-to-node and
# control-plane-to-node rules automatically via its own managed security group.
resource "aws_security_group" "eks_nodes" {
  name        = "${local.name_prefix}-eks-nodes"
  description = "EKS node group security group"
  vpc_id      = aws_vpc.this.id

  # Allow all outbound traffic from nodes (to pull images, call AWS APIs, etc.)
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"          # -1 means all protocols
    cidr_blocks = ["0.0.0.0/0"] # anywhere
  }

  tags = {
    Name = "${local.name_prefix}-eks-nodes"
  }
}

# Security group for the RDS MySQL database.
# Ingress: only accepts MySQL connections (port 3306) from the EKS node SG.
#   - This means only application pods running on EKS nodes can connect to the DB.
#   - Your laptop, the internet, and any other resource cannot connect.
# Egress: all traffic allowed (standard for RDS responses).
resource "aws_security_group" "rds" {
  name        = "${local.name_prefix}-rds"
  description = "RDS security group - allows MySQL from EKS nodes"
  vpc_id      = aws_vpc.this.id

  # No inline ingress rules here — see aws_security_group_rule.rds_from_eks_cluster
  # in the root main.tf. Mixing inline ingress blocks with separate
  # aws_security_group_rule resources on the same SG causes the AWS provider
  # to override the separately-managed rules on every refresh.

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.name_prefix}-rds"
  }
}
