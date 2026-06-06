# modules/helm-addons/main.tf
#
# Installs two Helm charts into the EKS (Elastic Kubernetes Service) cluster
# using the Terraform helm provider.
# Helm is a package manager for Kubernetes — a "chart" is a packaged application.
#
# Why manage Helm charts in Terraform rather than running helm install manually?
#   Running helm install manually means the installation is not tracked anywhere.
#   Managing Helm releases here means they are part of the same apply/destroy
#   lifecycle as the rest of the infrastructure — no separate installation step.
#
# This module has a depends_on = [module.eks, module.iam] in root main.tf
# because it installs software INTO the cluster and uses IAM role ARNs FROM
# the iam module. Terraform cannot infer these dependencies from references alone.

# ── AWS Load Balancer Controller ─────────────────────────────────────────────
#
# The ALB (Application Load Balancer) controller watches for Kubernetes Ingress objects
# and automatically creates AWS ALBs to route external HTTP/HTTPS traffic into the cluster.
# When you apply k8s/base/ingress.yaml, the controller reads it and creates
# an ALB in AWS with the correct listener rules and target groups.
resource "helm_release" "alb_controller" {
  name       = "aws-load-balancer-controller"
  repository = "https://aws.github.io/eks-charts" # official AWS Helm chart repo
  chart      = "aws-load-balancer-controller"
  namespace  = "kube-system" # installed alongside other cluster-level components
  version    = "1.7.2"       # pin the version to avoid unexpected upgrades

  # The cluster name — the controller needs this to tag the ALBs it creates
  # so it knows which cluster owns them.
  set {
    name  = "clusterName"
    value = var.cluster_name
  }

  # Tell the chart to create a Kubernetes ServiceAccount for the controller.
  set {
    name  = "serviceAccount.create"
    value = "true"
  }

  # The name of the ServiceAccount to create — must match the name used in
  # the IRSA trust policy in modules/iam/main.tf.
  set {
    name  = "serviceAccount.name"
    value = "aws-load-balancer-controller"
  }

  # Annotate the ServiceAccount with the IRSA role ARN.
  # This annotation is how Kubernetes tells AWS: "when a pod using this
  # service account makes AWS API calls, assume this IAM role."
  # The backslash escaping is needed because the annotation key contains dots,
  # which Helm interprets as nested object separators without escaping.
  set {
    name  = "serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
    value = var.alb_controller_role_arn
  }

  # The AWS region where the controller will create load balancers.
  set {
    name  = "region"
    value = var.aws_region
  }

  # The VPC ID — the controller needs this to place load balancers in the
  # correct VPC and to find the tagged subnets.
  set {
    name  = "vpcId"
    value = var.vpc_id
  }
}

# ── External Secrets Operator ─────────────────────────────────────────────────
#
# The ESO (External Secrets Operator — a Kubernetes controller) watches for
# ExternalSecret custom resource objects. When it finds one, it reads the
# referenced secret from AWS Secrets Manager and creates or updates a
# Kubernetes Secret with the values so pods can consume them as environment variables.
#
# The ExternalSecret objects are defined in k8s/overlays/<env>/external-secret.yaml.
# They reference the "hello-login/<env>" secret path that module.secrets creates.
# Wait 60 seconds after the ALB controller is marked Ready before installing ESO.
#
# Why this is needed:
#   The ALB controller installs a mutating webhook (aws-load-balancer-webhook-service).
#   Terraform marks helm_release.alb_controller as complete when the pods reach the
#   Ready state, but the webhook endpoint takes a few extra seconds to register in
#   the Kubernetes API. If ESO installation starts immediately, any Service resource
#   it creates will hit the webhook and get "no endpoints available" error.
#
# 60 seconds is conservative — in practice the webhook is ready within 10-20s,
# but the extra margin avoids flaky applies on slower clusters.
resource "time_sleep" "alb_controller_ready" {
  depends_on      = [helm_release.alb_controller]
  create_duration = "60s"
}

resource "helm_release" "external_secrets" {
  name       = "external-secrets"
  repository = "https://charts.external-secrets.io" # official ESO Helm chart repo
  chart      = "external-secrets"
  namespace  = "external-secrets"
  version    = "0.9.13" # pin the version

  # Create the "external-secrets" namespace if it does not already exist.
  # Without this flag, the release would fail if the namespace is missing.
  create_namespace = true

  # Annotate the ESO ServiceAccount with the IRSA role ARN.
  # This allows ESO pods to read from Secrets Manager using the scoped
  # IAM role created in modules/iam/main.tf.
  set {
    name  = "serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
    value = var.eso_role_arn
  }

  # Wait for the ALB controller webhook to be ready before installing ESO.
  # See time_sleep.alb_controller_ready above for explanation.
  depends_on = [time_sleep.alb_controller_ready]
}
