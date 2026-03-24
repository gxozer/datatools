"""
conftest.py — Shared pytest fixtures for all test suites.
"""

import pytest
from app import create_app


@pytest.fixture
def app():
    """Create a Flask app instance configured for testing."""
    return create_app({
        "TESTING": True,
        "SQLALCHEMY_DATABASE_URI": "sqlite:///:memory:",
        "JWT_SECRET": "test-secret",
    })


@pytest.fixture
def client(app):
    """Return a Flask test client for making requests without a live server."""
    return app.test_client()


@pytest.fixture
def db_session(app):
    """
    Provide a database session backed by an in-memory SQLite DB.
    Tables are created fresh for each test and torn down after.
    """
    from app.database import db
    from app import models as _models  # ensure all models are registered before create_all()  # noqa: F401
    with app.app_context():
        db.create_all()
        yield db.session
        db.session.remove()
        db.drop_all()
