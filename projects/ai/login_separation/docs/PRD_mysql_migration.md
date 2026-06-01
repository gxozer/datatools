# Product Requirements Document — MySQL Migration

**Version:** 1.0
**Date:** 2026-04-17
**Status:** Draft
**Jira:** PR-34
**Related:** [TDS.md](TDS.md), [README.md](../README.md)

---

## 1. Purpose

Replace SQLite with MySQL as the application database to enable cloud and multi-replica deployments. SQLite stores data in a file on the local filesystem; any container restart or pod rescheduling in ECS or Kubernetes destroys the database. MySQL, managed via Amazon RDS, provides a persistent, scalable, and production-grade data store that is independent of the application container lifecycle.

This is a prerequisite for AWS deployment (PR-11).

---

## 2. Background

The current stack uses SQLite via SQLAlchemy with the database file written to `/app/instance/app.db`. This works for local development and single-container demos but is incompatible with:

- **Docker named volumes** shared across multiple replicas
- **ECS Fargate** — containers are stateless; the filesystem is ephemeral
- **Kubernetes** — pods are rescheduled across nodes; local volumes are lost
- **Zero-downtime deploys** — SQLite cannot handle concurrent writers

Amazon RDS for MySQL is the target managed service. The application already uses SQLAlchemy and Alembic, so the switch requires only a driver change and connection string update — no ORM or query changes.

---

## 3. Requirements

### 3.1 Database Driver

- Add `pymysql` to `requirements.txt` as the MySQL driver for SQLAlchemy
- Connection URL format: `mysql+pymysql://user:password@host:3306/dbname`

### 3.2 Configuration

- `DATABASE_URL` environment variable controls the connection string
- Default for local development (without `.env`): a MySQL service in docker-compose
- `backend/.env.example` updated with a `DATABASE_URL` example for local MySQL and a comment showing the RDS format
- `docker-compose.yml` passes `DATABASE_URL` through to the backend container
- `alembic.ini` hardcoded `sqlalchemy.url` replaced with a placeholder — `migrations/env.py` already reads from `DATABASE_URL` at runtime

### 3.3 Local Development

- `docker-compose.yml` gains a `mysql` service (official `mysql:8` image)
- Backend service declares `depends_on: mysql` with a health check condition so Compose waits for MySQL to be ready before starting the backend
- `entrypoint.sh` adds a wait loop (polling `mysqladmin ping`) before running `alembic upgrade head`, as a safety net in case the container starts before MySQL is healthy

### 3.4 Migrations

- Existing Alembic migrations must apply cleanly against MySQL 8
- Any SQLite-specific migration artifacts (e.g. `PRAGMA` statements) must be removed or made dialect-conditional
- `alembic upgrade head` is the sole mechanism for schema management — no `db.create_all()` calls in the production path

### 3.5 Tests

- Unit tests continue to use SQLite in-memory (`sqlite:///:memory:`) — they test business logic, not the database dialect
- Integration tests continue to use SQLite in-memory for speed and simplicity in CI
- E2E tests run against the full docker-compose stack including MySQL — this provides dialect coverage for the production path
- SQLite is not removed from `requirements.txt`; it remains available for the test suites

### 3.6 Documentation

- README updated with:
  - Local dev instructions using `docker compose up` (MySQL is included automatically)
  - `.env` configuration for `DATABASE_URL`
  - AWS RDS connection string format for production

---

## 4. Non-Functional Requirements

### 4.1 Compatibility

- MySQL 8.0+ (matches Amazon RDS for MySQL default)
- `DateTime(timezone=True)` columns: MySQL `DATETIME` does not store timezone info. All datetime values are stored as UTC. Application code must not rely on database-level timezone conversion.

### 4.2 Security

- MySQL credentials must never be hardcoded; always sourced from environment variables
- `DATABASE_URL` must not be logged at startup
- The `mysql` service in docker-compose must not expose port 3306 to the host in production compose files

### 4.3 Startup Reliability

- The backend must not crash on startup if MySQL is slow to initialise
- The wait mechanism in `entrypoint.sh` must time out after a reasonable period (e.g. 60 seconds) and exit with a non-zero code if MySQL never becomes reachable

---

## 5. Out of Scope

- PostgreSQL or any other database engine
- Data migration from existing SQLite databases (no production data exists yet)
- ORM or query-level changes (SQLAlchemy abstracts the dialect)
- MySQL user/permission management beyond what docker-compose provides for local dev
- Connection pooling configuration (SQLAlchemy defaults are acceptable for this scale)

---

## 6. Success Criteria

- `docker compose up` starts the full stack (backend + frontend + MySQL) with no manual steps
- `alembic upgrade head` runs cleanly against MySQL on first boot
- All unit and integration tests pass (SQLite in-memory, as today)
- All E2E tests pass against the docker-compose MySQL stack
- `DATABASE_URL=mysql+pymysql://...` can be set to point at an Amazon RDS instance and the app starts without code changes
- SQLite is no longer used in the production container path (`entrypoint.sh`, `factory.py` default, `docker-compose.yml`)
- `backend/.env.example` documents both local MySQL and RDS connection string formats
