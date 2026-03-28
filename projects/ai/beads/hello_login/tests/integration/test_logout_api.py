"""
test_logout_api.py — Integration tests for POST /api/logout.

Tests exercise the full Flask request/response stack via the test client,
including JWT validation by Auth.require_auth.
"""

import pytest

from app.auth import Auth
from app.database import db
from app.models import User

import bcrypt


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
def valid_token(app):
    """Create a test user and return a valid JWT for them."""
    from app import models as _models  # noqa: F401
    with app.app_context():
        db.create_all()
        hashed = bcrypt.hashpw(b"password1", bcrypt.gensalt(rounds=4)).decode()
        user = User(email="user@example.com", full_name="Test User", hashed_password=hashed)
        db.session.add(user)
        db.session.commit()
        token = Auth.generate_token(user)
        yield token
        db.session.remove()
        db.drop_all()


# ---------------------------------------------------------------------------
# Integration tests: POST /api/logout
# ---------------------------------------------------------------------------

class TestLogoutEndpoint:
    """Integration tests for POST /api/logout."""

    def test_valid_jwt_returns_200(self, db_client, valid_token):
        """Valid JWT in Authorization header returns 200."""
        response = db_client.post(
            "/api/logout",
            headers={"Authorization": f"Bearer {valid_token}"},
        )
        assert response.status_code == 200

    def test_valid_jwt_returns_ok_status(self, db_client, valid_token):
        """Response body contains status=ok."""
        response = db_client.post(
            "/api/logout",
            headers={"Authorization": f"Bearer {valid_token}"},
        )
        assert response.get_json()["status"] == "ok"

    def test_no_token_returns_401(self, db_client):
        """Missing Authorization header returns 401."""
        response = db_client.post("/api/logout")
        assert response.status_code == 401

    def test_invalid_token_returns_401(self, db_client):
        """Malformed token returns 401."""
        response = db_client.post(
            "/api/logout",
            headers={"Authorization": "Bearer not-a-real-token"},
        )
        assert response.status_code == 401

    def test_content_type_is_json(self, db_client, valid_token):
        """Response content type is application/json."""
        response = db_client.post(
            "/api/logout",
            headers={"Authorization": f"Bearer {valid_token}"},
        )
        assert "application/json" in response.content_type
