"""
test_api.py — Integration tests for the login-service Flask API endpoints.

These tests exercise the full Flask request/response stack using the test
client — no mocking of controllers or routes. They verify that routing,
CORS headers, and JSON serialisation all work end-to-end.

Note: TestHelloEndpoint has moved to hello-service/tests/integration/test_api.py.
"""

import json

import jwt as pyjwt


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
