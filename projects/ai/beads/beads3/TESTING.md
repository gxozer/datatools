# Testing

This project has three test suites: backend unit tests, backend integration tests, and end-to-end UI tests.

---

## Prerequisites

- Python 3.11+
- Node.js 18+
- Virtual environment set up (see [README.md](README.md))

---

## Backend Unit Tests

Tests controller methods directly without going through the HTTP stack.

```bash
cd projects/ai/beads/beads3
backend/.venv/bin/python -m pytest tests/unit/ -v
```

Expected output: **5 passed**

---

## Backend Integration Tests

Tests the full Flask request/response stack using Flask's test client. No live server required.

```bash
cd projects/ai/beads/beads3
backend/.venv/bin/python -m pytest tests/integration/ -v
```

Expected output: **8 passed**

---

## Run All Backend Tests

```bash
cd projects/ai/beads/beads3
backend/.venv/bin/python -m pytest tests/unit/ tests/integration/ -v
```

Expected output: **13 passed**

### With coverage report

```bash
backend/.venv/bin/python -m pytest tests/unit/ tests/integration/ --cov=app --cov-report=term-missing
```

---

## Frontend Unit Tests

Tests React components and the ApiClient class using Vitest and React Testing Library.

```bash
cd projects/ai/beads/beads3/frontend
npm test
```

Expected output: **5 passed** (2 test files)

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
cd projects/ai/beads/beads3/backend
.venv/bin/playwright install chromium
```

### Run E2E tests

```bash
cd projects/ai/beads/beads3
backend/.venv/bin/python -m pytest tests/e2e/ -v
```

> **Note:** E2E tests start Flask on port 5001 and Vite on port 5174. Make sure those ports are free before running.

Expected output: **3 passed**

---

## Run All Tests

```bash
# Backend (unit + integration + E2E)
cd projects/ai/beads/beads3
backend/.venv/bin/python -m pytest -v

# Frontend
cd frontend && npm test
```
