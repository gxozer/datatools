.PHONY: test-containers test-backend test-frontend build-backend build-frontend test-backend-unit compose-up compose-down test-e2e-docker

test-containers: test-backend test-frontend

build-backend:
	docker build -t hello-login-backend ./backend

build-frontend:
	docker build -t hello-login-frontend ./frontend

test-backend: build-backend
	container-structure-test test --image hello-login-backend --config tests/container/backend.yaml

test-backend-unit: build-backend
	docker run --rm \
		-e JWT_SECRET=test-secret \
		-e TESTING=true \
		-v $(CURDIR)/tests:/app/tests \
		-v $(CURDIR)/pytest.ini:/app/pytest.ini \
		hello-login-backend \
		sh -c "pip install -q pytest pytest-cov && PYTHONPATH=/app pytest tests/unit tests/integration --tb=short -q"

test-frontend: build-frontend
	container-structure-test test --image hello-login-frontend --config tests/container/frontend.yaml

compose-up:
	docker compose up -d

compose-down:
	docker compose down

test-e2e-docker:
	docker compose down -v
	docker compose up -d
	until curl -sf http://localhost:3000/api/health; do sleep 1; done
	E2E_BASE_URL=http://localhost:3000 pytest tests/e2e/ -v
