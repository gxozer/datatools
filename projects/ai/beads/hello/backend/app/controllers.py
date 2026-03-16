"""
controllers.py — Request handler classes for each resource.

Each controller groups related route logic as static methods.
Keeping controllers separate from route registration makes them
independently testable without a running Flask context.
"""

from flask import jsonify


class HelloController:
    """Handles requests for the /api/hello endpoint."""

    @staticmethod
    def hello():
        """
        Return a Hello World JSON response.

        Returns:
            Response: JSON payload with a message and status field.
        """
        return jsonify({"message": "Hello, World!", "status": "ok"})


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
