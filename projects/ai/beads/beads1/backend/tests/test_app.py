import pytest


class TestHelloEndpoint:
    """Unit tests for the GET /api/hello endpoint."""

    def test_returns_200(self, client):
        response = client.get("/api/hello")
        assert response.status_code == 200

    def test_returns_hello_world_message(self, client):
        response = client.get("/api/hello")
        assert response.get_json() == {"message": "Hello, World!"}

    def test_content_type_is_json(self, client):
        response = client.get("/api/hello")
        assert response.content_type == "application/json"

    def test_cors_header_present(self, client):
        response = client.get("/api/hello")
        assert response.headers.get("Access-Control-Allow-Origin") == "*"
