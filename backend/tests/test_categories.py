SEED = ["餐饮", "交通", "购物", "住房", "娱乐", "医疗", "教育", "其他"]


def _register_and_login(client, email: str, password: str = "secret123") -> dict:
    r = client.post("/api/auth/register", json={"email": email, "password": password})
    assert r.status_code == 201
    r = client.post("/api/auth/login", json={"email": email, "password": password})
    assert r.status_code == 200
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


def test_register_seeds_categories(client, auth_header):
    # auth_header fixture：注册用户后返回 {"Authorization": "Bearer ..."}
    r = client.get("/api/categories", headers=auth_header)
    assert r.status_code == 200
    names = [c["name"] for c in r.json()]
    for n in SEED:
        assert n in names


def test_create_update_delete_category(client, auth_header):
    r = client.post("/api/categories", headers=auth_header, json={"name": "宠物", "sort_order": 90})
    assert r.status_code == 201
    cid = r.json()["id"]
    r = client.patch(f"/api/categories/{cid}", headers=auth_header, json={"name": "萌宠"})
    assert r.status_code == 200
    assert r.json()["name"] == "萌宠"
    r = client.delete(f"/api/categories/{cid}", headers=auth_header)
    assert r.status_code == 204


def test_categories_require_auth(client):
    assert client.get("/api/categories").status_code >= 400
    assert client.post("/api/categories", json={"name": "x"}).status_code >= 400
    assert client.patch("/api/categories/1", json={"name": "x"}).status_code >= 400
    assert client.delete("/api/categories/1").status_code >= 400


def test_cross_user_patch_delete_returns_404(client, auth_header):
    r = client.post(
        "/api/categories",
        headers=auth_header,
        json={"name": "仅自己可见", "sort_order": 99},
    )
    assert r.status_code == 201
    cid = r.json()["id"]

    other = _register_and_login(client, "other@example.com")
    assert client.patch(f"/api/categories/{cid}", headers=other, json={"name": "劫持"}).status_code == 404
    assert client.delete(f"/api/categories/{cid}", headers=other).status_code == 404

    # 原用户仍可访问
    r = client.get("/api/categories", headers=auth_header)
    assert r.status_code == 200
    assert any(c["id"] == cid for c in r.json())


def test_client_id_conflict_returns_409(client, auth_header):
    r = client.post(
        "/api/categories",
        headers=auth_header,
        json={"name": "A", "sort_order": 1, "client_id": "cid-1"},
    )
    assert r.status_code == 201

    r = client.post(
        "/api/categories",
        headers=auth_header,
        json={"name": "B", "sort_order": 2, "client_id": "cid-1"},
    )
    assert r.status_code == 409

    r = client.post(
        "/api/categories",
        headers=auth_header,
        json={"name": "C", "sort_order": 3, "client_id": "cid-2"},
    )
    assert r.status_code == 201
    cid = r.json()["id"]
    r = client.patch(
        f"/api/categories/{cid}",
        headers=auth_header,
        json={"client_id": "cid-1"},
    )
    assert r.status_code == 409
