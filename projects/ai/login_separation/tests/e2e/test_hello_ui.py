"""
test_hello_ui.py — End-to-end UI tests using Playwright.

These tests start the Flask backend and the Vite frontend dev server,
then drive a real Chromium browser to verify the full stack works together.
"""

import os
import re
import subprocess
import sys
import time
import uuid
import pytest
from playwright.sync_api import Page, expect


# Ports used by the servers during E2E tests
BACKEND_PORT = 5001   # Use 5001 to avoid colliding with a running dev server
FRONTEND_PORT = 5174  # Vite uses 5173 by default; use 5174 as fallback

# When E2E_BASE_URL is set (e.g. for docker-compose testing), skip starting
# local servers and point Playwright at the running stack instead.
_EXTERNAL_URL = os.environ.get("E2E_BASE_URL", "").rstrip("/")
BASE_URL = _EXTERNAL_URL if _EXTERNAL_URL else f"http://localhost:{FRONTEND_PORT}"


@pytest.fixture(scope="session")
def backend_server():
    """Start the Flask backend for the duration of the E2E test session.
    No-op when E2E_BASE_URL is set (external stack is already running).
    """
    if _EXTERNAL_URL:
        yield None
        return

    proc = subprocess.Popen(
        [sys.executable, "run.py"],
        cwd="backend",
        env={
            **os.environ,
            "PORT": str(BACKEND_PORT),
            "FLASK_DEBUG": "false",
        },
    )
    # Allow the server a moment to start
    time.sleep(1)
    yield proc
    proc.terminate()
    proc.wait()


@pytest.fixture(scope="session")
def frontend_server(backend_server):
    """Start the Vite dev server for the duration of the E2E test session.
    No-op when E2E_BASE_URL is set (external stack is already running).
    """
    if _EXTERNAL_URL:
        yield None
        return

    proc = subprocess.Popen(
        ["npm", "run", "dev", "--", "--port", str(FRONTEND_PORT), "--strictPort"],
        cwd="frontend",
    )
    # Allow Vite a moment to compile and start
    time.sleep(3)
    yield proc
    proc.terminate()
    proc.wait()


class TestHelloWorldUI:
    """E2E tests verifying the Hello World message appears in the browser."""

    def test_page_loads(self, frontend_server, page: Page):
        """The app should load without errors."""
        page.goto(BASE_URL)
        expect(page).not_to_have_title("")

    def test_hello_message_visible(self, frontend_server, page: Page):
        """A personalised 'Hello, <name>!' heading should be visible after login."""
        # Sign up a unique user to obtain an authenticated session
        unique_email = f"hello.{uuid.uuid4().hex[:8]}@example.com"
        page.goto(f"{BASE_URL}/signup")
        page.get_by_label("Full Name").fill("Hello Tester")
        page.get_by_label("Email").fill(unique_email)
        page.get_by_label("Password").fill("Password1!")
        page.get_by_role("button", name=re.compile(r"sign.?up|register|submit", re.I)).first.click()
        expect(page).to_have_url(f"{BASE_URL}/hello")
        heading = page.get_by_role("heading", name=re.compile(r"hello", re.I))
        expect(heading).to_be_visible()

    def test_no_error_message(self, frontend_server, page: Page):
        """No error message should be displayed on a successful load."""
        page.goto(BASE_URL)
        # Wait for loading to finish, then assert no error is shown
        page.wait_for_selector(".status.error", state="detached", timeout=5000)
