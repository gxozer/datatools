package test

import (
	"testing"

	"github.com/gruntwork-io/terratest/modules/terraform"
	"github.com/stretchr/testify/assert"
)

func testRDS(t *testing.T, opts *terraform.Options) {
	dbEndpoint := terraform.Output(t, opts, "db_endpoint")

	// RDS instance was created and has a valid endpoint.
	// Format: hello-login-<env>.<hash>.<region>.rds.amazonaws.com:3306
	assert.NotEmpty(t, dbEndpoint, "db_endpoint output should not be empty")
	assert.Contains(t, dbEndpoint, "rds.amazonaws.com", "db_endpoint should be an RDS hostname")
	assert.Contains(t, dbEndpoint, ":3306", "db_endpoint should include the MySQL port")
}
