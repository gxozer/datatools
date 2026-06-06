import os
import pytest
from app.factory import AppFactory


@pytest.fixture
def app():
    os.environ["JWT_SECRET"] = "test-secret"
    application = AppFactory.create({"TESTING": True})
    return application


@pytest.fixture
def client(app):
    return app.test_client()
