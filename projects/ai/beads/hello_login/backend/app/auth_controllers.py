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

        if not email or not password:
            return jsonify({"error": "email and password are required", "status": "error"}), 400

        # Brute-force lockout check
        window_start = datetime.now(timezone.utc) - timedelta(minutes=_LOCKOUT_WINDOW_MINUTES)
        recent_failures = (
            db.session.query(LoginAttempt)
            .filter(
                LoginAttempt.email == email,
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

        # Look up user
        user = db.session.query(User).filter_by(email=email).first()

        # Verify credentials (constant-time: always check hash to prevent timing attacks)
        valid = (
            user is not None
            and bcrypt.checkpw(password.encode(), user.hashed_password.encode())
        )

        # Record the attempt
        attempt = LoginAttempt(
            email=email,
            success=valid,
            attempted_at=datetime.now(timezone.utc),
        )
        db.session.add(attempt)
        db.session.commit()

        if not valid:
            return jsonify({"error": "Invalid email or password", "status": "error"}), 401

        token = Auth.generate_token(user)
        return jsonify({"token": token, "status": "ok"}), 200
