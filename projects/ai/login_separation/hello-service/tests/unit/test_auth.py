"""
test_auth.py — Unit tests for the slim require_auth decorator in hello-service.

Tests cover: missing header, invalid token, expired token, missing jti,
wrong role, and valid token with correct role.
"""

import uuid
import pytest
from datetime import datetime, timedelta, timezone
from unittest.mock import MagicMock

import jwt as pyjwt

from app.auth import Auth


def _make_token(secret, role="user", include_jti=True, expired=False, invalid=False):
    """Build a JWT for testing."""
    if invalid:
        return "not-a-valid-token"
    now = datetime.now(timezone.utc)
    payload = {
        "sub": "1",
        "email": "test@example.com",
        "full_name": "Test User",
        "role": role,
        "exp": now - timedelta(hours=1) if expired else now + timedelta(hours=1),
        "iat": now,
    }
    if include_jti:
        payload["jti"] = str(uuid.uuid4())
    return pyjwt.encode(payload, secret, algorithm="HS256")


class TestRequireAuth:
    """Tests for Auth.require_auth decorator."""

    def test_missing_auth_header_returns_401(self, app, client):
        """Request with no Authorization header should return 401."""
        response = client.get("/api/hello")
        assert response.status_code == 401

    def test_non_bearer_scheme_returns_401(self, app, client):
        """Authorization header that doesn't start with 'Bearer ' returns 401."""
        response = client.get("/api/hello", headers={"Authorization": "Basic abc123"})
        assert response.status_code == 401

    def test_invalid_token_returns_401(self, app, client):
        """Malformed token returns 401."""
        response = client.get("/api/hello", headers={"Authorization": "Bearer not-a-token"})
        assert response.status_code == 401

    def test_expired_token_returns_401(self, app, client):
        """Expired JWT returns 401."""
        token = _make_token(app.config["JWT_SECRET"], expired=True)
        response = client.get("/api/hello", headers={"Authorization": f"Bearer {token}"})
        assert response.status_code == 401

    def test_missing_jti_returns_401(self, app, client):
        """JWT without 'jti' claim returns 401."""
        token = _make_token(app.config["JWT_SECRET"], include_jti=False)
        response = client.get("/api/hello", headers={"Authorization": f"Bearer {token}"})
        assert response.status_code == 401

    def test_wrong_role_returns_403(self, app, client):
        """Valid JWT with a role not in allowed_roles returns 403."""
        token = _make_token(app.config["JWT_SECRET"], role="guest")
        response = client.get("/api/hello", headers={"Authorization": f"Bearer {token}"})
        assert response.status_code == 403

    def test_valid_token_with_allowed_role_returns_200(self, app, client):
        """Valid JWT with role 'user' should call the wrapped function and return 200."""
        token = _make_token(app.config["JWT_SECRET"], role="user")
        response = client.get("/api/hello", headers={"Authorization": f"Bearer {token}"})
        assert response.status_code == 200

    def test_valid_token_with_admin_role_returns_200(self, app, client):
        """Valid JWT with role 'admin' should also be allowed."""
        token = _make_token(app.config["JWT_SECRET"], role="admin")
        response = client.get("/api/hello", headers={"Authorization": f"Bearer {token}"})
        assert response.status_code == 200
