# Testing

This project has three test suites: backend unit tests, backend integration tests, and end-to-end UI tests.

---

## Prerequisites

- Python 3.11+
- Node.js 18+
- Virtual environment set up (see [README.md](README.md))
- Database initialised: `cd hello_login/backend && .venv/bin/python -m alembic upgrade head`

---

## Backend Unit Tests

Tests controller methods directly without going through the HTTP stack.

```bash
cd hello_login
backend/.venv/bin/python -m pytest tests/unit/ -v
```

Expected output: **~60 passed**

---

## Backend Integration Tests

Tests the full Flask request/response stack using Flask's test client. No live server required.

```bash
cd hello_login
backend/.venv/bin/python -m pytest tests/integration/ -v
```

Expected output: **~76 passed**

---

## Run All Backend Tests

```bash
cd hello_login
backend/.venv/bin/python -m pytest tests/unit/ tests/integration/ -v
```

Expected output: **~136 passed**

### With coverage report

```bash
cd hello_login
backend/.venv/bin/python -m pytest tests/unit/ tests/integration/ --cov=app --cov-report=term-missing
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
cd hello_login
backend/.venv/bin/playwright install chromium
```

Ensure the database is initialised:

```bash
cd hello_login/backend
.venv/bin/python -m alembic upgrade head
```

### Run E2E tests

```bash
cd hello_login
backend/.venv/bin/python -m pytest tests/e2e/ -v
```

> **Note:** E2E tests start Flask on port **5002** and Vite on port **5175**. Make sure those ports are free before running.

Expected output: **6 passed**

---

## Run All Tests

```bash
# Backend (unit + integration + E2E)
cd hello_login
backend/.venv/bin/python -m pytest -v

# Frontend
cd hello_login/frontend && npm test
```
