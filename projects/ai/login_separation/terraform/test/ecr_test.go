package test

import (
	"testing"

	"github.com/gruntwork-io/terratest/modules/terraform"
	"github.com/stretchr/testify/assert"
)

func testECR(t *testing.T, opts *terraform.Options) {
	backendURL := terraform.Output(t, opts, "backend_repo_url")
	frontendURL := terraform.Output(t, opts, "frontend_repo_url")

	// Both repos were created and have a valid ECR URL format:
	// <account>.dkr.ecr.<region>.amazonaws.com/<repo-name>
	for _, url := range []string{backendURL, frontendURL} {
		assert.NotEmpty(t, url)
		assert.Contains(t, url, ".dkr.ecr.")
		assert.Contains(t, url, ".amazonaws.com")
	}

	assert.Contains(t, backendURL, "hello-login-backend", "backend repo URL should contain repo name")
	assert.Contains(t, frontendURL, "hello-login-frontend", "frontend repo URL should contain repo name")
}
