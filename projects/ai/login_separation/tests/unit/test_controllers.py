"""
test_controllers.py — Unit tests for HealthController in login-service.

Tests call controller methods directly inside a Flask app context so that
flask.jsonify works, but without going through the HTTP stack.

Note: HelloController has moved to hello-service/tests/unit/test_controllers.py.
"""

import json
import pytest
from app.controllers import HealthController


@pytest.fixture
def app_context(app):
    """Push a Flask application context so jsonify is available."""
    with app.app_context():
        yield app


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
