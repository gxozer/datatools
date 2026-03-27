"""
test_password_reset_api.py — Integration tests for password reset endpoints.

Tests exercise the full Flask request/response stack via the test client.
All tests are expected to fail until PasswordResetController is implemented (TDD red phase).
"""

import hashlib
import json
import secrets
from datetime import datetime, timedelta, timezone

import bcrypt
import pytest

from app.database import db
from app.models import PasswordResetToken, User


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def db_client(app):
    """Test client with a fully initialised in-memory database."""
    from app import models as _models  # noqa: F401
    with app.app_context():
        db.create_all()
        yield app.test_client()
        db.session.remove()
        db.drop_all()


@pytest.fixture
def existing_user(app):
    """Create a test user and return their credentials."""
    from app import models as _models  # noqa: F401
    with app.app_context():
        db.create_all()
        hashed = bcrypt.hashpw(b"password1", bcrypt.gensalt(rounds=4)).decode()
        user = User(email="user@example.com", full_name="Test User", hashed_password=hashed)
        db.session.add(user)
        db.session.commit()
        yield {"email": "user@example.com", "password": "password1", "id": user.id}
        db.session.remove()
        db.drop_all()


def _seed_token(app, user_id, *, expired=False, used=False):
    """Seed a PasswordResetToken and return the raw token string."""
    raw = secrets.token_urlsafe(32)
    token_hash = hashlib.sha256(raw.encode()).hexdigest()
    expires_at = (
        datetime.now(timezone.utc) - timedelta(hours=1)
        if expired
        else datetime.now(timezone.utc) + timedelta(hours=1)
    )
    with app.app_context():
        token = PasswordResetToken(
            user_id=user_id,
            token_hash=token_hash,
            expires_at=expires_at,
            used=used,
        )
        db.session.add(token)
        db.session.commit()
    return raw


# ---------------------------------------------------------------------------
# Integration tests: POST /api/password-reset/request
# ---------------------------------------------------------------------------

class TestRequestResetEndpoint:
    """Integration tests for POST /api/password-reset/request."""

    def test_known_email_returns_200(self, db_client, existing_user):
        """Known email should return 200."""
        response = db_client.post(
            "/api/password-reset/request",
            json={"email": existing_user["email"]},
        )
        assert response.status_code == 200

    def test_unknown_email_returns_200(self, db_client):
        """Unknown email should also return 200 (no enumeration)."""
        response = db_client.post(
            "/api/password-reset/request",
            json={"email": "nobody@example.com"},
        )
        assert response.status_code == 200

    def test_known_and_unknown_same_body(self, db_client, existing_user):
        """Known and unknown emails should return identical response bodies."""
        r1 = db_client.post(
            "/api/password-reset/request",
            json={"email": existing_user["email"]},
        )
        r2 = db_client.post(
            "/api/password-reset/request",
            json={"email": "nobody@example.com"},
        )
        assert json.loads(r1.data) == json.loads(r2.data)

    def test_missing_email_returns_400(self, db_client):
        """Missing email field should return 400."""
        response = db_client.post("/api/password-reset/request", json={})
        assert response.status_code == 400

    def test_content_type_is_json(self, db_client, existing_user):
        """Response should have JSON content type."""
        response = db_client.post(
            "/api/password-reset/request",
            json={"email": existing_user["email"]},
        )
        assert "application/json" in response.content_type


# ---------------------------------------------------------------------------
# Integration tests: POST /api/password-reset/confirm
# ---------------------------------------------------------------------------

class TestConfirmResetEndpoint:
    """Integration tests for POST /api/password-reset/confirm."""

    def test_valid_token_returns_200(self, app, db_client, existing_user):
        """Valid token and new password should return 200."""
        raw = _seed_token(app, existing_user["id"])
        response = db_client.post(
            "/api/password-reset/confirm",
            json={"token": raw, "password": "newpassword1"},
        )
        assert response.status_code == 200

    def test_expired_token_returns_400(self, app, db_client, existing_user):
        """Expired token should return 400."""
        raw = _seed_token(app, existing_user["id"], expired=True)
        response = db_client.post(
            "/api/password-reset/confirm",
            json={"token": raw, "password": "newpassword1"},
        )
        assert response.status_code == 400

    def test_used_token_returns_400(self, app, db_client, existing_user):
        """Already-used token should return 400."""
        raw = _seed_token(app, existing_user["id"], used=True)
        response = db_client.post(
            "/api/password-reset/confirm",
            json={"token": raw, "password": "newpassword1"},
        )
        assert response.status_code == 400

    def test_invalid_token_returns_400(self, db_client):
        """Non-existent token should return 400."""
        response = db_client.post(
            "/api/password-reset/confirm",
            json={"token": "not-a-real-token", "password": "newpassword1"},
        )
        assert response.status_code == 400

    def test_missing_token_returns_400(self, db_client):
        """Missing token field should return 400."""
        response = db_client.post(
            "/api/password-reset/confirm",
            json={"password": "newpassword1"},
        )
        assert response.status_code == 400

    def test_missing_password_returns_400(self, app, db_client, existing_user):
        """Missing password field should return 400."""
        raw = _seed_token(app, existing_user["id"])
        response = db_client.post(
            "/api/password-reset/confirm",
            json={"token": raw},
        )
        assert response.status_code == 400

    def test_content_type_is_json(self, app, db_client, existing_user):
        """Response should have JSON content type."""
        raw = _seed_token(app, existing_user["id"])
        response = db_client.post(
            "/api/password-reset/confirm",
            json={"token": raw, "password": "newpassword1"},
        )
        assert "application/json" in response.content_type
