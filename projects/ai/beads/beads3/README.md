# Hello World Web App

A minimal full-stack web application that displays a "Hello, World!" message fetched from a REST API.

**Stack:** Python + Flask (backend) · TypeScript + React (frontend)

---

## Architecture

```
Browser (React/Vite)
        │
        │  GET /api/hello
        ▼
Flask Backend
        │
        │  JSON: { "message": "Hello, World!", "status": "ok" }
        ▼
HelloController → Response
```

- The React frontend fetches `GET /api/hello` on page load via `ApiClient`
- Flask serves the response from `HelloController`
- In development, Vite proxies `/api/*` requests to `localhost:5000`

---

## Prerequisites

- Python 3.11+
- Node.js 18+
- npm 9+

---

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/gxozer/datatools.git
cd datatools/projects/ai/beads/beads3
```

### 2. Backend

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate       # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

Copy the example environment file:

```bash
cp .env.example .env
```

### 3. Frontend

```bash
cd ../frontend
npm install
```

---

## Running Locally

Open two terminals from the `beads3/` directory:

**Terminal 1 — Backend:**

```bash
cd backend
source .venv/bin/activate
python run.py
# Flask running on http://localhost:5000
```

**Terminal 2 — Frontend:**

```bash
cd frontend
npm run dev
# Vite running on http://localhost:5173
```

Open [http://localhost:5173](http://localhost:5173) in your browser. You should see **"Hello, World!"**.

---

## API Reference

| Method | Endpoint | Response |
|--------|----------|----------|
| GET | `/api/hello` | `{"message": "Hello, World!", "status": "ok"}` |
| GET | `/api/health` | `{"status": "ok"}` |

---

## Testing

See [TESTING.md](TESTING.md) for full instructions. Quick start:

```bash
# Backend tests (unit + integration)
backend/.venv/bin/python -m pytest tests/unit/ tests/integration/ -v

# Frontend tests
cd frontend && npm test
```

---

## Project Structure

```
beads3/
├── backend/
│   ├── app/
│   │   ├── __init__.py       # Package entry point
│   │   ├── factory.py        # Flask app factory (create_app)
│   │   ├── controllers.py    # HelloController, HealthController
│   │   └── routes.py         # API Blueprint and URL rules
│   ├── run.py                # Entry point
│   ├── requirements.txt      # Runtime dependencies
│   └── requirements-dev.txt  # Dev/test dependencies
├── frontend/
│   ├── src/
│   │   ├── api/
│   │   │   └── ApiClient.ts  # HTTP client class
│   │   ├── components/
│   │   │   └── HelloMessage.tsx  # Presentational component
│   │   ├── test/             # Vitest unit tests
│   │   └── App.tsx           # Root component
│   └── vite.config.ts        # Vite config with /api proxy
├── tests/
│   ├── conftest.py           # Shared pytest fixtures
│   ├── unit/                 # Controller unit tests
│   ├── integration/          # API integration tests
│   └── e2e/                  # Playwright end-to-end tests
├── pytest.ini                # Pytest configuration
├── TESTING.md                # Full testing instructions
└── README.md                 # This file
```
