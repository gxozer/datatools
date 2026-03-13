"""
routes.py — Blueprint and route registration.

Binds URL rules to controller methods. The blueprint is registered
in factory.py under the /api prefix.
"""

from flask import Blueprint
from .controllers import HelloController, HealthController

# All routes in this module are grouped under the 'api' blueprint
api_blueprint = Blueprint("api", __name__)

# GET /api/hello — returns the Hello World message
api_blueprint.add_url_rule(
    "/hello",
    view_func=HelloController.hello,
    methods=["GET"],
)

# GET /api/health — returns service health status
api_blueprint.add_url_rule(
    "/health",
    view_func=HealthController.health,
    methods=["GET"],
)
