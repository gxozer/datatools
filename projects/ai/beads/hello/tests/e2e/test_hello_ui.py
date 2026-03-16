"""
test_hello_ui.py — End-to-end UI tests using Playwright.

These tests start the Flask backend and the Vite frontend dev server,
then drive a real Chromium browser to verify the full stack works together.
"""

import os
import subprocess
import sys
import time
import pytest
from playwright.sync_api import Page, expect


# Ports used by the servers during E2E tests
BACKEND_PORT = 5001   # Use 5001 to avoid colliding with a running dev server
FRONTEND_PORT = 5174  # Vite uses 5173 by default; use 5174 as fallback


@pytest.fixture(scope="session")
def backend_server():
    """Start the Flask backend for the duration of the E2E test session."""
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
    """Start the Vite dev server for the duration of the E2E test session."""
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
        page.goto(f"http://localhost:{FRONTEND_PORT}")
        expect(page).not_to_have_title("")

    def test_hello_message_visible(self, frontend_server, page: Page):
        """'Hello, World!' should be visible on the page after load."""
        page.goto(f"http://localhost:{FRONTEND_PORT}")
        # Wait for the message to appear (replaces the loading state)
        heading = page.get_by_role("heading", name="Hello, World!")
        expect(heading).to_be_visible()

    def test_no_error_message(self, frontend_server, page: Page):
        """No error message should be displayed on a successful load."""
        page.goto(f"http://localhost:{FRONTEND_PORT}")
        # Wait for loading to finish, then assert no error is shown
        page.wait_for_selector(".status.error", state="detached", timeout=5000)
