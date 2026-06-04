# Testing

This project has four test suites: login-service unit tests, login-service integration tests, hello-service tests, and end-to-end UI tests.

---

## Prerequisites

- Python 3.11+
- Node.js 18+
- Virtual environment set up (see [README.md](README.md))
- Database initialised: `alembic -c login-service/alembic.ini upgrade head`

---

## login-service Unit Tests

Tests controller methods directly without going through the HTTP stack.

```bash
.venv/bin/pytest tests/unit/ -v
```

Expected output: **110 passed**

---

## login-service Integration Tests

Tests the full Flask request/response stack using Flask's test client. No live server required.

```bash
.venv/bin/pytest tests/integration/ -v
```

Expected output: **47 passed**

---

## Run All login-service Tests

```bash
.venv/bin/pytest tests/unit/ tests/integration/ -v
```

Expected output: **157 passed**

### With coverage report

```bash
.venv/bin/pytest tests/unit/ tests/integration/ --cov=app --cov-report=term-missing
```

---

## hello-service Tests

hello-service has its own test suite (no database required).

```bash
.venv/bin/pytest hello-service/tests/ -v
```

Expected output: **22 passed**

### With coverage

```bash
.venv/bin/pytest hello-service/tests/ --cov=app --cov-report=term-missing
```

---

## Frontend Unit Tests

Tests React components and the ApiClient class using Vitest and React Testing Library.

```bash
cd hello_login/frontend
npm test
```

Expected output: **73 passed**

### With coverage

```bash
npm run test:coverage
```

### Watch mode (during development)

```bash
npm run test:watch
```

---

## End-to-End UI Tests

Starts the Flask backend and Vite dev server, then drives a real Chromium browser via Playwright.

### Prerequisites

Install the Playwright browser (one-time):

```bash

backend/.venv/bin/playwright install chromium
```

Ensure the database is initialised:

```bash
cd hello_login/backend
.venv/bin/python -m alembic upgrade head
```

### Run E2E tests

```bash

.venv/bin/pytest tests/e2e/ -v
```

> **Note:** E2E tests start Flask on port **5002** and Vite on port **5175**. Make sure those ports are free before running.

Expected output: **6 passed**

---

## Run All Tests

```bash
# Backend (unit + integration + E2E)

.venv/bin/pytest -v

# Frontend
cd hello_login/frontend && npm test
```
