# Container Tests

Specs for validating Docker images using [container-structure-test](https://github.com/GoogleContainerTools/container-structure-test).

## Prerequisites

```bash
brew install container-structure-test
```

## Running tests

```bash
# From project root
make test-containers

# Or individually
container-structure-test test --image hello-login-backend --config tests/container/backend.yaml
container-structure-test test --image hello-login-frontend --config tests/container/frontend.yaml
```

## Files

| File | Purpose |
|------|---------|
| `backend.yaml` | Specs for the Flask backend image (PR-14) |
| `frontend.yaml` | Specs for the React/Vite frontend image (PR-15) |
| `verify_setup.sh` | Verifies this infrastructure is correctly set up (PR-18) |

## TDD workflow

1. Specs in `backend.yaml` / `frontend.yaml` are written **before** the Dockerfiles exist (red)
2. Dockerfiles are implemented until all specs pass (green)
3. `make test-containers` is run in CI after every `docker build`
