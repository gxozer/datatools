# Hello Login Web App

A full-stack web application with JWT authentication. Users log in to receive a personalised greeting fetched from a protected REST API.

**Stack:** Python + Flask (backend) · TypeScript + React (frontend)

---

## Architecture

```
Browser (React/Vite)
        │
        │  POST /api/login  →  JWT token
        │
        │  GET /api/hello
        │  Authorization: Bearer <token>
        ▼
Flask Backend
        │
        │  Auth.require_auth validates JWT, checks role
        │
        │  JSON: { "message": "Hello, Alice!", "status": "ok" }
        ▼
HelloController → Response
```

- The React frontend calls `POST /api/login` to obtain a JWT, stored in `localStorage`
- `GET /api/hello` requires a valid Bearer token with role `user` or `admin`
- Flask serves the personalised greeting from `HelloController`
- In development, Vite proxies `/api/*` requests to `localhost:5001`

---

## Prerequisites

**To run with Docker (recommended):**
- Docker Desktop 4.x+

**To run locally:**
- Python 3.11+
- Node.js 18+
- npm 9+

---

## Running with Docker

The fastest way to get the full stack running is with Docker Compose.

### 1. Clone the repository

```bash
git clone https://github.com/gxozer/datatools.git
cd datatools/projects/ai/hello_login_deploy
```

### 2. Start the stack

```bash
docker compose up -d
```

This will:
- Pull/build the Flask backend image (runs Alembic migrations on startup)
- Pull/build the React/nginx frontend image
- Start both services and wire them together

Open [http://localhost:3000](http://localhost:3000) in your browser.

### 3. Stop the stack

```bash
docker compose down       # keep the database volume
docker compose down -v    # also delete the database
```

### Environment variables (optional)

To use a custom JWT secret or enable real email sending, copy the example file and fill in your values:

```bash
cp backend/.env.example backend/.env
```

Then edit `backend/.env`:

```bash
JWT_SECRET=your-32-char-secret-here
MAIL_SERVER=smtp-relay.brevo.com
MAIL_PORT=587
MAIL_USE_TLS=true
MAIL_USERNAME=your-brevo-email@example.com
MAIL_PASSWORD=your-brevo-smtp-key
MAIL_DEFAULT_SENDER=your-brevo-email@example.com
```

By default `JWT_SECRET` uses a built-in development value and email sending is suppressed. The `backend/.env` file is optional — the stack starts without it.

---

## Setup (local development)

### 1. Clone the repository

```bash
git clone https://github.com/gxozer/datatools.git
cd datatools/projects/ai/beads/hello_login
```

### 2. Backend

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate       # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

Copy the example environment file and fill in your values:

```bash
cp .env.example .env
```

**Email (Brevo):** Password reset emails are sent via [Brevo](https://brevo.com) SMTP. Sign up for a free account (300 emails/day, no expiry), then:

1. Go to **Settings → SMTP & API → SMTP** and generate an SMTP key
2. Go to **Senders, Domains & Dedicated IPs** and verify your sender address
3. Fill in the `MAIL_*` variables in `.env`:

```
MAIL_SERVER=smtp-relay.brevo.com
MAIL_PORT=587
MAIL_USE_TLS=true
MAIL_USERNAME=your-brevo-login-email@example.com
MAIL_PASSWORD=your-brevo-smtp-key
MAIL_DEFAULT_SENDER=your-brevo-login-email@example.com
```

To skip email sending in development, set `MAIL_SUPPRESS_SEND=1` in `.env` instead.

Initialise the database:

```bash
alembic upgrade head
```

### 3. Frontend

```bash
cd ../frontend
npm install
```

---

## Running Locally

Open two terminals from the `hello_login/` directory:

**Terminal 1 — Backend:**

```bash
cd backend
source .venv/bin/activate
python run.py
# Flask running on http://localhost:5001
```

**Terminal 2 — Frontend:**

```bash
cd frontend
npm run dev
# Vite running on http://localhost:5173
```

Open [http://localhost:5173](http://localhost:5173) in your browser. The app will prompt you to log in. Once authenticated you will see a personalised greeting, e.g. **"Hello, Alice!"**.

---

## API Reference

| Method | Endpoint | Auth | Response |
|--------|----------|------|----------|
| POST | `/api/login` | None | `{"token": "<jwt>", "status": "ok"}` |
| GET | `/api/hello` | Bearer JWT (role: `user` or `admin`) | `{"message": "Hello, {name}!", "status": "ok"}` |
| GET | `/api/health` | None | `{"status": "ok"}` |

---

## Testing

See [TESTING.md](TESTING.md) for full instructions. Quick start:

```bash
# Backend tests (unit + integration)
backend/.venv/bin/python -m pytest tests/unit/ tests/integration/ -v

# Frontend tests (also run inside docker build automatically)
cd frontend && npm test

# Container structure tests (requires images to be built first)
make test-containers

# E2E tests against the containerized stack
make test-e2e-docker
```

### Container test infrastructure

Container specs live in `tests/container/` and use [container-structure-test](https://github.com/GoogleContainerTools/container-structure-test):

```bash
brew install container-structure-test   # one-time install
make test-backend                        # backend image specs
make test-frontend                       # frontend image specs
```

To verify the container test infrastructure is set up correctly:

```bash
bash tests/container/verify_setup.sh
```

---

## Debugging

### Backend — PyCharm

1. Open `backend/` in PyCharm and set the interpreter to `backend/.venv`
2. Open `run.py`
3. Click in the gutter next to any line to set a breakpoint
4. Click **🐛 Debug** (or press Shift+F9) to start the server in debug mode
5. Trigger the endpoint from the browser — PyCharm pauses at your breakpoint with the full debugger UI (variables, call stack, step controls)

### Backend — Docker (remote-pdb)

`remote-pdb` is a step-through debugger that runs inside the container and accepts connections over TCP on port 4444.

**1. Add a breakpoint in code**

```python
import remote_pdb; remote_pdb.set_trace()
```

**2. Start the stack with the debug overlay**

```bash
docker compose -f docker-compose.yml -f docker-compose.override.yml up -d --build
```

**3. Trigger the request** (e.g. submit the signup form). The process will freeze waiting for a connection.

**4. Connect from a terminal**

```bash
nc localhost 4444
```

You land in a pdb prompt with the full call stack:

```
> /app/app/auth_controllers.py(45)signup()
-> data = request.get_json()
(Pdb)
```

**Useful commands**

| Command | Action |
|---------|--------|
| `n` | Next line (stay in current function) |
| `s` | Step into function call |
| `r` | Run until current function returns |
| `c` | Continue to next breakpoint |
| `p expr` | Print expression — `p data`, `p request.method` |
| `pp expr` | Pretty-print (dicts, lists) |
| `l` | Show surrounding source |
| `ll` | Show full current function |
| `w` | Call stack |
| `u` / `d` | Move up/down the call stack |
| `b 72` | Set breakpoint at line 72 |
| `q` | Quit (request fails with 500, server keeps running) |

> Port 4444 is only exposed when using the override file. Remove `set_trace()` calls before committing — they will block requests indefinitely.

### Backend — terminal (pdb)

Add `breakpoint()` anywhere in the code, then run the server normally:

```bash
python run.py
```

When execution reaches that line the terminal drops into a `pdb` prompt:

```
(Pdb) n       # next line
(Pdb) s       # step into
(Pdb) p expr  # print expression
(Pdb) c       # continue
(Pdb) q       # quit
```

---

## Inspecting the Database

### Local development

```bash
sqlite3 backend/instance/app.db
```

### Docker

The database lives inside the Docker volume, not on your local filesystem. The slim image does not include the `sqlite3` CLI — use Python's built-in module instead:

```bash
docker exec -it hello_login_deploy-backend-1 python -c "
import sqlite3
conn = sqlite3.connect('/app/instance/app.db')
conn.row_factory = sqlite3.Row
rows = conn.execute('SELECT * FROM users').fetchall()
for r in rows: print(dict(r))
"
```

Replace `users` with `login_attempts` or `password_reset_tokens` to query other tables.

### Useful SQL commands

```sql
.tables                           -- list all tables
.mode column                      -- readable column layout
.headers on                       -- show column names
SELECT * FROM users;
SELECT * FROM login_attempts;
SELECT * FROM password_reset_tokens;
.quit
```

---

## Project Structure

```
hello_login_deploy/
├── backend/
│   ├── app/
│   │   ├── __init__.py           # Package entry point
│   │   ├── factory.py            # Flask app factory (create_app)
│   │   ├── auth.py               # Auth class: generate_token, require_auth
│   │   ├── auth_controllers.py   # LoginController, SignupController, etc.
│   │   ├── controllers.py        # HelloController, HealthController
│   │   ├── models.py             # User, LoginAttempt, PasswordResetToken
│   │   └── routes.py             # API Blueprint and URL rules
│   ├── Dockerfile            # Backend container image
│   ├── entrypoint.sh         # Runs migrations then starts Flask
│   ├── run.py                # Entry point
│   ├── requirements.txt      # Runtime dependencies
│   └── requirements-dev.txt  # Dev/test dependencies
├── frontend/
│   ├── src/
│   │   ├── api/
│   │   │   └── ApiClient.ts      # HTTP client class
│   │   ├── components/
│   │   │   └── HelloMessage.tsx  # Presentational component
│   │   ├── test/                 # Vitest unit tests
│   │   └── App.tsx               # Root component
│   ├── Dockerfile            # Multi-stage: build (Vitest) + nginx serve
│   ├── nginx.conf.template   # nginx config with /api proxy + SPA routing
│   └── vite.config.ts        # Vite config with /api proxy (dev only)
├── tests/
│   ├── conftest.py           # Shared pytest fixtures
│   ├── unit/                 # Controller unit tests
│   ├── integration/          # API integration tests
│   ├── e2e/                  # Playwright end-to-end tests
│   └── container/            # container-structure-test specs
│       ├── backend.yaml
│       ├── frontend.yaml
│       └── verify_setup.sh
├── docker-compose.yml        # Wires backend + frontend for one-command startup
├── docker-compose.override.yml  # Debug overlay: exposes remote-pdb on port 4444
├── Makefile                  # Build, test, and compose targets
├── pytest.ini                # Pytest configuration
├── TESTING.md                # Full testing instructions
└── README.md                 # This file
```
