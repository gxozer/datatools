"""
factory.py — Flask application factory.

AppFactory.create() builds and returns a configured Flask instance.
Using a class keeps the pattern consistent with the OO conventions
used in controllers.py and routes.py.
"""

import os
from flask import Flask
from flask_cors import CORS
from dotenv import load_dotenv

from .database import db
from .routes import Router


class AppFactory:
    @staticmethod
    def create() -> Flask:
        """
        Create and configure the Flask application.

        Loads environment variables from a .env file (if present), registers
        blueprints, and enables CORS for the React dev server.

        Returns:
            Flask: The configured Flask application instance.
        """
        # Load environment variables from .env file if it exists
        load_dotenv()

        app = Flask(__name__)

        # Database config — schema is managed by Alembic, not create_all()
        app.config["SQLALCHEMY_DATABASE_URI"] = os.environ.get(
            "DATABASE_URL", "sqlite:///app.db"
        )
        app.config["SQLALCHEMY_TRACK_MODIFICATIONS"] = False

        # Initialise extensions
        db.init_app(app)

        # Allow requests from the React dev server (localhost:5173 for Vite)
        CORS(app, origins=["http://localhost:5173", "http://localhost:3000"])

        # Register API routes
        Router.register(app)

        return app


# Module-level alias for backwards compatibility with run.py and conftest.py
create_app = AppFactory.create
