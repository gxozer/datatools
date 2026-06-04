import os
from functools import wraps
from flask import current_app, g, jsonify, request
import jwt


class Auth:
    @staticmethod
    def require_auth(f, *, allowed_roles=None):
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

            if not payload.get("jti"):
                return jsonify({"error": "Invalid token", "status": "error"}), 401

            if allowed_roles is not None and payload.get("role") not in allowed_roles:
                return jsonify({"error": "Forbidden", "status": "error"}), 403

            g.current_user = payload
            return f(*args, **kwargs)
        return decorated
