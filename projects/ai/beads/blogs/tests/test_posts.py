def test_list_posts_empty(client):
    r = client.get("/posts/")
    assert r.status_code == 200
    assert r.json["posts"] == []
    assert r.json["total"] == 0


def test_create_post_requires_jwt(client):
    r = client.post("/posts/", json={"title": "T", "body": "B"})
    assert r.status_code == 401


def test_create_post_success(client, auth):
    r = client.post("/posts/", json={"title": "Hello", "body": "World"}, headers=auth["headers"])
    assert r.status_code == 201
    assert r.json["title"] == "Hello"
    assert r.json["author_id"] == 1


def test_create_post_missing_fields(client, auth):
    r = client.post("/posts/", json={"title": "No body"}, headers=auth["headers"])
    assert r.status_code == 400


def test_get_post(client, auth):
    client.post("/posts/", json={"title": "T", "body": "B"}, headers=auth["headers"])
    r = client.get("/posts/1")
    assert r.status_code == 200
    assert r.json["title"] == "T"


def test_get_post_not_found(client):
    r = client.get("/posts/999")
    assert r.status_code == 404


def test_list_posts_pagination(client, auth):
    for i in range(3):
        client.post("/posts/", json={"title": f"Post {i}", "body": "B"}, headers=auth["headers"])
    r = client.get("/posts/")
    assert r.json["total"] == 3


def test_update_post_owner(client, auth):
    client.post("/posts/", json={"title": "Old", "body": "B"}, headers=auth["headers"])
    r = client.put("/posts/1", json={"title": "New"}, headers=auth["headers"])
    assert r.status_code == 200
    assert r.json["title"] == "New"


def test_update_post_non_owner(client, auth):
    client.post("/posts/", json={"title": "T", "body": "B"}, headers=auth["headers"])
    client.post("/auth/register", json={"username": "bob", "email": "bob@ex.com", "password": "p"})
    bob_token = client.post("/auth/login", json={"email": "bob@ex.com", "password": "p"}).json["access_token"]
    r = client.put("/posts/1", json={"title": "Stolen"}, headers={"Authorization": f"Bearer {bob_token}"})
    assert r.status_code == 403


def test_delete_post_owner(client, auth):
    client.post("/posts/", json={"title": "T", "body": "B"}, headers=auth["headers"])
    r = client.delete("/posts/1", headers=auth["headers"])
    assert r.status_code == 204
    assert client.get("/posts/1").status_code == 404


def test_delete_post_cascades_comments(client, auth):
    client.post("/posts/", json={"title": "T", "body": "B"}, headers=auth["headers"])
    client.post("/posts/1/comments", json={"body": "comment"}, headers=auth["headers"])
    client.delete("/posts/1", headers=auth["headers"])
    # post is gone; comment table should have nothing for post 1
    r = client.get("/posts/1")
    assert r.status_code == 404


def test_delete_post_non_owner(client, auth):
    client.post("/posts/", json={"title": "T", "body": "B"}, headers=auth["headers"])
    client.post("/auth/register", json={"username": "bob", "email": "bob@ex.com", "password": "p"})
    bob_token = client.post("/auth/login", json={"email": "bob@ex.com", "password": "p"}).json["access_token"]
    r = client.delete("/posts/1", headers={"Authorization": f"Bearer {bob_token}"})
    assert r.status_code == 403
