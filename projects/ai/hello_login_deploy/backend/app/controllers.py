"""
controllers.py — Request handler classes for each resource.

Each controller groups related route logic as static methods.
Keeping controllers separate from route registration makes them
independently testable without a running Flask context.
"""

from flask import g, jsonify

class HelloController:
    """Handles requests for the /api/hello endpoint."""

    @staticmethod
    def hello():
        """
        Return a personalised greeting for the authenticated user.

        Requires g.current_user to be set by the require_auth decorator
        applied at route registration.

        Returns:
            Response: JSON payload with a personalised message and status field.
        """
        name = g.current_user["full_name"]
        return jsonify({"message": f"Hello, {name}!", "status": "ok"})


class HealthController:
    """Handles requests for the /api/health endpoint."""

    @staticmethod
    def health():
        """
        Return a service health check response.

        Returns:
            Response: JSON payload indicating the service is running.
        """
        return jsonify({"status": "ok"})
