def test_register_success(client):
    r = client.post("/auth/register", json={
        "username": "alice", "email": "alice@ex.com", "password": "secret123"
    })
    assert r.status_code == 201
    assert r.json["username"] == "alice"
    assert "password_hash" not in r.json


def test_register_missing_fields(client):
    r = client.post("/auth/register", json={"username": "alice"})
    assert r.status_code == 400


def test_register_duplicate_username(client):
    client.post("/auth/register", json={"username": "alice", "email": "a@ex.com", "password": "p"})
    r = client.post("/auth/register", json={"username": "alice", "email": "b@ex.com", "password": "p"})
    assert r.status_code == 409


def test_register_duplicate_email(client):
    client.post("/auth/register", json={"username": "alice", "email": "a@ex.com", "password": "p"})
    r = client.post("/auth/register", json={"username": "bob", "email": "a@ex.com", "password": "p"})
    assert r.status_code == 409


def test_login_success(client):
    client.post("/auth/register", json={"username": "alice", "email": "a@ex.com", "password": "secret"})
    r = client.post("/auth/login", json={"email": "a@ex.com", "password": "secret"})
    assert r.status_code == 200
    assert "access_token" in r.json
    assert "refresh_token" in r.json


def test_login_wrong_password(client):
    client.post("/auth/register", json={"username": "alice", "email": "a@ex.com", "password": "secret"})
    r = client.post("/auth/login", json={"email": "a@ex.com", "password": "wrong"})
    assert r.status_code == 401


def test_login_unknown_email(client):
    r = client.post("/auth/login", json={"email": "nobody@ex.com", "password": "x"})
    assert r.status_code == 401


def test_login_missing_fields(client):
    r = client.post("/auth/login", json={"email": "a@ex.com"})
    assert r.status_code == 400


def test_refresh(client, auth):
    r = client.post("/auth/refresh", headers={"Authorization": f"Bearer {auth['refresh_token']}"})
    assert r.status_code == 200
    assert "access_token" in r.json


def test_refresh_with_access_token_rejected(client, auth):
    r = client.post("/auth/refresh", headers=auth["headers"])
    assert r.status_code == 422
