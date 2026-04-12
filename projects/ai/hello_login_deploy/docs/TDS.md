# Technical Design Specification — hello_login

**Version:** 1.0
**Date:** 2026-03-16
**Status:** Draft
**Related:** [PRD.md](PRD.md)

---

## 1. System Overview

`hello_login` is a two-process application: a Python/Flask REST API and a React/Vite single-page app. In development they run as separate servers; the Vite dev server proxies `/api/*` requests to Flask so the browser only talks to one origin.

```
Browser (localhost:5173)
        │
        │  GET /api/hello
        │  (proxied by Vite)
        ▼
Flask Backend (localhost:5001)
        │
        │  HelloController.hello()
        ▼
JSON response: {"message": "Hello, World!", "status": "ok"}
```

---

## 2. Backend

### 2.1 Directory Structure

```
backend/
├── app/
│   ├── __init__.py       # Re-exports create_app for import convenience
│   ├── factory.py        # Flask app factory
│   ├── controllers.py    # HelloController, HealthController
│   └── routes.py         # Blueprint + URL rules
├── run.py                # Entry point (python run.py)
├── requirements.txt      # Runtime dependencies
└── requirements-dev.txt  # Dev/test dependencies (same file in this project)
```

### 2.2 App Factory (`factory.py`)

`create_app()` follows the Flask application factory pattern:

1. Loads `.env` via `python-dotenv`
2. Creates a `Flask` instance
3. Enables CORS for `localhost:5173` and `localhost:3000` via `flask-cors`
4. Registers `api_blueprint` under the `/api` URL prefix
5. Returns the configured app

Using a factory (rather than a module-level `app` object) allows the test suite to create isolated app instances with `TESTING = True`.

### 2.3 Controllers (`controllers.py`)

Controllers are plain classes with static methods. They have no dependency on Flask routing, making them callable directly in unit tests without going through the HTTP stack.

| Class | Method | Returns |
|---|---|---|
| `HelloController` | `hello()` | `{"message": "Hello, World!", "status": "ok"}` |
| `HealthController` | `health()` | `{"status": "ok"}` |

Both methods use `flask.jsonify`, so they require an active Flask app context.

### 2.4 Routes (`routes.py`)

An `api_blueprint` Blueprint is defined here and registered in `factory.py` under `/api`. URL rules bind endpoints to controller static methods:

| Rule | Methods | View function |
|---|---|---|
| `/hello` | GET | `HelloController.hello` |
| `/health` | GET | `HealthController.health` |

### 2.5 Entry Point (`run.py`)

Reads `PORT` (default `5000`) and `FLASK_DEBUG` from the environment, then calls `app.run()`. The E2E test suite overrides `PORT=5001` to avoid port collisions with a running dev server.

### 2.6 Dependencies

| Package | Version | Purpose |
|---|---|---|
| `flask` | 3.1.0 | Web framework |
| `flask-cors` | 5.0.0 | CORS headers for the React dev server |
| `python-dotenv` | 1.0.1 | `.env` file loading |
| `pytest` | 8.3.5 | Test runner |
| `pytest-cov` | 6.0.0 | Coverage reporting |
| `playwright` | 1.50.0 | Browser automation |
| `pytest-playwright` | 0.7.0 | Playwright pytest integration |

---

## 3. Frontend

### 3.1 Directory Structure

```
frontend/
├── src/
│   ├── api/
│   │   └── ApiClient.ts       # HTTP client; sole fetch() caller
│   ├── components/
│   │   └── HelloMessage.tsx   # Stateless presentational component
│   ├── test/
│   │   ├── setup.ts           # jest-dom matchers setup
│   │   ├── ApiClient.test.ts  # ApiClient unit tests
│   │   └── HelloMessage.test.tsx  # Component unit tests
│   ├── App.tsx                # Root component (state + data fetching)
│   ├── App.css                # App-level styles
│   └── main.tsx               # React DOM entry point
├── vite.config.ts             # Vite + Vitest config
├── tsconfig.json
└── package.json
```

### 3.2 Data Flow

```
App (mounts)
  │
  └─ useEffect → ApiClient.getHello()
                        │
                        │  fetch('/api/hello')
                        ▼
                 Vite proxy → Flask /api/hello
                        │
                        ▼
                 { message, status }
                        │
  ┌────────────────────┘
  │
  ├─ loading=true  → <p>Loading…</p>
  ├─ error         → <p className="status error">Error: {msg}</p>
  └─ success       → <HelloMessage message={message} />
```

### 3.3 Components

**`App.tsx`** — Stateful root component. Manages three state variables:

| State | Type | Initial |
|---|---|---|
| `message` | `string \| null` | `null` |
| `loading` | `boolean` | `true` |
| `error` | `string \| null` | `null` |

Fetches on mount via `useEffect`. Renders one of three states: loading, error, or the `HelloMessage` component.

**`HelloMessage.tsx`** — Stateless presentational component. Accepts a single `message: string` prop and renders it in an `<h1>` inside a `.hello-message` div. Has no side effects.

### 3.4 ApiClient (`ApiClient.ts`)

A static class that centralises all `fetch()` calls. Components never call `fetch()` directly.

```typescript
ApiClient.getHello(): Promise<HelloResponse>
```

- Calls `GET /api/hello` (relative URL; proxied by Vite in dev)
- Throws a descriptive `Error` if `response.ok` is false
- Returns the parsed JSON typed as `HelloResponse { message: string, status: string }`

Using a class (rather than bare functions) allows tests to mock `ApiClient.getHello` without patching the global `fetch`.

### 3.5 Vite Dev Proxy

`vite.config.ts` forwards all `/api/*` requests to `http://localhost:5001`, eliminating CORS issues during development:

```typescript
server: {
  proxy: {
    '/api': 'http://localhost:5001',
  },
}
```

### 3.6 Dependencies

**Runtime:**

| Package | Version | Purpose |
|---|---|---|
| `react` | ^19.2.4 | UI framework |
| `react-dom` | ^19.2.4 | DOM renderer |

**Dev:**

| Package | Purpose |
|---|---|
| `vite` ^8 + `@vitejs/plugin-react` | Build tool and dev server |
| `typescript` ~5.9 | Type checking |
| `vitest` ^4 | Unit test runner |
| `@testing-library/react` | Component test utilities |
| `@testing-library/jest-dom` | Custom DOM matchers |
| `jsdom` | Browser environment for Vitest |

---

## 4. API Contract

### `GET /api/hello`

**Response (200 OK):**
```json
{
  "message": "Hello, World!",
  "status": "ok"
}
```

### `GET /api/health`

**Response (200 OK):**
```json
{
  "status": "ok"
}
```

**Error responses:** Flask's default 404 JSON response for unregistered routes. No custom error handling is implemented.

---

## 5. Test Architecture

All test files live outside the application source tree under `tests/` (backend) or `src/test/` (frontend).

### 5.1 Backend Unit Tests (`tests/unit/test_controllers.py`)

- Call controller static methods directly inside a Flask app context
- No HTTP stack involved; no routes exercised
- Verify: status code, `message` field value, `status` field value
- **5 tests** across `TestHelloController` and `TestHealthController`

### 5.2 Backend Integration Tests (`tests/integration/test_api.py`)

- Use the Flask test client (`app.test_client()`) — no live server
- Exercise routing, Blueprint registration, CORS, and JSON serialisation
- Verify: status codes, `Content-Type` header, response body fields, 404 for unknown routes
- **8 tests** across `TestHelloEndpoint`, `TestHealthEndpoint`, and `TestNotFound`

### 5.3 Shared Fixtures (`tests/conftest.py`)

```python
@pytest.fixture
def app():     # creates Flask app with TESTING=True
def client(app):  # returns app.test_client()
```

Both unit and integration suites use these fixtures. The unit suite adds a local `app_context` fixture on top to push the app context.

### 5.4 Frontend Unit Tests (`src/test/`)

- Run with Vitest in a jsdom browser environment
- `ApiClient` tests mock `fetch` via `vi.stubGlobal` to test success and error paths
- `HelloMessage` tests use React Testing Library to assert rendered output
- **5 tests** across 2 test files

### 5.5 End-to-End Tests (`tests/e2e/test_hello_ui.py`)

- Use Playwright (Chromium) + pytest-playwright
- Two `session`-scoped fixtures start Flask (`PORT=5001`) and Vite (`--port 5174`) as subprocesses
- Tests navigate to `http://localhost:5174` and assert on the live DOM
- **3 tests:** page loads, `Hello, World!` heading visible, no error element present

---

## 6. Local Development Setup

### Backend

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env   # set PORT, FLASK_DEBUG if needed
python run.py          # starts on localhost:5001
```

### Frontend

```bash
cd frontend
npm install
npm run dev            # starts on localhost:5173, proxies /api → :5001
```

### Environment Variables

| Variable | Default | Description |
|---|---|---|
| `PORT` | `5001` | Flask listen port |
| `FLASK_DEBUG` | `false` | Enable Flask debug/reloader |

---

## 7. Key Design Decisions

### 7.1 Flask App Factory Pattern

**Decision:** `create_app()` builds and returns a configured `Flask` instance rather than creating a module-level `app = Flask(__name__)` object.

**Alternatives considered:**
- Module-level `app` object (the Flask quickstart default)

**Tradeoffs:**

| | Factory | Module-level |
|---|---|---|
| Test isolation | Each test gets a fresh instance | All tests share one instance; config leaks between tests |
| Import side effects | None — construction is explicit | App created on first import; order-sensitive |
| Multiple configs | Trivial (`create_app(config=TestConfig)`) | Requires patching after import |

**Why chosen:** The factory is the standard Flask recommendation for any project with a test suite. The extra indirection is minimal and the isolation benefit is immediate — `conftest.py` calls `create_app()` to get a `TESTING=True` instance without touching the production instance.

---

### 7.2 Controllers Separate from Routes

**Decision:** Route logic lives in `controllers.py` as static methods on plain classes. `routes.py` is a thin binding layer that wires URLs to those methods.

**Alternatives considered:**
- Inline view functions directly in `routes.py`
- Flask `MethodView` classes

**Tradeoffs:**

| | Separate controllers | Inline view functions |
|---|---|---|
| Unit testability | Call `HelloController.hello()` directly — no HTTP needed | Must go through Flask test client even for simple logic |
| Discoverability | Two files to navigate | All logic in one place |
| Overhead for simple routes | Slightly more structure than strictly needed | Simpler for trivial endpoints |

**Why chosen:** Even though the logic is trivial here, the separation demonstrates the pattern that scales well. The unit test suite (`test_controllers.py`) validates controller behaviour independently of routing, which means routing bugs and logic bugs surface in different test layers — easier to diagnose.

---

### 7.3 `ApiClient` as a Static Class

**Decision:** All `fetch()` calls are centralised in `ApiClient` as static methods. Components import `ApiClient` and call `ApiClient.getHello()`; they never call `fetch()` directly.

**Alternatives considered:**
- Bare exported async functions (e.g. `export async function getHello()`)
- React Query or SWR for data fetching

**Tradeoffs:**

| | Static class | Bare functions | React Query/SWR |
|---|---|---|---|
| Mockability | `vi.spyOn(ApiClient, 'getHello')` — no global patching needed | Must mock the module or patch global `fetch` | Library handles caching/retries but adds a dependency |
| Discoverability | All API calls in one place | Scattered across modules or a functions file | Queries defined at call site |
| Weight | Zero dependencies | Zero dependencies | ~50 kB runtime dependency |

**Why chosen:** The static class makes test setup one line (`vi.spyOn(ApiClient, 'getHello').mockResolvedValue(...)`) and avoids patching the global `fetch`. React Query would be overkill for a single endpoint with no caching or retry requirements.

---

### 7.4 Stateless `HelloMessage` Component

**Decision:** `HelloMessage` is a pure presentational component — it receives `message: string` as a prop and renders it. All state and data fetching live in `App`.

**Alternatives considered:**
- Fetching directly inside `HelloMessage` (self-contained component)
- Combining everything in `App` with no separate component

**Tradeoffs:**

| | Stateless `HelloMessage` | Self-fetching `HelloMessage` | Everything in `App` |
|---|---|---|---|
| Testability | Render with any prop value, no mocking needed | Must mock `fetch` in every component test | No component test surface |
| Reusability | Drop into any parent that has a message string | Hardcoded to one data source | Not reusable |
| Separation of concerns | Clear: App owns data, HelloMessage owns display | Mixed concerns in one component | No separation |

**Why chosen:** The container/presentational split is idiomatic React and makes `HelloMessage` trivially testable — pass a string, assert it renders. It also documents the intended pattern for contributors adding future components.

---

### 7.5 Vite Dev Proxy for `/api`

**Decision:** `vite.config.ts` proxies all `/api/*` requests to `http://localhost:5001`. The frontend uses relative URLs (`/api/hello`) with no base URL configuration.

**Alternatives considered:**
- Configure CORS on Flask to allow the Vite origin and use absolute URLs in `ApiClient`
- Run both servers behind a local reverse proxy (nginx, Caddy)

**Tradeoffs:**

| | Vite proxy | CORS on Flask | Local reverse proxy |
|---|---|---|---|
| Config complexity | One `proxy` block in `vite.config.ts` | CORS origins in `factory.py` + absolute URLs in `ApiClient` | Extra tool to install and configure |
| Production parity | Relative URLs work in production too (assuming same-origin deployment) | Absolute URLs need environment-specific config | Closer to production but heavy for local dev |
| Credentials / cookies | Transparent — same origin from browser's perspective | Requires `credentials: 'include'` if cookies are added later | Transparent |

**Why chosen:** The proxy keeps the frontend code free of environment-specific base URLs and is the standard Vite approach. Flask-CORS is still configured (for `localhost:5173` and `localhost:3000`) as a fallback for tools that call the API directly (e.g. `curl`, Postman, integration tests).

---

### 7.6 Single `tests/` Root for All Backend Tests

**Decision:** All backend test files — unit, integration, and e2e — live under `tests/` at the project root, outside the `backend/` package.

**Alternatives considered:**
- `backend/tests/` alongside the application package
- Separate top-level `unit/`, `integration/`, `e2e/` directories

**Tradeoffs:**

| | `tests/` at root | `backend/tests/` | Separate top-level dirs |
|---|---|---|---|
| `pytest.ini` config | Single `testpaths = tests` entry | Works but tests sit inside the installable package | Multiple `testpaths` entries |
| Shared fixtures | `conftest.py` at `tests/` root serves all suites | Same | Fixtures must be duplicated or hoisted |
| Clarity | One place to look for all tests | Tests bundled with package | Flat but harder to see suite relationships |

**Why chosen:** A single `tests/` root with subdirectories per suite (`unit/`, `integration/`, `e2e/`) maps cleanly to `pytest.ini`, keeps `conftest.py` shared across all suites, and is immediately obvious to a new contributor. The global project instructions (CLAUDE.md) also mandate this layout.
