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


def test_transactions_require_auth(client):
    assert client.get("/api/transactions").status_code >= 400
    assert client.post("/api/transactions/sync/push", json={"transactions": []}).status_code >= 400
    assert (
        client.get("/api/transactions/sync/pull", params={"since": "1970-01-01T00:00:00Z"}).status_code
        >= 400
    )
