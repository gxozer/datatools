"""
auth_controllers.py — Request handlers for authentication endpoints.

Controllers are implemented as classes with static methods, consistent
with the OO conventions used in controllers.py.
"""

from datetime import datetime, timedelta, timezone

import bcrypt
from flask import jsonify, request

from .auth import Auth
from .database import db
from .models import LoginAttempt, User

_LOCKOUT_WINDOW_MINUTES = 15
_LOCKOUT_THRESHOLD = 5

# Precomputed dummy hash used when the email is not found.
# Always running bcrypt.checkpw() (even against this dummy) prevents timing
# side-channels that would otherwise reveal whether an email exists.
_DUMMY_HASH = bcrypt.hashpw(b"dummy", bcrypt.gensalt()).decode()


class LoginController:
    """Handles POST /api/login."""

    @staticmethod
    def login():
        """
        Authenticate a user with email and password.

        Checks brute-force lockout, verifies credentials, records the
        attempt, and returns a signed JWT on success.

        Returns:
            200 + JWT on success.
            400 if required fields are missing.
            401 if credentials are invalid.
            429 if the account is temporarily locked.
        """
        data = request.get_json(silent=True) or {}

        email = data.get("email")
        password = data.get("password")

        if not isinstance(email, str) or not isinstance(password, str):
            return jsonify({"error": "email and password are required", "status": "error"}), 400
        if not email or not password:
            return jsonify({"error": "email and password are required", "status": "error"}), 400

        # Canonicalize email to prevent lockout bypass via casing/whitespace variants
        email = email.strip().lower()

        # Look up user first — needed to key lockout on user_id rather than email string,
        # preventing an attacker from locking out arbitrary (even non-existent) addresses.
        user = db.session.query(User).filter_by(email=email).first()

        # Brute-force lockout check (only for existing accounts, keyed by user_id)
        if user is not None:
            window_start = datetime.now(timezone.utc) - timedelta(minutes=_LOCKOUT_WINDOW_MINUTES)
            recent_failures = (
                db.session.query(LoginAttempt)
                .filter(
                    LoginAttempt.user_id == user.id,
                    LoginAttempt.success == False,  # noqa: E712
                    LoginAttempt.attempted_at > window_start,
                )
                .count()
            )
            if recent_failures >= _LOCKOUT_THRESHOLD:
                return jsonify({
                    "error": "Account temporarily locked. Try again later.",
                    "status": "error",
                }), 429

        # Verify credentials — always run bcrypt.checkpw() regardless of whether the user
        # exists, to prevent email enumeration via timing side-channel.
        hash_to_check = user.hashed_password if user is not None else _DUMMY_HASH
        pw_match = bcrypt.checkpw(password.encode(), hash_to_check.encode())
        valid = user is not None and pw_match

        # Record the attempt (user_id nullable for unknown emails)
        attempt = LoginAttempt(
            email=email,
            user_id=user.id if user is not None else None,
            success=valid,
            attempted_at=datetime.now(timezone.utc),
        )
        db.session.add(attempt)
        db.session.commit()

        if not valid:
            return jsonify({"error": "Invalid email or password", "status": "error"}), 401

        token = Auth.generate_token(user)
        return jsonify({"token": token, "status": "ok"}), 200
