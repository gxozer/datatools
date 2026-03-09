import pytest
from app import create_app
from app.extensions import db as _db


@pytest.fixture
def app():
    app = create_app("testing")
    with app.app_context():
        yield app
        _db.drop_all()


@pytest.fixture
def client(app):
    return app.test_client()


@pytest.fixture
def auth(client):
    """Register alice and return her auth headers + tokens."""
    client.post("/auth/register", json={
        "username": "alice", "email": "alice@ex.com", "password": "secret123"
    })
    r = client.post("/auth/login", json={"email": "alice@ex.com", "password": "secret123"})
    token = r.json["access_token"]
    refresh = r.json["refresh_token"]
    return {
        "headers": {"Authorization": f"Bearer {token}"},
        "access_token": token,
        "refresh_token": refresh,
    }
