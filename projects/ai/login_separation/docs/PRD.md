# Product Requirements Document — hello_login

**Version:** 1.0
**Date:** 2026-03-16
**Status:** Draft

---

## 1. Purpose

`hello_login` is a minimal full-stack reference application that serves two goals:

1. **Demonstrate the beads workflow** — show how AI-assisted development with `bd` (beads) works end-to-end, from issue creation through implementation, testing, and commit.
2. **Provide a clean starting template** — offer a well-structured, fully-tested full-stack scaffold that future projects can fork or learn from.

The application content itself (a "Hello, World!" greeting) is intentionally trivial. The quality of the code structure, test coverage, and workflow discipline are what matter.

---

## 2. Target Audience

- **AI agents** (Claude Code, etc.) using this project as a beads workflow sandbox
- **Developers** learning beads-driven development or evaluating the tool
- **Contributors** who want a reference for how to structure a full-stack Python + React project

---

## 3. Functional Requirements

### 3.1 Backend API

| Endpoint | Method | Response | Notes |
|---|---|---|---|
| `/api/hello` | GET | `{"message": "Hello, World!", "status": "ok"}` | Core greeting endpoint |
| `/api/health` | GET | `{"status": "ok"}` | Liveness check |

- Responses are JSON with `Content-Type: application/json`
- No authentication required
- No database or persistent state

### 3.2 Frontend

- On page load, fetch `GET /api/hello` via `ApiClient`
- Display a **loading state** while the request is in flight
- Display the **greeting message** (`Hello, World!`) on success
- Display a **user-facing error message** if the request fails
- No routing, no user input, no forms

### 3.3 Dev Proxy

- Vite proxies `/api/*` to `localhost:5001` in development, so the frontend never needs CORS configuration

---

## 4. Non-Functional Requirements

### 4.1 Stack

| Layer | Technology |
|---|---|
| Backend language | Python 3.11+ |
| Backend framework | Flask |
| Frontend language | TypeScript |
| Frontend framework | React |
| Frontend build tool | Vite |
| Package manager | npm |

### 4.2 Test Coverage

| Suite | Tool | Target count |
|---|---|---|
| Backend unit | pytest | 5 tests |
| Backend integration | pytest + Flask test client | 8 tests |
| Frontend unit | Vitest + React Testing Library | 5 tests |
| End-to-end | Playwright (Chromium) | 3 tests |

All suites must pass before any issue is closed.

### 4.3 Code Structure

- Controllers are separated from route registration and independently testable
- Frontend data fetching is isolated in `ApiClient`; components are stateless presentational
- All test files live under `tests/` (backend) or `src/test/` (frontend)
- `.venv/` is never committed

### 4.4 Issue Tracking

- All work tracked in **beads** (`bd`), not markdown TODOs or external trackers
- Issues created before code is written; closed only after tests pass

---

## 5. Out of Scope

The following are explicitly not part of this project:

- A database or any persistent storage (login uses a single hardcoded user; see [PRD_login.md](PRD_login.md))
- Multiple API endpoints beyond `/hello` and `/health`
- Frontend routing or multiple pages
- Deployment infrastructure (Docker, CI/CD pipelines, cloud hosting)
- Internationalisation or accessibility requirements

---

## 6. Success Criteria

- `npm run dev` + `python run.py` starts the app and displays "Hello, World!" in the browser
- All test suites pass (`pytest` backend, `npm test` frontend, `pytest tests/e2e/` e2e)
- The beads workflow (create → in_progress → close) is exercised for every change
- Code is clean enough to serve as a readable reference for new contributors
