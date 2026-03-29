"""
test_logout_controller.py — Unit tests for LogoutController.

LogoutController.logout() is called only after Auth.require_auth has
already validated the JWT, so the controller itself simply returns 200.
These tests call the controller directly inside a Flask request context.
"""

import pytest

from app.auth_controllers import LogoutController


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _unpack(result):
    """Unpack a controller return value into (response, status_code)."""
    if isinstance(result, tuple):
        return result[0], result[1]
    return result, result.status_code


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

class TestLogoutController:
    """Unit tests for LogoutController.logout()."""

    def test_returns_200(self, app):
        """logout() returns HTTP 200."""
        with app.test_request_context("/api/logout", method="POST"):
            response, status = _unpack(LogoutController.logout())
        assert status == 200

    def test_response_has_ok_status(self, app):
        """Response body contains status=ok."""
        with app.test_request_context("/api/logout", method="POST"):
            response, _ = _unpack(LogoutController.logout())
        data = response.get_json()
        assert data["status"] == "ok"

    def test_response_is_json(self, app):
        """Response content type is application/json."""
        with app.test_request_context("/api/logout", method="POST"):
            response, _ = _unpack(LogoutController.logout())
        assert "application/json" in response.content_type
