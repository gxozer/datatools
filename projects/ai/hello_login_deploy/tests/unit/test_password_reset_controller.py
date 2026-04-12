"""
test_password_reset_controller.py — Unit tests for PasswordResetController.

Tests call PasswordResetController methods directly inside a Flask app+db context.
All tests are expected to fail until PasswordResetController is implemented (TDD red phase).
"""

import hashlib
import json
import secrets
from datetime import datetime, timedelta, timezone
from unittest.mock import patch

import bcrypt
import pytest

from app.auth_controllers import PasswordResetController
from app.database import db
from app.models import PasswordResetToken, User


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _hashed(password: str) -> str:
    return bcrypt.hashpw(password.encode(), bcrypt.gensalt(rounds=4)).decode()


def _create_user(session, email="user@example.com", password="password1"):
    user = User(
        email=email,
        full_name="Test User",
        hashed_password=_hashed(password),
    )
    session.add(user)
    session.commit()
    return user


def _create_reset_token(session, user, *, expired=False, used=False):
    """Create a PasswordResetToken and return the raw (unhashed) token string."""
    raw = secrets.token_urlsafe(32)
    token_hash = hashlib.sha256(raw.encode()).hexdigest()
    expires_at = (
        datetime.now(timezone.utc) - timedelta(hours=1)
        if expired
        else datetime.now(timezone.utc) + timedelta(hours=1)
    )
    token = PasswordResetToken(
        user_id=user.id,
        token_hash=token_hash,
        expires_at=expires_at,
        used=used,
    )
    session.add(token)
    session.commit()
    return raw


def _unpack(result):
    if isinstance(result, tuple):
        return result[0], result[1]
    return result, result.status_code


# ---------------------------------------------------------------------------
# Unit tests: PasswordResetController.request_reset()
# ---------------------------------------------------------------------------

class TestRequestReset:
    """Unit tests for PasswordResetController.request_reset()."""

    def test_known_email_returns_200(self, app, db_session):
        """request_reset() with a known email should return 200."""
        _create_user(db_session)
        with app.test_request_context(
            "/api/password-reset/request", method="POST",
            json={"email": "user@example.com"},
        ):
            _, status = _unpack(PasswordResetController.request_reset())
            assert status == 200

    def test_unknown_email_returns_200(self, app, db_session):
        """request_reset() with an unknown email should still return 200 (no enumeration)."""
        with app.test_request_context(
            "/api/password-reset/request", method="POST",
            json={"email": "nobody@example.com"},
        ):
            _, status = _unpack(PasswordResetController.request_reset())
            assert status == 200

    def test_known_and_unknown_return_same_body(self, app, db_session):
        """Both known and unknown emails should return the same response body."""
        _create_user(db_session)
        with app.test_request_context(
            "/api/password-reset/request", method="POST",
            json={"email": "user@example.com"},
        ):
            r1, _ = _unpack(PasswordResetController.request_reset())
            d1 = json.loads(r1.get_data(as_text=True))

        with app.test_request_context(
            "/api/password-reset/request", method="POST",
            json={"email": "nobody@example.com"},
        ):
            r2, _ = _unpack(PasswordResetController.request_reset())
            d2 = json.loads(r2.get_data(as_text=True))

        assert d1 == d2

    def test_known_email_creates_reset_token(self, app, db_session):
        """request_reset() for a known email should create a PasswordResetToken record."""
        user = _create_user(db_session)
        with app.test_request_context(
            "/api/password-reset/request", method="POST",
            json={"email": "user@example.com"},
        ):
            PasswordResetController.request_reset()
        assert db_session.query(PasswordResetToken).filter_by(user_id=user.id).count() == 1

    def test_unknown_email_creates_no_token(self, app, db_session):
        """request_reset() for an unknown email should not create any token."""
        with app.test_request_context(
            "/api/password-reset/request", method="POST",
            json={"email": "nobody@example.com"},
        ):
            PasswordResetController.request_reset()
        assert db_session.query(PasswordResetToken).count() == 0

    def test_missing_email_returns_400(self, app, db_session):
        """request_reset() with no email field should return 400."""
        with app.test_request_context(
            "/api/password-reset/request", method="POST",
            json={},
        ):
            _, status = _unpack(PasswordResetController.request_reset())
            assert status == 400

    def test_mail_failure_still_returns_200(self, app, db_session):
        """request_reset() should return 200 even if mail sending fails."""
        _create_user(db_session)
        with app.test_request_context(
            "/api/password-reset/request", method="POST",
            json={"email": "user@example.com"},
        ):
            with patch("app.auth_controllers.mail.send", side_effect=Exception("SMTP error")):
                _, status = _unpack(PasswordResetController.request_reset())
        assert status == 200


# ---------------------------------------------------------------------------
# Unit tests: PasswordResetController.confirm_reset()
# ---------------------------------------------------------------------------

class TestConfirmReset:
    """Unit tests for PasswordResetController.confirm_reset()."""

    def test_valid_token_returns_200(self, app, db_session):
        """confirm_reset() with a valid token and new password should return 200."""
        user = _create_user(db_session)
        raw = _create_reset_token(db_session, user)
        with app.test_request_context(
            "/api/password-reset/confirm", method="POST",
            json={"token": raw, "password": "newpassword1"},
        ):
            _, status = _unpack(PasswordResetController.confirm_reset())
            assert status == 200

    def test_valid_token_updates_password(self, app, db_session):
        """confirm_reset() with a valid token should update the user's hashed password."""
        user = _create_user(db_session, password="oldpassword")
        raw = _create_reset_token(db_session, user)
        with app.test_request_context(
            "/api/password-reset/confirm", method="POST",
            json={"token": raw, "password": "newpassword1"},
        ):
            PasswordResetController.confirm_reset()
        db_session.refresh(user)
        assert bcrypt.checkpw(b"newpassword1", user.hashed_password.encode())

    def test_valid_token_marks_token_used(self, app, db_session):
        """confirm_reset() should mark the token as used after successful reset."""
        user = _create_user(db_session)
        raw = _create_reset_token(db_session, user)
        with app.test_request_context(
            "/api/password-reset/confirm", method="POST",
            json={"token": raw, "password": "newpassword1"},
        ):
            PasswordResetController.confirm_reset()
        token = db_session.query(PasswordResetToken).filter_by(user_id=user.id).first()
        assert token.used is True

    def test_expired_token_returns_400(self, app, db_session):
        """confirm_reset() with an expired token should return 400."""
        user = _create_user(db_session)
        raw = _create_reset_token(db_session, user, expired=True)
        with app.test_request_context(
            "/api/password-reset/confirm", method="POST",
            json={"token": raw, "password": "newpassword1"},
        ):
            _, status = _unpack(PasswordResetController.confirm_reset())
            assert status == 400

    def test_used_token_returns_400(self, app, db_session):
        """confirm_reset() with an already-used token should return 400."""
        user = _create_user(db_session)
        raw = _create_reset_token(db_session, user, used=True)
        with app.test_request_context(
            "/api/password-reset/confirm", method="POST",
            json={"token": raw, "password": "newpassword1"},
        ):
            _, status = _unpack(PasswordResetController.confirm_reset())
            assert status == 400

    def test_invalid_token_returns_400(self, app, db_session):
        """confirm_reset() with a non-existent token should return 400."""
        with app.test_request_context(
            "/api/password-reset/confirm", method="POST",
            json={"token": "not-a-real-token", "password": "newpassword1"},
        ):
            _, status = _unpack(PasswordResetController.confirm_reset())
            assert status == 400

    def test_missing_token_returns_400(self, app, db_session):
        """confirm_reset() with missing token field should return 400."""
        with app.test_request_context(
            "/api/password-reset/confirm", method="POST",
            json={"password": "newpassword1"},
        ):
            _, status = _unpack(PasswordResetController.confirm_reset())
            assert status == 400

    def test_missing_password_returns_400(self, app, db_session):
        """confirm_reset() with missing password field should return 400."""
        user = _create_user(db_session)
        raw = _create_reset_token(db_session, user)
        with app.test_request_context(
            "/api/password-reset/confirm", method="POST",
            json={"token": raw},
        ):
            _, status = _unpack(PasswordResetController.confirm_reset())
            assert status == 400

    def test_empty_token_returns_400(self, app, db_session):
        """confirm_reset() with an empty token string should return 400."""
        with app.test_request_context(
            "/api/password-reset/confirm", method="POST",
            json={"token": "", "password": "newpassword1"},
        ):
            _, status = _unpack(PasswordResetController.confirm_reset())
            assert status == 400

    def test_empty_password_returns_400(self, app, db_session):
        """confirm_reset() with an empty password string should return 400."""
        with app.test_request_context(
            "/api/password-reset/confirm", method="POST",
            json={"token": "sometoken", "password": ""},
        ):
            _, status = _unpack(PasswordResetController.confirm_reset())
            assert status == 400
