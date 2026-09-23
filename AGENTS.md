# 语声记账

Android（Kotlin / Compose）客户端 + FastAPI 后端。付款通知与无障碍双通道捕获，用户确认后入账。本地 Room 是账本，登录后才云同步。

产品说明见 `README.md`。规格见 `docs/superpowers/`。以代码为准；README 里「release 无密钥时回退 debug 签名」已过时。

## 目录

| 路径 | 说明 |
|------|------|
| `android/` | 客户端。用 Android Studio 打开这一层，不是仓库根目录 |
| `ios/` | iOS 客户端。用 Xcode 打开 `ios/YuWhisperAccount.xcodeproj`。解析和账本在 `ios/LedgerCore` |
| `backend/` | FastAPI，包名 `app` |
| `download/` | 侧载页、隐私说明、验收清单 |
| `docs/superpowers/` | 设计与实现计划，不是运行时代码 |

包名 `com.yuwhisper.account`。`minSdk` 26，`compileSdk` / `targetSdk` 35，JDK 17，Kotlin 2.0。

## 命令

后端（在 `backend/` 下，测试用内存 SQLite，不碰 `account.db`）：

```bash
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
.venv/bin/pytest
```

Android（在 `android/` 下，需要带 `jlink` 的 JDK 17）：

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

模拟器访问本机后端用 `http://10.0.2.2:8000`。明文只放行 `10.0.2.2`、`127.0.0.1`、`localhost`（`network_security_config.xml`）。局域网 IP 必须 HTTPS，或先改网络安全配置。

## 数据与同步

- 金额一律用整数分（`amountCents` / `amount_cents`），展示时再除以 100。
- 同步身份是 `client_id`（用户内唯一），不是自增主键。分类、流水都带 `updated_at`，冲突取较新的一侧。
- 云同步顺序在 `CloudSync`：删分类 → 删流水 → 拉分类 → 推分类 → 推 `pendingSync` 流水 → 按 `lastPull` 拉流水。流水的 `category_id` 必须是服务端 id。
- 拉流水的上界在查询前冻结为 `server_time`。不要把响应构造之后的时间回给客户端，否则中间提交的行会被永久跳过。
- 后端没有 Alembic。表由 `Base.metadata.create_all` 创建。已有库加列要单独迁移，不能假设启动会改表。
- Room `version = 1`，没有 `fallbackToDestructiveMigration`。改实体必须加 Migration，否则已安装的应用会在打开时崩溃。
- 默认库是 `DATABASE_URL=sqlite:///./account.db`。MySQL 要自己设 URL；连接时会 `SET time_zone = '+00:00'`。
- `Mapped[Optional[...]]` 不要用。SQLAlchemy 2.0.36 在 Python 3.14 上对 Union 有问题，可空列写成不带 `Mapped` 的 `mapped_column`。

## 捕获

通知监听与无障碍都进 `ConfirmDispatcher.onPaymentDetected`。同一笔在 `DEDUPE_WINDOW_SECONDS`（8 秒）内按来源 + 金额 + 商户去重；金额未知时窗口只有 15 秒。不要把窗口拉长，否则同金额连刷会被丢掉。

捕获只产生待确认记录和「待入账」通知，不自动入账，也不把应用拉到前台。确认 UI 优先无障碍浮层，其次 `SYSTEM_ALERT_WINDOW`。解析规则在 `PaymentParser`，单测在 `PaymentParseTest` / `DedupeTest`。

iOS 用同一套金额、`client_id` 和确认后入账规则，但没有通知监听和无障碍。自动捕获只走截图：快捷指令、分享扩展或相册，文字识别后进待确认。逻辑在 `ios/LedgerCore`，检查命令是 `swift run LedgerCoreChecks`。App Group 是 `group.com.yuwhisper.account`。

## 不要提交

`keystore.properties`、`*.jks`、`*.apk`、`.secret_key`、`*.db`、`local.properties`。Release 没有 `android/keystore.properties` 时直接失败，不要改回 debug 签名。

JWT 密钥来自 `SECRET_KEY`，否则写入 `backend/.secret_key`（至少 32 字符）。开发密钥不能当生产密钥。
