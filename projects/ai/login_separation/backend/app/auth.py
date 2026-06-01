"""
auth.py — JWT helpers and require_auth decorator.

Auth.generate_token() creates a signed JWT for an authenticated user.
Auth.require_auth is a decorator that protects routes by validating the
Authorization: Bearer <token> header and injecting g.current_user.
"""

import uuid
from datetime import datetime, timedelta, timezone
from functools import wraps

import jwt
from flask import current_app, g, jsonify, request


class Auth:
    """JWT authentication helpers and route protection decorator."""

    @staticmethod
    def generate_token(user) -> str:
        """
        Generate a signed JWT for the given user.

        Args:
            user: A User model instance with id, email, full_name, and role.

        Returns:
            str: A signed HS256 JWT valid for 24 hours.
        """
        payload = {
            "jti": str(uuid.uuid4()),
            "iat": datetime.now(timezone.utc),
            "sub": str(user.id),
            "email": user.email,
            "full_name": user.full_name,
            "role": user.role,
            "exp": datetime.now(timezone.utc) + timedelta(hours=24),
        }
        return jwt.encode(payload, current_app.config["JWT_SECRET"], algorithm="HS256")

    @staticmethod
    def require_auth(f, *, allowed_roles=None):
        """
        Decorator that enforces JWT authentication (and optional role check) on a route.

        Reads the Authorization: Bearer <token> header, decodes and validates
        the JWT, and injects the decoded payload into flask.g.current_user.

        Args:
            f: The view function to protect.
            allowed_roles: Optional set of role strings permitted to access the
                route (e.g. {"user", "admin"}). When provided, tokens whose
                "role" claim is not in the set receive a 403. When omitted,
                any valid token is accepted.

        Returns 401 if the header is missing, the scheme is not Bearer,
        or the token is invalid or expired.
        Returns 403 if the token's role is not in allowed_roles.
        """
        @wraps(f)
        def decorated(*args, **kwargs):
            auth_header = request.headers.get("Authorization", "")

            if not auth_header.startswith("Bearer "):
                return jsonify({"error": "Unauthorized", "status": "error"}), 401

            token = auth_header[len("Bearer "):]

            try:
                payload = jwt.decode(
                    token,
                    current_app.config["JWT_SECRET"],
                    algorithms=["HS256"],
                )
            except jwt.ExpiredSignatureError:
                return jsonify({"error": "Token expired", "status": "error"}), 401
            except jwt.InvalidTokenError:
                return jsonify({"error": "Invalid token", "status": "error"}), 401

            jti = payload.get("jti")
            if not jti:
                return jsonify({"error": "Invalid token", "status": "error"}), 401

            from .database import db
            from .models import DeniedToken, User

            if db.session.query(DeniedToken).filter_by(jti=jti).first():
                return jsonify({"error": "Token has been revoked", "status": "error"}), 401

            # Reject tokens issued before the user's last session invalidation (e.g. password reset)
            iat = payload.get("iat")
            try:
                uid = int(payload.get("sub", ""))
            except (TypeError, ValueError):
                uid = None
            if iat and uid is not None:
                user = db.session.query(User).filter_by(id=uid).first()
                if user and user.tokens_invalidated_at is not None:
                    inv = user.tokens_invalidated_at
                    if inv.tzinfo is None:
                        inv = inv.replace(tzinfo=timezone.utc)
                    if datetime.fromtimestamp(iat, tz=timezone.utc) <= inv:
                        return jsonify({"error": "Token has been revoked", "status": "error"}), 401

            if allowed_roles is not None and payload.get("role") not in allowed_roles:
                return jsonify({"error": "Forbidden", "status": "error"}), 403

            g.current_user = payload
            return f(*args, **kwargs)

        return decorated
