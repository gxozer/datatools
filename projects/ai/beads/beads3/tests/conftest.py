"""
conftest.py — Shared pytest fixtures for all test suites.
"""

import pytest
from app import create_app


@pytest.fixture
def app():
    """Create a Flask app instance configured for testing."""
    flask_app = create_app()
    flask_app.config["TESTING"] = True
    return flask_app


@pytest.fixture
def client(app):
    """Return a Flask test client for making requests without a live server."""
    return app.test_client()
