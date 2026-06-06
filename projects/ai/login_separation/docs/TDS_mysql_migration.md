# Technical Design Specification — MySQL Migration

**Version:** 1.0
**Date:** 2026-04-17
**Status:** Draft
**Jira:** PR-43 (subtask of PR-34)
**Related:** [PRD_mysql_migration.md](PRD_mysql_migration.md), [TDS.md](TDS.md)

---

## 1. Overview

This document describes the technical changes required to replace SQLite with MySQL as the application database. The changes span five areas: Python dependencies, Alembic configuration, docker-compose, `entrypoint.sh`, and `.env.example`. No ORM models, queries, or application logic need to change.

---

## 2. Changes by File

### 2.1 `backend/requirements.txt`

Add `pymysql` as the MySQL driver for SQLAlchemy:

```
pymysql==1.1.1
```

SQLAlchemy uses pymysql via the `mysql+pymysql://` URL scheme. No other driver (e.g. `mysqlclient`, `aiomysql`) is needed.

---

### 2.2 `backend/alembic.ini`

The current file has a hardcoded `sqlalchemy.url` that is misleading:

```ini
sqlalchemy.url = sqlite:///app.db   # current — wrong
```

Replace with a placeholder so it is clear that `migrations/env.py` overrides this at runtime via `DATABASE_URL`:

```ini
sqlalchemy.url =   # set via DATABASE_URL env var; see migrations/env.py
```

No functional change — `migrations/env.py` already calls `config.set_main_option("sqlalchemy.url", ...)` before any migration runs.

---

### 2.3 `backend/migrations/env.py`

**No changes required.** The file already reads `DATABASE_URL` from the environment and uses it as the Alembic connection URL:

```python
database_url = os.environ.get("DATABASE_URL", "sqlite:///instance/app.db")
config.set_main_option("sqlalchemy.url", database_url)
```

When `DATABASE_URL=mysql+pymysql://...` is set, Alembic will connect to MySQL automatically.

The SQLite default is retained so that developers without a running MySQL instance can still run migrations locally.

---

### 2.4 `backend/entrypoint.sh`

Add a wait loop before `alembic upgrade head` to handle the race condition where the backend container starts before MySQL is ready to accept connections.

**Current:**
```sh
#!/bin/sh
set -e
python -m alembic upgrade head
exec python run.py
```

**Updated:**
```sh
#!/bin/sh
set -e

# Wait for MySQL to be ready (no-op if DATABASE_URL is SQLite)
if echo "${DATABASE_URL:-}" | grep -q "mysql"; then
  echo "Waiting for MySQL..."
  until python -c "
import os, sys, time
url = os.environ['DATABASE_URL']
# Extract host from mysql+pymysql://user:pass@host:port/db
host = url.split('@')[1].split(':')[0].split('/')[0]
import socket
try:
    socket.create_connection((host, 3306), timeout=1).close()
    sys.exit(0)
except OSError:
    sys.exit(1)
"; do
    sleep 1
  done
  echo "MySQL is ready."
fi

python -m alembic upgrade head
exec python run.py
```

The wait loop uses a Python one-liner (already available in the container) to probe TCP port 3306 — no additional tooling (`mysqladmin`, `wait-for-it.sh`) is required. It only runs when `DATABASE_URL` contains `mysql`, so local SQLite development is unaffected. The loop has no hard timeout; docker-compose's `depends_on` health check (see §2.5) acts as the primary gate and bounds the wait in practice.

---

### 2.5 `docker-compose.yml`

**Add a `mysql` service** and update the `backend` service.

```yaml
services:
  mysql:
    image: mysql:8
    environment:
      MYSQL_ROOT_PASSWORD: rootpassword
      MYSQL_DATABASE: hello_login
      MYSQL_USER: hello
      MYSQL_PASSWORD: hello
    volumes:
      - mysql_data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "hello", "-phello"]
      interval: 5s
      timeout: 3s
      retries: 10
      start_period: 20s

  backend:
    build: ./backend
    image: hello-login-backend
    depends_on:
      mysql:
        condition: service_healthy
    ports:
      - "5001:5001"
    env_file:
      - path: ./backend/.env
        required: false
    environment:
      - FLASK_DEBUG=0
      - MAIL_SUPPRESS_SEND=${MAIL_SUPPRESS_SEND:-1}
      - JWT_SECRET=${JWT_SECRET:-dev-only-secret-change-for-production-32c}
      - DATABASE_URL=${DATABASE_URL:-mysql+pymysql://hello:hello@mysql:3306/hello_login}

  frontend:
    build: ./frontend
    image: hello-login-frontend
    ports:
      - "3000:80"
    environment:
      - BACKEND_HOST=backend
    depends_on:
      - backend

volumes:
  db_data:       # kept for backwards compatibility; no longer used by backend
  mysql_data:
```

Key decisions:
- `depends_on: condition: service_healthy` ensures Compose waits for the MySQL health check before starting the backend. The `entrypoint.sh` wait loop is a secondary safety net.
- The default `DATABASE_URL` in `docker-compose.yml` uses the `mysql` service hostname — no `.env` file is needed for local dev.
- `MYSQL_ROOT_PASSWORD` is only used internally by the MySQL image during initialisation; application code connects as the `hello` user.
- The old `db_data` volume is retained in the `volumes:` block to avoid errors if any existing local state references it, but is no longer mounted by the backend.

---

### 2.6 `backend/.env.example`

Add `DATABASE_URL` with examples for local MySQL and AWS RDS:

```bash
# Database
# Local MySQL via docker-compose (default — no change needed for local dev):
# DATABASE_URL=mysql+pymysql://hello:hello@localhost:3306/hello_login
#
# AWS RDS for MySQL (production):
# DATABASE_URL=mysql+pymysql://app_user:STRONG_PASSWORD@your-rds-endpoint.rds.amazonaws.com:3306/hello_login
```

---

## 3. Schema Compatibility

The existing Alembic migration (`845373ac03c6_initial_schema.py`) uses only portable SQLAlchemy types:

| Column type | SQLite | MySQL 8 | Notes |
|---|---|---|---|
| `Integer` | `INTEGER` | `INT` | Compatible |
| `String(255)` | `VARCHAR(255)` | `VARCHAR(255)` | Compatible |
| `Boolean` | `INTEGER` (0/1) | `TINYINT(1)` | SQLAlchemy handles transparently |
| `DateTime(timezone=True)` | `DATETIME` | `DATETIME` | See §3.1 |

No migration changes are needed. The existing migration applies cleanly against MySQL 8.

### 3.1 `DateTime(timezone=True)` Behaviour

MySQL `DATETIME` does not store timezone information. SQLAlchemy maps `DateTime(timezone=True)` to `DATETIME` on MySQL (not `TIMESTAMP`, which has a 2038 limit and range restrictions).

**Impact:** All datetime values must be stored and compared as UTC. The application already uses `datetime.now(timezone.utc)` for all defaults (`_now()` in `models.py`). The brute-force lockout window check in `auth_controllers.py` compares `attempted_at` values directly — this works correctly as long as all writes use UTC, which they do.

No code changes are needed, but any future datetime queries must use UTC-aware values.

---

## 4. Test Strategy

| Suite | Database | Rationale |
|---|---|---|
| Unit (`tests/unit/`) | SQLite in-memory | Tests business logic only; no dialect-specific SQL |
| Integration (`tests/integration/`) | SQLite in-memory | Fast CI; SQLAlchemy ORM abstracts dialect differences |
| E2E (`tests/e2e/`) | MySQL via docker-compose | Full production-path dialect coverage |

`tests/unit/conftest.py` and `tests/integration/conftest.py` hardcode `sqlite:///:memory:` — no changes needed there.

The CI E2E job already runs `docker compose up --build`, which will now include the MySQL service. No CI workflow changes are needed beyond ensuring `JWT_SECRET` is set (already done).

---

## 5. Key Design Decisions

### 5.1 Python TCP probe vs `mysqladmin` in `entrypoint.sh`

**Decision:** Use a Python socket probe rather than `mysqladmin ping`.

**Alternatives considered:**
- `mysqladmin ping` — requires MySQL client tools installed in the image (not present in `python:3.11-slim`)
- `wait-for-it.sh` — requires downloading an additional script; adds complexity

**Why chosen:** Python is already in the container. A one-liner TCP probe to port 3306 requires no additional dependencies and works without MySQL credentials.

---

### 5.2 `depends_on: service_healthy` vs wait loop only

**Decision:** Use both: `depends_on: condition: service_healthy` in docker-compose **and** a wait loop in `entrypoint.sh`.

**Why:** `depends_on` is the primary gate and eliminates the wait in the happy path (MySQL is ready before Compose starts the backend). The `entrypoint.sh` loop is a defence-in-depth fallback for environments that do not honour `service_healthy` (e.g. some CI setups, `docker run` without Compose).

---

### 5.3 Keep SQLite for unit/integration tests

**Decision:** Unit and integration tests continue to use `sqlite:///:memory:`.

**Alternatives considered:** Run all tests against MySQL in CI (add a `mysql` service to the `backend-tests` CI job).

**Tradeoffs:**

| | SQLite in-memory | MySQL in CI |
|---|---|---|
| CI speed | Fast — no service startup overhead | Slower — MySQL takes 10–20s to initialise |
| Dialect coverage | None for unit/integration | Full coverage at all levels |
| Complexity | No change to CI workflow | Requires `services:` block in `backend-tests` job |

**Why chosen:** The unit and integration tests exercise application logic via the SQLAlchemy ORM — they never write raw SQL. Dialect differences (e.g. `DATETIME` vs `TIMESTAMP`, case sensitivity) only surface in the production-path schema, which is covered by the E2E tests running against MySQL. The speed benefit of in-memory SQLite is meaningful in a tight feedback loop.

---

## 6. Files Changed Summary

| File | Change |
|---|---|
| `backend/requirements.txt` | Add `pymysql==1.1.1` |
| `backend/alembic.ini` | Replace hardcoded SQLite URL with placeholder comment |
| `backend/entrypoint.sh` | Add MySQL TCP wait loop before `alembic upgrade head` |
| `docker-compose.yml` | Add `mysql` service; add `DATABASE_URL` env var to backend; add `mysql_data` volume |
| `backend/.env.example` | Add `DATABASE_URL` examples for local MySQL and AWS RDS |
| `backend/migrations/env.py` | No change |
| `backend/app/models.py` | No change |
| `backend/app/factory.py` | No change |
| All test files | No change |
