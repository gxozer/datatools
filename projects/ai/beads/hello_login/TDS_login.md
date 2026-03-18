# Technical Design Specification — Login Functionality

**Version:** 1.1
**Date:** 2026-03-17
**Status:** Draft
**Parent feature:** beads3-mmn
**Related:** [PRD_login.md](PRD_login.md), [TDS.md](TDS.md)

---

## 1. Overview

This document describes the technical design for adding full authentication to `hello_login`. It covers all changes to the backend, frontend, database, and test suites required to implement the features defined in [PRD_login.md](PRD_login.md).

High-level additions:

- SQLite database with a `users` table (via SQLAlchemy)
- Four new backend controllers and six new API endpoints
- JWT-based authentication with a decorator for protected routes
- Brute-force protection via a `login_attempts` table
- Email delivery via Flask-Mail + Mailgun (suppressed in dev)
- React Router for client-side routing
- Three new frontend pages: Sign-Up, Login, Forgot Password
- `AuthContext` for global auth state
- Updated `ApiClient` with JWT attachment and auth methods

---

## 2. System Architecture (Updated)

```
Browser (localhost:5173)
        │
        ├─ /             → redirect based on auth state
        ├─ /login        → LoginPage
        ├─ /signup       → SignupPage
        ├─ /forgot       → ForgotPasswordPage
        └─ /hello        → GreetingPage (protected)
                │
                │  Bearer <jwt>
                │  POST /api/login, /api/signup, etc.
                │  GET  /api/hello
                │  (proxied by Vite)
                ▼
Flask Backend (localhost:5001)
        │
        ├─ AuthMiddleware (JWT decode + role check)
        ├─ SignupController
        ├─ LoginController
        ├─ LogoutController
        ├─ PasswordResetController
        ├─ HelloController (now auth-gated)
        └─ HealthController
                │
                ▼
        SQLite (backend/app.db)
        ├─ users
        └─ login_attempts
```

---

## 3. Backend

### 3.1 Directory Structure (Updated)

```
backend/
├── app/
│   ├── __init__.py
│   ├── factory.py          # Updated: init db, register new blueprint
│   ├── database.py         # SQLAlchemy db instance
│   ├── models.py           # User, LoginAttempt models
│   ├── auth.py             # JWT helpers + require_auth decorator
│   ├── controllers.py      # Updated: HelloController now auth-gated
│   ├── auth_controllers.py # SignupController, LoginController,
│   │                       # LogoutController, PasswordResetController
│   └── routes.py           # Updated: auth blueprint + protected hello
├── run.py
├── requirements.txt        # Updated: new dependencies
└── .env.example            # Updated: JWT_SECRET, DATABASE_URL
```

### 3.2 Database (`database.py`, `models.py`)

**Engine:** SQLite via SQLAlchemy + Alembic for schema migrations. `db.create_all()` is NOT used; all schema changes are applied via `alembic upgrade head` instead. This means schema changes are incremental and safe — no data is lost on restart or upgrade. The initial migration creates all three tables.

**`users` table:**

| Column | Type | Constraints |
|---|---|---|
| `id` | INTEGER | PRIMARY KEY, AUTOINCREMENT |
| `full_name` | VARCHAR(255) | NOT NULL |
| `email` | VARCHAR(255) | NOT NULL, UNIQUE |
| `hashed_password` | VARCHAR(255) | NOT NULL |
| `role` | VARCHAR(50) | NOT NULL, DEFAULT `'user'` |
| `created_at` | DATETIME | NOT NULL, DEFAULT now |
| `updated_at` | DATETIME | NOT NULL, DEFAULT now, ON UPDATE now |

**`login_attempts` table:**

| Column | Type | Constraints |
|---|---|---|
| `id` | INTEGER | PRIMARY KEY, AUTOINCREMENT |
| `email` | VARCHAR(255) | NOT NULL, INDEX |
| `attempted_at` | DATETIME | NOT NULL, DEFAULT now |
| `success` | BOOLEAN | NOT NULL |

Brute-force check: count rows in `login_attempts` where `email = ?` AND `success = false` AND `attempted_at > now() - 15 minutes`. If count ≥ 5, return `429`.

**`password_reset_tokens` table:**

| Column | Type | Constraints |
|---|---|---|
| `id` | INTEGER | PRIMARY KEY, AUTOINCREMENT |
| `user_id` | INTEGER | FOREIGN KEY → users.id |
| `token_hash` | VARCHAR(255) | NOT NULL, UNIQUE |
| `expires_at` | DATETIME | NOT NULL |
| `used` | BOOLEAN | NOT NULL, DEFAULT false |

### 3.3 JWT Helpers (`auth.py`)

```python
# Generate a signed JWT for a user
def generate_token(user: User) -> str:
    payload = {
        "sub": user.id,
        "email": user.email,
        "full_name": user.full_name,
        "role": user.role,
        "exp": datetime.utcnow() + timedelta(hours=24),
    }
    return jwt.encode(payload, current_app.config["JWT_SECRET"], algorithm="HS256")

# Decorator for protected routes
def require_auth(f):
    # Reads Authorization: Bearer <token>
    # Decodes and validates JWT
    # Injects g.current_user
    # Returns 401 if missing or invalid
    # Returns 403 if role check fails
```

`g.current_user` is set to the decoded JWT payload and is available to controllers on protected routes.

### 3.4 Controllers

#### `SignupController`

- Validates `full_name`, `email`, `password` (all required; email format; password ≥ 8 chars, ≥ 1 letter, ≥ 1 digit)
- Checks for existing email → `409` if taken
- Hashes password with `bcrypt` (cost 12)
- Creates `User` record
- Returns signed JWT → `201`

#### `LoginController`

- Checks brute-force lockout → `429` if locked
- Looks up user by email → records failed attempt + returns `401` if not found
- Verifies bcrypt hash → records failed attempt + returns `401` if wrong
- Records successful attempt
- Returns signed JWT → `200`

#### `LogoutController`

- Protected by `@require_auth`
- JWT is stateless; logout is client-side (frontend deletes token from `localStorage`)
- Returns `200` to confirm the action; no server state to clear
- Future: add a token denylist if server-side invalidation is needed

#### `PasswordResetController`

**`request_reset()`**
- Looks up user by email (silently no-ops if not found — prevents enumeration)
- Generates a cryptographically random token (`secrets.token_urlsafe(32)`)
- Stores `sha256(token)` in `password_reset_tokens` with `expires_at = now + 1h`
- Sends reset email via Flask-Mail (`MAIL_SUPPRESS_SEND=true` in dev suppresses actual delivery)
- Returns `200` with generic success message regardless of outcome

**`confirm_reset()`**
- Accepts `token` and `new_password`
- Looks up `sha256(token)` in `password_reset_tokens`
- Validates: exists, not expired, not used → `400` if any check fails
- Updates `user.hashed_password` with new bcrypt hash
- Marks token as `used = true`
- Returns `200`

#### `HelloController` (updated)

- Now decorated with `@require_auth`
- Returns `{"message": "Hello, {full_name}!", "status": "ok"}` using `g.current_user["full_name"]`

### 3.5 Routes (Updated)

```python
# api_blueprint (existing, now has auth routes added)
POST  /api/signup
POST  /api/login
POST  /api/logout                  # @require_auth
POST  /api/password-reset/request
POST  /api/password-reset/confirm
GET   /api/hello                   # @require_auth
GET   /api/health
```

### 3.6 App Factory (Updated)

`create_app()` gains two new steps:

1. Set `app.config["JWT_SECRET"]` from environment variable
2. Set `app.config["SQLALCHEMY_DATABASE_URI"]` (default: `sqlite:///app.db`)
3. Call `db.init_app(app)` — schema is managed by Alembic, not `create_all()`

### 3.7 New Dependencies

| Package | Purpose |
|---|---|
| `sqlalchemy` | ORM and database abstraction |
| `flask-sqlalchemy` | SQLAlchemy integration for Flask |
| `alembic` | Database schema migrations |
| `bcrypt` | Password hashing |
| `pyjwt` | JWT encode/decode |
| `flask-mail` | Email delivery (SMTP); suppressed in dev via `MAIL_SUPPRESS_SEND` |

---

## 4. Frontend

### 4.1 Directory Structure (Updated)

```
frontend/src/
├── api/
│   └── ApiClient.ts          # Updated: auth methods, Bearer token attachment
├── auth/
│   └── AuthContext.tsx        # JWT storage, login/logout helpers, auth state
├── components/
│   ├── HelloMessage.tsx       # Unchanged
│   ├── ProtectedRoute.tsx     # Redirects unauthenticated users to /login
│   └── LoadingSpinner.tsx     # Shared loading state component
├── pages/
│   ├── LoginPage.tsx          # Login form + forgot password link
│   ├── SignupPage.tsx         # Sign-up form
│   ├── ForgotPasswordPage.tsx # Request reset form
│   ├── ResetPasswordPage.tsx  # Confirm reset form (reads token from URL)
│   └── GreetingPage.tsx       # Protected hello page + logout button
├── test/
│   ├── setup.ts
│   ├── ApiClient.test.ts      # Updated
│   ├── HelloMessage.test.tsx  # Unchanged
│   ├── LoginPage.test.tsx     # New
│   ├── SignupPage.test.tsx    # New
│   └── ForgotPasswordPage.test.tsx  # New
├── App.tsx                    # Updated: React Router routes
└── main.tsx
```

### 4.2 Routing

React Router v6 is added. Route structure:

```
/              → redirect to /hello if authenticated, else /login
/login         → LoginPage (public)
/signup        → SignupPage (public)
/forgot        → ForgotPasswordPage (public)
/reset         → ResetPasswordPage (public, reads ?token= from query string)
/hello         → GreetingPage (ProtectedRoute)
```

`ProtectedRoute` reads auth state from `AuthContext`. If no valid token, redirects to `/login`.

### 4.3 AuthContext (`auth/AuthContext.tsx`)

Provides global auth state to all components via React Context:

```typescript
interface AuthContextValue {
  token: string | null;       // JWT from localStorage
  user: JwtPayload | null;    // Decoded payload (sub, email, full_name, role)
  login: (token: string) => void;   // Store token, decode payload, update state
  logout: () => void;               // Remove token, clear state, redirect /login
  isAuthenticated: boolean;
}
```

- On mount, reads JWT from `localStorage` and validates expiry
- Expired tokens are cleared automatically

### 4.4 ApiClient (Updated)

New methods added; all authenticated methods attach the JWT automatically:

```typescript
// Auth
ApiClient.signup(fullName, email, password): Promise<AuthResponse>
ApiClient.login(email, password): Promise<AuthResponse>
ApiClient.logout(): Promise<void>
ApiClient.requestPasswordReset(email): Promise<void>
ApiClient.confirmPasswordReset(token, newPassword): Promise<void>

// Existing (now sends Authorization header)
ApiClient.getHello(): Promise<HelloResponse>
```

Private helper:

```typescript
private static authHeaders(): HeadersInit {
  const token = localStorage.getItem('jwt');
  return token ? { Authorization: `Bearer ${token}` } : {};
}
```

`getHello()` is updated to include `authHeaders()`. On `401` response, it throws a typed `AuthError` that `ProtectedRoute` / `AuthContext` can catch to trigger logout.

### 4.5 Pages

**`LoginPage`** — Email + password fields, Log in button, Forgot password link, link to /signup. Calls `ApiClient.login()`; on success calls `AuthContext.login(token)` and navigates to `/hello`.

**`SignupPage`** — Full name, email, password, confirm password fields. Client-side validation before submit. Calls `ApiClient.signup()`; on success calls `AuthContext.login(token)` and navigates to `/hello`.

**`ForgotPasswordPage`** — Email field only. Calls `ApiClient.requestPasswordReset()`. Always shows success message after submit regardless of response (mirrors backend enumeration prevention).

**`ResetPasswordPage`** — New password + confirm password fields. Reads `?token=` from URL query string. Calls `ApiClient.confirmPasswordReset(token, newPassword)`. On success navigates to `/login`.

**`GreetingPage`** — Fetches `GET /api/hello` on mount. Displays personalised greeting and Log out button. Logout calls `AuthContext.logout()`.

### 4.6 New Frontend Dependencies

| Package | Purpose |
|---|---|
| `react-router-dom` v6 | Client-side routing |

---

## 5. API Contract (Updated)

### `POST /api/signup`
**Request:** `{ "full_name": string, "email": string, "password": string }`
**201:** `{ "token": string, "status": "ok" }`
**400:** `{ "error": string, "field": string, "status": "error" }`
**409:** `{ "error": "Email already in use", "status": "error" }`

### `POST /api/login`
**Request:** `{ "email": string, "password": string }`
**200:** `{ "token": string, "status": "ok" }`
**401:** `{ "error": "Invalid email or password", "status": "error" }`
**429:** `{ "error": "Account temporarily locked. Try again later.", "status": "error" }`

### `POST /api/logout`
**Headers:** `Authorization: Bearer <token>`
**200:** `{ "status": "ok" }`
**401:** `{ "error": "Unauthorized", "status": "error" }`

### `POST /api/password-reset/request`
**Request:** `{ "email": string }`
**200:** `{ "status": "ok" }` (always, regardless of email existence)

### `POST /api/password-reset/confirm`
**Request:** `{ "token": string, "new_password": string }`
**200:** `{ "status": "ok" }`
**400:** `{ "error": "Invalid or expired token", "status": "error" }`

### `GET /api/hello` (updated)
**Headers:** `Authorization: Bearer <token>`
**200:** `{ "message": "Hello, {full_name}!", "status": "ok" }`
**401:** `{ "error": "Unauthorized", "status": "error" }`

### `GET /api/health` (unchanged)
**200:** `{ "status": "ok" }`

---

## 6. Test Architecture (Updated)

### 6.1 Backend Unit Tests

New file: `tests/unit/test_auth_controllers.py`

| Test class | Cases |
|---|---|
| `TestSignupController` | valid signup, missing fields, invalid email, weak password, duplicate email |
| `TestLoginController` | valid login, wrong password, unknown email, locked account |
| `TestPasswordResetController` | request (known email), request (unknown email), confirm (valid), confirm (expired), confirm (used) |
| `TestHelloController` (updated) | hello with auth context, verify full_name in message |

### 6.2 Backend Integration Tests

New file: `tests/integration/test_auth_api.py`

| Test class | Cases |
|---|---|
| `TestSignupEndpoint` | 201 on success, 400 on validation error, 409 on duplicate email |
| `TestLoginEndpoint` | 200 + JWT on success, 401 on bad credentials, 429 after 5 failures |
| `TestLogoutEndpoint` | 200 with valid JWT, 401 without JWT |
| `TestPasswordResetEndpoint` | request always 200, confirm 200/400 |
| `TestHelloEndpoint` (updated) | 200 with valid JWT, 401 without JWT, message contains full_name |

New fixture in `conftest.py`:
```python
@pytest.fixture
def auth_token(client):
    """Register a test user and return a valid JWT."""
```

### 6.3 Frontend Unit Tests

New test files under `src/test/`:

| File | Cases |
|---|---|
| `LoginPage.test.tsx` | renders fields, submit calls ApiClient.login, shows error on failure, navigates on success |
| `SignupPage.test.tsx` | renders fields, client-side validation, submit calls ApiClient.signup, navigates on success |
| `ForgotPasswordPage.test.tsx` | renders email field, always shows success message after submit |

### 6.4 End-to-End Tests

New file: `tests/e2e/test_auth_ui.py`

| Test | Description |
|---|---|
| `test_signup_flow` | Navigate to /signup, fill form, submit, assert greeting page |
| `test_login_flow` | Sign up, log out, log in again, assert greeting |
| `test_failed_login` | Submit wrong password, assert error message visible |
| `test_forgot_password` | Submit email on forgot page, assert success message |
| `test_logout` | Log in, click logout, assert redirected to /login |
| `test_protected_route_redirect` | Visit /hello unauthenticated, assert redirect to /login |

---

## 7. Environment Variables (Updated)

| Variable | Default | Description |
|---|---|---|
| `PORT` | `5000` | Flask listen port |
| `FLASK_DEBUG` | `false` | Enable Flask debug/reloader |
| `JWT_SECRET` | — | **Required.** Secret key for signing JWTs |
| `DATABASE_URL` | `sqlite:///app.db` | SQLAlchemy / Alembic database URI |

---

## 8. Key Design Decisions

### 8.1 JWT in `localStorage` vs. `HttpOnly` Cookie

**Decision:** JWT stored in `localStorage`, sent as `Authorization: Bearer` header.

**Alternatives considered:**
- `HttpOnly` cookie (XSS-safe but requires CSRF protection)

**Tradeoffs:**

| | `localStorage` + Bearer | `HttpOnly` cookie |
|---|---|---|
| XSS risk | Token accessible to JS — vulnerable if XSS present | Inaccessible to JS — XSS-safe |
| CSRF risk | Not sent automatically — CSRF-safe by default | Requires CSRF token or `SameSite` |
| Implementation | Simple; no server-side cookie config | Requires `Set-Cookie` + CSRF middleware |
| Mobile/API parity | Works for any API client | Cookie-specific |

**Why chosen:** For a reference implementation with no XSS surface (no user-generated content, no third-party scripts), `localStorage` keeps the implementation straightforward and the `ApiClient` pattern clean. A production deployment with user content should migrate to `HttpOnly` cookies.

---

### 8.2 Stateless JWT Logout

**Decision:** Logout is client-side only — the frontend deletes the token from `localStorage`. The backend returns `200` but holds no server state.

**Alternatives considered:**
- Server-side token denylist (Redis or DB table of invalidated JTIs)

**Tradeoffs:**

| | Client-side logout | Server denylist |
|---|---|---|
| Implementation | Trivial | Requires denylist storage + check on every request |
| Security | Token valid until expiry if stolen post-logout | Immediate invalidation |
| Scalability | Stateless — no shared state needed | Requires shared cache (Redis) across instances |

**Why chosen:** With a 24-hour expiry and no sensitive data beyond the greeting, the risk window is acceptable for a reference project. The logout endpoint exists (so the pattern is demonstrated) with a comment noting where a denylist would be added.

---

### 8.3 SQLAlchemy + Alembic for Migrations

**Decision:** Use Alembic to manage all schema changes. `db.create_all()` is not used. The initial migration creates all three tables; future schema changes are added as new migration scripts.

**Alternatives considered:**
- `db.create_all()` on startup (no migration tool)

**Tradeoffs:**

| | Alembic | `create_all()` |
|---|---|---|
| Schema changes | Incremental migrations — no data loss | Requires dropping and recreating DB |
| Setup cost | `pip install alembic` + `alembic init` + one config edit | Zero |
| Production suitability | Production-grade | Not safe for production data |
| Dev workflow | `alembic upgrade head` after pulling changes | Just restart the server |

**Why chosen:** With a real user table and password reset tokens, data loss on schema change is unacceptable even in development — you'd lose all test accounts on every model change. Alembic's setup cost is low (a one-time `alembic init`) and makes the project a better reference for production-style development.

**Workflow:**

```bash
# One-time setup (already done in the repo)
alembic init alembic

# After changing a model
alembic revision --autogenerate -m "describe the change"
alembic upgrade head

# On first clone / fresh DB
alembic upgrade head
```

**Directory structure addition:**

```
backend/
├── alembic/
│   ├── env.py              # Imports app models so autogenerate works
│   ├── script.py.mako
│   └── versions/
│       └── 0001_initial.py # Creates users, login_attempts, password_reset_tokens
└── alembic.ini             # Points to DATABASE_URL env var
```

---

### 8.4 Brute-Force Protection via DB Table

**Decision:** Track failed login attempts in a `login_attempts` table in SQLite. Lock for 15 minutes after 5 failures.

**Alternatives considered:**
- In-memory dict (lost on restart)
- Redis with TTL keys

**Tradeoffs:**

| | DB table | In-memory | Redis |
|---|---|---|---|
| Persistence across restarts | Yes | No | Yes |
| Setup complexity | Zero (reuses existing DB) | Zero | Requires Redis |
| Scalability | Fine for single-process dev | Single-process only | Multi-process safe |

**Why chosen:** The DB table survives restarts and requires no additional infrastructure. For a single-process dev server this is sufficient. A note in the code should indicate that Redis would be appropriate for a multi-process production deployment.

---

### 8.5 Email Delivery via Flask-Mail + Mailgun

**Decision:** Use **Flask-Mail** for email delivery, backed by **Mailgun** as the SMTP relay. In development, Flask-Mail's `MAIL_SUPPRESS_SEND=true` suppresses actual sending and logs the message instead.

**Alternatives considered:**
- Log reset link to stdout only (no real email)
- Local SMTP mock (MailHog, Mailpit)

**Tradeoffs:**

| | Flask-Mail + Mailgun | Log to stdout | Local SMTP mock |
|---|---|---|---|
| Production-ready | Yes — same code path in dev and prod | No — requires a code change to add real sending | Partial — real sending still needs wiring |
| Setup | `pip install flask-mail` + 5 env vars | Zero | Requires running a separate mail server |
| Dev experience | Suppressed send; output visible in Flask logs | Instant in terminal | Requires checking a web UI |
| Switching provider | Change 2 env vars | Rewrite controller | Change env vars |

**Why chosen:** Flask-Mail keeps dev and prod on the same code path — the only difference is env vars. `MAIL_SUPPRESS_SEND=true` in development means no real emails are sent but the full send logic is exercised. Mailgun's free tier (100 emails/day) is sufficient for a reference project.

**Configuration:**

```
# .env (development)
MAIL_SERVER=smtp.mailgun.org
MAIL_PORT=587
MAIL_USE_TLS=true
MAIL_USERNAME=postmaster@yourdomain.mailgun.org
MAIL_PASSWORD=your-mailgun-smtp-password
MAIL_DEFAULT_SENDER=noreply@yourdomain.com
MAIL_SUPPRESS_SEND=true    # suppress in dev; remove in production
```

**Usage in `PasswordResetController`:**

```python
mail.send_message(
    subject="Reset your hello_login password",
    recipients=[user.email],
    body=f"Click the link below to reset your password (expires in 1 hour):\n\n{reset_link}"
)
```

---

### 8.6 React Router for Client-Side Routing

**Decision:** Add `react-router-dom` v6 to handle `/login`, `/signup`, `/forgot`, `/reset`, and `/hello` routes.

**Alternatives considered:**
- Conditional rendering in `App.tsx` based on auth state (no router)

**Tradeoffs:**

| | React Router | Conditional rendering |
|---|---|---|
| Deep-linkable URLs | Yes | No (all routes hit `/`) |
| Browser history (back/forward) | Yes | No |
| Code complexity | Slightly more setup | Simpler for 1-2 views |
| Reference value | Demonstrates standard pattern | Non-standard workaround |

**Why chosen:** With four distinct pages (login, signup, forgot password, greeting), conditional rendering in a single component would become unwieldy. React Router is the standard solution and is more valuable as a reference pattern.

---

## 9. Implementation Plan (TDD)

This feature is implemented using **strict Test-Driven Development**: for every implementation ticket, a paired test ticket must be completed first (tests written and confirmed failing) before implementation begins.

### 9.1 Workflow per ticket pair

```
1. Pick the test ticket (bd ready)
2. Write failing tests (red)
3. Close the test ticket
4. Pick the now-unblocked implementation ticket
5. Write minimum code to make tests pass (green)
6. Refactor if needed
7. Close the implementation ticket
```

### 9.2 Ticket pairs

Each row is a TDD pair. The test ticket must be closed before the implementation ticket is unblocked.

**Backend:**

| Test ticket | Implementation ticket |
|---|---|
| beads3-c9r Tests: DB models and Alembic migration | beads3-h8o DB models and Alembic initial migration |
| beads3-15w Tests: JWT helpers and require_auth decorator | beads3-3ok JWT helpers and require_auth decorator |
| beads3-b2d Tests: SignupController | beads3-aiv SignupController and POST /api/signup |
| beads3-kxs Tests: LoginController | beads3-6ij LoginController and POST /api/login |
| beads3-7zv Tests: LogoutController | beads3-5cq LogoutController and POST /api/logout |
| beads3-pmz Tests: PasswordResetController | beads3-7v5 PasswordResetController and reset endpoints |
| beads3-2bf Tests: Updated HelloController | beads3-9td Update HelloController to require auth and personalise greeting |

**Frontend:**

| Test ticket | Implementation ticket |
|---|---|
| beads3-9wp Tests: React Router routing structure | beads3-o00 Add React Router and page routing structure |
| beads3-afq Tests: AuthContext | beads3-7bi Implement AuthContext |
| beads3-8e4 Tests: Updated ApiClient | beads3-tx5 Update ApiClient with auth methods |
| beads3-9mt Tests: ProtectedRoute component | beads3-bta Implement ProtectedRoute component |
| beads3-ib8 Tests: LoginPage | beads3-cs5 Implement LoginPage |
| beads3-odj Tests: SignupPage | beads3-a42 Implement SignupPage |
| beads3-b7k Tests: ForgotPasswordPage and ResetPasswordPage | beads3-kde Implement ForgotPasswordPage and ResetPasswordPage |
| beads3-lfn Tests: GreetingPage | beads3-u9c Implement GreetingPage |

**Final validation:**

| Ticket | Description |
|---|---|
| beads3-da1 | E2E tests for auth flows — runs after all implementation tickets are closed |

### 9.3 Dependency order

Backend must be completed before frontend integration can be fully tested. Recommended sequence:

1. DB models + Alembic (`beads3-c9r` → `beads3-h8o`)
2. JWT helpers (`beads3-15w` → `beads3-3ok`)
3. All backend controllers in parallel (SignupController, LoginController, LogoutController, PasswordResetController, HelloController update)
4. React Router (`beads3-9wp` → `beads3-o00`)
5. AuthContext (`beads3-afq` → `beads3-7bi`)
6. ApiClient + ProtectedRoute in parallel (`beads3-8e4` → `beads3-tx5`, `beads3-9mt` → `beads3-bta`)
7. All pages in parallel (LoginPage, SignupPage, ForgotPasswordPage, GreetingPage)
8. E2E tests (`beads3-da1`)
