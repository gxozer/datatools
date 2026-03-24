"""
routes.py — Route registration.

Router groups the API blueprint and URL rules as a class, consistent
with the OO patterns used in controllers.py. Call Router.register(app)
from the factory to wire all routes under the /api prefix.
"""

from flask import Blueprint
from .controllers import HelloController, HealthController


class Router:
    @staticmethod
    def register(app):
        """
        Create the API blueprint, bind all URL rules, and register it
        under the /api prefix. Called once from AppFactory.create().
        """
        blueprint = Blueprint("api", __name__)

        # GET /api/hello — returns the Hello World message
        # Note: auth protection is added in beads3-9td (HelloController update)
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

        app.register_blueprint(blueprint, url_prefix="/api")
