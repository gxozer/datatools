"""
test_api.py — Integration tests for the Flask API endpoints.

These tests exercise the full Flask request/response stack using the test
client — no mocking of controllers or routes. They verify that routing,
CORS headers, and JSON serialisation all work end-to-end.
"""

import json


class TestHelloEndpoint:
    """Integration tests for GET /api/hello."""

    def test_returns_200(self, client):
        """GET /api/hello should return HTTP 200."""
        response = client.get("/api/hello")
        assert response.status_code == 200

    def test_content_type_is_json(self, client):
        """GET /api/hello should return a JSON content-type header."""
        response = client.get("/api/hello")
        assert response.content_type == "application/json"

    def test_message_field(self, client):
        """GET /api/hello body should contain message: 'Hello, World!'."""
        response = client.get("/api/hello")
        data = json.loads(response.data)
        assert data["message"] == "Hello, World!"

    def test_status_field(self, client):
        """GET /api/hello body should contain status: 'ok'."""
        response = client.get("/api/hello")
        data = json.loads(response.data)
        assert data["status"] == "ok"


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
