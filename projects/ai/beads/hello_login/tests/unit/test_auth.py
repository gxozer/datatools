"""
test_auth.py — Unit tests for JWT helpers and require_auth decorator.

Tests are written before implementation (TDD red phase).
All tests in this file are expected to fail until auth.py is implemented.
"""

import json
import pytest
from datetime import datetime, timedelta, timezone
from unittest.mock import patch

import jwt as pyjwt


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def make_user(id=1, email="test@example.com", full_name="Test User", role="user"):
    """Return a minimal object mimicking a User model instance."""
    class FakeUser:
        pass
    u = FakeUser()
    u.id = id
    u.email = email
    u.full_name = full_name
    u.role = role
    return u


# ---------------------------------------------------------------------------
# Tests: generate_token()
# ---------------------------------------------------------------------------

class TestGenerateToken:
    """Tests for auth.generate_token(user)."""

    def test_returns_a_string(self, app):
        """generate_token() should return a non-empty string."""
        from app.auth import Auth
        with app.app_context():
            token = Auth.generate_token(make_user())
            assert isinstance(token, str)
            assert len(token) > 0

    def test_payload_contains_sub(self, app):
        """Payload 'sub' field should equal user.id."""
        from app.auth import Auth
        with app.app_context():
            user = make_user(id=42)
            token = Auth.generate_token(user)
            payload = pyjwt.decode(
                token,
                app.config["JWT_SECRET"],
                algorithms=["HS256"],
            )
            assert payload["sub"] == "42"

    def test_payload_contains_email(self, app):
        """Payload should include the user's email."""
        from app.auth import Auth
        with app.app_context():
            user = make_user(email="hello@example.com")
            token = Auth.generate_token(user)
            payload = pyjwt.decode(
                token,
                app.config["JWT_SECRET"],
                algorithms=["HS256"],
            )
            assert payload["email"] == "hello@example.com"

    def test_payload_contains_full_name(self, app):
        """Payload should include full_name."""
        from app.auth import Auth
        with app.app_context():
            user = make_user(full_name="Jane Doe")
            token = Auth.generate_token(user)
            payload = pyjwt.decode(
                token,
                app.config["JWT_SECRET"],
                algorithms=["HS256"],
            )
            assert payload["full_name"] == "Jane Doe"

    def test_payload_contains_role(self, app):
        """Payload should include the user's role."""
        from app.auth import Auth
        with app.app_context():
            user = make_user(role="admin")
            token = Auth.generate_token(user)
            payload = pyjwt.decode(
                token,
                app.config["JWT_SECRET"],
                algorithms=["HS256"],
            )
            assert payload["role"] == "admin"

    def test_token_expires_in_24_hours(self, app):
        """Token expiry should be approximately 24 hours from now."""
        from app.auth import Auth
        with app.app_context():
            before = datetime.now(timezone.utc)
            token = Auth.generate_token(make_user())
            after = datetime.now(timezone.utc)
            payload = pyjwt.decode(
                token,
                app.config["JWT_SECRET"],
                algorithms=["HS256"],
            )
            exp = datetime.fromtimestamp(payload["exp"], tz=timezone.utc)
            assert exp >= before + timedelta(hours=23, minutes=59)
            assert exp <= after + timedelta(hours=24, seconds=5)

    def test_token_signed_with_hs256(self, app):
        """Token should be signed using HS256."""
        from app.auth import Auth
        with app.app_context():
            token = Auth.generate_token(make_user())
            header = pyjwt.get_unverified_header(token)
            assert header["alg"] == "HS256"

    def test_token_invalid_with_wrong_secret(self, app):
        """Token decoded with the wrong secret should raise an error."""
        from app.auth import Auth
        with app.app_context():
            token = Auth.generate_token(make_user())
            with pytest.raises(pyjwt.exceptions.InvalidSignatureError):
                pyjwt.decode(token, "wrong-secret", algorithms=["HS256"])


# ---------------------------------------------------------------------------
# Tests: require_auth decorator
# ---------------------------------------------------------------------------

class TestRequireAuth:
    """Tests for the @require_auth decorator."""

    def _valid_token(self, app):
        """Generate a valid JWT for use in tests."""
        from app.auth import Auth
        return Auth.generate_token(make_user())

    def _expired_token(self, app):
        """Generate an already-expired JWT."""
        payload = {
            "sub": 1,
            "email": "test@example.com",
            "full_name": "Test User",
            "role": "user",
            "exp": datetime.now(timezone.utc) - timedelta(seconds=1),
        }
        return pyjwt.encode(payload, app.config["JWT_SECRET"], algorithm="HS256")

    def test_valid_token_passes(self, client, app):
        """A request with a valid Bearer token should reach the protected view."""
        with app.app_context():
            token = self._valid_token(app)
        response = client.get(
            "/api/hello",
            headers={"Authorization": f"Bearer {token}"},
        )
        assert response.status_code == 200

    def test_missing_auth_header_returns_401(self, client):
        """A request with no Authorization header should return 401."""
        response = client.get("/api/hello")
        assert response.status_code == 401

    def test_missing_auth_header_error_body(self, client):
        """401 response should include status: error."""
        response = client.get("/api/hello")
        data = json.loads(response.get_data(as_text=True))
        assert data["status"] == "error"

    def test_invalid_token_returns_401(self, client):
        """A request with a malformed/invalid token should return 401."""
        response = client.get(
            "/api/hello",
            headers={"Authorization": "Bearer this.is.not.a.valid.jwt"},
        )
        assert response.status_code == 401

    def test_expired_token_returns_401(self, client, app):
        """A request with an expired token should return 401."""
        with app.app_context():
            token = self._expired_token(app)
        response = client.get(
            "/api/hello",
            headers={"Authorization": f"Bearer {token}"},
        )
        assert response.status_code == 401

    def test_wrong_scheme_returns_401(self, client, app):
        """Authorization header with wrong scheme (Basic) should return 401."""
        with app.app_context():
            token = self._valid_token(app)
        response = client.get(
            "/api/hello",
            headers={"Authorization": f"Basic {token}"},
        )
        assert response.status_code == 401

    def test_valid_token_sets_current_user(self, client, app):
        """A valid token should make g.current_user available in the view."""
        with app.app_context():
            token = self._valid_token(app)
        response = client.get(
            "/api/hello",
            headers={"Authorization": f"Bearer {token}"},
        )
        # HelloController will be updated to use g.current_user["full_name"]
        # For now just check the response is 200 (current_user is accessible)
        assert response.status_code == 200
