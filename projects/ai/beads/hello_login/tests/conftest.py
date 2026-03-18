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
    flask_app.config["SQLALCHEMY_DATABASE_URI"] = "sqlite:///:memory:"
    return flask_app


@pytest.fixture
def client(app):
    """Return a Flask test client for making requests without a live server."""
    return app.test_client()


@pytest.fixture
def db_app():
    """Flask app with an in-memory SQLite database for model tests."""
    flask_app = create_app()
    flask_app.config["TESTING"] = True
    flask_app.config["SQLALCHEMY_DATABASE_URI"] = "sqlite:///:memory:"
    return flask_app


@pytest.fixture
def db_session(db_app):
    """
    Provide a database session backed by an in-memory SQLite DB.
    Tables are created fresh for each test and torn down after.
    """
    from app.database import db
    import app.models  # ensure all models are registered before create_all()  # noqa: F401
    with db_app.app_context():
        db.create_all()
        yield db.session
        db.session.remove()
        db.drop_all()
