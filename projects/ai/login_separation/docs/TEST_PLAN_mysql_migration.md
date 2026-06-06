# Test Plan — MySQL Migration

**Version:** 1.0
**Date:** 2026-04-17
**Jira:** PR-44 (subtask of PR-34)
**Related:** [PRD_mysql_migration.md](PRD_mysql_migration.md), [TDS_mysql_migration.md](TDS_mysql_migration.md)

---

## 1. Scope

This plan covers verification of the SQLite → MySQL migration. It tests that:

1. Existing unit and integration tests continue to pass (no regressions)
2. The docker-compose stack starts cleanly with MySQL
3. Alembic migrations apply against MySQL
4. The application functions end-to-end against MySQL
5. The CI pipeline passes

---

## 2. Prerequisites

```bash
# Docker Desktop running
docker info

# Python venv active
source backend/.venv/bin/activate

# pymysql installed
pip install pymysql==1.1.1
```

---

## 3. Test Cases

### 3.1 Unit Tests — no regressions (SQLite in-memory)

These tests exercise business logic only and are unaffected by the database dialect change.

```bash
backend/.venv/bin/python -m pytest tests/unit/ -v
```

**Expected:** All tests pass (~60). No failures, no errors.

---

### 3.2 Integration Tests — no regressions (SQLite in-memory)

```bash
backend/.venv/bin/python -m pytest tests/integration/ -v
```

**Expected:** All tests pass (~76). No failures, no errors.

---

### 3.3 Docker Compose Stack — MySQL service starts

```bash
docker compose up -d
docker compose ps
```

**Expected:**
- `mysql` service status: `healthy`
- `backend` service status: `running`
- `frontend` service status: `running`

If MySQL is not healthy after 60 seconds:

```bash
docker compose logs mysql
```

---

### 3.4 Alembic Migration — applies against MySQL

Verify migrations ran cleanly on backend startup:

```bash
docker compose logs backend | grep -E "alembic|migration|upgrade|error"
```

**Expected output includes:**
```
Running upgrade  -> 845373ac03c6, initial schema
```

**No errors expected.** If migrations failed, the backend will have exited — check `docker compose ps` and `docker compose logs backend`.

Verify schema directly in MySQL:

```bash
docker compose exec mysql mysql -u hello -phello hello_login -e "SHOW TABLES;"
```

**Expected:**
```
+------------------------+
| Tables_in_hello_login  |
+------------------------+
| alembic_version        |
| login_attempts         |
| password_reset_tokens  |
| users                  |
+------------------------+
```

---

### 3.5 Manual Smoke Test — full user flow against MySQL

Open [http://localhost:3000](http://localhost:3000) in a browser.

| Step | Action | Expected result |
|------|--------|-----------------|
| 1 | Visit `http://localhost:3000` | Redirected to `/login` |
| 2 | Click "Sign up" | `/signup` page loads |
| 3 | Fill in name, email, password and submit | Redirected to `/hello`, greeting shown |
| 4 | Click "Log out" | Redirected to `/login` |
| 5 | Log in with same credentials | Redirected to `/hello`, greeting shown |
| 6 | Visit `http://localhost:3000/forgot-password`, enter email, submit | "Check your email" message shown |

Verify the user was written to MySQL:

```bash
docker compose exec mysql mysql -u hello -phello hello_login -e "SELECT id, full_name, email, role FROM users;"
```

**Expected:** One row for the registered user.

---

### 3.6 Automated E2E Tests — against MySQL stack

With the docker-compose stack running:

```bash
E2E_BASE_URL=http://localhost:3000 backend/.venv/bin/python -m pytest tests/e2e/ -v
```

**Expected:** All 9 tests pass.

---

### 3.7 DATABASE_URL Override — point at local MySQL directly

Verify the app can connect to MySQL using an explicit `DATABASE_URL` (simulates RDS-style config):

```bash
DATABASE_URL=mysql+pymysql://hello:hello@127.0.0.1:3306/hello_login \
  JWT_SECRET=test-secret \
  backend/.venv/bin/python -m alembic upgrade head
```

**Expected:** `INFO [alembic.runtime.migration] Running upgrade ...` or `No migrations to run` (already applied). No errors.

---

### 3.8 Wait Loop — entrypoint handles delayed MySQL startup

Verify the wait loop fires when MySQL is slow:

```bash
# Stop just the backend, then start it while MySQL is still initialising
docker compose stop backend
docker compose start backend
docker compose logs -f backend
```

**Expected log output:**
```
Waiting for MySQL...
MySQL is ready.
INFO  [alembic.runtime.migration] ...
 * Running on all addresses (0.0.0.0)
```

---

### 3.9 CI Pipeline

Push the branch and verify all 4 CI jobs pass on GitHub Actions:

| Job | Expected result |
|-----|----------------|
| Backend unit + integration tests | Pass |
| Backend container build + structure tests | Pass |
| Frontend container build + structure tests | Pass |
| E2E tests (docker-compose stack) | Pass |

The E2E job runs `docker compose up --build` which now includes MySQL. No CI workflow changes are required.

Check results at: [github.com/gxozer/datatools/actions](https://github.com/gxozer/datatools/actions)

---

## 4. Cleanup

```bash
docker compose down        # stop stack, keep volumes
docker compose down -v     # stop stack and delete mysql_data + db_data volumes
```

---

## 5. Pass Criteria

All of the following must be true before PR-34 is closed:

- [ ] Unit tests: all pass
- [ ] Integration tests: all pass
- [ ] `docker compose up`: all three services healthy/running
- [ ] MySQL tables created by Alembic: `users`, `login_attempts`, `password_reset_tokens`, `alembic_version`
- [ ] Manual smoke test: signup → login → hello → logout all work
- [ ] E2E tests: all 9 pass against the MySQL stack
- [ ] CI pipeline: all 4 jobs green
