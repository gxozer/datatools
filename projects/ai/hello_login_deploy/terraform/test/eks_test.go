package test

import (
	"fmt"
	"testing"

	"github.com/gruntwork-io/terratest/modules/terraform"
	"github.com/stretchr/testify/assert"
)

func testEKS(t *testing.T, opts *terraform.Options, environment string) {
	clusterName := terraform.Output(t, opts, "cluster_name")
	clusterEndpoint := terraform.Output(t, opts, "cluster_endpoint")

	// Cluster was created with the correct name and has a reachable API endpoint.
	assert.Equal(t, fmt.Sprintf("hello-login-%s", environment), clusterName,
		"cluster_name should follow the hello-login-<env> naming convention")

	assert.NotEmpty(t, clusterEndpoint, "cluster_endpoint should not be empty")
	assert.True(t, len(clusterEndpoint) > 8, "cluster_endpoint should be a real URL")
	assert.Contains(t, clusterEndpoint, "https://", "cluster API endpoint must use HTTPS")
	assert.Contains(t, clusterEndpoint, ".eks.amazonaws.com", "cluster_endpoint should be an EKS URL")
}
