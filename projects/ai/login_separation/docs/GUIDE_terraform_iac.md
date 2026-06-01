# Terraform IaC Guide — hello-login Infrastructure

**Audience:** Engineers new to Terraform  
**Related code:** `terraform/` directory  
**Related docs:** TDS_terraform_iac.md, PRD_terraform_iac.md

---

## What is Terraform and why are we using it?

Terraform is a tool that lets you describe AWS infrastructure in text files (code), and then create or destroy that infrastructure with a single command. Instead of clicking through the AWS console or running dozens of `aws` CLI commands by hand, you write what you want and Terraform figures out how to make it happen.

**Why this matters for hello-login:**  
The cluster, database, and load balancer were originally created manually. If we needed to recreate them (disaster recovery, new region, new environment), someone would have to follow a long README step by step — and hope nothing was missed. With Terraform, recreation is `terraform apply`. Destruction is `terraform destroy`.

Terraform uses a language called **HCL** (HashiCorp Configuration Language). It looks like JSON but is easier to read and supports variables, loops, and functions.

---

## How Terraform works — the basics

Before reading the code, you need three mental models:

**1. Providers**  
Terraform doesn't know how to talk to AWS by itself. A *provider* is a plugin that adds that ability. We declare which providers we need (AWS, Helm, Kubernetes) and Terraform downloads them automatically on `terraform init`.

**2. Resources**  
A *resource* is one real thing in AWS — a VPC, a database, an IAM role. Each resource block tells Terraform "make sure this thing exists with these settings."

**3. State**  
Terraform keeps a file called *state* that records what it has already created. On the next run, it compares your code to the state and only makes the changes needed. Without state, Terraform would try to create everything again from scratch every time.

---

## Which directory to run Terraform from

Terraform always operates on the **current working directory**. Since all `.tf` files live in `terraform/`, you must be inside that folder before running any command:

```bash
cd terraform
terraform init
terraform plan -var-file=staging.tfvars
```

If you prefer to stay in the repo root, use the `-chdir` flag — it tells Terraform to switch directories before doing anything:

```bash
# From the repo root — no cd needed
terraform -chdir=terraform init
terraform -chdir=terraform plan -var-file=staging.tfvars
terraform -chdir=terraform apply -var-file=staging.tfvars
```

Both are equivalent. The GitHub Actions workflow uses `working-directory: terraform` on each step, which is the CI equivalent of `cd terraform`.

---

## Execution flow — what runs first and why

### The most important thing to understand

Terraform does **not** execute files sequentially like a Python script. There is no "file 1 runs first, then file 2." Instead:

1. Terraform reads **all `.tf` files in the directory at once** — `versions.tf`, `backend.tf`, `variables.tf`, `main.tf`, `outputs.tf` are all loaded simultaneously into one combined configuration.
2. Terraform builds a **dependency graph** from the references in your code.
3. Terraform executes resources **in dependency order** — anything that doesn't depend on anything else can run in parallel.

This means the filename doesn't control order. What controls order is *which resources reference which other resources*.

---

### Phase 1 — `terraform init` (one-time setup)

```bash
terraform init
```

`terraform init`:
1. Reads `versions.tf` to find which providers are needed (aws, helm, kubernetes, random, tls)
2. Downloads those provider plugins from the Terraform Registry into `.terraform/`
3. Reads the `modules/` directories and validates they exist
4. Reads `backend.tf` — because the S3 backend is commented out by default, Terraform uses a **local state file** (`terraform.tfstate` in the same directory)

After this, you have a `.terraform/` directory locally (gitignored) and a local state file.

**What is state and why does it matter?**  
Terraform keeps a `terraform.tfstate` file that records everything it has created in AWS. On each run, it compares your code to this file and only makes the changes needed. Without it, Terraform would try to create everything from scratch on every run.

**Local state vs S3 state**  
By default, state is stored locally on your machine — fine for solo use. The `backend.tf` file contains a commented-out S3 backend configuration for when you need team or CI/CD access to state. The comments in that file explain exactly when and how to enable it.

---

### Phase 2 — `terraform plan` or `terraform apply`

This is where the dependency graph matters. Here is the actual execution order for our codebase:

```
┌─────────────────────────────────────────────────┐
│  STEP 1 — data sources (read-only AWS lookups)  │
│                                                 │
│  data.aws_caller_identity.current               │  ← gets AWS account ID
│  data.aws_availability_zones.available          │  ← gets AZ list (in networking)
└────────────────────┬────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────┐
│  STEP 2 — networking module                     │
│  (nothing depends on networking yet)            │
│                                                 │
│  aws_vpc.this                                   │
│  aws_subnet.public[0,1,2]                       │
│  aws_subnet.private[0,1,2]                      │
│  aws_internet_gateway.this                      │
│  aws_eip.nat → aws_nat_gateway.this             │
│  aws_route_table.public + associations          │
│  aws_route_table.private + associations         │
│  aws_security_group.eks_nodes                   │
│  aws_security_group.rds                         │
└──────────┬──────────────────────────────────────┘
           │  outputs: vpc_id, subnet IDs, sg IDs
           │
           ├────────────────────┬──────────────────┐
           ▼                    ▼                  ▼
┌──────────────────┐  ┌────────────────┐  ┌───────────────┐
│  STEP 3a — eks   │  │  STEP 3b — rds │  │ STEP 3c — ecr │
│  module          │  │  module        │  │ module        │
│                  │  │                │  │               │
│  IAM cluster     │  │  db_subnet_    │  │ ecr_repos x2  │
│  role + attach   │  │  group         │  │ lifecycle     │
│  eks_cluster     │  │  db_instance   │  │ policies      │
│  IAM node role   │  │  param_group   │  └───────────────┘
│  eks_node_group  │  └────────────────┘
│  eks addons      │
│  OIDC provider   │
└──────────┬───────┘
           │  outputs: cluster_name, endpoint,
           │           cluster_ca, oidc_provider_arn/url
           │
           ├────────────────────────────┐
           ▼                            ▼
┌──────────────────────┐    ┌───────────────────────┐
│  STEP 4a — secrets   │    │  STEP 4b — iam module  │
│  module              │    │                        │
│  (no dependencies    │    │  alb_controller role   │
│   on eks/rds/ecr —  │    │  eso role              │
│   runs in parallel)  │    │  github_actions role   │
│                      │    │  github OIDC provider  │
│  sm_secret shell     │    └──────────┬─────────────┘
└──────────────────────┘               │  outputs: role ARNs
                                       │
                                       ▼
                          ┌────────────────────────┐
                          │  STEP 5 — helm-addons   │
                          │  module                 │
                          │  (explicit depends_on   │
                          │   eks + iam)            │
                          │                         │
                          │  helm: alb-controller   │
                          │  helm: external-secrets │
                          └────────────────────────┘
```

**Key points about this graph:**

- **Networking always runs first** — every other module needs VPC/subnet IDs.
- **EKS, RDS, and ECR run in parallel** after networking — none of them depend on each other.
- **IAM runs after EKS** — it needs the `oidc_provider_arn` and `cluster_name` outputs from the EKS module.
- **Secrets runs in parallel with everything** — it has no dependencies on other modules.
- **Helm-addons runs last** — it installs software *into* the cluster, so the cluster must exist and the IAM roles must exist first. The `depends_on` in `main.tf` enforces this explicitly.

---

### Parallel execution paths — in detail

Terraform runs as much as it can in parallel at every step. Below is the full picture, including what runs in parallel *within* each module.

#### networking module — internal parallel paths

```
aws_vpc.this  ←─────────────────────────────── must exist first
      │
      ├──────────────────┬──────────────────┬──────────────────┐
      ▼                  ▼                  ▼                  ▼
aws_subnet         aws_subnet         aws_internet       aws_eip.nat
.public[0,1,2]     .private[0,1,2]    _gateway.this      (no dependency
(x3 in parallel)   (x3 in parallel)                       on vpc at all
                                                           — runs freely)
      │                  │                  │                  │
      └──────────────────┴──────────┬───────┘                  │
                                    ▼                          │
                              aws_nat_gateway  ◄───────────────┘
                              (needs: public
                               subnet[0] +
                               IGW + EIP)
                                    │
      ┌─────────────────────────────┴─────────────────────────┐
      ▼                                                        ▼
aws_route_table.public                              aws_route_table.private
(needs: vpc + IGW)                                  (needs: vpc + NAT)
      │                                                        │
      ▼                                                        ▼
route_table_association                             route_table_association
.public[0,1,2]                                      .private[0,1,2]
(x3 in parallel)                                    (x3 in parallel)

── also in parallel after vpc ──────────────────────────────────────────
aws_security_group.eks_nodes   (needs vpc only)
aws_security_group.rds         (needs vpc + eks_nodes sg)
```

#### eks module — internal parallel paths

```
aws_iam_role.cluster  ──────────────────────── and simultaneously:
      │                                         aws_iam_role.node
      ▼                                               │
aws_iam_role_policy_                           x4 policy attachments
attachment.cluster_policy                      (all run in parallel)
      │                                               │
      ▼                                               │
aws_eks_cluster.this  ◄────────────────────────────── (node role ready
      │                                                but not needed yet)
      │
      ├─────────────────────────────────────────────────────────────────┐
      ▼                                                                 ▼
data.tls_certificate          aws_eks_addon.kube_proxy     aws_eks_addon
.eks_oidc                     aws_eks_addon.vpc_cni        .coredns (needs
      │                       (need cluster only           cluster + nodes)
      ▼                        — run in parallel)
aws_iam_openid_connect_                                    aws_eks_addon
provider.this                                              .ebs_csi (needs
                                                           cluster + nodes)

aws_eks_node_group.this  (needs cluster + all 4 node policy attachments)
```

#### ecr module — internal parallel paths

```
aws_ecr_repository.this["hello-login-backend"]   ──┐  (both repos in
aws_ecr_repository.this["hello-login-frontend"]  ──┘   parallel)
      │                                          │
      ▼                                          ▼
aws_ecr_lifecycle_policy["backend"]    aws_ecr_lifecycle_policy["frontend"]
(both lifecycle policies in parallel after their respective repos)
```

#### rds module — internal parallel paths

```
random_password.db  ──┐
aws_db_subnet_group ──┼──► aws_db_instance.this
aws_db_parameter_   ──┘    (waits for all three)
group
(all three run in parallel — none depend on each other)
```

#### iam module — internal parallel paths

All three role chains are fully independent and run in parallel:

```
┌─────────────────────┐  ┌─────────────────────┐  ┌──────────────────────┐
│  ALB controller     │  │  ESO                 │  │  GitHub Actions      │
│                     │  │                      │  │                      │
│  aws_iam_role       │  │  aws_iam_role        │  │  aws_iam_role        │
│  aws_iam_policy     │  │  aws_iam_policy      │  │  aws_iam_policy      │
│  role_policy_attach │  │  role_policy_attach  │  │  role_policy_attach  │
└─────────────────────┘  └─────────────────────┘  └──────────────────────┘

aws_iam_openid_connect_provider.github  (independent — runs freely)
```

#### helm-addons module — internal parallel paths

```
helm_release.alb_controller    helm_release.external_secrets
(both install in parallel — they don't depend on each other)
```

---

#### Summary — what is actually running at the same time

| Time window | What runs in parallel |
| --- | --- |
| Start | data sources: `aws_caller_identity`, `aws_availability_zones`, `aws_eip.nat` |
| After VPC | 6 subnets, IGW (all 7 in parallel) |
| After IGW + subnet[0] + EIP | NAT gateway |
| After NAT + IGW | public and private route tables (in parallel) |
| After route tables | 6 route table associations (all in parallel) |
| After networking outputs ready | EKS cluster role, RDS subnet group, RDS param group, RDS password, ECR repos x2, SM secret — all in parallel |
| After EKS cluster role | EKS cluster (and EKS node role in parallel) |
| After EKS cluster | OIDC cert fetch, kube-proxy addon, vpc-cni addon, node group — in parallel |
| After node group | coredns addon, ebs-csi addon (in parallel) |
| After EKS OIDC ready | All 3 IAM roles + GitHub OIDC provider (all in parallel) |
| After EKS + IAM ready | ALB controller helm release, ESO helm release (in parallel) |

---

### How Terraform knows the order — references

Terraform infers dependencies from references. When you write:

```hcl
module "eks" {
  vpc_id = module.networking.vpc_id   # ← reference to networking output
}
```

Terraform sees that `module.eks` references `module.networking.vpc_id` and concludes: "I must finish networking before I can start eks." No manual ordering is needed — the graph is derived automatically from the code.

The only time you need to be explicit is when there's a *hidden* dependency that doesn't appear as a reference. For example, Helm charts are installed into the EKS cluster but the `helm_release` resource doesn't directly reference an `aws_eks_cluster` resource — it just connects via the provider configuration. That's why `main.tf` has:

```hcl
module "helm_addons" {
  ...
  depends_on = [module.eks, module.iam]   # explicit because it's not in a reference
}
```

---

### Within each module — how files relate

Inside a module folder (e.g., `modules/eks/`), all three files are read together as one unit:

| File | Role |
| --- | --- |
| `variables.tf` | Declares what inputs this module accepts |
| `main.tf` | Defines the resources to create |
| `outputs.tf` | Declares what values this module exposes to the caller |

Data flows in one direction:

```
main.tf (root) → variables.tf (module) → main.tf (module) → outputs.tf (module) → main.tf (root)
     passes inputs ↑                                          exposes outputs ↑
```

Concretely for the EKS module:
- `main.tf` (root) calls the module and passes `vpc_id`, `private_subnet_ids`, etc.
- `variables.tf` (eks module) declares those as accepted inputs
- `main.tf` (eks module) uses `var.vpc_id` to create the `aws_eks_cluster` resource
- `outputs.tf` (eks module) exposes `cluster_name`, `cluster_endpoint`, etc.
- `main.tf` (root) receives those outputs via `module.eks.cluster_name`

---

### Reading `module.X.Y` references

You will see this pattern throughout the code:

```hcl
module.eks.cluster_endpoint
```

This always means: **the output named `cluster_endpoint` as declared in `modules/eks/outputs.tf`.**

The three parts:

```
module . eks . cluster_endpoint
   │       │         │
   │       │         └── output name declared in modules/eks/outputs.tf
   │       └── the name you gave the module block in main.tf (see below)
   └── keyword meaning "this value comes from a module"
```

The middle part — `eks` — is the name you gave the module block in the **root `main.tf`** (line 11):

```hcl
# terraform/main.tf  ← this is where "eks" is defined
module "eks" {             # ← the label "eks" lives here, not inside the module folder
  source = "./modules/eks"
  ...
}
```

**The code inside `modules/eks/main.tf` does not define this name and does not know it.** The files inside the module folder just define resources using `var.*` inputs — they have no idea what label the caller chose. The label is entirely the caller's (root `main.tf`'s) concern.

Think of it like a function in Python:

```python
# modules/eks/main.tf is like defining the function — it has no name of its own
def create_cluster(vpc_id, node_type):
    ...

# root main.tf is like calling it and assigning it to a variable
eks = create_cluster(vpc_id="vpc-123", node_type="t3.small")

# module.eks.cluster_endpoint is like reading a value from that result
eks.cluster_endpoint
```

That name (`eks`) has nothing to do with the folder name either. The folder is specified separately in `source`. You could write:

```hcl
module "banana" {
  source = "./modules/eks"   # still reads code from the eks folder
}
```

And then every reference would have to say `module.banana.cluster_endpoint`. The label is simply what you call it in root `main.tf`, and it must match exactly wherever you reference it.

**Concrete example — tracing `module.eks.cluster_endpoint`:**

Step 1 — AWS creates the EKS cluster and sets its endpoint attribute:

```hcl
# modules/eks/main.tf
resource "aws_eks_cluster" "this" {
  name = local.cluster_name
  ...
}
# After apply, AWS populates: aws_eks_cluster.this.endpoint
# e.g. "https://ABC123.gr7.us-west-2.eks.amazonaws.com"
```

Step 2 — The EKS module exposes that attribute as an output:

```hcl
# modules/eks/outputs.tf
output "cluster_endpoint" {
  value = aws_eks_cluster.this.endpoint   # ← reads from the resource above
}
```

Step 3 — Any other file can consume it as `module.eks.cluster_endpoint`:

```hcl
# versions.tf
provider "helm" {
  kubernetes {
    host = module.eks.cluster_endpoint   # ← reads the output from step 2
  }
}
```

So `module.eks.cluster_endpoint` is not a magic reference — it is just a value that travels: **AWS resource attribute → output declaration → consumer**. If you ever want to know what a `module.X.Y` reference actually contains, look in `modules/X/outputs.tf` and find the output named `Y`.

---

### Reading `resource_type.label.attribute` references

Inside a module you will see a different but related pattern:

```hcl
# modules/eks/outputs.tf
output "cluster_endpoint" {
  value = aws_eks_cluster.this.endpoint
}
```

`aws_eks_cluster.this.endpoint` also has three parts:

```
aws_eks_cluster . this . endpoint
       │            │        │
       │            │        └── an attribute AWS sets on the resource
       │            │            after it is created (you don't write this)
       │            └── the label you gave this resource instance
       └── the resource type — tells Terraform what to create in AWS
```

**Part 1 — `aws_eks_cluster`**
The resource type. Every type in Terraform maps to one real AWS service. It is defined by the AWS provider — not something you invent. Examples: `aws_eks_cluster` → EKS cluster, `aws_db_instance` → RDS database, `aws_vpc` → VPC. The full list of attributes available on each type is in the Terraform AWS provider documentation.

**Part 2 — `this`**
The label you gave the resource when you declared it in `main.tf` of the module:

```hcl
# modules/eks/main.tf
resource "aws_eks_cluster" "this" {   # ← "this" is the label you chose
  name    = local.cluster_name
  version = "1.29"
  ...
}
```

`this` is a convention — when there is only one resource of a type in a file, many Terraform codebases name it `this` rather than something more specific. You could have written:

```hcl
resource "aws_eks_cluster" "main_cluster" { ... }
# then the reference would be: aws_eks_cluster.main_cluster.endpoint
```

**Part 3 — `endpoint`**
An attribute that AWS populates on the resource after it has been created. You do not write this value — AWS generates it and Terraform reads it back. For an EKS cluster, `endpoint` contains the Kubernetes API server URL, e.g.:

```
https://ABC123DEF456.gr7.us-west-2.eks.amazonaws.com
```

**Comparing the two patterns side by side:**

```
module.eks.cluster_endpoint          ← crosses a module boundary
   │     │       │
   │     │       output name in modules/eks/outputs.tf
   │     module label in root main.tf
   keyword

aws_eks_cluster.this.endpoint        ← stays inside one module
   │              │      │
   │              │      attribute set by AWS after creation
   │              resource label in modules/eks/main.tf
   resource type from the AWS provider
```

The rule is simple: **`module.X.Y` reads an output; `resource_type.label.attribute` reads a resource attribute.** Outputs are how a module packages up resource attributes and hands them to the outside world.

---

### `terraform destroy` — same graph, reversed

When you run `terraform destroy`, Terraform uses the same dependency graph but walks it in reverse:

1. `helm-addons` destroyed first (it depends on eks + iam, so it must go before them)
2. `iam` and `secrets` destroyed next (parallel)
3. `eks`, `rds`, `ecr` destroyed next (parallel)
4. `networking` destroyed last (everything else was inside it)

This ensures, for example, that the load balancer (created by the ALB controller inside EKS) is removed before the VPC is deleted — otherwise AWS would refuse to delete the VPC because it still contains resources.

---

## Directory layout

```
terraform/
├── versions.tf       ← which providers to use and their versions
├── backend.tf        ← where to store the state file (S3)
├── variables.tf      ← input parameters (environment name, CIDR, etc.)
├── main.tf           ← calls all the modules
├── outputs.tf        ← values printed after apply (endpoints, ARNs)
├── staging.tfvars    ← concrete values for the staging environment
├── production.tfvars ← concrete values for the production environment
└── modules/
    ├── networking/   ← VPC, subnets, NAT gateway, security groups
    ├── eks/          ← Kubernetes cluster
    ├── ecr/          ← Docker image registries
    ├── rds/          ← MySQL database
    ← secrets/        ← AWS Secrets Manager secret (shell only)
    ├── iam/          ← IAM roles for the cluster add-ons
    └── helm-addons/  ← load balancer controller + external secrets operator
```

A **module** is just a folder of Terraform files that does one focused job. Breaking things into modules keeps each piece understandable and lets us reuse them. The root `main.tf` is the conductor — it calls each module and wires their outputs together.

---

## Root files

### `versions.tf` — providers and their versions

```hcl
terraform {
  required_version = ">= 1.7"
```
This line says "this code requires Terraform version 1.7 or newer." Without it, someone running an older version might get confusing errors. We need 1.7 specifically because that version introduced mock provider support for `terraform test`.

```hcl
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
```
We declare the AWS provider. `~> 5.0` means "version 5.anything — but not version 6." This is called a *pessimistic constraint*. It allows bug-fix updates (5.1, 5.2) but prevents breaking changes from a major version bump landing silently.

```hcl
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.13"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.30"
    }
```
We also need the Helm and Kubernetes providers because the `helm-addons` module installs software into the EKS cluster using Helm charts. These providers speak to the Kubernetes API using the cluster's credentials.

```hcl
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
```
`random` is used to generate the initial RDS password. `tls` is used to fetch the OIDC certificate thumbprint from the EKS cluster — required when creating the OIDC provider for IAM roles.

```hcl
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
```
This configures the AWS provider. `default_tags` automatically applies these tags to every resource Terraform creates. This is important for cost tracking (you can filter AWS Cost Explorer by `Project=hello-login`) and for knowing at a glance which resources are managed by Terraform vs. created manually.

```hcl
provider "helm" {
  kubernetes {
    host                   = module.eks.cluster_endpoint
    cluster_ca_certificate = base64decode(module.eks.cluster_ca)
    token                  = data.aws_eks_cluster_auth.this.token
  }
}
```
The Helm provider needs to know how to reach the Kubernetes cluster. We give it the API endpoint, the cluster's certificate (to verify it's the real cluster), and a short-lived auth token. `base64decode` is needed because Kubernetes stores the certificate in base64 format but the provider wants raw bytes.

```hcl
data "aws_eks_cluster_auth" "this" {
  name = module.eks.cluster_name
}
```
This is a **data source** — it reads existing information from AWS rather than creating something new. Here it fetches a temporary authentication token for the EKS cluster. Tokens expire after 15 minutes, so Terraform fetches a fresh one on each run.

---

### `backend.tf` — where state is stored

By default, Terraform stores state in a local file called `terraform.tfstate` on your machine. That is fine for solo use. The `backend.tf` file contains a commented-out S3 backend configuration for when you need to share state across a team or with CI/CD pipelines.

```hcl
# The backend block is commented out by default.
# Uncomment it when multiple people or a CI/CD pipeline need to apply infrastructure.
# Before uncommenting, create the S3 bucket (see comments inside backend.tf).

# terraform {
#   backend "s3" {
#     bucket       = "hello-login-tfstate"
#     key          = "hello-login/terraform.tfstate"
#     region       = "us-west-2"
#     use_lockfile = true
#     encrypt      = true
#   }
# }
```

When you do enable the S3 backend, here is what each field does:

- **`bucket`** — the S3 bucket that holds the state file. Must be created before `terraform init` (the commands are in `backend.tf`'s comments).
- **`key`** — the path within the bucket. When using workspaces (staging/production), Terraform automatically prefixes this with `env:/<workspace>/`.
- **`use_lockfile = true`** — S3 native locking. Before applying, Terraform writes a lock file into the bucket. If another `apply` is already running, it sees the lock and refuses to start — preventing two people from corrupting state simultaneously. Replaces the older `dynamodb_table` approach (deprecated in AWS provider v5).
- **`encrypt = true`** — the state file can contain sensitive values (RDS passwords, ARNs). This enables S3 server-side encryption at rest.

**Why can't we use variables in the backend block?**  
Terraform initialises the backend before it processes any variables, so `var.environment` is not available here. Workspaces handle per-environment state isolation instead.

---

### `variables.tf` — input parameters

```hcl
variable "environment" {
  description = "Environment name (staging or production)"
  type        = string

  validation {
    condition     = contains(["staging", "production"], var.environment)
    error_message = "environment must be 'staging' or 'production'."
  }
}
```
Variables are the knobs that change between environments. This one controls everything that differs between staging and production (resource names, CIDR blocks, deletion protection).

The `validation` block is a guard rail. If someone typos `"prodution"` or tries to use `"dev"`, Terraform will error immediately with a clear message rather than creating resources with wrong names.

```hcl
variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
}
```
The VPC CIDR determines the IP address range for the entire environment. Staging uses `10.0.0.0/16` and production uses `10.1.0.0/16` — keeping them different means the two networks could be peered in the future without conflicts.

```hcl
variable "rds_prevent_destroy" {
  description = "Enable lifecycle prevent_destroy on RDS (set true for production)"
  type        = bool
  default     = false
}
```
This variable drives deletion protection on the database. For staging it's `false` (so `terraform destroy` works cleanly for cost management). For production it's `true` — the database will refuse to be deleted even if someone runs `terraform destroy`.

```hcl
variable "github_org" {
  description = "GitHub organisation or user that owns the repo"
  type        = string
  default     = "gxozer"
}

variable "github_repo" {
  description = "GitHub repository name"
  type        = string
  default     = "hello_login_deploy"
}
```
These are needed for the GitHub Actions IAM role — the role's trust policy only allows tokens from this specific repository, so no other GitHub repo can assume it.

---

### `main.tf` — the composition layer

`main.tf` is the conductor. It calls each module and wires outputs from one into inputs of another. Nothing is created here directly — all resources live inside the modules.

```hcl
data "aws_caller_identity" "current" {}
```
This data source fetches the AWS account ID of whoever is running Terraform. We need the account ID to construct IAM ARNs (`arn:aws:iam::277070500859:role/...`). Using a data source instead of hardcoding the account ID means this code works in any AWS account.

```hcl
module "networking" {
  source = "./modules/networking"

  environment = var.environment
  aws_region  = var.aws_region
  vpc_cidr    = var.vpc_cidr
}
```
Calls the networking module. We pass in the variables it needs. Note that `source = "./modules/networking"` points to a local directory — this is a local module as opposed to one from the Terraform Registry.

```hcl
module "eks" {
  source = "./modules/eks"

  environment        = var.environment
  vpc_id             = module.networking.vpc_id
  private_subnet_ids = module.networking.private_subnet_ids
  ...
}
```
The EKS module needs to know which VPC and subnets to place the cluster in. It gets these from the networking module's *outputs*. Terraform automatically works out the dependency: it will always create networking before EKS.

```hcl
module "helm_addons" {
  source = "./modules/helm-addons"
  ...
  depends_on = [module.eks, module.iam]
}
```
`depends_on` is an explicit dependency hint. Helm charts are installed *into* the EKS cluster, so the cluster must be fully ready first. Without this, Terraform might try to install Helm charts before the cluster API is available.

---

### `outputs.tf` — values printed after apply

```hcl
output "cluster_name" {
  description = "EKS cluster name"
  value       = module.eks.cluster_name
}
```
After `terraform apply` completes, outputs are printed to the terminal. They also become available to other systems (CI pipelines, scripts) via `terraform output cluster_name`. This saves having to look up values in the AWS console after provisioning.

---

### `staging.tfvars` / `production.tfvars` — concrete values

```hcl
environment         = "staging"
aws_region          = "us-west-2"
vpc_cidr            = "10.0.0.0/16"
eks_node_type       = "t3.small"
eks_min_nodes       = 2
eks_max_nodes       = 6
rds_instance_class  = "db.t3.micro"
rds_prevent_destroy = false
```

These files supply the values for the variables declared in `variables.tf`. You pass one to Terraform at runtime:

```bash
terraform apply -var-file=staging.tfvars
```

**Why separate files instead of one file with if/else?**  
Terraform does not have if/else at the file level. Separate `.tfvars` files are the idiomatic pattern — each file is a complete, readable description of one environment's settings. No logic to follow, no chance of a typo in a condition affecting the wrong environment.

**Why are these files committed to git?**  
They contain no secrets — only infrastructure shape (sizes, CIDRs, flags). Secrets (database passwords, API keys) are never in `.tfvars` files; they live in AWS Secrets Manager and are populated out-of-band.

---

## Modules

### `modules/networking/`

**Why do we need a dedicated VPC?**  
AWS accounts come with a default VPC, but using it for applications is bad practice. Resources in the default VPC often have public IPs by default, and the default VPC is shared across all your workloads. A dedicated VPC gives us full control over networking, isolation from other workloads, and predictable IP address ranges.

```hcl
data "aws_availability_zones" "available" {
  state = "available"
}
```
This fetches the list of Availability Zones in the region. Rather than hardcoding `["us-west-2a", "us-west-2b", "us-west-2c"]`, we ask AWS for the available ones — this makes the code work if zone names change or in other regions.

```hcl
locals {
  azs             = slice(data.aws_availability_zones.available.names, 0, 3)
  public_subnets  = [for i in range(3) : cidrsubnet(var.vpc_cidr, 8, i)]
  private_subnets = [for i in range(3) : cidrsubnet(var.vpc_cidr, 8, i + 10)]
}
```
`locals` are computed values used within this module — like local variables in a programming language.

- `slice(..., 0, 3)` — take the first 3 AZs.
- `cidrsubnet(var.vpc_cidr, 8, i)` — carves a `/24` subnet out of the VPC's `/16`. With `vpc_cidr = "10.0.0.0/16"`: subnet 0 → `10.0.0.0/24`, subnet 1 → `10.0.1.0/24`, subnet 10 → `10.0.10.0/24`, etc. Using a function instead of hardcoded CIDRs means the same code works for both staging (`10.0.x.x`) and production (`10.1.x.x`) automatically.

**Why public AND private subnets?**  
- **Public subnets** — have a route to the internet via the Internet Gateway. Used for load balancers (which need to receive traffic from the internet).
- **Private subnets** — no direct internet route. Used for EKS nodes and the RDS database. If a node is compromised, it cannot be reached directly from the internet. Outbound traffic (pulling Docker images, calling AWS APIs) goes through the NAT gateway.

```hcl
resource "aws_subnet" "public" {
  count = 3
  ...
  tags = {
    "kubernetes.io/role/elb" = "1"
  }
}
```
The `count = 3` creates three resources from one block — one per AZ. The `kubernetes.io/role/elb` tag is read by the AWS Load Balancer Controller running inside EKS. When you create a Kubernetes Ingress, the controller looks for subnets with this tag to know where to place the public load balancer. Without it, the controller cannot find the right subnets and the Ingress will fail.

```hcl
resource "aws_nat_gateway" "this" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[0].id
  ...
}
```
A NAT (Network Address Translation) gateway lets resources in private subnets make outbound internet connections (e.g., EKS nodes pulling Docker images from ECR) without being reachable from the internet. We put it in a public subnet because it needs internet access itself. We use a single NAT gateway (not one per AZ) to save cost — the tradeoff is that if `us-west-2a` has an outage, nodes in other AZs lose outbound connectivity. For production HA this can be changed to one per AZ.

```hcl
resource "aws_security_group" "rds" {
  ingress {
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.eks_nodes.id]
  }
}
```
This security group only allows MySQL connections (port 3306) from the EKS node security group. Nothing else — not the internet, not your laptop. This is the principle of least privilege applied to network access: the database is only reachable from the application nodes.

---

### `modules/eks/`

**Why EKS and not plain EC2?**  
Running Kubernetes on bare EC2 requires managing the control plane yourself (etcd, API server, scheduler, upgrades). EKS is AWS's managed Kubernetes — AWS runs the control plane, we only manage the worker nodes. We pay for it but avoid significant operational overhead.

```hcl
resource "aws_iam_role" "cluster" {
  name               = "${local.cluster_name}-cluster"
  assume_role_policy = data.aws_iam_policy_document.eks_assume_role.json
}

resource "aws_iam_role_policy_attachment" "cluster_policy" {
  role       = aws_iam_role.cluster.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}
```
EKS needs an IAM role to make AWS API calls on your behalf (creating load balancers, describing EC2 instances, etc.). The `assume_role_policy` says "only the EKS service (`eks.amazonaws.com`) is allowed to use this role" — no other service or user can assume it. We then attach the `AmazonEKSClusterPolicy` which grants EKS the specific permissions it needs.

```hcl
resource "aws_eks_cluster" "this" {
  name     = local.cluster_name   # "hello-login-staging" or "hello-login-production"
  version  = "1.29"
  ...
  vpc_config {
    endpoint_private_access = true
    endpoint_public_access  = true
  }
}
```
`endpoint_private_access = true` means pods and nodes inside the VPC can reach the Kubernetes API over the private network (faster, free). `endpoint_public_access = true` means you can run `kubectl` from your laptop. In a stricter setup you'd set public access to false and require a VPN — left public here for developer convenience.

```hcl
resource "aws_eks_node_group" "this" {
  subnet_ids     = var.private_subnet_ids   # nodes go in private subnets
  instance_types = [var.node_type]          # "t3.small"
  ami_type       = "AL2_x86_64"            # Amazon Linux 2
  ...
  scaling_config {
    desired_size = var.min_nodes   # start with minimum
    min_size     = var.min_nodes
    max_size     = var.max_nodes
  }
}
```
Nodes go in private subnets — they don't need public IPs. The cluster's API server talks to them over the private network. AL2 (Amazon Linux 2) is the standard EKS-optimised AMI. The scaling config enables the Kubernetes Cluster Autoscaler to add nodes when pods can't be scheduled, up to `max_nodes`.

```hcl
resource "aws_eks_addon" "coredns" { addon_name = "coredns" }
resource "aws_eks_addon" "kube_proxy" { addon_name = "kube-proxy" }
resource "aws_eks_addon" "vpc_cni" { addon_name = "vpc-cni" }
resource "aws_eks_addon" "ebs_csi" { addon_name = "aws-ebs-csi-driver" }
```
These are essential cluster components:
- **coredns** — DNS resolution inside the cluster (pods look up services by name)
- **kube-proxy** — routes network traffic between pods and services
- **vpc-cni** — gives each pod a real VPC IP address (native AWS networking, no overlay)
- **ebs-csi-driver** — lets pods mount EBS volumes as persistent storage

```hcl
data "tls_certificate" "eks_oidc" {
  url = aws_eks_cluster.this.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "this" {
  url             = aws_eks_cluster.this.identity[0].oidc[0].issuer
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.eks_oidc.certificates[0].sha1_fingerprint]
}
```
This is **IRSA** (IAM Roles for Service Accounts) setup. Normally pods inherit the IAM permissions of the EC2 node they run on — too broad. IRSA lets individual pods assume a specific IAM role. It works by creating an OIDC identity provider in IAM that trusts the EKS cluster's built-in OIDC issuer. The thumbprint is a fingerprint of the OIDC server's TLS certificate — fetched dynamically so it stays correct if AWS rotates the certificate.

---

### `modules/ecr/`

**Why ECR and not Docker Hub?**  
ECR (Elastic Container Registry) is AWS's private Docker registry. Images are pulled over the private network (no internet egress cost), authentication is handled via IAM (no separate credentials), and images stay within your AWS account.

```hcl
locals {
  repos = ["hello-login-backend", "hello-login-frontend"]
}

resource "aws_ecr_repository" "this" {
  for_each = toset(local.repos)
  name     = each.key
  ...
}
```
`for_each` creates one resource per item in the set — here, one ECR repo for the backend and one for the frontend. This is cleaner than writing two nearly-identical `resource` blocks.

```hcl
  image_scanning_configuration {
    scan_on_push = true
  }
```
Every time a new image is pushed, ECR automatically scans it for known CVEs using AWS Inspector. Results appear in the ECR console. This adds zero cost and gives early warning of vulnerable base images.

```hcl
locals {
  lifecycle_policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images after 1 day"
        ...
      },
      {
        rulePriority = 2
        description  = "Keep last 10 tagged images"
        ...
      }
    ]
  })
}
```
Without lifecycle policies, ECR fills up indefinitely. Every CI build pushes a new image. The policy deletes untagged images (build artifacts that were never tagged for release) after 1 day, and keeps only the 10 most recent tagged releases. `jsonencode` converts the HCL map to a JSON string, which is what the ECR API expects.

---

### `modules/rds/`

```hcl
resource "aws_db_subnet_group" "this" {
  subnet_ids = var.private_subnet_ids
}
```
RDS requires a *subnet group* — a list of subnets it can place the database in. We use the private subnets so the database has no internet exposure. If Multi-AZ is enabled later, RDS will place the standby replica in a different subnet (different AZ) from this group automatically.

```hcl
resource "aws_db_instance" "this" {
  engine         = "mysql"
  engine_version = "8.0"
  instance_class = var.instance_class     # db.t3.micro
  allocated_storage = 20                  # GB
  storage_type      = "gp2"              # general purpose SSD
  storage_encrypted = true               # always encrypt at rest
  publicly_accessible = false            # no public endpoint
  backup_retention_period = 7            # keep 7 days of automated backups
  deletion_protection = var.prevent_destroy
  ...
}
```
Key decisions:
- **`storage_encrypted = true`** — always on, regardless of environment. Encrypting the database at rest is a baseline security requirement with zero performance impact on MySQL 8.0.
- **`publicly_accessible = false`** — the RDS instance gets no public endpoint. Application pods reach it via the private endpoint inside the VPC.
- **`backup_retention_period = 7`** — AWS takes automated daily snapshots and keeps them 7 days. This enables point-in-time recovery.
- **`deletion_protection`** — when `true` (production), AWS refuses to delete the instance even if you explicitly request it. An extra confirmation step protects against accidental destruction.

```hcl
resource "random_password" "db" {
  length  = 32
  special = false
}
```
The initial database password is generated randomly. We don't want it in a `.tfvars` file (that would be committed to git). The password ends up in Terraform state (which is encrypted in S3). After provisioning, it should be rotated via Secrets Manager. `special = false` avoids characters that cause issues in MySQL connection strings.

```hcl
  lifecycle {
    ignore_changes = [password]
  }
```
After the initial apply, if someone rotates the password in RDS directly, Terraform would detect "drift" and try to reset it back. `ignore_changes = [password]` tells Terraform to ignore future password differences — rotation is handled out-of-band.

---

### `modules/secrets/`

```hcl
resource "aws_secretsmanager_secret" "this" {
  name = "hello-login/${var.environment}"
}
```
This creates the *shell* of a Secrets Manager secret — the container — but does not set any values. The values (DATABASE_URL, JWT_SECRET, mail credentials) are populated manually or via a separate secrets rotation process.

**Why not put the values in Terraform?**  
Terraform state would contain the plaintext secret values. Even though state is encrypted in S3, it's still a risk — anyone with access to the state bucket could read the secrets. Keeping values out of Terraform state is a security best practice.

The `k8s/overlays/staging/external-secret.yaml` tells the External Secrets Operator to read from `hello-login/staging` and inject the values into a Kubernetes Secret. Terraform ensures the path exists; humans or a separate secrets tool fill in the values.

---

### `modules/iam/`

This module creates three IAM roles. All three use **IRSA** (IAM Roles for Service Accounts) — the mechanism that lets Kubernetes pods assume specific AWS roles without sharing node-level credentials.

**How IRSA works:**  
Each role has a *trust policy* that says: "Only allow assumptions if the request comes from the EKS cluster's OIDC issuer AND the Kubernetes service account is `namespace/serviceaccount-name`." This scopes each role to exactly one workload.

```hcl
data "aws_iam_policy_document" "alb_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [var.oidc_provider_arn]   # the EKS OIDC provider
    }
    condition {
      test     = "StringEquals"
      variable = "${local.oidc_host}:sub"
      values   = ["system:serviceaccount:kube-system:aws-load-balancer-controller"]
    }
  }
}
```
The `condition` pins the role to exactly one Kubernetes service account: `aws-load-balancer-controller` in namespace `kube-system`. If any other pod (even in the same cluster) tries to assume this role, AWS denies it.

**ALB Controller role** — needs permission to create and manage Application Load Balancers in response to Kubernetes Ingress objects. The policy JSON (`policies/alb-controller.json`) comes from the official AWS EKS documentation — it's the exact policy AWS publishes for this controller.

**ESO role** — External Secrets Operator needs to read from Secrets Manager:
```hcl
data "aws_iam_policy_document" "eso" {
  statement {
    actions   = ["secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret"]
    resources = ["${var.secret_arn}*"]
  }
}
```
The `*` at the end of the ARN matches versioned secret ARNs (`hello-login/staging-ABCDEF`). Only `GetSecretValue` and `DescribeSecret` are granted — not `DeleteSecret`, `PutSecretValue`, or anything else. ESO only needs to *read*.

**GitHub Actions role** — allows GitHub Actions CI/CD to push Docker images and deploy to EKS:
```hcl
condition {
  test     = "StringLike"
  variable = "token.actions.githubusercontent.com:sub"
  values   = ["repo:${var.github_org}/${var.github_repo}:*"]
}
```
The `sub` claim in the GitHub OIDC token identifies the specific repository. `StringLike` with `*` at the end allows any branch or environment within this repo. Only this repository's Actions can assume the role — other repos cannot.

---

### `modules/helm-addons/`

**Why install Helm charts via Terraform rather than `helm install`?**  
Running `helm install` manually means the installation isn't tracked anywhere. If someone runs it twice, or with different values, there's no record. Managing Helm releases in Terraform means they're part of the same `apply`/`destroy` lifecycle as the rest of the infrastructure. The cluster's add-ons are treated as infrastructure, not as manual steps.

```hcl
resource "helm_release" "alb_controller" {
  name       = "aws-load-balancer-controller"
  repository = "https://aws.github.io/eks-charts"
  chart      = "aws-load-balancer-controller"
  namespace  = "kube-system"
  version    = "1.7.2"

  set {
    name  = "serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
    value = var.alb_controller_role_arn
  }
}
```
The `set` block passes the IAM role ARN to the Helm chart as a value. The chart uses this annotation to configure IRSA on the controller's service account. The backslash escaping (`eks\\.amazonaws\\.com`) is needed because the annotation key contains dots, which Helm interprets as path separators.

```hcl
resource "helm_release" "external_secrets" {
  ...
  create_namespace = true
```
`create_namespace = true` tells the Helm provider to create the `external-secrets` namespace if it doesn't exist. Without this, the release would fail if the namespace is missing.

---

## Design decisions summary

| Decision | What we chose | Why |
| --- | --- | --- |
| State storage | S3 + S3 native locking | Team-safe, locking prevents corruption (no DynamoDB needed in AWS provider v5+) |
| Environment separation | Workspaces + tfvars | One codebase, two configs — easier to keep in sync |
| Subnet design | Public + private | Security: DB and nodes not directly internet-accessible |
| NAT gateway | Single (not per-AZ) | Cost — HA NAT can be added for production if needed |
| RDS password | `random_password`, not in tfvars | Secrets out of git; rotated out-of-band |
| Secret values | Not in Terraform state | Reduces blast radius if state is accessed |
| Pod IAM | IRSA (per-pod roles) | Least privilege — each workload gets only what it needs |
| Helm charts | Managed by Terraform | Add-ons are infrastructure, not manual steps |
| ECR | Private, scan on push | Security scanning, no internet egress cost |
| Tags | `default_tags` on provider | Every resource tagged automatically — no per-resource repetition |

---

## Common commands

```bash
cd terraform

# Initialise (downloads providers, sets up local state)
# backend.tf is commented out by default so no S3 bucket is needed
terraform init

# Run unit tests (mock providers — no AWS credentials needed)
terraform test

# Switch to or create the staging environment
terraform workspace new staging       # first time
terraform workspace select staging    # subsequent times

# See what would change without making changes
terraform plan -var-file=staging.tfvars

# Apply changes
terraform apply -var-file=staging.tfvars

# Destroy all staging resources
# IMPORTANT: delete Kubernetes resources FIRST so the ALB controller
# removes the Application Load Balancer before Terraform destroys the VPC.
# If the ALB still exists, AWS will refuse to delete the public subnets.
kubectl delete -k k8s/overlays/staging/   # removes ALB, wait ~60s
terraform destroy -var-file=staging.tfvars

# Print current output values
terraform output

# Evaluate an expression interactively
terraform console -var-file=staging.tfvars
```
