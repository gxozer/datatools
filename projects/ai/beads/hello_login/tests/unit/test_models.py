"""
test_models.py — Failing tests for User, LoginAttempt, and PasswordResetToken models.

TDD red phase: these tests are written before the models exist.
They verify fields, constraints, relationships, and default values.
"""

import pytest
from datetime import datetime, timezone
from sqlalchemy.exc import IntegrityError


class TestUserModel:
    """Tests for the User SQLAlchemy model."""

    def test_user_can_be_created(self, db_session):
        """A User can be saved and retrieved from the database."""
        from app.models import User
        user = User(
            full_name="Jane Doe",
            email="jane@example.com",
            hashed_password="hashed",
        )
        db_session.add(user)
        db_session.commit()
        saved = db_session.get(User, user.id)
        assert saved is not None
        assert saved.full_name == "Jane Doe"
        assert saved.email == "jane@example.com"

    def test_user_has_default_role_of_user(self, db_session):
        """A new User gets the 'user' role by default."""
        from app.models import User
        user = User(full_name="Jane", email="jane2@example.com", hashed_password="x")
        db_session.add(user)
        db_session.commit()
        assert user.role == "user"

    def test_user_email_is_unique(self, db_session):
        """Two users cannot share the same email address."""
        from app.models import User
        db_session.add(User(full_name="A", email="dup@example.com", hashed_password="x"))
        db_session.commit()
        db_session.add(User(full_name="B", email="dup@example.com", hashed_password="y"))
        with pytest.raises(IntegrityError):
            db_session.commit()

    def test_user_created_at_is_set_automatically(self, db_session):
        """created_at is populated automatically on insert."""
        from app.models import User
        user = User(full_name="Jane", email="jane3@example.com", hashed_password="x")
        db_session.add(user)
        db_session.commit()
        assert isinstance(user.created_at, datetime)

    def test_user_updated_at_is_set_automatically(self, db_session):
        """updated_at is populated automatically on insert."""
        from app.models import User
        user = User(full_name="Jane", email="jane4@example.com", hashed_password="x")
        db_session.add(user)
        db_session.commit()
        assert isinstance(user.updated_at, datetime)

    def test_user_full_name_is_required(self, db_session):
        """full_name cannot be null."""
        from app.models import User
        user = User(email="noname@example.com", hashed_password="x")
        db_session.add(user)
        with pytest.raises(IntegrityError):
            db_session.commit()

    def test_user_email_is_required(self, db_session):
        """email cannot be null."""
        from app.models import User
        user = User(full_name="No Email", hashed_password="x")
        db_session.add(user)
        with pytest.raises(IntegrityError):
            db_session.commit()

    def test_user_hashed_password_is_required(self, db_session):
        """hashed_password cannot be null."""
        from app.models import User
        user = User(full_name="No Pass", email="nopass@example.com")
        db_session.add(user)
        with pytest.raises(IntegrityError):
            db_session.commit()


class TestLoginAttemptModel:
    """Tests for the LoginAttempt SQLAlchemy model."""

    def test_login_attempt_can_be_created(self, db_session):
        """A LoginAttempt can be saved and retrieved."""
        from app.models import LoginAttempt
        attempt = LoginAttempt(email="jane@example.com", success=False)
        db_session.add(attempt)
        db_session.commit()
        saved = db_session.get(LoginAttempt, attempt.id)
        assert saved is not None
        assert saved.email == "jane@example.com"
        assert saved.success is False

    def test_login_attempt_attempted_at_is_set_automatically(self, db_session):
        """attempted_at is populated automatically on insert."""
        from app.models import LoginAttempt
        attempt = LoginAttempt(email="jane@example.com", success=True)
        db_session.add(attempt)
        db_session.commit()
        assert isinstance(attempt.attempted_at, datetime)

    def test_login_attempt_email_is_required(self, db_session):
        """email cannot be null."""
        from app.models import LoginAttempt
        attempt = LoginAttempt(success=False)
        db_session.add(attempt)
        with pytest.raises(IntegrityError):
            db_session.commit()

    def test_login_attempt_success_is_required(self, db_session):
        """success cannot be null."""
        from app.models import LoginAttempt
        attempt = LoginAttempt(email="jane@example.com")
        db_session.add(attempt)
        with pytest.raises(IntegrityError):
            db_session.commit()


class TestPasswordResetTokenModel:
    """Tests for the PasswordResetToken SQLAlchemy model."""

    def test_token_can_be_created(self, db_session):
        """A PasswordResetToken can be saved and retrieved."""
        from app.models import User, PasswordResetToken
        user = User(full_name="Jane", email="jane5@example.com", hashed_password="x")
        db_session.add(user)
        db_session.commit()

        token = PasswordResetToken(
            user_id=user.id,
            token_hash="abc123",
            expires_at=datetime(2099, 1, 1, tzinfo=timezone.utc),
        )
        db_session.add(token)
        db_session.commit()
        saved = db_session.get(PasswordResetToken, token.id)
        assert saved is not None
        assert saved.token_hash == "abc123"
        assert saved.user_id == user.id

    def test_token_used_defaults_to_false(self, db_session):
        """used defaults to False on creation."""
        from app.models import User, PasswordResetToken
        user = User(full_name="Jane", email="jane6@example.com", hashed_password="x")
        db_session.add(user)
        db_session.commit()

        token = PasswordResetToken(
            user_id=user.id,
            token_hash="def456",
            expires_at=datetime(2099, 1, 1, tzinfo=timezone.utc),
        )
        db_session.add(token)
        db_session.commit()
        assert token.used is False

    def test_token_hash_is_unique(self, db_session):
        """Two tokens cannot share the same token_hash."""
        from app.models import User, PasswordResetToken
        user = User(full_name="Jane", email="jane7@example.com", hashed_password="x")
        db_session.add(user)
        db_session.commit()

        expires = datetime(2099, 1, 1, tzinfo=timezone.utc)
        db_session.add(PasswordResetToken(user_id=user.id, token_hash="samehash", expires_at=expires))
        db_session.commit()
        db_session.add(PasswordResetToken(user_id=user.id, token_hash="samehash", expires_at=expires))
        with pytest.raises(IntegrityError):
            db_session.commit()

    def test_token_user_relationship(self, db_session):
        """PasswordResetToken.user returns the associated User."""
        from app.models import User, PasswordResetToken
        user = User(full_name="Jane", email="jane8@example.com", hashed_password="x")
        db_session.add(user)
        db_session.commit()

        token = PasswordResetToken(
            user_id=user.id,
            token_hash="reltest",
            expires_at=datetime(2099, 1, 1, tzinfo=timezone.utc),
        )
        db_session.add(token)
        db_session.commit()
        assert token.user.email == "jane8@example.com"
