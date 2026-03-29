"""
routes.py — Route registration.

Router groups the API blueprint and URL rules as a class, consistent
with the OO patterns used in controllers.py. Call Router.register(app)
from the factory to wire all routes under the /api prefix.
"""

from flask import Blueprint
from .auth import Auth
from .auth_controllers import LoginController, LogoutController, PasswordResetController
from .controllers import HelloController, HealthController


class Router:
    @staticmethod
    def register(app):
        """
        Create the API blueprint, bind all URL rules, and register it
        under the /api prefix. Called once from AppFactory.create().
        """
        blueprint = Blueprint("api", __name__)

        # GET /api/hello — returns personalised greeting for authenticated users
        blueprint.add_url_rule(
            "/hello",
            view_func=Auth.require_auth(HelloController.hello, allowed_roles={"user", "admin"}),
            methods=["GET"],
        )

        # POST /api/login — authenticate and return JWT
        blueprint.add_url_rule(
            "/login",
            view_func=LoginController.login,
            methods=["POST"],
        )

        # POST /api/logout — invalidate session (stateless JWT; see controller for denylist note)
        blueprint.add_url_rule(
            "/logout",
            view_func=Auth.require_auth(LogoutController.logout),
            methods=["POST"],
        )

        # POST /api/password-reset/request — send reset email
        blueprint.add_url_rule(
            "/password-reset/request",
            view_func=PasswordResetController.request_reset,
            methods=["POST"],
        )

        # POST /api/password-reset/confirm — apply new password
        blueprint.add_url_rule(
            "/password-reset/confirm",
            view_func=PasswordResetController.confirm_reset,
            methods=["POST"],
        )

        # GET /api/health — returns service health status
        blueprint.add_url_rule(
            "/health",
            view_func=HealthController.health,
            methods=["GET"],
        )

        app.register_blueprint(blueprint, url_prefix="/api")
