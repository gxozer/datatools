# Outputs exposed to the root main.tf.
# These values are available after the cluster is created and are consumed
# by the iam module (for IRSA trust policies) and the helm-addons module
# (to connect to the cluster API).

# The cluster name — used by:
#   - module.iam    to name IAM roles and scope trust policies
#   - module.helm_addons  to tell the ALB controller which cluster it manages
#   - aws eks update-kubeconfig --name <cluster_name> (to configure kubectl)
output "cluster_name" {
  value = aws_eks_cluster.this.name
}

# The Kubernetes API server URL — used by the helm and kubernetes providers
# in versions.tf to connect to the cluster.
# Format: https://<hash>.gr7.<region>.eks.amazonaws.com
output "cluster_endpoint" {
  value = aws_eks_cluster.this.endpoint
}

# The cluster's TLS certificate (base64-encoded). Used by the helm and
# kubernetes providers to verify they are talking to the correct cluster
# and not an impostor. base64decode() is applied in versions.tf before use.
output "cluster_ca" {
  value = aws_eks_cluster.this.certificate_authority[0].data
}

# The ARN of the OIDC (OpenID Connect — a federated identity standard) identity provider.
# Used in IAM role trust policies to identify which OIDC issuer is trusted for IRSA.
# Format: arn:aws:iam::<account>:oidc-provider/oidc.eks.<region>.amazonaws.com/id/<hash>
output "oidc_provider_arn" {
  value = aws_iam_openid_connect_provider.this.arn
}

# The URL of the OIDC issuer — used to build the condition key in IAM trust
# policies, e.g. "<oidc_host>:sub" = "system:serviceaccount:kube-system:..."
output "oidc_provider_url" {
  value = aws_iam_openid_connect_provider.this.url
}

# The ARN of the node IAM role — available for future use if other resources
# (e.g. additional IAM policy attachments) need to reference it.
output "node_role_arn" {
  value = aws_iam_role.node.arn
}

# The security group EKS automatically creates and attaches to all managed node group
# instances. This is NOT the same as the custom eks_nodes SG defined in the networking
# module — EKS creates its own cluster SG which is the actual source of pod traffic
# to RDS and other resources inside the VPC.
output "cluster_security_group_id" {
  value = aws_eks_cluster.this.vpc_config[0].cluster_security_group_id
}
