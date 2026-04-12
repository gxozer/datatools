# Product Requirements Document — Login Functionality

**Version:** 1.2
**Date:** 2026-03-17
**Status:** Draft
**Parent feature:** beads3-mmn
**Related:** [PRD.md](PRD.md), [TDS.md](TDS.md)

---

## 1. Purpose

Add a full authentication system to `hello_login`. After this feature, users can register an account, log in with email and password, access the protected greeting, recover a forgotten password, and log out. Sessions are maintained with JWT.

This extends the project's value as a reference implementation — demonstrating how to add a complete auth layer to an existing full-stack Flask + React application.

---

## 2. User Stories

| ID | As a… | I want to… | So that… |
|---|---|---|---|
| US-1 | visitor | see a login form when I open the app | I know I need to authenticate |
| US-2 | visitor | sign up with my full name, email, and password | I can create an account |
| US-3 | visitor | log in with my email and password | I can access the app |
| US-4 | visitor | see a clear error message if my credentials are wrong | I know to try again |
| US-5 | visitor | request a password reset if I forget my password | I can recover access to my account |
| US-6 | authenticated user | see the "Hello, World!" greeting | I can use the app |
| US-7 | authenticated user | log out | I can end my session |
| US-8 | authenticated user | refresh the page and stay logged in | I don't have to log in on every visit |

---

## 3. Functional Requirements

### 3.1 Sign-Up Page

- Accessible at `/signup`
- Contains: **Full name**, **Email**, **Password**, **Confirm password** fields, and a **Sign up** button
- Validates on submit:
  - All fields required
  - Email must be a valid format
  - Password and confirm password must match
  - Password must meet minimum strength requirements (see §4.1)
- Submits to `POST /api/signup`
- On success: navigates to the greeting page (user is automatically logged in)
- On failure: displays inline field-level error messages

### 3.2 Login Page

- Displayed at `/login` (and at `/` when unauthenticated)
- Contains: **Email**, **Password** fields, a **Log in** button, and a **Forgot password?** link
- Submits credentials to `POST /api/login`
- On success: navigates to the greeting page
- On failure: displays an inline error message (e.g. "Invalid email or password")
- After a configurable number of failed attempts, the account is temporarily locked (see §4.1)
- Fields are cleared on error; focus returns to the email field

### 3.3 Forgot Password Flow

- Accessible via the **Forgot password?** link on the login page
- **Step 1 — Request reset:** User enters their email; app calls `POST /api/password-reset/request`
  - Always shows a success message regardless of whether the email exists (prevents user enumeration)
- **Step 2 — Reset:** User follows a link containing a time-limited token; app displays a new-password form
  - Submits to `POST /api/password-reset/confirm`
  - Token expires after 1 hour
  - Token is single-use

### 3.4 Greeting Page (Protected)

- Accessible only to authenticated users
- Unauthenticated requests to `GET /api/hello` return `401 Unauthorized`
- The frontend redirects unauthenticated users to `/login`
- Displays the user's full name alongside the greeting (e.g. "Hello, Jane!")

### 3.5 Logout

- A **Log out** button is visible on the greeting page
- Clicking it calls `POST /api/logout`, invalidates the JWT, and redirects to `/login`

### 3.6 Backend API

| Endpoint | Method | Auth required | Description |
|---|---|---|---|
| `/api/signup` | POST | No | Register a new user |
| `/api/login` | POST | No | Validate credentials; return JWT |
| `/api/logout` | POST | Yes | Invalidate JWT |
| `/api/password-reset/request` | POST | No | Send password-reset email |
| `/api/password-reset/confirm` | POST | No | Reset password with token |
| `/api/hello` | GET | **Yes** | Greeting (was previously public) |
| `/api/health` | GET | No | Liveness check (unchanged) |

**`POST /api/signup` request:**
```json
{ "full_name": "string", "email": "string", "password": "string" }
```
**Response (201 Created):**
```json
{ "token": "string", "status": "ok" }
```
**Response (400 Bad Request — validation error):**
```json
{ "error": "string", "field": "string", "status": "error" }
```
**Response (409 Conflict — email already registered):**
```json
{ "error": "Email already in use", "status": "error" }
```

---

**`POST /api/login` request:**
```json
{ "email": "string", "password": "string" }
```
**Response (200 OK):**
```json
{ "token": "string", "status": "ok" }
```
**Response (401 Unauthorized):**
```json
{ "error": "Invalid email or password", "status": "error" }
```
**Response (429 Too Many Requests — account locked):**
```json
{ "error": "Account temporarily locked. Try again later.", "status": "error" }
```

---

**`GET /api/hello` response (200 OK, authenticated):**
```json
{ "message": "Hello, Jane!", "status": "ok" }
```

### 3.7 User Store

- Users are persisted in a database (SQLite for development; see TDS for schema)
- Each user record stores: `id`, `full_name`, `email`, `hashed_password`, `created_at`, `updated_at` 
- Passwords are hashed with bcrypt; plain-text passwords are never stored or logged
- Failed login attempts are tracked per email for brute-force protection

### 3.8 Session Persistence (JWT)

- On successful login or signup, the backend returns a signed JWT
- The frontend stores the JWT in `localStorage`
- The JWT is sent as a `Bearer` token in the `Authorization` header on all authenticated requests
- JWT expiry: 24 hours
- On expiry, the user is redirected to the login page

### 3.9 Role-Based Access Control

- Two roles: `user` (default) and `admin`
- Role is encoded in the JWT payload
- For this implementation, all registered users get the `user` role; `admin` is reserved for future use
- The `/api/hello` endpoint requires the `user` or `admin` role

---

## 4. Non-Functional Requirements

### 4.1 Security

- Passwords must never be logged or returned in API responses
- Passwords must be hashed with bcrypt (min cost factor 12)
- Password minimum strength: 8 characters, at least one letter and one number
- JWT tokens must be signed with a secret key from an environment variable (`JWT_SECRET`)
- Brute-force protection: lock account for 15 minutes after 5 consecutive failed login attempts; return `429` during lockout
- Password-reset tokens are single-use and expire after 1 hour
- The forgot-password response never confirms whether an email is registered (prevents enumeration)
- HTTPS / TLS configuration is out of scope for local development but must be noted as a production requirement

### 4.2 UX

- All forms must be usable with keyboard only (tab between fields, Enter to submit)
- Error messages must be visible without scrolling on a standard viewport
- Loading state must be shown while any auth request is in flight
- The greeting personalises the message with the user's full name

### 4.3 Test Coverage

New tests must be added alongside the implementation. Minimum additions:

| Suite | New tests |
|---|---|
| Backend unit | `SignupController`, `LoginController`, `PasswordResetController` — valid + invalid cases |
| Backend integration | All new endpoints (success, validation failure, auth failure, lockout, token expiry) |
| Frontend unit | `SignupForm`, `LoginForm`, `ForgotPasswordForm` components — render, submit, error display |
| End-to-end | Full signup flow, login flow, failed login, forgot password, logout |

### 4.4 Backwards Compatibility

- `GET /api/health` remains public and unchanged
- Existing tests for `GET /api/hello` must be updated to supply a valid JWT

---

## 5. Out of Scope

- OAuth / SSO / third-party auth providers (Google, GitHub, etc.)
- HTTPS / TLS configuration (production concern, not local dev)

---

## 6. Success Criteria

- A new visitor can sign up with full name, email, and password and see the personalised greeting
- A returning user can log in with email and password and see the personalised greeting
- Submitting incorrect credentials shows an inline error; 5 failures locks the account for 15 minutes
- A user who forgets their password can request a reset link and set a new password
- Clicking Log out returns the user to the login form
- Refreshing the page while logged in does not require re-authentication (JWT in localStorage)
- All existing and new test suites pass
