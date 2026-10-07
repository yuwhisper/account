# 语声记账

付款之后先认出金额和商户，你确认分类后才入账。账先记在手机里，登录后再和服务器同步。Android 桌面上的名字是「惜夏记」。

Android 同时看付款通知和付款成功页。iOS 读不了其他应用的通知和界面，改为识别付款成功截图。两端都不会在你确认之前入账。

仓库：https://github.com/yuwhisper/account

## 安装

Android 8.0 及以上，安装包放在 GitHub Releases，不上应用商店。

[下载 v0.1.0](https://github.com/yuwhisper/account/releases/download/v0.1.0/yuwhisper-account-0.1.0.apk)

用手机浏览器打开上面的链接。如果系统提示不能安装未知应用，到设置里允许当前浏览器或文件管理器安装。装好后按应用里的引导打开通知使用权、无障碍和悬浮窗。这些权限只用来识别付款并弹出确认，不会自动记上一笔。

安装步骤、权限和隐私说明在 [`download/`](download/index.html)。

iOS 没有现成的安装包。用 Xcode 打开 `ios/YuWhisperAccount.xcodeproj`，装到自己的手机。付款成功后截一张图，用快捷指令在「截屏时」把最新照片交给「识别付款截图」，也可以从相册选图，或把截图分享给「语声记账」。

## 目录

| 路径 | 说明 |
|------|------|
| `android/` | Kotlin / Compose。用 Android Studio 打开这一层，不是仓库根目录 |
| `ios/` | Swift 客户端。用 Xcode 打开 `ios/YuWhisperAccount.xcodeproj` |
| `backend/` | FastAPI。注册登录、分类和流水同步 |
| `download/` | 下载页、隐私说明、验收清单 |
| `docs/superpowers/` | 设计与实现计划 |

包名 `com.yuwhisper.account`。Android `minSdk` 26，`compileSdk` 35。

## 后端

在 `backend/` 下：

```bash
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
.venv/bin/pytest
```

接口文档：http://127.0.0.1:8000/docs

Android 模拟器访问这台电脑用 `http://10.0.2.2:8000`，iOS 模拟器用 `http://127.0.0.1:8000`。真机填电脑的局域网地址。Android 只放行 `10.0.2.2`、`127.0.0.1` 和 `localhost` 的明文请求，局域网 IP 要走 HTTPS。

JWT 密钥读环境变量 `SECRET_KEY`（至少 32 个字符）。没设置时，开发环境会把密钥写进 `backend/.secret_key`。这个文件和开发密钥都不能当生产密钥，也不要提交。

默认数据库是当前目录下的 `account.db`。要换 MySQL，设置 `DATABASE_URL`。

## Android

需要带 `jlink` 的 JDK 17。用 Android Studio 打开 `android/`，或在该目录执行：

```bash
export JAVA_HOME="/path/to/jdk-17"
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Release 安装包：

```bash
./gradlew :app:assembleRelease
```

没有 `android/keystore.properties` 时，这条命令会失败。把 `android/keystore.properties.example` 复制成 `android/keystore.properties`，按里面的说明填密钥路径和密码。不要把 `keystore.properties`、`*.jks` 或 `*.apk` 提交进仓库。

产物在 `android/app/build/outputs/apk/release/app-release.apk`。

## iOS

用完整的 Xcode 打开 `ios/YuWhisperAccount.xcodeproj`，只装命令行工具不够。给 App 和分享扩展选同一个签名团队，并打开 App Group `group.com.yuwhisper.account`。两边不在同一个组里时，分享扩展认出的账进不了主应用。

不编译 App、只检查识别和入账时：

```bash
cd ios/LedgerCore
swift run LedgerCoreChecks
```

## 同步

不登录也能在本机记账。登录之后，分类和流水按 `client_id` 对齐；同一条两边都改过时，留 `updated_at` 更新的那一边。给后续改代码用的约束写在 [`AGENTS.md`](AGENTS.md)。
