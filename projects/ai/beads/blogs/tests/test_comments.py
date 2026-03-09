import pytest


@pytest.fixture(autouse=True)
def post(client, auth):
    client.post("/posts/", json={"title": "T", "body": "B"}, headers=auth["headers"])


def test_list_comments_empty(client):
    r = client.get("/posts/1/comments")
    assert r.status_code == 200
    assert r.json == []


def test_list_comments_post_not_found(client):
    r = client.get("/posts/999/comments")
    assert r.status_code == 404


def test_create_comment_requires_jwt(client):
    r = client.post("/posts/1/comments", json={"body": "hi"})
    assert r.status_code == 401


def test_create_comment_success(client, auth):
    r = client.post("/posts/1/comments", json={"body": "Nice post!"}, headers=auth["headers"])
    assert r.status_code == 201
    assert r.json["body"] == "Nice post!"
    assert r.json["post_id"] == 1


def test_create_comment_missing_body(client, auth):
    r = client.post("/posts/1/comments", json={}, headers=auth["headers"])
    assert r.status_code == 400


def test_create_comment_post_not_found(client, auth):
    r = client.post("/posts/999/comments", json={"body": "hi"}, headers=auth["headers"])
    assert r.status_code == 404


def test_update_comment_owner(client, auth):
    client.post("/posts/1/comments", json={"body": "old"}, headers=auth["headers"])
    r = client.put("/posts/1/comments/1", json={"body": "updated"}, headers=auth["headers"])
    assert r.status_code == 200
    assert r.json["body"] == "updated"


def test_update_comment_non_owner(client, auth):
    client.post("/posts/1/comments", json={"body": "old"}, headers=auth["headers"])
    client.post("/auth/register", json={"username": "bob", "email": "bob@ex.com", "password": "p"})
    bob_token = client.post("/auth/login", json={"email": "bob@ex.com", "password": "p"}).json["access_token"]
    r = client.put("/posts/1/comments/1", json={"body": "stolen"}, headers={"Authorization": f"Bearer {bob_token}"})
    assert r.status_code == 403


def test_delete_comment_owner(client, auth):
    client.post("/posts/1/comments", json={"body": "bye"}, headers=auth["headers"])
    r = client.delete("/posts/1/comments/1", headers=auth["headers"])
    assert r.status_code == 204
    assert client.get("/posts/1/comments").json == []


def test_delete_comment_non_owner(client, auth):
    client.post("/posts/1/comments", json={"body": "bye"}, headers=auth["headers"])
    client.post("/auth/register", json={"username": "bob", "email": "bob@ex.com", "password": "p"})
    bob_token = client.post("/auth/login", json={"email": "bob@ex.com", "password": "p"}).json["access_token"]
    r = client.delete("/posts/1/comments/1", headers={"Authorization": f"Bearer {bob_token}"})
    assert r.status_code == 403
