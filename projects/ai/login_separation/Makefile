.PHONY: test-containers test-backend test-frontend build-backend build-frontend build-hello test-backend-unit test-hello-unit compose-up compose-down test-e2e-docker \
        dev-init dev-ssh dev-deploy dev-logs dev-down dev-k8s-apply

# ── Dev EC2 environment ───────────────────────────────────────────────────────
# Reads the EC2 public IP from Terraform output. Override with: make dev-ssh DEV_IP=x.x.x.x
DEV_IP  ?= $(shell cd infra/dev && terraform output -raw public_ip 2>/dev/null)
DEV_KEY ?= ~/.ssh/hello-login-dev.pem
SSH     := ssh -i $(DEV_KEY) -o StrictHostKeyChecking=no
SCP     := scp -i $(DEV_KEY) -o StrictHostKeyChecking=no

dev-init:
	cd infra/dev && terraform init

dev-plan:
	cd infra/dev && terraform plan -var-file=dev.tfvars

dev-ssh:
	@test -n "$(DEV_IP)" || (echo "DEV_IP not set — run: terraform -chdir=infra/dev apply -var-file=dev.tfvars" && exit 1)
	$(SSH) ec2-user@$(DEV_IP)

dev-deploy:
	@test -n "$(DEV_IP)" || (echo "DEV_IP not set — run: terraform -chdir=infra/dev apply -var-file=dev.tfvars" && exit 1)
	@test -f .env.ec2 || (echo "Missing .env.ec2 — copy from .env.ec2.example and fill in values" && exit 1)
	$(SCP) docker-compose.ec2.yml ec2-user@$(DEV_IP):/opt/hello-login/docker-compose.yml
	$(SCP) Caddyfile ec2-user@$(DEV_IP):/opt/hello-login/Caddyfile
	$(SCP) .env.ec2 ec2-user@$(DEV_IP):/opt/hello-login/.env
	$(SSH) ec2-user@$(DEV_IP) 'cd /opt/hello-login && \
	  aws ecr get-login-password --region us-west-2 | \
	  docker login --username AWS --password-stdin 277070500859.dkr.ecr.us-west-2.amazonaws.com && \
	  docker compose pull && docker compose up -d'

dev-logs:
	@test -n "$(DEV_IP)" || (echo "DEV_IP not set" && exit 1)
	$(SSH) ec2-user@$(DEV_IP) 'cd /opt/hello-login && docker compose logs -f'

dev-down:
	@test -n "$(DEV_IP)" || (echo "DEV_IP not set" && exit 1)
	$(SSH) ec2-user@$(DEV_IP) 'cd /opt/hello-login && docker compose down'

dev-k8s-apply:
	@test -f k8s/overlays/dev/.env.dev || \
	  (echo "Missing k8s/overlays/dev/.env.dev — copy from .env.dev.example and fill in values" && exit 1)
	kubectl apply -k k8s/overlays/dev/

test-containers: test-backend test-frontend

build-backend:
	docker build -t hello-login-login ./login-service

build-backend-test:
	docker build --target test -t hello-login-login-test ./login-service

build-hello:
	docker build -t hello-login-hello ./hello-service

build-hello-test:
	docker build --target test -t hello-login-hello-test ./hello-service

build-frontend:
	docker build -t hello-login-frontend ./frontend

test-backend: build-backend
	container-structure-test test --image hello-login-login --config tests/container/backend.yaml

test-backend-unit: build-backend-test
	docker run --rm \
		-e JWT_SECRET=test-secret \
		-e TESTING=true \
		-v $(CURDIR)/tests:/app/tests \
		-v $(CURDIR)/pytest.ini:/app/pytest.ini \
		hello-login-login-test \
		sh -c "PYTHONPATH=/app pytest tests/unit tests/integration --tb=short -q"

test-hello-unit: build-hello-test
	docker run --rm \
		-e JWT_SECRET=test-secret \
		-e TESTING=true \
		hello-login-hello-test \
		pytest tests/unit tests/integration --tb=short -q

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
