"""
test_login_api.py — Integration tests for POST /api/login.

Tests exercise the full Flask request/response stack via the test client.
All tests are expected to fail until LoginController is implemented (TDD red phase).
"""

import json
import pytest
from datetime import datetime, timedelta, timezone

import bcrypt

from app.database import db
from app.models import User, LoginAttempt


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
        yield {"email": "user@example.com", "password": "password1"}
        db.session.remove()
        db.drop_all()


# ---------------------------------------------------------------------------
# Integration tests: POST /api/login
# ---------------------------------------------------------------------------

class TestLoginEndpoint:
    """Integration tests for POST /api/login."""

    def test_valid_credentials_returns_200(self, db_client, existing_user):
        """Valid credentials should return 200."""
        response = db_client.post(
            "/api/login",
            json={"email": existing_user["email"], "password": existing_user["password"]},
        )
        assert response.status_code == 200

    def test_valid_credentials_returns_token(self, db_client, existing_user):
        """Valid credentials should return a JWT token."""
        response = db_client.post(
            "/api/login",
            json={"email": existing_user["email"], "password": existing_user["password"]},
        )
        data = json.loads(response.data)
        assert "token" in data
        assert len(data["token"]) > 0

    def test_valid_credentials_returns_status_ok(self, db_client, existing_user):
        """Valid credentials response should contain status: ok."""
        response = db_client.post(
            "/api/login",
            json={"email": existing_user["email"], "password": existing_user["password"]},
        )
        data = json.loads(response.data)
        assert data["status"] == "ok"

    def test_wrong_password_returns_401(self, db_client, existing_user):
        """Wrong password should return 401."""
        response = db_client.post(
            "/api/login",
            json={"email": existing_user["email"], "password": "wrongpassword"},
        )
        assert response.status_code == 401

    def test_unknown_email_returns_401(self, db_client, existing_user):
        """Unknown email should return 401."""
        response = db_client.post(
            "/api/login",
            json={"email": "nobody@example.com", "password": "password1"},
        )
        assert response.status_code == 401

    def test_locked_after_5_failures_returns_429(self, app, db_client, existing_user):
        """Account should be locked after 5 failed attempts within 15 minutes."""
        with app.app_context():
            user = db.session.query(User).filter_by(email=existing_user["email"]).first()
            for _ in range(5):
                attempt = LoginAttempt(
                    email=existing_user["email"],
                    user_id=user.id if user else None,
                    success=False,
                    attempted_at=datetime.now(timezone.utc) - timedelta(minutes=1),
                )
                db.session.add(attempt)
            db.session.commit()

        response = db_client.post(
            "/api/login",
            json={"email": existing_user["email"], "password": existing_user["password"]},
        )
        assert response.status_code == 429

    def test_missing_email_returns_400(self, db_client):
        """Missing email field should return 400."""
        response = db_client.post("/api/login", json={"password": "password1"})
        assert response.status_code == 400

    def test_missing_password_returns_400(self, db_client):
        """Missing password field should return 400."""
        response = db_client.post("/api/login", json={"email": "user@example.com"})
        assert response.status_code == 400

    def test_content_type_is_json(self, db_client, existing_user):
        """Response should have JSON content type."""
        response = db_client.post(
            "/api/login",
            json={"email": existing_user["email"], "password": existing_user["password"]},
        )
        assert "application/json" in response.content_type
