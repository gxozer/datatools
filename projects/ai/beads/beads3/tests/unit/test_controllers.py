"""
test_controllers.py — Unit tests for HelloController and HealthController.

Tests call controller methods directly inside a Flask app context so that
flask.jsonify works, but without going through the HTTP stack.
"""

import json
import pytest
from app import create_app
from app.controllers import HelloController, HealthController


@pytest.fixture
def app_context():
    """Push a Flask application context so jsonify is available."""
    flask_app = create_app()
    with flask_app.app_context():
        yield flask_app


class TestHelloController:
    """Unit tests for HelloController.hello()."""

    def test_hello_returns_200(self, app_context):
        """hello() should return an HTTP 200 response."""
        with app_context.test_request_context():
            response = HelloController.hello()
            assert response.status_code == 200

    def test_hello_message_field(self, app_context):
        """hello() response body should contain a 'message' field."""
        with app_context.test_request_context():
            response = HelloController.hello()
            data = json.loads(response.get_data(as_text=True))
            assert "message" in data
            assert data["message"] == "Hello, World!"

    def test_hello_status_field(self, app_context):
        """hello() response body should contain status: 'ok'."""
        with app_context.test_request_context():
            response = HelloController.hello()
            data = json.loads(response.get_data(as_text=True))
            assert data["status"] == "ok"


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
