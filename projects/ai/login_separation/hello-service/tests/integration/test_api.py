"""
test_api.py — Integration tests for the hello-service Flask API endpoints.

These tests exercise the full Flask request/response stack using the test
client — no mocking of controllers or routes.
"""

import json
import uuid
from datetime import datetime, timedelta, timezone

import jwt as pyjwt


def _make_token(app, full_name="Test User", email="test@example.com", role="user"):
    """Generate a valid JWT for testing using the app's JWT_SECRET."""
    payload = {
        "jti": str(uuid.uuid4()),
        "iat": datetime.now(timezone.utc),
        "sub": "1",
        "email": email,
        "full_name": full_name,
        "role": role,
        "exp": datetime.now(timezone.utc) + timedelta(hours=1),
    }
    return pyjwt.encode(payload, app.config["JWT_SECRET"], algorithm="HS256")


def _expired_token(app):
    """Generate an expired JWT for testing."""
    payload = {
        "jti": str(uuid.uuid4()),
        "sub": "1",
        "email": "test@example.com",
        "full_name": "Test User",
        "role": "user",
        "exp": datetime.now(timezone.utc) - timedelta(hours=1),
    }
    return pyjwt.encode(payload, app.config["JWT_SECRET"], algorithm="HS256")


class TestHelloEndpoint:
    """Integration tests for GET /api/hello."""

    def test_no_auth_returns_401(self, client):
        """GET /api/hello without Authorization header should return 401."""
        response = client.get("/api/hello")
        assert response.status_code == 401

    def test_invalid_token_returns_401(self, client):
        """GET /api/hello with a malformed token should return 401."""
        response = client.get("/api/hello", headers={"Authorization": "Bearer not-a-token"})
        assert response.status_code == 401

    def test_valid_jwt_with_user_role_returns_200(self, app, client):
        """GET /api/hello with a valid JWT (role=user) should return 200."""
        token = _make_token(app)
        response = client.get("/api/hello", headers={"Authorization": f"Bearer {token}"})
        assert response.status_code == 200

    def test_valid_jwt_returns_greeting(self, app, client):
        """GET /api/hello should greet the user by name from the JWT payload."""
        token = _make_token(app, full_name="Alice")
        response = client.get("/api/hello", headers={"Authorization": f"Bearer {token}"})
        data = json.loads(response.data)
        assert data["message"] == "Hello, Alice!"

    def test_valid_jwt_with_guest_role_returns_403(self, app, client):
        """GET /api/hello with role 'guest' should return 403."""
        token = _make_token(app, role="guest")
        response = client.get("/api/hello", headers={"Authorization": f"Bearer {token}"})
        assert response.status_code == 403

    def test_expired_token_returns_401(self, app, client):
        """GET /api/hello with an expired JWT should return 401."""
        token = _expired_token(app)
        response = client.get("/api/hello", headers={"Authorization": f"Bearer {token}"})
        assert response.status_code == 401


class TestHealthEndpoint:
    """Integration tests for GET /api/health."""

    def test_returns_200(self, client):
        """GET /api/health should return HTTP 200."""
        response = client.get("/api/health")
        assert response.status_code == 200

    def test_status_field(self, client):
        """GET /api/health body should contain status: 'ok'."""
        response = client.get("/api/health")
        data = json.loads(response.data)
        assert data["status"] == "ok"
