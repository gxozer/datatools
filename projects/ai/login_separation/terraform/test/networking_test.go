package test

import (
	"testing"

	"github.com/gruntwork-io/terratest/modules/terraform"
	"github.com/stretchr/testify/assert"
)

func testNetworking(t *testing.T, opts *terraform.Options) {
	vpcID := terraform.Output(t, opts, "vpc_id")

	// VPC was created and has the correct AWS ID format.
	assert.NotEmpty(t, vpcID, "vpc_id output should not be empty")
	assert.True(t, len(vpcID) > 4, "vpc_id should be a real AWS ID")
	assert.Contains(t, vpcID, "vpc-", "vpc_id should start with vpc-")
}
