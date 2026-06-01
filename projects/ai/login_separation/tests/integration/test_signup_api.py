"""
test_signup_api.py — Integration tests for POST /api/signup.

Tests exercise the full Flask request/response stack via the test client.
"""

import pytest
import bcrypt

from app.database import db
from app.models import User


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
    """Seed a user with a known email."""
    from app import models as _models  # noqa: F401
    with app.app_context():
        db.create_all()
        hashed = bcrypt.hashpw(b"Password1!", bcrypt.gensalt(rounds=4)).decode()
        user = User(email="existing@example.com", full_name="Existing", hashed_password=hashed)
        db.session.add(user)
        db.session.commit()
        yield user
        db.session.remove()
        db.drop_all()


# ---------------------------------------------------------------------------
# Integration tests: POST /api/signup
# ---------------------------------------------------------------------------

class TestSignupEndpoint:

    def test_valid_signup_returns_201(self, db_client):
        response = db_client.post(
            "/api/signup",
            json={"full_name": "Alice", "email": "alice@example.com", "password": "Password1!"},
        )
        assert response.status_code == 201

    def test_valid_signup_returns_token(self, db_client):
        response = db_client.post(
            "/api/signup",
            json={"full_name": "Alice", "email": "alice@example.com", "password": "Password1!"},
        )
        assert "token" in response.get_json()

    def test_missing_email_returns_400(self, db_client):
        response = db_client.post(
            "/api/signup",
            json={"full_name": "Alice", "password": "Password1!"},
        )
        assert response.status_code == 400

    def test_missing_password_returns_400(self, db_client):
        response = db_client.post(
            "/api/signup",
            json={"full_name": "Alice", "email": "alice@example.com"},
        )
        assert response.status_code == 400

    def test_missing_full_name_returns_400(self, db_client):
        response = db_client.post(
            "/api/signup",
            json={"email": "alice@example.com", "password": "Password1!"},
        )
        assert response.status_code == 400

    def test_invalid_email_returns_400(self, db_client):
        response = db_client.post(
            "/api/signup",
            json={"full_name": "Alice", "email": "notanemail", "password": "Password1!"},
        )
        assert response.status_code == 400

    def test_weak_password_returns_400(self, db_client):
        response = db_client.post(
            "/api/signup",
            json={"full_name": "Alice", "email": "alice@example.com", "password": "short"},
        )
        assert response.status_code == 400

    def test_duplicate_email_returns_409(self, db_client, existing_user):
        response = db_client.post(
            "/api/signup",
            json={"full_name": "Other", "email": "existing@example.com", "password": "Password1!"},
        )
        assert response.status_code == 409

    def test_content_type_is_json(self, db_client):
        response = db_client.post(
            "/api/signup",
            json={"full_name": "Alice", "email": "alice@example.com", "password": "Password1!"},
        )
        assert "application/json" in response.content_type
