import pytest
import requests


class TestHelloEndpointIntegration:
    """Integration tests for GET /api/hello using a live Flask server."""

    def test_full_http_get_returns_200(self, live_server):
        response = requests.get(f"{live_server.url()}/api/hello")
        assert response.status_code == 200

    def test_full_http_get_returns_correct_json(self, live_server):
        response = requests.get(f"{live_server.url()}/api/hello")
        assert response.json() == {"message": "Hello, World!"}

    def test_content_type_is_json(self, live_server):
        response = requests.get(f"{live_server.url()}/api/hello")
        assert "application/json" in response.headers["Content-Type"]

    def test_cors_header_present_on_real_http_response(self, live_server):
        response = requests.get(
            f"{live_server.url()}/api/hello",
            headers={"Origin": "http://localhost:5173"},
        )
        assert response.headers.get("Access-Control-Allow-Origin") is not None

    def test_options_preflight_returns_correct_headers(self, live_server):
        response = requests.options(
            f"{live_server.url()}/api/hello",
            headers={
                "Origin": "http://localhost:5173",
                "Access-Control-Request-Method": "GET",
            },
        )
        assert response.status_code in (200, 204)
        assert response.headers.get("Access-Control-Allow-Origin") is not None
