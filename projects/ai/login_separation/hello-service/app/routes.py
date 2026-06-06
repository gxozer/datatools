from flask import Blueprint
from .auth import Auth
from .controllers import HelloController, HealthController

bp = Blueprint("hello", __name__)


class Router:
    @staticmethod
    def register(app):
        blueprint = Blueprint("hello_api", __name__)

        # GET /api/hello — returns personalised greeting for authenticated users
        blueprint.add_url_rule(
            "/hello",
            view_func=Auth.require_auth(HelloController.hello, allowed_roles={"user", "admin"}),
            methods=["GET"],
        )

        # GET /api/health — returns service health status
        blueprint.add_url_rule(
            "/health",
            view_func=HealthController.health,
            methods=["GET"],
        )

        app.register_blueprint(blueprint, url_prefix="/api")
