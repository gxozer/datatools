# Technical Design Specification — Amazon EKS Deployment

**Version:** 1.0
**Date:** 2026-05-10
**Status:** Draft
**Jira:** PR-57 (subtask of PR-56)
**Related:** [PRD_eks_deployment.md](PRD_eks_deployment.md), [TDS.md](TDS.md)

---

## 1. Overview

This document describes the technical implementation for deploying the hello-login stack (Flask backend + React/nginx frontend + RDS MySQL) to Amazon EKS. It covers cluster provisioning, Kubernetes manifests, ALB ingress, secrets injection, the Alembic migration job, and CI/CD pipeline changes.

---

## 2. Architecture Diagram

```
Internet
    │
    ▼
AWS ALB (HTTPS, ACM cert)
    │
    ├─ /api/*  ──▶  backend Service (ClusterIP)
    │                   │
    │               backend Pods (Flask, 2–6 replicas)
    │                   │
    │               RDS MySQL (same VPC)
    │
    └─ /*  ──────▶  frontend Service (ClusterIP)
                        │
                    frontend Pods (nginx/React, 2–6 replicas)
```

---

## 3. Infrastructure

### 3.1 EKS Cluster

Provisioned via `eksctl`:

```bash
eksctl create cluster \
  --name hello-login \
  --region us-west-2 \
  --nodegroup-name standard \
  --node-type t3.small \
  --nodes 2 \
  --nodes-min 2 \
  --nodes-max 6 \
  --managed
```

- Kubernetes version: 1.29
- Node AMI: Amazon Linux 2 (managed, auto-updated)
- Node group autoscaling: 2–6 nodes

### 3.2 ECR Repositories

```bash
aws ecr create-repository --repository-name hello-login-backend --region us-west-2
aws ecr create-repository --repository-name hello-login-frontend --region us-west-2
```

Images tagged with the Git SHA: `<account>.dkr.ecr.us-west-2.amazonaws.com/hello-login-backend:<sha>`

### 3.3 Namespaces

```yaml
# k8s/namespaces.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: hello-login-staging
---
apiVersion: v1
kind: Namespace
metadata:
  name: hello-login-production
```

### 3.4 AWS Load Balancer Controller

Installed via Helm into `kube-system`. Requires an IAM policy attached to the node role:

```bash
helm repo add eks https://aws.github.io/eks-charts
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=hello-login \
  --set serviceAccountAnnotations."eks\.amazonaws\.com/role-arn"=<ALB_IAM_ROLE_ARN>
```

---

## 4. Secrets Management

Secrets stored in AWS Secrets Manager. Initial setup (one-time per environment):

```bash
aws secretsmanager create-secret \
  --name hello-login/production \
  --secret-string '{
    "DATABASE_URL": "mysql+pymysql://...",
    "JWT_SECRET": "...",
    "MAIL_PASSWORD": "..."
  }'
```

Injected into the cluster as a Kubernetes Secret via External Secrets Operator (ESO):

```yaml
# k8s/external-secret.yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: hello-login-secrets
  namespace: hello-login-production
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secrets-manager
    kind: ClusterSecretStore
  target:
    name: hello-login-secrets
  data:
    - secretKey: DATABASE_URL
      remoteRef:
        key: hello-login/production
        property: DATABASE_URL
    - secretKey: JWT_SECRET
      remoteRef:
        key: hello-login/production
        property: JWT_SECRET
    - secretKey: MAIL_PASSWORD
      remoteRef:
        key: hello-login/production
        property: MAIL_PASSWORD
```

---

## 5. Kubernetes Manifests

All manifests live in `k8s/` at the repo root. Namespaced per environment via Kustomize overlays.

### 5.1 ConfigMap

```yaml
# k8s/base/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: hello-login-config
data:
  FLASK_DEBUG: "false"
  PORT: "5001"
  FRONTEND_URL: "https://hello-login.example.com"
  CORS_ORIGINS: "https://hello-login.example.com"
  MAIL_SERVER: "smtp-relay.brevo.com"
  MAIL_PORT: "587"
  MAIL_USE_TLS: "true"
  MAIL_SUPPRESS_SEND: "0"
  MAX_LOGIN_ATTEMPTS: "5"
  LOCKOUT_WINDOW_MINUTES: "15"
```

### 5.2 Backend Deployment

```yaml
# k8s/base/backend-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend
spec:
  replicas: 2
  selector:
    matchLabels:
      app: backend
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    metadata:
      labels:
        app: backend
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
      initContainers:
        - name: migrate
          image: <ECR_BACKEND_IMAGE>
          command: ["python", "-m", "alembic", "upgrade", "head"]
          envFrom:
            - configMapRef:
                name: hello-login-config
            - secretRef:
                name: hello-login-secrets
      containers:
        - name: backend
          image: <ECR_BACKEND_IMAGE>
          ports:
            - containerPort: 5001
          envFrom:
            - configMapRef:
                name: hello-login-config
            - secretRef:
                name: hello-login-secrets
          livenessProbe:
            httpGet:
              path: /api/health
              port: 5001
            initialDelaySeconds: 10
            periodSeconds: 15
          readinessProbe:
            httpGet:
              path: /api/health
              port: 5001
            initialDelaySeconds: 5
            periodSeconds: 10
          resources:
            requests:
              cpu: 100m
              memory: 128Mi
            limits:
              cpu: 500m
              memory: 256Mi
```

> Note: The Alembic migration runs as an `initContainer` so it completes before any backend pod starts serving traffic. This replaces the TCP wait loop in `entrypoint.sh` — in Kubernetes, the init container handles sequencing.

### 5.3 Frontend Deployment

```yaml
# k8s/base/frontend-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: frontend
spec:
  replicas: 2
  selector:
    matchLabels:
      app: frontend
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    metadata:
      labels:
        app: frontend
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 101
      containers:
        - name: frontend
          image: <ECR_FRONTEND_IMAGE>
          ports:
            - containerPort: 8080
          env:
            - name: BACKEND_HOST
              value: "backend"
          livenessProbe:
            httpGet:
              path: /
              port: 8080
            initialDelaySeconds: 5
            periodSeconds: 15
          resources:
            requests:
              cpu: 50m
              memory: 64Mi
            limits:
              cpu: 200m
              memory: 128Mi
```

> Note: The Dockerfile uses `nginxinc/nginx-unprivileged:alpine` (not `nginx:alpine`) to support `runAsNonRoot: true`. The unprivileged image runs as user 101 and listens on port 8080 instead of 80.

### 5.4 Services

```yaml
# k8s/base/services.yaml
apiVersion: v1
kind: Service
metadata:
  name: backend
spec:
  selector:
    app: backend
  ports:
    - port: 5001
      targetPort: 5001
---
apiVersion: v1
kind: Service
metadata:
  name: frontend
spec:
  selector:
    app: frontend
  ports:
    - port: 8080
      targetPort: 8080
```

### 5.5 Ingress

```yaml
# k8s/base/ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: hello-login
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
    alb.ingress.kubernetes.io/certificate-arn: <ACM_CERT_ARN>
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
    alb.ingress.kubernetes.io/healthcheck-path: /api/health
spec:
  rules:
    - host: hello-login.example.com
      http:
        paths:
          - path: /api
            pathType: Prefix
            backend:
              service:
                name: backend
                port:
                  number: 5001
          - path: /
            pathType: Prefix
            backend:
              service:
                name: frontend
                port:
                  number: 8080
```

### 5.6 HorizontalPodAutoscaler

```yaml
# k8s/base/hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: backend
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: backend
  minReplicas: 2
  maxReplicas: 6
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: frontend
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: frontend
  minReplicas: 2
  maxReplicas: 6
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

### 5.7 Topology Spread (availability zone distribution)

Added to both Deployment pod specs:

```yaml
topologySpreadConstraints:
  - maxSkew: 1
    topologyKey: topology.kubernetes.io/zone
    whenUnsatisfiable: DoNotSchedule
    labelSelector:
      matchLabels:
        app: backend
```

---

## 6. Kustomize Overlays

```
k8s/
├── base/
│   ├── configmap.yaml
│   ├── backend-deployment.yaml
│   ├── frontend-deployment.yaml
│   ├── services.yaml
│   ├── ingress.yaml
│   ├── hpa.yaml
│   └── kustomization.yaml
└── overlays/
    ├── staging/
    │   ├── kustomization.yaml   # namespace: hello-login-staging, image tags, host
    │   └── configmap-patch.yaml # FRONTEND_URL, CORS_ORIGINS for staging domain
    └── production/
        ├── kustomization.yaml   # namespace: hello-login-production, image tags, host
        └── configmap-patch.yaml # FRONTEND_URL, CORS_ORIGINS for production domain
```

Deploy staging:
```bash
kubectl apply -k k8s/overlays/staging
```

---

## 7. CI/CD Pipeline Changes

Extend `.github/workflows/hello-login-ci.yml` with a deploy job triggered on merge to `master`:

```yaml
deploy:
  needs: [backend-tests, backend-container, frontend-container, e2e]
  runs-on: ubuntu-latest
  if: github.ref == 'refs/heads/master'
  steps:
    - uses: actions/checkout@v4

    - name: Configure AWS credentials
      uses: aws-actions/configure-aws-credentials@v4
      with:
        role-to-assume: ${{ secrets.AWS_DEPLOY_ROLE_ARN }}
        aws-region: us-west-2

    - name: Login to ECR
      uses: aws-actions/amazon-ecr-login@v2

    - name: Build and push backend
      run: |
        IMAGE=${{ env.ECR_BACKEND }}:${{ github.sha }}
        docker build -t $IMAGE projects/ai/hello_login_deploy/backend
        docker push $IMAGE

    - name: Build and push frontend
      run: |
        IMAGE=${{ env.ECR_FRONTEND }}:${{ github.sha }}
        docker build -t $IMAGE projects/ai/hello_login_deploy/frontend
        docker push $IMAGE

    - name: Deploy to staging
      run: |
        cd projects/ai/hello_login_deploy
        kustomize edit set image \
          hello-login-backend=${{ env.ECR_BACKEND }}:${{ github.sha }} \
          hello-login-frontend=${{ env.ECR_FRONTEND }}:${{ github.sha }}
        kubectl apply -k k8s/overlays/staging
        kubectl rollout status deployment/backend -n hello-login-staging
        kubectl rollout status deployment/frontend -n hello-login-staging
```

---

## 8. Rollback

```bash
# Roll back backend to previous image
kubectl rollout undo deployment/backend -n hello-login-production

# Verify
kubectl rollout status deployment/backend -n hello-login-production
```

RDS rollback: AWS automated backups enabled with 7-day retention. Point-in-time recovery via the RDS console.

---

## 9. Key Design Decisions

### 9.1 initContainer for migrations vs standalone Job

**Decision:** Alembic runs as an `initContainer` on the backend Deployment rather than a separate Job.

**Why:** Simpler — no Job lifecycle to manage, no ordering between Job and Deployment rollout. The init container runs before any backend pod starts, guaranteeing schema is up to date before the app serves traffic. Downside: if migration fails, all backend pods stay in `Init:Error` and the rollout halts — which is the correct behaviour.

### 9.2 External Secrets Operator vs manual secrets

**Decision:** Use External Secrets Operator for production; manual `kubectl create secret` acceptable for initial staging setup.

**Why:** ESO keeps Secrets Manager as the source of truth and auto-rotates secrets without pod restarts. Manual secrets are simpler to start with and acceptable while the ESO setup is being configured.

### 9.3 Kustomize vs Helm

**Decision:** Kustomize for environment overlays.

**Why:** No templating needed — the manifests are straightforward and environment differences are limited to image tags, hostnames, and namespace names. Kustomize is built into `kubectl` with no extra tooling. Helm would add value if the chart needed to be shared or versioned independently.

---

## 10. Files to Create

| File | Purpose |
|------|---------|
| `k8s/base/kustomization.yaml` | Base resource list |
| `k8s/base/configmap.yaml` | Non-sensitive env vars |
| `k8s/base/backend-deployment.yaml` | Backend pods + init migration |
| `k8s/base/frontend-deployment.yaml` | Frontend pods |
| `k8s/base/services.yaml` | ClusterIP services |
| `k8s/base/ingress.yaml` | ALB ingress |
| `k8s/base/hpa.yaml` | Autoscalers |
| `k8s/overlays/staging/kustomization.yaml` | Staging overrides |
| `k8s/overlays/production/kustomization.yaml` | Production overrides |
| `k8s/external-secret.yaml` | ESO secret sync |
| `.github/workflows/hello-login-ci.yml` | Add deploy job |
