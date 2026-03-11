# Testing Instructions

This project has four levels of testing: backend unit tests, backend integration tests, frontend unit tests, and end-to-end (E2E) tests.

## Prerequisites

- Python 3.11+
- Node.js 18+
- Virtual environment set up (see `backend/.venv/`)

---

## Backend Unit Tests

Tests the Flask `/api/hello` endpoint using Flask's test client (no real HTTP server).

```bash
cd backend
source .venv/bin/activate
pytest tests/test_app.py -v
```

**Expected output:** 4 tests passing.

---

## Backend Integration Tests

Tests the Flask API over a real HTTP connection using a live server spun up by `pytest-flask`.

```bash
cd backend
source .venv/bin/activate
pytest tests/test_integration.py -v
```

**Expected output:** 5 tests passing.

### Run all backend tests at once

```bash
cd backend
source .venv/bin/activate
pytest -v
```

**Expected output:** 9 tests passing.

---

## Frontend Unit Tests

Tests the React `App` component and `HelloService` class using Jest and React Testing Library. The `fetch` API is mocked — no running server needed.

```bash
cd frontend
npm test
```

**Expected output:** 6 tests passing.

---

## End-to-End (E2E) Tests

Tests the full stack in a real browser using Playwright. **Both servers must be running.**

### 1. Start the Flask backend

```bash
cd backend
source .venv/bin/activate
python app.py
```

### 2. Start the React frontend (in a new terminal)

```bash
cd frontend
npm run dev
```

### 3. Run Playwright tests (in a new terminal)

```bash
cd frontend
npx playwright test
```

**Expected output:** Browser opens, "Hello, World!" message is visible on the page.

---

## Running All Tests

```bash
# Backend (unit + integration)
cd backend && source .venv/bin/activate && pytest -v

# Frontend unit tests
cd ../frontend && npm test

# E2E (requires both servers running — see above)
npx playwright test
```

---

## Interpreting Results

| Symbol | Meaning |
|--------|---------|
| `PASSED` / `✓` | Test passed |
| `FAILED` / `✗` | Test failed — check the error output |
| `ERROR` | Test setup failed (e.g. missing fixture or import error) |

If backend tests fail with `No module named 'pytest'`, make sure you have activated the virtual environment:
```bash
source backend/.venv/bin/activate
```

If frontend tests fail with type errors, ensure all dependencies are installed:
```bash
cd frontend && npm install
```
