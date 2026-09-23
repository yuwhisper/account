def _tx(client_id="c1"):
    return {
        "client_id": client_id,
        "amount_cents": 3650,
        "merchant": "瑞幸",
        "source": "wechat",
        "category_id": None,
        "note": "",
        "occurred_at": "2026-08-12T10:00:00+08:00",
        "updated_at": "2026-08-12T10:00:05+08:00",
        "type": "expense",
    }


def test_push_idempotent(client, auth_header, category_id):
    payload = {"transactions": [{**_tx(), "category_id": category_id}]}
    r1 = client.post("/api/transactions/sync/push", headers=auth_header, json=payload)
    r2 = client.post("/api/transactions/sync/push", headers=auth_header, json=payload)
    assert r1.status_code == 200 and r2.status_code == 200
    r = client.get("/api/transactions", headers=auth_header)
    assert len(r.json()) == 1


def test_pull_since(client, auth_header, category_id):
    client.post(
        "/api/transactions/sync/push",
        headers=auth_header,
        json={"transactions": [{**_tx("c2"), "category_id": category_id}]},
    )
    r = client.get(
        "/api/transactions/sync/pull",
        headers=auth_header,
        params={"since": "1970-01-01T00:00:00Z"},
    )
    assert r.status_code == 200
    assert len(r.json()["transactions"]) >= 1
    assert "server_time" in r.json()


def test_push_older_updated_at_does_not_overwrite(client, auth_header, category_id):
    newer = {**_tx("c3"), "category_id": category_id, "merchant": "新商户", "updated_at": "2026-08-12T12:00:00+08:00"}
    older = {**_tx("c3"), "category_id": category_id, "merchant": "旧商户", "updated_at": "2026-08-12T11:00:00+08:00"}
    assert client.post("/api/transactions/sync/push", headers=auth_header, json={"transactions": [newer]}).status_code == 200
    assert client.post("/api/transactions/sync/push", headers=auth_header, json={"transactions": [older]}).status_code == 200
    r = client.get("/api/transactions", headers=auth_header)
    assert r.status_code == 200
    items = [t for t in r.json() if t["client_id"] == "c3"]
    assert len(items) == 1
    assert items[0]["merchant"] == "新商户"


def test_push_plus08_older_then_newer_overwrites(client, auth_header, category_id):
    older = {
        **_tx("c_tz"),
        "category_id": category_id,
        "merchant": "旧商户",
        "updated_at": "2026-08-12T11:00:00+08:00",
    }
    newer = {
        **_tx("c_tz"),
        "category_id": category_id,
        "merchant": "新商户",
        "updated_at": "2026-08-12T12:00:00+08:00",
    }
    assert (
        client.post(
            "/api/transactions/sync/push",
            headers=auth_header,
            json={"transactions": [older]},
        ).status_code
        == 200
    )
    assert (
        client.post(
            "/api/transactions/sync/push",
            headers=auth_header,
            json={"transactions": [newer]},
        ).status_code
        == 200
    )
    r = client.get("/api/transactions", headers=auth_header)
    items = [t for t in r.json() if t["client_id"] == "c_tz"]
    assert len(items) == 1
    assert items[0]["merchant"] == "新商户"


def test_pull_since_server_time_excludes_already_seen(client, auth_header, category_id):
    assert (
        client.post(
            "/api/transactions/sync/push",
            headers=auth_header,
            json={"transactions": [{**_tx("c_cursor"), "category_id": category_id}]},
        ).status_code
        == 200
    )
    r1 = client.get(
        "/api/transactions/sync/pull",
        headers=auth_header,
        params={"since": "1970-01-01T00:00:00Z"},
    )
    assert r1.status_code == 200
    body1 = r1.json()
    assert any(t["client_id"] == "c_cursor" for t in body1["transactions"])
    server_time = body1["server_time"]

    r2 = client.get(
        "/api/transactions/sync/pull",
        headers=auth_header,
        params={"since": server_time},
    )
    assert r2.status_code == 200
    assert not any(t["client_id"] == "c_cursor" for t in r2.json()["transactions"])


def test_push_batch_duplicate_client_id_no_500(client, auth_header, category_id):
    older = {
        **_tx("c_dup"),
        "category_id": category_id,
        "merchant": "旧",
        "updated_at": "2026-08-12T10:00:00+08:00",
    }
    newer = {
        **_tx("c_dup"),
        "category_id": category_id,
        "merchant": "新",
        "updated_at": "2026-08-12T11:00:00+08:00",
    }
    r = client.post(
        "/api/transactions/sync/push",
        headers=auth_header,
        json={"transactions": [older, newer]},
    )
    assert r.status_code == 200
    items = [
        t
        for t in client.get("/api/transactions", headers=auth_header).json()
        if t["client_id"] == "c_dup"
    ]
    assert len(items) == 1
    assert items[0]["merchant"] == "新"


def test_cross_user_isolation(client, auth_header, category_id):
    assert (
        client.post(
            "/api/transactions/sync/push",
            headers=auth_header,
            json={"transactions": [{**_tx("c_a"), "category_id": category_id}]},
        ).status_code
        == 200
    )

    r = client.post("/api/auth/register", json={"email": "other@example.com", "password": "secret123"})
    assert r.status_code == 201
    r = client.post("/api/auth/login", json={"email": "other@example.com", "password": "secret123"})
    assert r.status_code == 200
    other_header = {"Authorization": f"Bearer {r.json()['access_token']}"}

    listed = client.get("/api/transactions", headers=other_header)
    assert listed.status_code == 200
    assert listed.json() == []

    pulled = client.get(
        "/api/transactions/sync/pull",
        headers=other_header,
        params={"since": "1970-01-01T00:00:00Z"},
    )
    assert pulled.status_code == 200
    assert pulled.json()["transactions"] == []


def test_delete_transaction(client, auth_header, category_id):
    assert (
        client.post(
            "/api/transactions/sync/push",
            headers=auth_header,
            json={"transactions": [{**_tx("c_del"), "category_id": category_id}]},
        ).status_code
        == 200
    )
    listed = client.get("/api/transactions", headers=auth_header)
    row = next(t for t in listed.json() if t["client_id"] == "c_del")
    deleted = client.delete(f"/api/transactions/{row['id']}", headers=auth_header)
    assert deleted.status_code == 204
    listed2 = client.get("/api/transactions", headers=auth_header)
    assert all(t["client_id"] != "c_del" for t in listed2.json())
    missing = client.delete(f"/api/transactions/{row['id']}", headers=auth_header)
    assert missing.status_code == 404

