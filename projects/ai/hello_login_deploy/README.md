# Hello Login Web App

A full-stack web application with JWT authentication. Users log in to receive a personalised greeting fetched from a protected REST API.

**Stack:** Python + Flask (backend) · TypeScript + React (frontend)

---

## Architecture

```
Browser (React/Vite)
        │
        │  POST /api/login  →  JWT token
        │
        │  GET /api/hello
        │  Authorization: Bearer <token>
        ▼
Flask Backend
        │
        │  Auth.require_auth validates JWT, checks role
        │
        │  JSON: { "message": "Hello, Alice!", "status": "ok" }
        ▼
HelloController → Response
```

- The React frontend calls `POST /api/login` to obtain a JWT, stored in `localStorage`
- `GET /api/hello` requires a valid Bearer token with role `user` or `admin`
- Flask serves the personalised greeting from `HelloController`
- In development, Vite proxies `/api/*` requests to `localhost:5001`

---

## Prerequisites

**To run with Docker (recommended):**
- Docker Desktop 4.x+

**To run locally:**
- Python 3.11+
- Node.js 18+
- npm 9+

---

## Running with Docker

The fastest way to get the full stack running is with Docker Compose.

### 1. Clone the repository

```bash
git clone https://github.com/gxozer/datatools.git
cd datatools/projects/ai/hello_login_deploy
```

### 2. Start the stack

```bash
docker compose up -d
```

This will:
- Pull/build the Flask backend image (runs Alembic migrations on startup)
- Pull/build the React/nginx frontend image
- Start both services and wire them together

Open [http://localhost:3000](http://localhost:3000) in your browser.

### 3. Stop the stack

```bash
docker compose down       # keep the database volume
docker compose down -v    # also delete the database
```

### Environment variables (optional)

To use a custom JWT secret or enable real email sending, copy the example file and fill in your values:

```bash
cp backend/.env.example backend/.env
```

Then edit `backend/.env`:

```bash
JWT_SECRET=your-32-char-secret-here
MAIL_SERVER=smtp-relay.brevo.com
MAIL_PORT=587
MAIL_USE_TLS=true
MAIL_USERNAME=your-brevo-email@example.com
MAIL_PASSWORD=your-brevo-smtp-key
MAIL_DEFAULT_SENDER=your-brevo-email@example.com
```

By default `JWT_SECRET` uses a built-in development value and email sending is suppressed. The `backend/.env` file is optional — the stack starts without it.

---

## Setup (local development)

### 1. Clone the repository

```bash
git clone https://github.com/gxozer/datatools.git
cd datatools/projects/ai/beads/hello_login
```

### 2. Backend

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate       # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

Copy the example environment file and fill in your values:

```bash
cp .env.example .env
```

**Email (Brevo):** Password reset emails are sent via [Brevo](https://brevo.com) SMTP. Sign up for a free account (300 emails/day, no expiry), then:

1. Go to **Settings → SMTP & API → SMTP** and generate an SMTP key
2. Go to **Senders, Domains & Dedicated IPs** and verify your sender address
3. Fill in the `MAIL_*` variables in `.env`:

```
MAIL_SERVER=smtp-relay.brevo.com
MAIL_PORT=587
MAIL_USE_TLS=true
MAIL_USERNAME=your-brevo-login-email@example.com
MAIL_PASSWORD=your-brevo-smtp-key
MAIL_DEFAULT_SENDER=your-brevo-login-email@example.com
```

To skip email sending in development, set `MAIL_SUPPRESS_SEND=1` in `.env` instead.

Initialise the database:

```bash
alembic upgrade head
```

### 3. Frontend

```bash
cd ../frontend
npm install
```

---

## Running Locally

Open two terminals from the `hello_login/` directory:

**Terminal 1 — Backend:**

```bash
cd backend
source .venv/bin/activate
python run.py
# Flask running on http://localhost:5001
```

**Terminal 2 — Frontend:**

```bash
cd frontend
npm run dev
# Vite running on http://localhost:5173
```

Open [http://localhost:5173](http://localhost:5173) in your browser. The app will prompt you to log in. Once authenticated you will see a personalised greeting, e.g. **"Hello, Alice!"**.

---

## API Reference

| Method | Endpoint | Auth | Response |
|--------|----------|------|----------|
| POST | `/api/login` | None | `{"token": "<jwt>", "status": "ok"}` |
| GET | `/api/hello` | Bearer JWT (role: `user` or `admin`) | `{"message": "Hello, {name}!", "status": "ok"}` |
| GET | `/api/health` | None | `{"status": "ok"}` |

---

## Testing

See [TESTING.md](TESTING.md) for full instructions. Quick start:

```bash
# Backend tests (unit + integration)
backend/.venv/bin/python -m pytest tests/unit/ tests/integration/ -v

# Frontend tests (also run inside docker build automatically)
cd frontend && npm test

# Container structure tests (requires images to be built first)
make test-containers

# E2E tests against the containerized stack
make test-e2e-docker
```

### Container test infrastructure

Container specs live in `tests/container/` and use [container-structure-test](https://github.com/GoogleContainerTools/container-structure-test):

```bash
brew install container-structure-test   # one-time install
make test-backend                        # backend image specs
make test-frontend                       # frontend image specs
```

To verify the container test infrastructure is set up correctly:

```bash
bash tests/container/verify_setup.sh
```

### CI (GitHub Actions)

All tests run automatically on every push or pull request that touches files under `projects/ai/hello_login_deploy/`. The pipeline is defined in `.github/workflows/hello-login-ci.yml`.

#### Pipeline jobs

| Job | Trigger | What it runs |
|-----|---------|-------------|
| Backend unit + integration | every push/PR | `pytest tests/unit/ tests/integration/` |
| Backend container | every push/PR | `docker build` → container-structure-test |
| Frontend container | every push/PR | `docker build` (includes Vitest) → container-structure-test |
| E2E tests | after both container jobs pass | `docker compose up` → Playwright against the live stack |

#### Viewing results

1. Go to [github.com/gxozer/datatools/actions](https://github.com/gxozer/datatools/actions)
2. Click the workflow run to see all jobs
3. Click any job to see step-by-step logs
4. Failed steps are highlighted in red with the full error output

On a pull request, results appear directly on the PR page — all jobs must pass before merging.

#### Adding secrets

If the workflow needs credentials (e.g. for future AWS deployment steps), add them as GitHub secrets:

1. Go to **Settings → Secrets and variables → Actions**
2. Click **New repository secret**
3. Reference in the workflow as `${{ secrets.MY_SECRET }}`

Never hardcode credentials in the workflow file.

#### Testing the pipeline locally

Use [`act`](https://github.com/nektos/act) to run the workflow on your machine without pushing to GitHub:

```bash
brew install act   # one-time install
cd /path/to/datatools
act push --workflows .github/workflows/hello-login-ci.yml
```

`act` runs jobs inside Docker containers that mirror GitHub's runners. Some features (e.g. caching) behave slightly differently locally — treat it as a fast smoke test before pushing.

---

## Debugging

### Backend — PyCharm

1. Open `backend/` in PyCharm and set the interpreter to `backend/.venv`
2. Open `run.py`
3. Click in the gutter next to any line to set a breakpoint
4. Click **🐛 Debug** (or press Shift+F9) to start the server in debug mode
5. Trigger the endpoint from the browser — PyCharm pauses at your breakpoint with the full debugger UI (variables, call stack, step controls)

### Backend — Docker (remote-pdb)

`remote-pdb` is a step-through debugger that runs inside the container and accepts connections over TCP on port 4444.

**1. Add a breakpoint in code**

```python
import remote_pdb; remote_pdb.set_trace()
```

**2. Start the stack with the debug overlay**

```bash
docker compose -f docker-compose.yml -f docker-compose.override.yml up -d --build
```

**3. Trigger the request** (e.g. submit the signup form). The process will freeze waiting for a connection.

**4. Connect from a terminal**

```bash
nc localhost 4444
```

You land in a pdb prompt with the full call stack:

```
> /app/app/auth_controllers.py(45)signup()
-> data = request.get_json()
(Pdb)
```

**Useful commands**

| Command | Action |
|---------|--------|
| `n` | Next line (stay in current function) |
| `s` | Step into function call |
| `r` | Run until current function returns |
| `c` | Continue to next breakpoint |
| `p expr` | Print expression — `p data`, `p request.method` |
| `pp expr` | Pretty-print (dicts, lists) |
| `l` | Show surrounding source |
| `ll` | Show full current function |
| `w` | Call stack |
| `u` / `d` | Move up/down the call stack |
| `b 72` | Set breakpoint at line 72 |
| `q` | Quit (request fails with 500, server keeps running) |

> Port 4444 is only exposed when using the override file. Remove `set_trace()` calls before committing — they will block requests indefinitely.

### Backend — terminal (pdb)

Add `breakpoint()` anywhere in the code, then run the server normally:

```bash
python run.py
```

When execution reaches that line the terminal drops into a `pdb` prompt:

```
(Pdb) n       # next line
(Pdb) s       # step into
(Pdb) p expr  # print expression
(Pdb) c       # continue
(Pdb) q       # quit
```

---

## Inspecting the Database

### MySQL (Docker)

Connect to the MySQL container with an interactive shell:

```bash
docker compose exec mysql mysql -u hello -phello hello_login
```

Or run a one-liner without an interactive session:

```bash
docker compose exec mysql mysql -u hello -phello hello_login -e "SELECT * FROM users LIMIT 10;"
```

Useful SQL commands once connected:

```sql
SHOW TABLES;
DESCRIBE users;
SELECT * FROM users;
SELECT * FROM login_attempts;
SELECT * FROM password_reset_tokens;
EXIT;
```


---

## Deploying to Amazon EKS

Full technical details are in [docs/PRD_eks_deployment.md](docs/PRD_eks_deployment.md) and [docs/TDS_eks_deployment.md](docs/TDS_eks_deployment.md). This section is the operational runbook.

### Prerequisites

- AWS CLI configured with an `admin` profile
- `eksctl` — `brew install eksctl`
- `kubectl` — `brew install kubectl`
- `helm` — `brew install helm`

```bash
export AWS_PROFILE=admin
export AWS_DEFAULT_REGION=us-west-2
```

Setting `AWS_DEFAULT_REGION` means you don't need `--region us-west-2` on every command.

---

### Phase 1 — AWS Infrastructure

#### 1.1 Provision EKS cluster and ECR repositories (PR-61)

**Step 1 — Set AWS profile and region**

All commands in this section require admin credentials. Set them for the session:

```
export AWS_PROFILE=admin
export AWS_DEFAULT_REGION=us-west-2
```

Verify you're using the right account:

```
aws sts get-caller-identity
```

Expected: account `277070500859`, not `dev1`.

---

**Step 2 — Create the EKS cluster**

Creates the Kubernetes control plane (managed by AWS) and a node group of 2 `t3.small` EC2 worker nodes with autoscaling up to 6. Takes ~15 minutes.

```
eksctl create cluster --name hello-login --region us-west-2 --nodegroup-name standard --node-type t3.small --nodes 2 --nodes-min 2 --nodes-max 6 --managed
```

If the cluster already exists (e.g. after a session break), reconnect instead:

```
aws eks update-kubeconfig --name hello-login --region us-west-2
```

Verify the cluster is reachable:

```
kubectl get nodes
```

Expected: 2 nodes in `Ready` state.

---

**Step 3 — Create ECR repositories**

ECR (Elastic Container Registry) is the private Docker registry where CI/CD pushes built images. Check if repos already exist:

```
aws ecr describe-repositories --region us-west-2 --query 'repositories[].repositoryName'
```

If `hello-login-backend` or `hello-login-frontend` are missing, create them:

```
aws ecr create-repository --repository-name hello-login-backend --region us-west-2
```

```
aws ecr create-repository --repository-name hello-login-frontend --region us-west-2
```

---

**Step 4 — Test image push (optional but recommended)**

Confirms Docker can authenticate and push to ECR before the CI/CD pipeline runs:

```
AWS_ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
```

```
aws ecr get-login-password --region us-west-2 | docker login --username AWS --password-stdin $AWS_ACCOUNT.dkr.ecr.us-west-2.amazonaws.com
```

```
docker build -t $AWS_ACCOUNT.dkr.ecr.us-west-2.amazonaws.com/hello-login-backend:test backend/
```

```
docker push $AWS_ACCOUNT.dkr.ecr.us-west-2.amazonaws.com/hello-login-backend:test
```

Expected: `Pushed` confirmation with no errors.

---

#### 1.2 Namespaces and AWS Load Balancer Controller (PR-62)

**Step 1 — Enable OIDC for IRSA**

EKS (Elastic Kubernetes Service) uses an OIDC (OpenID Connect) identity provider to let Kubernetes service accounts assume IAM (Identity and Access Management) roles without storing AWS credentials inside the cluster. This is required for the ALB (Application Load Balancer) controller and ESO (External Secrets Operator) to make authenticated calls to AWS APIs. Run once per cluster:

```
eksctl utils associate-iam-oidc-provider --region us-west-2 --cluster hello-login --approve
```

Verify:

```
aws iam list-open-id-connect-providers
```

Expected: one provider listed with the cluster's OIDC URL.

---

**Step 2 — Create namespaces**

Namespaces logically separate staging and production resources within the same cluster:

```
kubectl create namespace hello-login-staging
```

```
kubectl create namespace hello-login-production
```

Verify:

```
kubectl get namespaces | grep hello-login
```

Expected: both namespaces in `Active` state.

---

**Step 3 — Create IAM policy for the ALB controller**

Downloads the official AWS policy that grants the controller permission to create and manage ALBs:

```
curl -O https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v2.7.2/docs/install/iam_policy.json
```

Check if the policy already exists first:

```
aws iam list-policies --query 'Policies[?PolicyName==`AWSLoadBalancerControllerIAMPolicy`].PolicyName' --output text
```

If the output is empty, create it:

```
aws iam create-policy --policy-name AWSLoadBalancerControllerIAMPolicy --policy-document file://iam_policy.json
```

If it already exists, skip this step.

---

**Step 4 — Create IRSA for the ALB controller**

Links a Kubernetes service account to an IAM role so the controller pod can call AWS APIs without storing credentials:

```
AWS_ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
```

```
eksctl create iamserviceaccount --cluster hello-login --namespace kube-system --name aws-load-balancer-controller --attach-policy-arn arn:aws:iam::${AWS_ACCOUNT}:policy/AWSLoadBalancerControllerIAMPolicy --approve --region us-west-2
```

---

**Step 5 — Install the ALB controller via Helm**

Deploys the controller pod into `kube-system`. It watches for Ingress resources and automatically provisions AWS ALBs:

```
helm repo add eks https://aws.github.io/eks-charts && helm repo update
```

```
helm install aws-load-balancer-controller eks/aws-load-balancer-controller -n kube-system --set clusterName=hello-login --set serviceAccount.create=false --set serviceAccount.name=aws-load-balancer-controller
```

Verify:

```
kubectl get deployment -n kube-system aws-load-balancer-controller
```

Expected: `2/2 READY`.

---

#### 1.3 Provision RDS MySQL (PR-69)

**Step 1 — Enable DNS on the VPC**

RDS requires DNS resolution and DNS hostnames to be enabled on the VPC. Without this, the subnet group creation fails.

First get the VPC ID from the EKS cluster:

```
VPC_ID=$(aws eks describe-cluster --name hello-login --region us-west-2 --query 'cluster.resourcesVpcConfig.vpcId' --output text)
```

```
echo "VPC: $VPC_ID"
```

Then enable DNS:

```
aws ec2 modify-vpc-attribute --vpc-id $VPC_ID --enable-dns-support --region us-west-2
```

```
aws ec2 modify-vpc-attribute --vpc-id $VPC_ID --enable-dns-hostnames --region us-west-2
```

---

**Step 2 — Create a DB subnet group**

Tells RDS which subnets it can use. Must span at least 2 availability zones. Uses the 3 private subnets created by eksctl:

Create the DB subnet group via the **AWS Console** — the CLI command fails due to an account-level subnet validation issue that the Console bypasses:

1. Go to **RDS → Subnet groups → Create DB subnet group**
2. Name: `hello-login-db-subnet`
3. VPC: select the VPC matching `$VPC_ID`
4. Add subnets from at least 2 AZs (us-west-2a and us-west-2b)
5. Click **Create**

---

**Step 3 — Create a security group for RDS**

Controls which resources can connect to the database. Only EKS nodes are allowed on port 3306:

```
RDS_SG=$(aws ec2 create-security-group --group-name hello-login-rds-sg --description "Allow MySQL from EKS nodes" --vpc-id $VPC_ID --region us-west-2 --query 'GroupId' --output text)
```

```
NODE_SG=$(aws eks describe-cluster --name hello-login --region us-west-2 --query 'cluster.resourcesVpcConfig.clusterSecurityGroupId' --output text)
```

```
aws ec2 authorize-security-group-ingress --group-id $RDS_SG --protocol tcp --port 3306 --source-group $NODE_SG --region us-west-2
```

---

**Step 4 — Create the RDS instance**

Provisions a `db.t3.micro` MySQL 8.0 instance. Not publicly accessible — only reachable from within the VPC:

```
aws rds create-db-instance --db-instance-identifier hello-login --db-instance-class db.t3.micro --engine mysql --engine-version 8.0 --master-username hello --master-user-password helloRDS1 --db-name hello_login --db-subnet-group-name hello-login-db-subnet --vpc-security-group-ids $RDS_SG --no-publicly-accessible --allocated-storage 20 --region us-west-2
```

Wait for it to become available (~5 minutes):

```
aws rds wait db-instance-available --db-instance-identifier hello-login --region us-west-2
```

---

**Step 5 — Get the RDS endpoint**

Save this value — it is needed for the DATABASE_URL secret in the next step:

```
aws rds describe-db-instances --db-instance-identifier hello-login --region us-west-2 --query 'DBInstances[0].Endpoint.Address' --output text
```

The DATABASE_URL will be: `mysql+pymysql://hello:helloRDS1@<endpoint>:3306/hello_login`

---

#### 1.4 Secrets Manager and External Secrets Operator (PR-63)

**Step 1 — Create secrets in AWS Secrets Manager**

Secrets Manager stores sensitive credentials outside the cluster. Do NOT pass JSON inline — use a file to avoid shell quoting issues.

Generate a JWT secret:

```
python3 -c "import secrets; print(secrets.token_hex(32))"
```

Write staging credentials to a file (fill in actual values):

```bash
cat > /tmp/staging-secret.json << 'EOF'
{
  "DATABASE_URL": "mysql+pymysql://hello:helloRDS1@<rds-endpoint>:3306/hello_login",
  "JWT_SECRET": "<output-from-above>",
  "MAIL_PASSWORD": "<brevo-smtp-key>",
  "MAIL_USERNAME": "<brevo-login-email>",
  "MAIL_DEFAULT_SENDER": "<sender-email>"
}
EOF
```

```
aws secretsmanager create-secret --name hello-login/staging --region us-west-2 --secret-string file:///tmp/staging-secret.json
```

Repeat for production (use a different `JWT_SECRET`):

```bash
cat > /tmp/production-secret.json << 'EOF'
{
  "DATABASE_URL": "mysql+pymysql://hello:helloRDS1@<rds-endpoint>:3306/hello_login",
  "JWT_SECRET": "<different-strong-secret>",
  "MAIL_PASSWORD": "<brevo-smtp-key>",
  "MAIL_USERNAME": "<brevo-login-email>",
  "MAIL_DEFAULT_SENDER": "<sender-email>"
}
EOF
```

```
aws secretsmanager create-secret --name hello-login/production --region us-west-2 --secret-string file:///tmp/production-secret.json
```

To update a secret later:

```
aws secretsmanager put-secret-value --secret-id hello-login/staging --region us-west-2 --secret-string file:///tmp/staging-secret.json
```

Verify:

```
aws secretsmanager list-secrets --region us-west-2 --query 'SecretList[].Name'
```

Expected: both `hello-login/staging` and `hello-login/production` listed.

---

**Step 2 — Install External Secrets Operator (ESO)**

ESO watches for `ExternalSecret` resources in the cluster and automatically syncs values from Secrets Manager into Kubernetes Secrets. Pods then use those Kubernetes Secrets as environment variables:

```
helm repo add external-secrets https://charts.external-secrets.io && helm repo update
```

```
helm install external-secrets external-secrets/external-secrets -n external-secrets --create-namespace
```

Verify:

```
kubectl get pods -n external-secrets
```

Expected: ESO pods in `Running` state.

---

**Step 3 — Create IRSA for ESO**

ESO needs an IAM role to call Secrets Manager. Create the policy and link it to the ESO service account:

```bash
cat > /tmp/eso-policy.json << 'EOF'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue",
        "secretsmanager:DescribeSecret"
      ],
      "Resource": "arn:aws:secretsmanager:us-west-2:*:secret:hello-login/*"
    }
  ]
}
EOF
```

```
aws iam create-policy --policy-name ESOSecretsManagerPolicy --policy-document file:///tmp/eso-policy.json
```

If `ESOSecretsManagerPolicy` already exists, skip the create. Then:

```
AWS_ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
```

```
eksctl create iamserviceaccount --cluster hello-login --namespace external-secrets --name external-secrets --attach-policy-arn arn:aws:iam::${AWS_ACCOUNT}:policy/ESOSecretsManagerPolicy --approve --region us-west-2
```

Verify:

```
kubectl get serviceaccount external-secrets -n external-secrets -o yaml | grep eks.amazonaws.com
```

Expected: an annotation with the IAM role ARN.

If the annotation is missing (because Helm created the service account before eksctl), rerun with:

```
eksctl create iamserviceaccount --cluster hello-login --namespace external-secrets --name external-secrets --attach-policy-arn arn:aws:iam::${AWS_ACCOUNT}:policy/ESOSecretsManagerPolicy --approve --region us-west-2 --override-existing-serviceaccounts
```

---

**Step 4 — Create a ClusterSecretStore**

The ClusterSecretStore tells ESO where to read secrets from (AWS Secrets Manager in us-west-2).

Note: the API version is `external-secrets.io/v1` (not `v1beta1`). Use Python to write the file — heredocs break when pasted due to indentation:

```
printf 'apiVersion: external-secrets.io/v1\nkind: ClusterSecretStore\nmetadata:\n  name: aws-secrets-manager\nspec:\n  provider:\n    aws:\n      service: SecretsManager\n      region: us-west-2\n      auth:\n        jwt:\n          serviceAccountRef:\n            name: external-secrets\n            namespace: external-secrets\n' > k8s/base/cluster-secret-store.yaml
```

```
kubectl apply -f k8s/base/cluster-secret-store.yaml
```

Verify:

```
kubectl get clustersecretstore
```

Expected: `aws-secrets-manager` with `READY = True`.

---

**Step 5 — Create ExternalSecret manifests**

ExternalSecret resources tell ESO which keys to pull from Secrets Manager and map into Kubernetes Secrets. Create one per namespace.

ExternalSecret manifests belong in the repo at `k8s/base/`. Create the staging manifest (API version is `v1`, not `v1beta1`):

```
printf 'apiVersion: external-secrets.io/v1\nkind: ExternalSecret\nmetadata:\n  name: hello-login-secrets\nspec:\n  refreshInterval: 1h\n  secretStoreRef:\n    name: aws-secrets-manager\n    kind: ClusterSecretStore\n  target:\n    name: hello-login-secrets\n  data:\n    - secretKey: DATABASE_URL\n      remoteRef:\n        key: hello-login/staging\n        property: DATABASE_URL\n    - secretKey: JWT_SECRET\n      remoteRef:\n        key: hello-login/staging\n        property: JWT_SECRET\n    - secretKey: MAIL_PASSWORD\n      remoteRef:\n        key: hello-login/staging\n        property: MAIL_PASSWORD\n    - secretKey: MAIL_USERNAME\n      remoteRef:\n        key: hello-login/staging\n        property: MAIL_USERNAME\n    - secretKey: MAIL_DEFAULT_SENDER\n      remoteRef:\n        key: hello-login/staging\n        property: MAIL_DEFAULT_SENDER\n' > k8s/base/external-secret-staging.yaml
```

Apply to staging:

```
kubectl apply -f k8s/base/external-secret-staging.yaml -n hello-login-staging
```

Create and apply the production manifest (same file, `hello-login/production` key):

```
printf 'apiVersion: external-secrets.io/v1\nkind: ExternalSecret\nmetadata:\n  name: hello-login-secrets\nspec:\n  refreshInterval: 1h\n  secretStoreRef:\n    name: aws-secrets-manager\n    kind: ClusterSecretStore\n  target:\n    name: hello-login-secrets\n  data:\n    - secretKey: DATABASE_URL\n      remoteRef:\n        key: hello-login/production\n        property: DATABASE_URL\n    - secretKey: JWT_SECRET\n      remoteRef:\n        key: hello-login/production\n        property: JWT_SECRET\n    - secretKey: MAIL_PASSWORD\n      remoteRef:\n        key: hello-login/production\n        property: MAIL_PASSWORD\n    - secretKey: MAIL_USERNAME\n      remoteRef:\n        key: hello-login/production\n        property: MAIL_USERNAME\n    - secretKey: MAIL_DEFAULT_SENDER\n      remoteRef:\n        key: hello-login/production\n        property: MAIL_DEFAULT_SENDER\n' > k8s/base/external-secret-production.yaml
```

```
kubectl apply -f k8s/base/external-secret-production.yaml -n hello-login-production
```

Verify:

```
kubectl get secret hello-login-secrets -n hello-login-staging
```

```
kubectl get secret hello-login-secrets -n hello-login-production
```

Expected: both secrets exist with the correct keys.

---

### Phase 2 — Kubernetes Manifests (PR-64, PR-65)

Apply base manifests and environment overlays:

```bash
kubectl apply -k k8s/overlays/staging
kubectl rollout status deployment/backend -n hello-login-staging
kubectl rollout status deployment/frontend -n hello-login-staging
```

---

### Phase 3 — CI/CD (PR-66)

#### Prerequisites

Before the deploy job can run, complete the following setup once:

**1. Create the GitHub Actions IAM role** — see PR-66 Step 1 for full commands. In summary:
- Register the GitHub Actions OIDC provider in your AWS account
- Create `GitHubActionsDeployRole` with trust policy scoped to `repo:gxozer/datatools:ref:refs/heads/master`
- Attach `AmazonEC2ContainerRegistryPowerUser` and `EKSDeployPolicy`

**2. Add GitHub secrets** at `github.com/gxozer/datatools/settings/secrets/actions`:
- `AWS_DEPLOY_ROLE_ARN` — ARN of the role created above
- `ECR_BACKEND` — `277070500859.dkr.ecr.us-west-2.amazonaws.com/hello-login-backend`
- `ECR_FRONTEND` — `277070500859.dkr.ecr.us-west-2.amazonaws.com/hello-login-frontend`

**3. Grant the role EKS access** — add to the `aws-auth` ConfigMap:

```
kubectl edit configmap aws-auth -n kube-system
```

Add under `mapRoles`:

```yaml
- rolearn: arn:aws:iam::277070500859:role/GitHubActionsDeployRole
  username: github-actions
  groups:
    - system:masters
```

---

#### Automatic deploy (on merge to master)

On merge to `master`, GitHub Actions automatically:
1. Runs all tests (unit, integration, container, E2E)
2. Builds and pushes images to ECR tagged with the Git SHA
3. Applies the staging overlay with the new image tags
4. Waits for rollout to complete

#### Manual deploy (for testing)

The workflow supports manual triggers. To deploy without pushing to master:

1. Go to **GitHub → Actions → hello-login CI**
2. Click **Run workflow**
3. Select branch and click **Run workflow**

This runs all test jobs (unit, integration, container, E2E) but NOT the deploy job — the deploy step only runs on a push to `master`. Use this to verify tests pass before merging.

---

### Teardown (cost saving)

The easiest way to tear down is via the GitHub Actions workflow:

1. Go to **Actions → hello-login Teardown → Run workflow**
2. Check the boxes for what you want to stop:
   - **Stop RDS** (default: on) — saves ~$14/month, reversible (RDS can be restarted)
   - **Delete EKS cluster** (default: off) — saves ~$88/month, irreversible (must re-provision from Phase 1)
3. Click **Run workflow**

ECR images are always retained. To restart RDS after stopping it:

```
aws rds start-db-instance --db-instance-identifier hello-login --region us-west-2
```

**Manual teardown (if pipeline is unavailable):**

Stop RDS (~$14/month):

```
aws rds stop-db-instance --db-instance-identifier hello-login --region us-west-2
```

Delete EKS cluster (~$88/month) — delete Ingress resources first so the ALB Controller removes any provisioned ALBs before the VPC is torn down:

```
aws eks update-kubeconfig --name hello-login --region us-west-2
kubectl delete ingress --all -n hello-login-staging --ignore-not-found
kubectl delete ingress --all -n hello-login-production --ignore-not-found
sleep 30
eksctl delete cluster --name hello-login --region us-west-2
```

To restore after cluster deletion, re-run from Phase 1.

---

### Full AWS cleanup (permanent removal)

Run these steps in order to remove every AWS resource created by this project. This is irreversible.

**Step 1 — Delete Ingress and cluster**

Deletes the EKS control plane, nodes, NAT gateway, VPC, EKS OIDC provider, and the IRSA IAM roles for the ALB controller and ESO (eksctl handles all of these automatically).

```
aws eks update-kubeconfig --name hello-login --region us-west-2
kubectl delete ingress --all -n hello-login-staging --ignore-not-found
kubectl delete ingress --all -n hello-login-production --ignore-not-found
sleep 30
eksctl delete cluster --name hello-login --region us-west-2
```

**Step 2 — Delete RDS instance**

```
aws rds delete-db-instance --db-instance-identifier hello-login --skip-final-snapshot --region us-west-2
aws rds wait db-instance-deleted --db-instance-identifier hello-login --region us-west-2
```

**Step 3 — Delete RDS subnet group and security group**

```
aws rds delete-db-subnet-group --db-subnet-group-name hello-login-db-subnet --region us-west-2
aws ec2 delete-security-group --group-name hello-login-rds-sg --region us-west-2
```

**Step 4 — Delete Secrets Manager secrets**

```
aws secretsmanager delete-secret --secret-id hello-login/staging --force-delete-without-recovery --region us-west-2
aws secretsmanager delete-secret --secret-id hello-login/production --force-delete-without-recovery --region us-west-2
```

**Step 5 — Delete ECR repositories**

```
aws ecr delete-repository --repository-name hello-login-backend --force --region us-west-2
aws ecr delete-repository --repository-name hello-login-frontend --force --region us-west-2
```

**Step 6 — Delete IAM roles and policies**

The IRSA roles for the ALB controller and ESO are deleted automatically by eksctl in Step 1. Only the GitHub Actions deploy role and the managed policies need manual cleanup:

```
aws iam delete-role-policy --role-name GitHubActionsDeployRole --policy-name EKSDeployAccess
aws iam delete-role-policy --role-name GitHubActionsDeployRole --policy-name TeardownAccess
aws iam delete-role-policy --role-name GitHubActionsDeployRole --policy-name EKSDeployAccess
aws iam detach-role-policy --role-name GitHubActionsDeployRole --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryPowerUser
aws iam detach-role-policy --role-name GitHubActionsDeployRole --policy-arn arn:aws:iam::aws:policy/AmazonEKSClusterPolicy
aws iam delete-role --role-name GitHubActionsDeployRole
aws iam delete-policy --policy-arn arn:aws:iam::277070500859:policy/AWSLoadBalancerControllerIAMPolicy
aws iam delete-policy --policy-arn arn:aws:iam::277070500859:policy/ESOSecretsManagerPolicy
```

**Step 7 — Delete GitHub Actions OIDC provider**

The EKS cluster OIDC provider is deleted automatically by eksctl in Step 1. The GitHub Actions OIDC provider is separate and must be deleted manually:

```
aws iam delete-open-id-connect-provider --open-id-connect-provider-arn arn:aws:iam::277070500859:oidc-provider/token.actions.githubusercontent.com
```

**Step 8 — Remove GitHub repository secrets**

```
gh secret delete AWS_DEPLOY_ROLE_ARN --repo gxozer/datatools
gh secret delete ECR_BACKEND --repo gxozer/datatools
gh secret delete ECR_FRONTEND --repo gxozer/datatools
```

---

## Project Structure

```
hello_login_deploy/
├── backend/
│   ├── app/
│   │   ├── __init__.py           # Package entry point
│   │   ├── factory.py            # Flask app factory (create_app)
│   │   ├── auth.py               # Auth class: generate_token, require_auth
│   │   ├── auth_controllers.py   # LoginController, SignupController, etc.
│   │   ├── controllers.py        # HelloController, HealthController
│   │   ├── models.py             # User, LoginAttempt, PasswordResetToken
│   │   └── routes.py             # API Blueprint and URL rules
│   ├── Dockerfile            # Backend container image
│   ├── entrypoint.sh         # Runs migrations then starts Flask
│   ├── run.py                # Entry point
│   ├── requirements.txt      # Runtime dependencies
│   └── requirements-dev.txt  # Dev/test dependencies
├── frontend/
│   ├── src/
│   │   ├── api/
│   │   │   └── ApiClient.ts      # HTTP client class
│   │   ├── components/
│   │   │   └── HelloMessage.tsx  # Presentational component
│   │   ├── test/                 # Vitest unit tests
│   │   └── App.tsx               # Root component
│   ├── Dockerfile            # Multi-stage: build (Vitest) + nginx serve
│   ├── nginx.conf.template   # nginx config with /api proxy + SPA routing
│   └── vite.config.ts        # Vite config with /api proxy (dev only)
├── tests/
│   ├── conftest.py           # Shared pytest fixtures
│   ├── unit/                 # Controller unit tests
│   ├── integration/          # API integration tests
│   ├── e2e/                  # Playwright end-to-end tests
│   └── container/            # container-structure-test specs
│       ├── backend.yaml
│       ├── frontend.yaml
│       └── verify_setup.sh
├── docker-compose.yml        # Wires backend + frontend for one-command startup
├── docker-compose.override.yml  # Debug overlay: exposes remote-pdb on port 4444
├── Makefile                  # Build, test, and compose targets
├── pytest.ini                # Pytest configuration
├── TESTING.md                # Full testing instructions
└── README.md                 # This file

# CI pipeline lives at the repo root (not inside this directory):
# .github/workflows/hello-login-ci.yml
```
