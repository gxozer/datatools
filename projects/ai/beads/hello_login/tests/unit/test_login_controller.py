"""
test_login_controller.py — Unit tests for LoginController.

Tests call LoginController methods directly inside a Flask app+db context.
All tests are expected to fail until LoginController is implemented (TDD red phase).
"""

import json
import pytest
from datetime import datetime, timedelta, timezone

import bcrypt

from app.auth_controllers import LoginController
from app.database import db
from app.models import User, LoginAttempt


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _unpack(result):
    """Unpack a controller return value into (response, status_code).

    Controllers return (jsonify(...), status_code) tuples. When called
    directly (not via the HTTP stack), we receive the raw tuple.
    """
    if isinstance(result, tuple):
        return result[0], result[1]
    return result, result.status_code


def _hashed(password: str) -> str:
    return bcrypt.hashpw(password.encode(), bcrypt.gensalt(rounds=4)).decode()


def _create_user(session, email="user@example.com", password="password1", full_name="Test User"):
    user = User(
        email=email,
        full_name=full_name,
        hashed_password=_hashed(password),
    )
    session.add(user)
    session.commit()
    return user


def _add_failed_attempts(session, email, count, minutes_ago=1):
    """Add `count` failed login attempts for the given email."""
    user = session.query(User).filter_by(email=email).first()
    user_id = user.id if user else None
    for _ in range(count):
        attempt = LoginAttempt(
            email=email,
            user_id=user_id,
            success=False,
            attempted_at=datetime.now(timezone.utc) - timedelta(minutes=minutes_ago),
        )
        session.add(attempt)
    session.commit()


# ---------------------------------------------------------------------------
# Unit tests: LoginController
# ---------------------------------------------------------------------------

class TestLoginControllerUnit:
    """Unit tests for LoginController.login() called directly."""

    def test_valid_credentials_returns_200(self, app, db_session):
        """Valid email and password should return 200."""
        _create_user(db_session)
        with app.test_request_context(
            "/api/login", method="POST",
            json={"email": "user@example.com", "password": "password1"},
        ):
            _, status = _unpack(LoginController.login())
            assert status == 200

    def test_valid_credentials_returns_token(self, app, db_session):
        """Valid credentials should return a JWT token in the response."""
        _create_user(db_session)
        with app.test_request_context(
            "/api/login", method="POST",
            json={"email": "user@example.com", "password": "password1"},
        ):
            response, _ = _unpack(LoginController.login())
            data = json.loads(response.get_data(as_text=True))
            assert "token" in data
            assert isinstance(data["token"], str)
            assert len(data["token"]) > 0

    def test_valid_credentials_returns_status_ok(self, app, db_session):
        """Valid credentials response should contain status: ok."""
        _create_user(db_session)
        with app.test_request_context(
            "/api/login", method="POST",
            json={"email": "user@example.com", "password": "password1"},
        ):
            response, _ = _unpack(LoginController.login())
            data = json.loads(response.get_data(as_text=True))
            assert data["status"] == "ok"

    def test_wrong_password_returns_401(self, app, db_session):
        """Wrong password should return 401."""
        _create_user(db_session)
        with app.test_request_context(
            "/api/login", method="POST",
            json={"email": "user@example.com", "password": "wrongpassword"},
        ):
            _, status = _unpack(LoginController.login())
            assert status == 401

    def test_wrong_password_error_body(self, app, db_session):
        """Wrong password response should contain status: error."""
        _create_user(db_session)
        with app.test_request_context(
            "/api/login", method="POST",
            json={"email": "user@example.com", "password": "wrongpassword"},
        ):
            response, _ = _unpack(LoginController.login())
            data = json.loads(response.get_data(as_text=True))
            assert data["status"] == "error"

    def test_unknown_email_returns_401(self, app, db_session):
        """Unknown email should return 401."""
        with app.test_request_context(
            "/api/login", method="POST",
            json={"email": "nobody@example.com", "password": "password1"},
        ):
            _, status = _unpack(LoginController.login())
            assert status == 401

    def test_unknown_email_same_error_as_wrong_password(self, app, db_session):
        """Unknown email and wrong password should return the same error message (prevents enumeration)."""
        _create_user(db_session)
        with app.test_request_context(
            "/api/login", method="POST",
            json={"email": "nobody@example.com", "password": "password1"},
        ):
            r1, _ = _unpack(LoginController.login())
            d1 = json.loads(r1.get_data(as_text=True))

        with app.test_request_context(
            "/api/login", method="POST",
            json={"email": "user@example.com", "password": "wrongpassword"},
        ):
            r2, _ = _unpack(LoginController.login())
            d2 = json.loads(r2.get_data(as_text=True))

        assert d1["error"] == d2["error"]

    def test_locked_account_returns_429(self, app, db_session):
        """Account with 5+ recent failed attempts should return 429."""
        _create_user(db_session)
        _add_failed_attempts(db_session, "user@example.com", 5)
        with app.test_request_context(
            "/api/login", method="POST",
            json={"email": "user@example.com", "password": "password1"},
        ):
            _, status = _unpack(LoginController.login())
            assert status == 429

    def test_locked_account_error_body(self, app, db_session):
        """Locked account response should contain status: error."""
        _create_user(db_session)
        _add_failed_attempts(db_session, "user@example.com", 5)
        with app.test_request_context(
            "/api/login", method="POST",
            json={"email": "user@example.com", "password": "password1"},
        ):
            response, _ = _unpack(LoginController.login())
            data = json.loads(response.get_data(as_text=True))
            assert data["status"] == "error"

    def test_old_failed_attempts_do_not_lock(self, app, db_session):
        """Failed attempts older than 15 minutes should not trigger lockout."""
        _create_user(db_session)
        _add_failed_attempts(db_session, "user@example.com", 5, minutes_ago=20)
        with app.test_request_context(
            "/api/login", method="POST",
            json={"email": "user@example.com", "password": "password1"},
        ):
            _, status = _unpack(LoginController.login())
            assert status == 200

    def test_successful_login_records_attempt(self, app, db_session):
        """Successful login should record a successful LoginAttempt."""
        _create_user(db_session)
        with app.test_request_context(
            "/api/login", method="POST",
            json={"email": "user@example.com", "password": "password1"},
        ):
            LoginController.login()
        attempt = db_session.query(LoginAttempt).filter_by(email="user@example.com").first()
        assert attempt is not None
        assert attempt.success is True

    def test_failed_login_records_attempt(self, app, db_session):
        """Failed login should record a failed LoginAttempt."""
        _create_user(db_session)
        with app.test_request_context(
            "/api/login", method="POST",
            json={"email": "user@example.com", "password": "wrongpassword"},
        ):
            LoginController.login()
        attempt = db_session.query(LoginAttempt).filter_by(email="user@example.com").first()
        assert attempt is not None
        assert attempt.success is False
