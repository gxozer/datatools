.PHONY: test-containers test-backend test-frontend

test-containers: test-backend test-frontend

test-backend:
	container-structure-test test --image hello-login-backend --config tests/container/backend.yaml

test-frontend:
	container-structure-test test --image hello-login-frontend --config tests/container/frontend.yaml
