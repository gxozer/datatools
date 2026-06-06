"""
test_auth_ui.py — End-to-end UI tests for authentication flows.

These tests start the Flask backend and the Vite frontend dev server,
then drive a real Chromium browser to verify signup, login, logout, and
password-reset flows work end-to-end.
"""

import os
import re
import subprocess
import sys
import time
import uuid
import pytest
from playwright.sync_api import Page, expect


# Ports — offset from test_hello_ui to avoid collisions if both run together
BACKEND_PORT = 5002
FRONTEND_PORT = 5175

# When E2E_BASE_URL is set (e.g. for docker-compose testing), skip starting
# local servers and point Playwright at the running stack instead.
_EXTERNAL_URL = os.environ.get("E2E_BASE_URL", "").rstrip("/")
BASE_URL = _EXTERNAL_URL if _EXTERNAL_URL else f"http://localhost:{FRONTEND_PORT}"

# A test user shared across the session — unique email per run avoids conflicts
# with persistent databases (e.g. docker-compose volume).
_RUN_ID = uuid.uuid4().hex[:8]
TEST_USER = {
    "full_name": "Alice Auth",
    "email": f"alice.auth.{_RUN_ID}@example.com",
    "password": "Password1!",
}


# ---------------------------------------------------------------------------
# Session-scoped servers
# ---------------------------------------------------------------------------

@pytest.fixture(scope="session")
def auth_backend(tmp_path_factory):
    """Start a Flask backend with a fresh temporary SQLite DB.
    No-op when E2E_BASE_URL is set (external stack is already running).
    """
    if _EXTERNAL_URL:
        yield None
        return

    db_path = tmp_path_factory.mktemp("e2e_db") / "e2e.db"
    server_env = {
        **os.environ,
        "PORT": str(BACKEND_PORT),
        "FLASK_DEBUG": "false",
        "DATABASE_URL": f"sqlite:///{db_path}",
        "JWT_SECRET": "e2e-test-secret-key-32-bytes-long!",
        "MAIL_SUPPRESS_SEND": "1",
    }

    # Initialise schema via Alembic
    subprocess.run(
        [sys.executable, "-m", "alembic", "upgrade", "head"],
        cwd="backend",
        env=server_env,
        check=True,
    )

    proc = subprocess.Popen(
        [sys.executable, "run.py"],
        cwd="backend",
        env=server_env,
    )
    time.sleep(1)
    yield proc
    proc.terminate()
    proc.wait()


@pytest.fixture(scope="session")
def auth_frontend(auth_backend):
    """Start the Vite dev server pointing at the auth backend.
    No-op when E2E_BASE_URL is set (external stack is already running).
    """
    if _EXTERNAL_URL:
        yield None
        return

    proc = subprocess.Popen(
        ["npm", "run", "dev", "--", "--port", str(FRONTEND_PORT), "--strictPort"],
        cwd="frontend",
        env={
            **os.environ,
            "VITE_BACKEND_PORT": str(BACKEND_PORT),
        },
    )
    time.sleep(3)
    yield proc
    proc.terminate()
    proc.wait()


# ---------------------------------------------------------------------------
# Helper
# ---------------------------------------------------------------------------

def signup(page: Page, full_name: str, email: str, password: str):
    """Navigate to /signup and fill + submit the signup form."""
    page.goto(f"{BASE_URL}/signup")
    page.get_by_label("Full Name").fill(full_name)
    page.get_by_label("Email").fill(email)
    page.get_by_label("Password").fill(password)
    page.get_by_role("button", name=re.compile(r"sign.?up|register|submit", re.I)).first.click()


def login(page: Page, email: str, password: str):
    """Navigate to /login and fill + submit the login form."""
    page.goto(f"{BASE_URL}/login")
    page.get_by_label("Email").fill(email)
    page.get_by_label("Password").fill(password)
    page.get_by_role("button", name=re.compile(r"log.?in|sign.?in|submit", re.I)).first.click()


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

class TestAuthFlows:

    def test_signup_flow(self, auth_frontend, page: Page):
        """Sign up as a new user and land on the greeting page."""
        signup(page, TEST_USER["full_name"], TEST_USER["email"], TEST_USER["password"])
        expect(page).to_have_url(f"{BASE_URL}/hello")
        expect(page.get_by_role("main")).to_contain_text("Hello")

    def test_login_flow(self, auth_frontend, page: Page):
        """Sign up, log out, then log back in and verify the greeting."""
        # Sign up (creates the user in the session DB)
        signup(page, "Bob Login", f"bob.login.{_RUN_ID}@example.com", TEST_USER["password"])
        expect(page).to_have_url(f"{BASE_URL}/hello")

        # Log out
        page.get_by_role("button", name=re.compile(r"log.?out|sign.?out", re.I)).first.click()
        expect(page).to_have_url(f"{BASE_URL}/login")

        # Log back in
        login(page, f"bob.login.{_RUN_ID}@example.com", TEST_USER["password"])
        expect(page).to_have_url(f"{BASE_URL}/hello")
        expect(page.get_by_role("main")).to_contain_text("Hello")

    def test_failed_login(self, auth_frontend, page: Page):
        """Wrong password shows an error message."""
        login(page, TEST_USER["email"], "WrongPassword!")
        expect(page).to_have_url(f"{BASE_URL}/login")
        expect(page.get_by_role("alert")).to_be_visible()

    def test_forgot_password(self, auth_frontend, page: Page):
        """Submitting the forgot-password form always shows a success message."""
        page.goto(f"{BASE_URL}/forgot-password")
        page.get_by_label("Email").fill(TEST_USER["email"])
        page.get_by_role("button", name=re.compile(r"send|reset|submit", re.I)).first.click()
        expect(page.get_by_role("main")).to_contain_text(
            re.compile(r"check your email|email sent|sent", re.I)
        )

    def test_logout(self, auth_frontend, page: Page):
        """After logging in, clicking Log Out redirects to /login."""
        login(page, TEST_USER["email"], TEST_USER["password"])
        expect(page).to_have_url(f"{BASE_URL}/hello")

        page.get_by_role("button", name=re.compile(r"log.?out|sign.?out", re.I)).first.click()
        expect(page).to_have_url(f"{BASE_URL}/login")

    def test_protected_route_redirect(self, auth_frontend, page: Page):
        """Visiting /hello while unauthenticated redirects to /login."""
        # Navigate to the app first so localStorage is accessible, then clear it
        page.goto(BASE_URL)
        page.evaluate("localStorage.clear()")
        page.context.clear_cookies()

        page.goto(f"{BASE_URL}/hello")
        expect(page).to_have_url(f"{BASE_URL}/login")
