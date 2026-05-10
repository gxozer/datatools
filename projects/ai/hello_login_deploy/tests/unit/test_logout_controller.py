"""
test_logout_controller.py — Unit tests for LogoutController.
"""

import uuid
import pytest
from flask import g

from app.auth_controllers import LogoutController
from app.auth import Auth
from app.models import User
import bcrypt


def _unpack(result):
    if isinstance(result, tuple):
        return result[0], result[1]
    return result, result.status_code


def _make_user(db_session):
    user = User(
        full_name="Test User",
        email="test@example.com",
        hashed_password=bcrypt.hashpw(b"password", bcrypt.gensalt()).decode(),
        role="user",
    )
    db_session.add(user)
    db_session.flush()
    return user


class TestLogoutController:
    def test_returns_200(self, app, db_session):
        with app.test_request_context("/api/logout", method="POST"):
            g.current_user = {"jti": str(uuid.uuid4()), "exp": 9999999999, "sub": "1"}
            response, status = _unpack(LogoutController.logout())
        assert status == 200

    def test_response_has_ok_status(self, app, db_session):
        with app.test_request_context("/api/logout", method="POST"):
            g.current_user = {"jti": str(uuid.uuid4()), "exp": 9999999999, "sub": "1"}
            response, _ = _unpack(LogoutController.logout())
        data = response.get_json()
        assert data["status"] == "ok"

    def test_response_is_json(self, app, db_session):
        with app.test_request_context("/api/logout", method="POST"):
            g.current_user = {"jti": str(uuid.uuid4()), "exp": 9999999999, "sub": "1"}
            response, _ = _unpack(LogoutController.logout())
        assert "application/json" in response.content_type


class TestTokenDenylist:
    def test_token_rejected_after_logout(self, client, db_session):
        """A token used to log out is rejected on subsequent requests."""
        user = _make_user(db_session)
        token = Auth.generate_token(user)

        resp = client.post(
            "/api/logout",
            headers={"Authorization": f"Bearer {token}"},
        )
        assert resp.status_code == 200

        resp = client.get(
            "/api/hello",
            headers={"Authorization": f"Bearer {token}"},
        )
        assert resp.status_code == 401
        assert resp.get_json()["error"] == "Token has been revoked"

    def test_different_token_still_valid_after_logout(self, client, db_session):
        """Logging out one token does not invalidate other tokens."""
        user = _make_user(db_session)
        token_a = Auth.generate_token(user)
        token_b = Auth.generate_token(user)

        client.post("/api/logout", headers={"Authorization": f"Bearer {token_a}"})

        resp = client.get(
            "/api/hello",
            headers={"Authorization": f"Bearer {token_b}"},
        )
        assert resp.status_code == 200
