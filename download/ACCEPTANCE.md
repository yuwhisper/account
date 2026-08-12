# 第一版验收清单

对照规格 [`docs/superpowers/specs/2026-08-12-android-auto-ledger-design.md`](../docs/superpowers/specs/2026-08-12-android-auto-ledger-design.md) 第 1、10 节。

勾选表示已在真机 / 环境验证通过。

## 核心验收（规格成功标准）

- [ ] 微信 / 支付宝付款后能弹出确认卡并完成入账
- [ ] 双通道（通知监听 + 无障碍）至少一条可用；通知栏运行状态文案正确（运行中 / 已停止）
- [ ] 「识别场景」开关生效：关闭某应用后该应用付款不再触发
- [ ] 分类管理、流水列表、简单统计、导出 Excel/CSV 可用
- [ ] 换机（或清数据重装）登录同账号能拉回流水
- [ ] 陌生人能按 [下载页](./index.html) / GitHub Releases 装上 APK

## 捕获与交互

- [ ] 仅通知通道可触发确认卡
- [ ] 仅无障碍通道可触发确认卡
- [ ] 双开同时命中同一笔时去重，不产生两笔
- [ ] 确认卡无「忽略」；必须选分类后确认入账
- [ ] 解析失败时允许手改金额 / 商户后再入账
- [ ] 弹窗被拦时有「待入账」通知，点击进入同一确认流程
- [ ] 待确认队列在未确认前持续可见

## 权限与引导

- [ ] 分步引导顺序：无障碍 → 通知使用权 → 悬浮窗 → 通知栏 → 电池白名单
- [ ] 缺任一核心权限时不能标「自动记账已就绪」
- [ ] 短信权限不进入必做步骤

## 同步与离线

- [ ] 未登录可本地入账
- [ ] 离线入账后恢复网络能同步
- [ ] 登录后 pending 流水可 push；他端 pull 可见

## 分发与构建

- [ ] `download/index.html` 含版本、权限清单、未知来源说明、隐私链接
- [ ] `download/privacy.html` 写明无障碍/通知用途与不出售数据
- [ ] Release APK 已上传 GitHub Releases（或文档注明暂用 debug 签名包）
- [ ] 仓库未包含真实 `keystore` / `keystore.properties` 密钥

## 构建备注（本仓库）

| 项目 | 说明 |
|------|------|
| 正式签名 | 复制 `android/keystore.properties.example` → `android/keystore.properties`，填本地 keystore 路径后 `assembleRelease` |
| 无正式 keystore | `assembleRelease` 回退 debug 签名；或使用 `assembleDebug` 产物做侧载验证 |
| 产物路径 | `android/app/build/outputs/apk/release/app-release.apk` 或 `.../debug/app-debug.apk` |
