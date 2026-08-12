# 安卓自动记账 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在 `记账/` 交付可安装的安卓自动记账 App：付款后居中卡片强制选分类入账，双通道捕获（通知 + 无障碍），多用户 FastAPI 云同步，APK 可下载。

**架构：** 客户端 Kotlin + Room 本地优先；`NotificationListenerService` 与 `AccessibilityService` 将解析结果送入统一去重队列并弹出确认卡；后端 FastAPI + SQLite（JWT）提供注册登录与分类/流水增量同步；GitHub Releases / 静态页分发 APK。

**技术栈：** Kotlin、Jetpack (Room / ViewModel / Compose 或 XML View)、FastAPI、SQLAlchemy、JWT (python-jose)、passlib、pytest、Android Gradle

**规格：** `docs/superpowers/specs/2026-08-12-android-auto-ledger-design.md`  
**仓库：** https://github.com/yuwhisper/account.git

**执行约定：** 在 `记账/` 目录提交；提交信息格式 `YYYY-MM-dd HH:mm` + 简述，并 `git push origin master`。纯逻辑优先 TDD；真机权限/支付场景在任务末手工验收。

---

## 文件结构

| 路径 | 职责 |
|------|------|
| `backend/pyproject.toml` 或 `backend/requirements.txt` | Python 依赖 |
| `backend/app/main.py` | FastAPI 入口、CORS、路由挂载 |
| `backend/app/config.py` | 密钥、数据库 URL、JWT 过期 |
| `backend/app/db.py` | SQLAlchemy engine / Session |
| `backend/app/models.py` | User / Category / Transaction ORM |
| `backend/app/schemas.py` | Pydantic 请求响应模型 |
| `backend/app/auth.py` | 密码哈希、JWT 签发校验、依赖 `get_current_user` |
| `backend/app/routers/auth.py` | 注册 / 登录 / 刷新 |
| `backend/app/routers/categories.py` | 分类 CRUD + 同步拉取 |
| `backend/app/routers/transactions.py` | 流水推送 / 拉取 / 列表 |
| `backend/tests/` | pytest API 与幂等测试 |
| `android/` | Android Studio 工程根（applicationId `com.yuwhisper.account`） |
| `android/app/src/main/java/.../data/` | Room Entity/Dao/Database、仓库 |
| `android/app/src/main/java/.../domain/` | 付款解析、去重、分类种子 |
| `android/app/src/main/java/.../capture/` | 通知监听、无障碍、运行状态通知 |
| `android/app/src/main/java/.../ui/` | 账本、确认卡、设置、引导、登录 |
| `android/app/src/main/java/.../sync/` | Retrofit API、同步 Worker |
| `android/app/src/test/` | JVM 单测（解析/去重） |
| `download/index.html` | APK 下载与权限/隐私说明 |
| `README.md` | 本地运行后端、打开 Android 工程、权限说明 |

---

## 阶段总览

| 阶段 | 任务 | 可验证产出 |
|------|------|------------|
| A 后端 | 1–3 | 注册登录、分类/流水同步 API 全绿 |
| B 本地账本 | 4–6 | 安装 App：手动记账、列表、统计、分类、导出 |
| C 自动记账 | 7–10 | 双通道 + 确认卡 + 识别场景 + 运行状态 |
| D 云同步与分发 | 11–13 | 登录同步、下载页、Release APK |

---

### 任务 1：后端骨架 + 用户注册登录

**文件：**
- 创建：`backend/requirements.txt`
- 创建：`backend/app/__init__.py`（可空）
- 创建：`backend/app/config.py`、`db.py`、`models.py`、`schemas.py`、`auth.py`、`main.py`
- 创建：`backend/app/routers/auth.py`
- 创建：`backend/tests/conftest.py`、`backend/tests/test_auth.py`

- [ ] **步骤 1：写入依赖与失败测试**

`backend/requirements.txt`：

```text
fastapi==0.115.6
uvicorn[standard]==0.34.0
sqlalchemy==2.0.36
python-jose[cryptography]==3.3.0
passlib[bcrypt]==1.7.4
bcrypt==4.0.1
httpx==0.28.1
pytest==8.3.4
```

`backend/tests/test_auth.py`：

```python
def test_register_and_login(client):
    r = client.post("/api/auth/register", json={"email": "a@b.com", "password": "secret123"})
    assert r.status_code == 201
    r = client.post("/api/auth/login", json={"email": "a@b.com", "password": "secret123"})
    assert r.status_code == 200
    body = r.json()
    assert "access_token" in body
    assert body["token_type"] == "bearer"

def test_login_wrong_password(client):
    client.post("/api/auth/register", json={"email": "c@d.com", "password": "secret123"})
    r = client.post("/api/auth/login", json={"email": "c@d.com", "password": "bad"})
    assert r.status_code == 401
```

`backend/tests/conftest.py`：使用内存 SQLite，创建 `TestClient(app)` fixture，每个测试前 `Base.metadata.create_all`。

- [ ] **步骤 2：运行测试确认失败**

```powershell
cd d:\zyx\python\pycharm\project\记账\backend
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
.\.venv\Scripts\python -m pytest tests\test_auth.py -v
```

预期：FAIL（模块/路由不存在）

- [ ] **步骤 3：实现最少可通代码**

- `User(id, email unique, password_hash, created_at)`
- `POST /api/auth/register` → 201；重复邮箱 → 409
- `POST /api/auth/login` → `{access_token, token_type}`
- JWT：`sub=user_id`，`SECRET_KEY` 来自 `config`（开发默认写死并在 README 提醒生产替换）
- `get_current_user`：`Authorization: Bearer ...`

- [ ] **步骤 4：运行测试确认通过**

```powershell
.\.venv\Scripts\python -m pytest tests\test_auth.py -v
```

预期：PASS

- [ ] **步骤 5：Commit 并推送**

```powershell
cd d:\zyx\python\pycharm\project\记账
git add backend
git commit -m "2026-08-12 HH:mm 后端注册登录与 JWT"
git push
```

---

### 任务 2：分类 API + 注册种子分类

**文件：**
- 创建：`backend/app/routers/categories.py`
- 修改：`backend/app/models.py`、`schemas.py`、`main.py`
- 创建：`backend/tests/test_categories.py`

- [ ] **步骤 1：编写失败测试**

```python
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
```

- [ ] **步骤 2：运行测试确认失败**

```powershell
.\.venv\Scripts\python -m pytest tests\test_categories.py -v
```

- [ ] **步骤 3：实现**

- `Category(id, user_id, name, sort_order, updated_at, client_id nullable unique per user)`
- 注册成功后插入 8 个种子分类
- CRUD 均需登录；只能操作自己的分类

- [ ] **步骤 4：测试通过后 commit 推送**

提交信息：`后端分类 CRUD 与注册种子`

---

### 任务 3：流水同步 API（推送幂等 + 增量拉取）

**文件：**
- 创建：`backend/app/routers/transactions.py`
- 修改：`models.py`、`schemas.py`、`main.py`
- 创建：`backend/tests/test_transactions.py`

- [ ] **步骤 1：编写失败测试**

```python
def _tx(client_id="c1"):
    return {
        "client_id": client_id,
        "amount_cents": 3650,
        "merchant": "瑞幸",
        "source": "wechat",
        "category_id": None,  # 测试里先创建分类再填真实 id
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
    client.post("/api/transactions/sync/push", headers=auth_header, json={"transactions": [{**_tx("c2"), "category_id": category_id}]})
    r = client.get("/api/transactions/sync/pull", headers=auth_header, params={"since": "1970-01-01T00:00:00Z"})
    assert r.status_code == 200
    assert len(r.json()["transactions"]) >= 1
    assert "server_time" in r.json()
```

- [ ] **步骤 2：实现**

- `Transaction`：`user_id`, `client_id`（用户内唯一）, `amount_cents`（整数分）, `merchant`, `source`, `category_id`, `note`, `occurred_at`, `updated_at`, `type` (`expense`|`income`)
- `POST /api/transactions/sync/push`：按 `client_id` upsert；若已存在且入站 `updated_at` 较旧则保留服务端
- `GET /api/transactions/sync/pull?since=`：返回 `updated_at > since` 的流水 + `server_time`
- `GET /api/transactions`：当前用户全量列表（导出/调试）

- [ ] **步骤 3：pytest 全绿后 commit 推送**

提交信息：`后端流水幂等推送与增量拉取`

- [ ] **步骤 4：补充 README 启动命令**

```powershell
cd backend
.\.venv\Scripts\uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

---

### 任务 4：Android 工程骨架 + Room 模型

**文件：**
- 创建：`android/` Android Application 工程（minSdk 26，targetSdk 34+）
- 创建：Entity `CategoryEntity`、`TransactionEntity`、`PendingPaymentEntity`、`WatchAppEntity`、`SyncMetaEntity`
- 创建：对应 Dao + `AppDatabase` + `TypeConverters`（Instant）
- 创建：`android/app/src/test/.../Example` 可替换为数据库迁移烟雾测试（可选）

- [ ] **步骤 1：用 Android Studio / `gradle` 生成空 Activity 工程**，`applicationId` = `com.yuwhisper.account`，启用 ViewBinding 或 Compose（任选其一，后续 UI 任务保持一致；**推荐 Compose**）。

- [ ] **步骤 2：定义 Entity 字段与规格对齐**

- `TransactionEntity`：`localId`, `clientId`(UUID), `amountCents`, `merchant`, `source`, `categoryLocalId`, `note`, `occurredAt`, `updatedAt`, `type`, `pendingSync`(Boolean), `serverId`(可空)
- `PendingPaymentEntity`：解析结果快照 + `createdAt`，无忽略，只能确认转流水
- `WatchAppEntity`：`packageName`, `label`, `enabled`
- 预置 watch 包名常量：`com.tencent.mm`（微信）、`com.eg.android.AlipayGphone`（支付宝）、云闪付等

- [ ] **步骤 3：首次打开 DB 时写入种子分类与默认识别场景**

- [ ] **步骤 4：Commit 推送**

提交信息：`Android 工程与 Room 数据模型`

---

### 任务 5：本地账本 UI（列表 / 手动记账 / 分类 / 简单统计 / 导出）

**文件：**
- `ui/ledger/*`、`ui/category/*`、`ui/stats/*`、`ui/manual/*`
- `data/LedgerRepository.kt`

- [ ] **步骤 1：Repository 方法**

- `observeTransactions()`、`addManualTransaction(...)`、`observeCategories()`、`upsertCategory`、`deleteCategory`
- `exportCsv(file: File)`：表头 `occurred_at,type,amount,merchant,source,category,note`

- [ ] **步骤 2：UI**

- 首页：按日分组流水；FAB「记一笔」
- 记一笔：金额、类型（支出/收入）、分类下拉、商户、备注、时间
- 分类管理页：增删改
- 统计页：本月支出合计 + 按分类汇总（列表即可，不必图表库）
- 设置入口：导出 CSV（MediaStore / 分享 Intent）

- [ ] **步骤 3：安装到模拟器/真机手工点验**列表、记账、导出

- [ ] **步骤 4：Commit 推送**

提交信息：`本地账本列表分类统计与 CSV 导出`

---

### 任务 6：付款解析与去重（纯 Kotlin 单测）

**文件：**
- 创建：`android/.../domain/PaymentParse.kt`、`Dedupe.kt`
- 创建：`android/app/src/test/.../PaymentParseTest.kt`、`DedupeTest.kt`

- [ ] **步骤 1：失败测试**

```kotlin
@Test
fun parseWechatNotification() {
    val raw = ParsedRaw(title = "微信支付", text = "收款方商家：瑞幸咖啡\n支付金额：¥36.50")
    val r = PaymentParser.parseWechat(raw)
    assertEquals(3650, r.amountCents)
    assertTrue(r.merchant.contains("瑞幸"))
}

@Test
fun dedupeSamePaymentWithinWindow() {
    val a = Candidate("wechat", 3650, "瑞幸", Instant.parse("2026-08-12T10:00:00+08:00"))
    val b = Candidate("wechat", 3650, "瑞幸", Instant.parse("2026-08-12T10:00:20+08:00"))
    assertTrue(Dedupe.isDuplicate(a, listOf(a), windowSeconds = 120))
    assertTrue(Dedupe.isDuplicate(b, listOf(a), windowSeconds = 120))
}
```

（按真实通知样例迭代正则；支付宝另写 `parseAlipay`。）

- [ ] **步骤 2：实现解析器与去重后 `./gradlew test` 通过**

- [ ] **步骤 3：Commit 推送**

提交信息：`付款通知解析与去重单测`

---

### 任务 7：居中确认卡 + 待确认队列（强制入账）

**文件：**
- `ui/confirm/ConfirmPaymentActivity`（或 Dialog Compose，`THEME` 透明 + 压暗）
- `capture/ConfirmDispatcher.kt`
- Manifest：`SYSTEM_ALERT_WINDOW` 相关与导出 Activity（`showWhenLocked` / `turnScreenOn` 按需）

- [ ] **步骤 1：`ConfirmDispatcher.onPaymentDetected(candidate)`**

1. 若 `Dedupe` 命中已有 pending/已入账 → return  
2. 写入 `PendingPaymentEntity`  
3. 尝试启动确认界面；失败则发「待入账」通知（点击打开同一确认页）  
4. **无忽略按钮**；返回键不删除 pending（可回到通知再进）

- [ ] **步骤 2：确认页 UI（规格视觉）**

- 来源、大号金额、商户（可编辑）、分类下拉（必选）、备注、唯一按钮「确认入账」
- 确认：写 `TransactionEntity`（`pendingSync=true`）、删 pending、取消待入账通知

- [ ] **步骤 3：用调试入口「模拟一笔付款」验证强制确认流**

- [ ] **步骤 4：Commit 推送**

提交信息：`居中确认入账卡与待确认队列`

---

### 任务 8：通知监听通道 + 识别场景过滤

**文件：**
- `capture/PaymentNotificationListener.kt`
- `ui/settings/WatchAppsScreen.kt`
- `res/xml` 如需；Manifest 声明 `BIND_NOTIFICATION_LISTENER_SERVICE`

- [ ] **步骤 1：Listener**

- `onNotificationPosted`：若 `packageName` 在 enabled `WatchApp` 中 → 解析 → `ConfirmDispatcher`
- 忽略本 App 自己的通知

- [ ] **步骤 2：识别场景 UI**

- 列表开关微信/支付宝/银行等；持久化 `WatchAppEntity`

- [ ] **步骤 3：真机开通知使用权，用支付 App 测试通知（或 adb 模拟通知）**

- [ ] **步骤 4：Commit 推送**

提交信息：`通知监听自动记账与识别场景`

---

### 任务 9：无障碍通道 + 运行状态通知

**文件：**
- `capture/PaymentAccessibilityService.kt` + `res/xml/accessibility_service.xml`
- `capture/AutoBookkeepingStatusService.kt`（前台服务或常驻通知）

- [ ] **步骤 1：无障碍**

- 监听窗口变化；针对微信/支付宝成功页特征节点抽金额/商户（规则表可配置字符串）
- 送入同一 `ConfirmDispatcher`（去重）

- [ ] **步骤 2：运行状态**

- 自动记账总开关 ON 且（通知监听或无障碍）可用 → 通知「自动记账运行中」
- 权限丢失或服务断开 → 「自动记账已停止」+ 跳转设置

- [ ] **步骤 3：真机分别测仅通知 / 仅无障碍 / 双开去重**

- [ ] **步骤 4：Commit 推送**

提交信息：`无障碍捕获与自动记账运行状态通知`

---

### 任务 10：权限分步引导页

**文件：**
- `ui/onboarding/PermissionOnboarding.kt`

- [ ] **步骤 1：步骤顺序（规格）**

1. 无障碍  
2. 通知使用权  
3. 悬浮窗  
4. 通知栏权限（Android 13+ `POST_NOTIFICATIONS`）  
5. 电池白名单 / 后台运行说明（跳转厂商设置 Intent）

每步检测 `isGranted`；未完成不可标「自动记账已就绪」。短信权限不进入必做步骤。

- [ ] **步骤 2：设置页复用同一套状态检查**

- [ ] **步骤 3：Commit 推送**

提交信息：`自动记账分步权限引导`

---

### 任务 11：客户端登录与云同步

**文件：**
- `sync/ApiClient.kt`、`AuthApi`、`SyncApi`
- `sync/SyncWorker.kt`（WorkManager，联网约束）
- `ui/auth/LoginScreen.kt`

- [ ] **步骤 1：登录注册 UI**；Token 存 EncryptedSharedPreferences

- [ ] **步骤 2：同步**

- 启动/定时/`pendingSync` 变化时：push 本地未同步流水与分类变更；pull `since=SyncMeta.lastPull`；按 `updated_at` 合并
- 未登录：本地照常记账，登录后一次性 push

- [ ] **步骤 3：集成测试**

- 后端本地起 8000；模拟器 `10.0.2.2:8000`；真机用局域网 IP  
- 设备 A 记账 → 设备 B 登录同账号能拉到

- [ ] **步骤 4：Commit 推送**

提交信息：`客户端登录与流水分类云同步`

---

### 任务 12：下载页 + 隐私说明

**文件：**
- `download/index.html`
- `download/privacy.html`（可短文）
- 根目录 `README.md` 补充 Releases 上传步骤

- [ ] **步骤 1：下载页内容**

- 应用名、版本、APK 链接占位（指向 GitHub Releases asset）
- 权限清单、未知来源说明、隐私要点（与规格第 8 节一致）

- [ ] **步骤 2：Commit 推送**

提交信息：`APK 下载页与隐私说明`

---

### 任务 13：签名构建与 GitHub Release 验收

**文件：**
- `android/app/build.gradle.kts` release 签名配置（密钥路径用本地 `keystore.properties`，**勿提交 keystore**）
- `.gitignore`：`.venv/`、`*.apk`、`keystore.properties`、`*.jks`、`.idea/`、`local.properties`

- [ ] **步骤 1：打 release APK**

```powershell
cd android
.\gradlew assembleRelease
```

- [ ] **步骤 2：上传到 GitHub Release**（浏览器或 `git`/`gh`）；更新 `download/index.html` 链接

- [ ] **步骤 3：对照规格验收清单勾选**

- [ ] 微信/支付宝能弹出确认卡并入账  
- [ ] 双通道至少一条可用；运行状态通知正确  
- [ ] 识别场景开关生效  
- [ ] 分类/统计/导出可用  
- [ ] 换机登录能拉回流水  
- [ ] 陌生人能按下载页装上 APK  

- [ ] **步骤 4：最终 commit 推送**（若有链接修正）

提交信息：`release 构建说明与验收记录`

---

## 规格覆盖自检

| 规格需求 | 任务 |
|----------|------|
| Kotlin 客户端 + FastAPI | 1–5, 11 |
| 通知 + 无障碍双通道 | 8–9 |
| 居中卡强制入账、无忽略 | 7 |
| 识别场景 | 8 |
| 运行状态通知 | 9 |
| 分步权限引导 | 10 |
| 预置分类可编辑 | 2, 5 |
| 列表/统计/导出 | 5 |
| 本地优先云同步、幂等 | 3, 11 |
| 邮箱密码 JWT | 1, 11 |
| APK 下载页 / Releases | 12–13 |
| 待确认队列 | 7 |
| 短信可选不阻塞 | 10（不纳入必做） |

---

## 风险与实现提示

- 支付 App 改版会导致解析失败：把规则集中在 `PaymentParser` / 无障碍节点表，便于热改。
- 国产机杀后台：引导页文案写清；运行状态通知帮助排查。
- 无障碍属于敏感能力：隐私页必须写清用途；侧载分发，不上架。
- 金额一律 `amount_cents` 整数，避免浮点。
