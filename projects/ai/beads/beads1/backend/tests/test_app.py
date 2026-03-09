import pytest
from app import app


@pytest.fixture
def client():
    app.config["TESTING"] = True
    with app.test_client() as client:
        yield client


def test_hello_status_code(client):
    response = client.get("/api/hello")
    assert response.status_code == 200


def test_hello_returns_json(client):
    response = client.get("/api/hello")
    assert response.content_type == "application/json"


def test_hello_message_value(client):
    response = client.get("/api/hello")
    data = response.get_json()
    assert data["message"] == "Hello, World!"


def test_hello_json_shape(client):
    response = client.get("/api/hello")
    data = response.get_json()
    assert set(data.keys()) == {"message"}
