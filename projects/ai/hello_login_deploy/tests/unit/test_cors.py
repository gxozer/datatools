"""Unit tests for CORS origin configuration."""

import pytest
from app import create_app


BASE_CONFIG = {
    "TESTING": True,
    "SQLALCHEMY_DATABASE_URI": "sqlite:///:memory:",
    "JWT_SECRET": "test-secret",
}



def test_cors_default_origins(monkeypatch):
    monkeypatch.delenv("CORS_ORIGINS", raising=False)
    app = create_app(BASE_CONFIG)
    with app.test_client() as client:
        resp = client.options("/api/health", headers={"Origin": "http://localhost:5173"})
        assert resp.headers.get("Access-Control-Allow-Origin") == "http://localhost:5173"


def test_cors_default_rejects_unknown_origin(monkeypatch):
    monkeypatch.delenv("CORS_ORIGINS", raising=False)
    app = create_app(BASE_CONFIG)
    with app.test_client() as client:
        resp = client.options("/api/health", headers={"Origin": "http://evil.com"})
        assert resp.headers.get("Access-Control-Allow-Origin") != "http://evil.com"


def test_cors_custom_origins(monkeypatch):
    monkeypatch.setenv("CORS_ORIGINS", "https://app.example.com,https://www.example.com")
    app = create_app(BASE_CONFIG)
    with app.test_client() as client:
        resp = client.options("/api/health", headers={"Origin": "https://app.example.com"})
        assert resp.headers.get("Access-Control-Allow-Origin") == "https://app.example.com"


def test_cors_custom_origins_strips_whitespace(monkeypatch):
    monkeypatch.setenv("CORS_ORIGINS", "https://app.example.com, https://www.example.com")
    app = create_app(BASE_CONFIG)
    with app.test_client() as client:
        resp = client.options("/api/health", headers={"Origin": "https://www.example.com"})
        assert resp.headers.get("Access-Control-Allow-Origin") == "https://www.example.com"


def test_cors_custom_rejects_default_localhost(monkeypatch):
    monkeypatch.setenv("CORS_ORIGINS", "https://app.example.com")
    app = create_app(BASE_CONFIG)
    with app.test_client() as client:
        resp = client.options("/api/health", headers={"Origin": "http://localhost:5173"})
        assert resp.headers.get("Access-Control-Allow-Origin") != "http://localhost:5173"
