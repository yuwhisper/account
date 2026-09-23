# 语声记账（自动记账）

Android 原生客户端 + FastAPI 后端：付款通知 / 无障碍双通道捕获 → 居中确认入账 → 本地 Room 优先，登录后云同步。

仓库：https://github.com/yuwhisper/account

## 目录

| 路径 | 说明 |
|------|------|
| `android/` | Kotlin 客户端 |
| `ios/` | Swift 客户端。截图识别后确认入账 |
| `backend/` | FastAPI 服务 |
| `download/` | APK 下载页、隐私说明、验收清单 |
| `docs/superpowers/` | 规格与实现计划 |

## 跑后端

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
.\.venv\Scripts\uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

- API 文档：http://127.0.0.1:8000/docs
- 模拟器访问本机后端：`http://10.0.2.2:8000`
- 真机：使用电脑局域网 IP + `8000`
- 开发用 JWT `SECRET_KEY` 见 `backend/app/config.py`，上线务必更换

更多说明见 [`backend/README.md`](backend/README.md)。

## 打开 iOS 工程

用 Xcode 打开 `ios/YuWhisperAccount.xcodeproj`（需要完整 Xcode，不能只用 Command Line Tools）。签名里给 App 和 Share Extension 都选同一个 Team，并确认 App Group `group.com.yuwhisper.account` 已启用。

iOS 不能读取微信 / 支付宝的通知或页面。自动记账的做法是：付款成功后截屏，用「快捷指令 → 自动化 → 截屏时」把最新截图交给「识别付款截图」。也可以从相册识别，或把截图分享到「语声记账」。识别结果只进待确认，选分类后才入账，登录后走同一套后端同步。

模拟器访问本机后端用 `http://127.0.0.1:8000`。真机改成电脑的局域网地址。

不装 Xcode 时，可以在 Mac 上跑解析和入账检查：

```bash
cd ios/LedgerCore
swift run LedgerCoreChecks
```

## 打开 Android 工程

1. 用 Android Studio 打开 `android/` 目录。
2. 等待 Gradle Sync；JDK 17。
3. 连接模拟器或真机，运行 `app`（debug）。
4. 在应用内完成权限引导；云同步前先启动后端，并在登录页配置可达的 API 地址。

命令行构建（需 **JDK 17+**，勿用仅含 JBR 且缺 `jlink` 的运行时）：

```powershell
cd android
$env:JAVA_HOME = "C:\path\to\jdk-17"   # 按本机路径调整
.\gradlew :app:assembleDebug
# 或 release（见下方签名）
.\gradlew :app:assembleRelease
```

## 签名与 Release APK

- **勿将真实 keystore / `keystore.properties` 提交进仓库**（已在 `.gitignore`）。
- 示例配置：复制 `android/keystore.properties.example` 为 `android/keystore.properties`，按注释填写。
- `android/app/build.gradle.kts`：若存在 `keystore.properties` 则用正式签名；**文件不存在时 release 回退为 debug 签名**，便于无密钥环境打出可安装包。
- 产物：
  - Release：`android/app/build/outputs/apk/release/app-release.apk`
  - Debug：`android/app/build/outputs/apk/debug/app-debug.apk`

无正式密钥时可用 debug 签名的 release 包或 `assembleDebug` 做侧载验证；对外分发前请配置正式签名。

## 上传 GitHub Releases

1. 本地打出 APK（建议正式签名 release）。
2. 在 GitHub 仓库创建 Release（如标签 `v0.1.0`），上传 `app-release.apk`（或重命名后的 asset）。
3. 更新 [`download/index.html`](download/index.html) 中的版本号与 APK 直链占位为实际 Releases asset URL。
4. 将 `download/` 静态页托管到任意静态站点，或直接把 Releases 链接发给用户。

下载页：[`download/index.html`](download/index.html)  
隐私说明：[`download/privacy.html`](download/privacy.html)  
验收勾选：[`download/ACCEPTANCE.md`](download/ACCEPTANCE.md)

## 许可与范围

第一版侧载分发，不上应用商店。无障碍 / 通知仅用于记账，详见隐私页。
