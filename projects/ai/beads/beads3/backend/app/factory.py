"""
factory.py — Flask application factory.

Uses the factory pattern so the app can be created with different
configurations (e.g. testing vs. production) without side effects at
import time.
"""

from flask import Flask
from flask_cors import CORS
from dotenv import load_dotenv

from .routes import api_blueprint


def create_app() -> Flask:
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

    # Allow requests from the React dev server (localhost:5173 for Vite)
    CORS(app, origins=["http://localhost:5173", "http://localhost:3000"])

    # Register the API blueprint under the /api prefix
    app.register_blueprint(api_blueprint, url_prefix="/api")

    return app
