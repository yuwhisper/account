SEED = ["餐饮", "交通", "购物", "住房", "娱乐", "医疗", "教育", "其他"]


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
