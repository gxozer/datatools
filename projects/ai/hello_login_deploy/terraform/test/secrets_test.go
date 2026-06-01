package test

import (
	"fmt"
	"testing"

	"github.com/gruntwork-io/terratest/modules/terraform"
	"github.com/stretchr/testify/assert"
)

func testSecrets(t *testing.T, opts *terraform.Options, environment string) {
	secretARN := terraform.Output(t, opts, "secret_arn")

	// Secrets Manager secret was created at the correct path.
	// ARN format: arn:aws:secretsmanager:<region>:<account>:secret:hello-login/<env>-<suffix>
	assert.NotEmpty(t, secretARN, "secret_arn output should not be empty")
	assert.Contains(t, secretARN, "arn:aws:secretsmanager:", "secret_arn should be a valid ARN")
	assert.Contains(t, secretARN, fmt.Sprintf("hello-login/%s", environment),
		"secret ARN should contain the correct path hello-login/<env>")
}
