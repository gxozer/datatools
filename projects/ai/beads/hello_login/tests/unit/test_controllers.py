"""
test_controllers.py — Unit tests for HelloController and HealthController.

Tests call controller methods directly inside a Flask app context so that
flask.jsonify works, but without going through the HTTP stack.

HelloController tests are written for the updated implementation (beads3-9td):
- hello() reads g.current_user and returns a personalized greeting.
- Tests in TestHelloController will fail until beads3-9td is implemented (TDD red phase).
"""

import json
import pytest
from flask import g
from app.controllers import HelloController, HealthController


@pytest.fixture
def app_context(app):
    """Push a Flask application context so jsonify is available."""
    with app.app_context():
        yield app


def _push_user_context(app_context, full_name="Test User", email="test@example.com"):
    """Push a request context with g.current_user pre-populated."""
    ctx = app_context.test_request_context()
    ctx.push()
    g.current_user = {"sub": "1", "email": email, "full_name": full_name, "role": "user"}
    return ctx


class TestHelloController:
    """Unit tests for HelloController.hello() with auth context.

    All tests here will fail until beads3-9td updates HelloController to read
    g.current_user and return a personalized greeting.
    """

    def test_hello_returns_200(self, app_context):
        """hello() with a user context should return 200."""
        ctx = _push_user_context(app_context)
        try:
            response = HelloController.hello()
            assert response.status_code == 200
        finally:
            ctx.pop()

    def test_hello_returns_personalized_message(self, app_context):
        """hello() should greet the authenticated user by name."""
        ctx = _push_user_context(app_context, full_name="Alice")
        try:
            response = HelloController.hello()
            data = json.loads(response.get_data(as_text=True))
            assert data["message"] == "Hello, Alice!"
        finally:
            ctx.pop()

    def test_hello_message_field_present(self, app_context):
        """hello() response body should contain a 'message' field."""
        ctx = _push_user_context(app_context)
        try:
            response = HelloController.hello()
            data = json.loads(response.get_data(as_text=True))
            assert "message" in data
        finally:
            ctx.pop()

    def test_hello_status_field(self, app_context):
        """hello() response body should contain status: 'ok'."""
        ctx = _push_user_context(app_context)
        try:
            response = HelloController.hello()
            data = json.loads(response.get_data(as_text=True))
            assert data["status"] == "ok"
        finally:
            ctx.pop()


class TestHealthController:
    """Unit tests for HealthController.health()."""

    def test_health_returns_200(self, app_context):
        """health() should return an HTTP 200 response."""
        with app_context.test_request_context():
            response = HealthController.health()
            assert response.status_code == 200

    def test_health_status_field(self, app_context):
        """health() response body should contain status: 'ok'."""
        with app_context.test_request_context():
            response = HealthController.health()
            data = json.loads(response.get_data(as_text=True))
            assert data["status"] == "ok"
