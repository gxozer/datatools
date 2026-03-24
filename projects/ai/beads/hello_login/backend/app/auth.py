"""
auth.py — JWT helpers and require_auth decorator.

Auth.generate_token() creates a signed JWT for an authenticated user.
Auth.require_auth is a decorator that protects routes by validating the
Authorization: Bearer <token> header and injecting g.current_user.
"""

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
            "sub": str(user.id),
            "email": user.email,
            "full_name": user.full_name,
            "role": user.role,
            "exp": datetime.now(timezone.utc) + timedelta(hours=24),
        }
        return jwt.encode(payload, current_app.config["JWT_SECRET"], algorithm="HS256")

    @staticmethod
    def require_auth(f):
        """
        Decorator that enforces JWT authentication on a route.

        Reads the Authorization: Bearer <token> header, decodes and validates
        the JWT, and injects the decoded payload into flask.g.current_user.

        Returns 401 if the header is missing, the scheme is not Bearer,
        or the token is invalid or expired.
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

            g.current_user = payload
            return f(*args, **kwargs)

        return decorated
