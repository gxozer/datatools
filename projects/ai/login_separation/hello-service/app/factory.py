import os
from flask import Flask
from flask_cors import CORS
from dotenv import load_dotenv


class AppFactory:
    @staticmethod
    def create(config=None):
        app = Flask(__name__)
        load_dotenv()
        jwt_secret = os.environ.get("JWT_SECRET")
        if not jwt_secret:
            raise RuntimeError("JWT_SECRET environment variable is required")
        app.config["JWT_SECRET"] = jwt_secret
        cors_origins = os.environ.get("CORS_ORIGINS", "http://localhost:5173,http://localhost:3000")
        CORS(app, origins=cors_origins.split(","))
        from .routes import Router
        Router.register(app)
        if config:
            app.config.update(config)
        return app
