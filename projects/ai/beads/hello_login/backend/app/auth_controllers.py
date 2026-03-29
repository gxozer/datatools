"""
auth_controllers.py — Request handlers for authentication endpoints.

Controllers are implemented as classes with static methods, consistent
with the OO conventions used in controllers.py.
"""

import hashlib
import secrets
from datetime import datetime, timedelta, timezone

import bcrypt
from flask import current_app, jsonify, request
from flask_mail import Message

from .auth import Auth
from .database import db, mail
from .models import LoginAttempt, PasswordResetToken, User

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


class LogoutController:
    """Handles POST /api/logout."""

    @staticmethod
    def logout():
        """
        Log out the current user.

        Auth is enforced by Auth.require_auth at the route level, so this
        method is only reached with a valid JWT. JWTs are stateless — there
        is no server-side session to destroy. A token denylist (e.g. Redis
        set keyed by jti with TTL matching token expiry) would be inserted
        here if revocation is needed in the future.

        Returns:
            200 always (require_auth returns 401 before we get here).
        """
        return jsonify({"message": "Logged out successfully.", "status": "ok"}), 200


class PasswordResetController:
    """Handles POST /api/password-reset/request and /api/password-reset/confirm."""

    _TOKEN_EXPIRY_HOURS = 1
    _RESET_RESPONSE = {"message": "If that email is registered, a reset link has been sent.", "status": "ok"}

    @staticmethod
    def request_reset():
        """
        Request a password reset link.

        Looks up the user by email. If found, generates a secure token, stores
        its SHA-256 hash in the DB, and sends a reset email. Always returns 200
        regardless of whether the email exists to prevent enumeration.

        Returns:
            200 always (silent no-op for unknown emails).
            400 if the email field is missing.
        """
        data = request.get_json(silent=True) or {}
        email = data.get("email")

        if not isinstance(email, str) or not email.strip():
            return jsonify({"error": "email is required", "status": "error"}), 400

        email = email.strip().lower()
        user = db.session.query(User).filter_by(email=email).first()

        if user is not None:
            raw_token = secrets.token_urlsafe(32)
            token_hash = hashlib.sha256(raw_token.encode()).hexdigest()
            expires_at = datetime.now(timezone.utc) + timedelta(hours=PasswordResetController._TOKEN_EXPIRY_HOURS)

            reset_token = PasswordResetToken(
                user_id=user.id,
                token_hash=token_hash,
                expires_at=expires_at,
            )
            db.session.add(reset_token)
            db.session.flush()  # assign PK before sending — rolled back on mail failure

            try:
                msg = Message(
                    subject="Password Reset",
                    recipients=[email],
                    body=f"Use this token to reset your password: {raw_token}\nExpires in 1 hour.",
                )
                mail.send(msg)
                db.session.commit()
            except Exception:
                db.session.rollback()
                current_app.logger.exception("Failed to send password reset email to %s", email)
                # Fall through — always return the same 200 to prevent enumeration

        return jsonify(PasswordResetController._RESET_RESPONSE), 200

    @staticmethod
    def confirm_reset():
        """
        Confirm a password reset using a token.

        Validates the token (exists, not expired, not used), updates the user's
        password, and marks the token as used.

        Returns:
            200 on success.
            400 if fields are missing or the token is invalid/expired/used.
        """
        data = request.get_json(silent=True) or {}
        raw_token = data.get("token")
        new_password = data.get("password")

        if not isinstance(raw_token, str) or not isinstance(new_password, str):
            return jsonify({"error": "token and password are required", "status": "error"}), 400
        if not raw_token.strip() or not new_password:
            return jsonify({"error": "token and password are required", "status": "error"}), 400
        raw_token = raw_token.strip()

        token_hash = hashlib.sha256(raw_token.encode()).hexdigest()
        reset_token = db.session.query(PasswordResetToken).filter_by(token_hash=token_hash).first()

        if reset_token is None:
            return jsonify({"error": "Invalid or expired reset token", "status": "error"}), 400

        now = datetime.now(timezone.utc)
        if reset_token.used:
            return jsonify({"error": "Invalid or expired reset token", "status": "error"}), 400
        if reset_token.expires_at.replace(tzinfo=timezone.utc) < now:
            return jsonify({"error": "Invalid or expired reset token", "status": "error"}), 400

        user = db.session.query(User).filter_by(id=reset_token.user_id).first()
        user.hashed_password = bcrypt.hashpw(new_password.encode(), bcrypt.gensalt()).decode()
        reset_token.used = True
        db.session.commit()

        return jsonify({"message": "Password has been reset.", "status": "ok"}), 200
