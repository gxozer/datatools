"""
test_api.py — Integration tests for the Flask API endpoints.

These tests exercise the full Flask request/response stack using the test
client — no mocking of controllers or routes. They verify that routing,
CORS headers, and JSON serialisation all work end-to-end.

TestHelloEndpoint has been updated for the auth-protected, personalized
greeting (beads3-9td). Tests that require a JWT will fail until
beads3-9td is implemented (TDD red phase).
"""

import json
from datetime import datetime, timedelta, timezone

import jwt as pyjwt


def _make_token(app, full_name="Test User", email="test@example.com"):
    """Generate a valid JWT for testing using the app's JWT_SECRET."""
    payload = {
        "sub": "1",
        "email": email,
        "full_name": full_name,
        "role": "user",
        "exp": datetime.now(timezone.utc) + timedelta(hours=1),
    }
    return pyjwt.encode(payload, app.config["JWT_SECRET"], algorithm="HS256")


def _expired_token(app):
    """Generate an expired JWT for testing."""
    payload = {
        "sub": "1",
        "email": "test@example.com",
        "full_name": "Test User",
        "role": "user",
        "exp": datetime.now(timezone.utc) - timedelta(hours=1),
    }
    return pyjwt.encode(payload, app.config["JWT_SECRET"], algorithm="HS256")


class TestHelloEndpoint:
    """Integration tests for GET /api/hello (auth-protected, personalized greeting).

    Tests without a JWT and tests asserting the personalized message will fail
    until beads3-9td is implemented (TDD red phase).
    """

    def test_without_jwt_returns_401(self, client):
        """GET /api/hello without Authorization header should return 401."""
        response = client.get("/api/hello")
        assert response.status_code == 401

    def test_with_valid_jwt_returns_200(self, app, client):
        """GET /api/hello with a valid JWT should return 200."""
        token = _make_token(app)
        response = client.get("/api/hello", headers={"Authorization": f"Bearer {token}"})
        assert response.status_code == 200

    def test_with_valid_jwt_returns_personalized_message(self, app, client):
        """GET /api/hello should greet the user by name from the JWT payload."""
        token = _make_token(app, full_name="Alice")
        response = client.get("/api/hello", headers={"Authorization": f"Bearer {token}"})
        data = json.loads(response.data)
        assert data["message"] == "Hello, Alice!"

    def test_with_valid_jwt_returns_status_ok(self, app, client):
        """GET /api/hello with a valid JWT should return status: ok."""
        token = _make_token(app)
        response = client.get("/api/hello", headers={"Authorization": f"Bearer {token}"})
        data = json.loads(response.data)
        assert data["status"] == "ok"

    def test_with_valid_jwt_content_type_is_json(self, app, client):
        """GET /api/hello with a valid JWT should return JSON content-type."""
        token = _make_token(app)
        response = client.get("/api/hello", headers={"Authorization": f"Bearer {token}"})
        assert "application/json" in response.content_type

    def test_with_expired_jwt_returns_401(self, app, client):
        """GET /api/hello with an expired JWT should return 401."""
        token = _expired_token(app)
        response = client.get("/api/hello", headers={"Authorization": f"Bearer {token}"})
        assert response.status_code == 401

    def test_with_invalid_jwt_returns_401(self, client):
        """GET /api/hello with a malformed token should return 401."""
        response = client.get("/api/hello", headers={"Authorization": "Bearer not-a-token"})
        assert response.status_code == 401


class TestHealthEndpoint:
    """Integration tests for GET /api/health."""

    def test_returns_200(self, client):
        """GET /api/health should return HTTP 200."""
        response = client.get("/api/health")
        assert response.status_code == 200

    def test_content_type_is_json(self, client):
        """GET /api/health should return a JSON content-type header."""
        response = client.get("/api/health")
        assert response.content_type == "application/json"

    def test_status_field(self, client):
        """GET /api/health body should contain status: 'ok'."""
        response = client.get("/api/health")
        data = json.loads(response.data)
        assert data["status"] == "ok"


class TestNotFound:
    """Integration tests for unregistered routes."""

    def test_unknown_route_returns_404(self, client):
        """Requests to undefined routes should return HTTP 404."""
        response = client.get("/api/unknown")
        assert response.status_code == 404
