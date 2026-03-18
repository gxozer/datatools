"""
routes.py — Route registration.

Router groups the API blueprint and URL rules as a class, consistent
with the OO patterns used in controllers.py. Call Router.register(app)
from the factory to wire all routes under the /api prefix.
"""

from flask import Blueprint
from .controllers import HelloController, HealthController


class Router:
    blueprint = Blueprint("api", __name__)

    # GET /api/hello — returns the Hello World message
    blueprint.add_url_rule(
        "/hello",
        view_func=HelloController.hello,
        methods=["GET"],
    )

    # GET /api/health — returns service health status
    blueprint.add_url_rule(
        "/health",
        view_func=HealthController.health,
        methods=["GET"],
    )

    @staticmethod
    def register(app):
        """Register the API blueprint under the /api prefix."""
        app.register_blueprint(Router.blueprint, url_prefix="/api")
