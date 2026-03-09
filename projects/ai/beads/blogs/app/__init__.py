from flask import Flask, jsonify
from app.config import config
from app.extensions import db, jwt, ma


def create_app(config_name="default"):
    app = Flask(__name__)
    app.config.from_object(config[config_name])

    db.init_app(app)
    jwt.init_app(app)
    ma.init_app(app)

    from app.routes.auth import bp as auth_bp
    from app.routes.posts import bp as posts_bp
    from app.routes.comments import bp as comments_bp

    app.register_blueprint(auth_bp)
    app.register_blueprint(posts_bp)
    app.register_blueprint(comments_bp)

    @app.errorhandler(400)
    def bad_request(e):
        return jsonify({"error": "Bad request", "detail": str(e)}), 400

    @app.errorhandler(401)
    def unauthorized(e):
        return jsonify({"error": "Unauthorized"}), 401

    @app.errorhandler(403)
    def forbidden(e):
        return jsonify({"error": "Forbidden"}), 403

    @app.errorhandler(404)
    def not_found(e):
        return jsonify({"error": "Not found"}), 404

    @app.errorhandler(422)
    def unprocessable(e):
        return jsonify({"error": "Unprocessable entity", "detail": str(e)}), 422

    @app.errorhandler(500)
    def server_error(e):
        return jsonify({"error": "Internal server error"}), 500

    with app.app_context():
        from app import models as _models  # noqa: F401
        db.create_all()

    return app
