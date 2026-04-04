"""
test_signup_controller.py — Unit tests for SignupController.

Tests call SignupController.signup() directly inside a Flask app+db context.
All tests are expected to fail until SignupController is implemented (TDD red phase).
"""

import json
import pytest
import bcrypt

from app.auth_controllers import SignupController
from app.database import db
from app.models import User


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _unpack(result):
    """Unpack a controller return value into (response, status_code)."""
    if isinstance(result, tuple):
        return result[0], result[1]
    return result, result.status_code


def _json(response):
    return json.loads(response.get_data(as_text=True))


def _make_request(app, payload):
    """Push a request context with the given JSON body."""
    import json as _json_mod
    return app.test_request_context(
        "/api/signup",
        method="POST",
        content_type="application/json",
        data=_json_mod.dumps(payload),
    )


def _create_user(session, email="existing@example.com"):
    hashed = bcrypt.hashpw(b"Password1!", bcrypt.gensalt(rounds=4)).decode()
    user = User(email=email, full_name="Existing User", hashed_password=hashed)
    session.add(user)
    session.commit()
    return user


# ---------------------------------------------------------------------------
# Valid signup
# ---------------------------------------------------------------------------

class TestSignupControllerSuccess:

    def test_valid_signup_returns_201(self, db_session, app):
        with _make_request(app, {"full_name": "Alice", "email": "alice@example.com", "password": "Password1!"}):
            response, status = _unpack(SignupController.signup())
        assert status == 201

    def test_valid_signup_returns_token(self, db_session, app):
        with _make_request(app, {"full_name": "Alice", "email": "alice@example.com", "password": "Password1!"}):
            response, _ = _unpack(SignupController.signup())
        data = _json(response)
        assert "token" in data

    def test_valid_signup_status_ok(self, db_session, app):
        with _make_request(app, {"full_name": "Alice", "email": "alice@example.com", "password": "Password1!"}):
            response, _ = _unpack(SignupController.signup())
        data = _json(response)
        assert data["status"] == "ok"

    def test_valid_signup_creates_user_in_db(self, db_session, app):
        with _make_request(app, {"full_name": "Bob", "email": "bob@example.com", "password": "Password1!"}):
            SignupController.signup()
        user = db_session.query(User).filter_by(email="bob@example.com").first()
        assert user is not None
        assert user.full_name == "Bob"

    def test_valid_signup_hashes_password(self, db_session, app):
        with _make_request(app, {"full_name": "Carol", "email": "carol@example.com", "password": "Password1!"}):
            SignupController.signup()
        user = db_session.query(User).filter_by(email="carol@example.com").first()
        assert user.hashed_password != "Password1!"
        assert bcrypt.checkpw(b"Password1!", user.hashed_password.encode())

    def test_valid_signup_assigns_user_role(self, db_session, app):
        with _make_request(app, {"full_name": "Dave", "email": "dave@example.com", "password": "Password1!"}):
            SignupController.signup()
        user = db_session.query(User).filter_by(email="dave@example.com").first()
        assert user.role == "user"

    def test_email_is_canonicalized(self, db_session, app):
        with _make_request(app, {"full_name": "Eve", "email": "  EVE@Example.COM  ", "password": "Password1!"}):
            SignupController.signup()
        user = db_session.query(User).filter_by(email="eve@example.com").first()
        assert user is not None


# ---------------------------------------------------------------------------
# Missing / invalid fields
# ---------------------------------------------------------------------------

class TestSignupControllerValidation:

    def test_missing_email_returns_400(self, db_session, app):
        with _make_request(app, {"full_name": "Alice", "password": "Password1!"}):
            response, status = _unpack(SignupController.signup())
        assert status == 400

    def test_missing_password_returns_400(self, db_session, app):
        with _make_request(app, {"full_name": "Alice", "email": "alice@example.com"}):
            response, status = _unpack(SignupController.signup())
        assert status == 400

    def test_missing_full_name_returns_400(self, db_session, app):
        with _make_request(app, {"email": "alice@example.com", "password": "Password1!"}):
            response, status = _unpack(SignupController.signup())
        assert status == 400

    def test_empty_email_returns_400(self, db_session, app):
        with _make_request(app, {"full_name": "Alice", "email": "", "password": "Password1!"}):
            response, status = _unpack(SignupController.signup())
        assert status == 400

    def test_non_string_fields_return_400(self, db_session, app):
        with _make_request(app, {"full_name": 123, "email": "alice@example.com", "password": "Password1!"}):
            response, status = _unpack(SignupController.signup())
        assert status == 400

    def test_invalid_email_no_at_returns_400(self, db_session, app):
        with _make_request(app, {"full_name": "Alice", "email": "notanemail", "password": "Password1!"}):
            response, status = _unpack(SignupController.signup())
        assert status == 400

    def test_invalid_email_no_domain_returns_400(self, db_session, app):
        with _make_request(app, {"full_name": "Alice", "email": "alice@", "password": "Password1!"}):
            response, status = _unpack(SignupController.signup())
        assert status == 400

    def test_weak_password_too_short_returns_400(self, db_session, app):
        with _make_request(app, {"full_name": "Alice", "email": "alice@example.com", "password": "short"}):
            response, status = _unpack(SignupController.signup())
        assert status == 400

    def test_response_is_json(self, db_session, app):
        with _make_request(app, {}):
            response, _ = _unpack(SignupController.signup())
        assert "application/json" in response.content_type


# ---------------------------------------------------------------------------
# Duplicate email
# ---------------------------------------------------------------------------

class TestSignupControllerDuplicate:

    def test_duplicate_email_returns_409(self, db_session, app):
        _create_user(db_session)
        with _make_request(app, {"full_name": "Other", "email": "existing@example.com", "password": "Password1!"}):
            response, status = _unpack(SignupController.signup())
        assert status == 409

    def test_duplicate_email_case_insensitive_returns_409(self, db_session, app):
        _create_user(db_session)
        with _make_request(app, {"full_name": "Other", "email": "EXISTING@EXAMPLE.COM", "password": "Password1!"}):
            response, status = _unpack(SignupController.signup())
        assert status == 409
