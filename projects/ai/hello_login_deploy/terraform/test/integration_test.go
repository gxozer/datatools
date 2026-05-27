// integration_test.go
//
// Terratest integration tests for the hello-login Terraform stack.
//
// These tests provision REAL AWS infrastructure, run assertions, then destroy
// everything. Each full run costs ~$5-10 (EKS is the main cost driver) and
// takes ~30 minutes. Run deliberately, not on every PR.
//
// Usage:
//   cd terraform/test
//   go test -v -timeout 60m -run TestTerraformIntegration
//
// Prerequisites:
//   - AWS credentials configured (env vars or ~/.aws/credentials)
//   - Credentials must have permissions to create EKS, RDS, ECR, VPC, IAM resources
//   - Go 1.21+ installed
//   - Run `go mod tidy` once to download dependencies
//
// Constraint — GitHub OIDC provider:
//   The IAM module creates an account-wide GitHub OIDC provider
//   (token.actions.githubusercontent.com). Only one can exist per AWS account.
//   If the staging environment is currently running, destroy it first:
//     terraform destroy -var-file=staging.tfvars
//   Otherwise this test will fail on the OIDC provider creation step.

package test

import (
	"fmt"
	"strings"
	"testing"

	"github.com/gruntwork-io/terratest/modules/random"
	"github.com/gruntwork-io/terratest/modules/terraform"
	"github.com/stretchr/testify/require"
)

const awsRegion = "us-west-2"

// TestTerraformIntegration provisions the full hello-login stack in a
// randomly-named workspace, runs all module sub-tests, then destroys it.
func TestTerraformIntegration(t *testing.T) {
	t.Parallel()

	// Unique 6-char suffix keeps resource names collision-free across concurrent runs.
	uniqueID := strings.ToLower(random.UniqueId())
	environment := fmt.Sprintf("tt%s", uniqueID)

	opts := terraform.WithDefaultRetryableErrors(t, &terraform.Options{
		TerraformDir: "../",
		Vars: map[string]interface{}{
			"environment":         environment,
			"aws_region":          awsRegion,
			"vpc_cidr":            "10.99.0.0/16", // avoids overlap with staging (10.0) and production (10.1)
			"eks_node_type":       "t3.small",
			"eks_min_nodes":       2,
			"eks_max_nodes":       6,
			"rds_instance_class":  "db.t3.micro",
			"rds_prevent_destroy": false,
			"github_org":          "gxozer",
			"github_repo":         "hello_login_deploy",
		},
		NoColor: true,
	})

	// Init and select an isolated workspace so this run's state is separate
	// from staging and production.
	terraform.Init(t, opts)
	terraform.WorkspaceSelectOrNew(t, opts, environment)
	defer func() {
		terraform.Destroy(t, opts)
		terraform.WorkspaceSelectOrNew(t, opts, "default")
		terraform.WorkspaceDelete(t, opts, environment)
	}()

	_, err := terraform.ApplyE(t, opts)
	require.NoError(t, err, "terraform apply failed")

	t.Run("Networking", func(t *testing.T) { testNetworking(t, opts) })
	t.Run("ECR", func(t *testing.T) { testECR(t, opts) })
	t.Run("RDS", func(t *testing.T) { testRDS(t, opts) })
	t.Run("EKS", func(t *testing.T) { testEKS(t, opts, environment) })
	t.Run("Secrets", func(t *testing.T) { testSecrets(t, opts, environment) })
}
