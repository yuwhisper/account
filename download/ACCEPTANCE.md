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
- [ ] 正式签名 Release APK 已上传 GitHub Releases（内部验收可使用 Debug APK）
- [ ] 仓库未包含真实 `keystore` / `keystore.properties` 密钥

## 构建备注（本仓库）

| 项目 | 说明 |
|------|------|
| 正式签名 | 复制 `android/keystore.properties.example` → `android/keystore.properties`，填本地 keystore 路径后 `assembleRelease` |
| 无正式 keystore | `assembleRelease` 直接失败；使用 `assembleDebug` 产物做侧载验证 |
| 产物路径 | `android/app/build/outputs/apk/release/app-release.apk` 或 `.../debug/app-debug.apk` |

## 两端兰花日夜界面验收（2026-10-07）

本轮参考 meix.xin 的兰花、纸色与宋体阅读感。Android / iOS 使用开放的纸面布局、细分隔线和少量朱印，金额与主要操作优先；移除大面积粉色卡片、重复标题和装饰性图标底座。系统权限弹窗、键盘、分享菜单及 iOS 交互式 sheet 保留平台体验。

| 设计项 | 当前实现与验收重点 |
|------|------|
| 白天 | 纸色 `#F4F5EE`、深绿文字 `#273F35`、次级文字 `#617064`、朱印 `#A54B3B`、细线 `#C5CDC1` |
| 黑夜 | 底色 `#101513`、浅绿文字 `#DCE5D8`、次级文字 `#A5B49F`、控件强调色 `#9DB7A3`、细线 `#303D35`，配套夜色兰花素材 |
| 字体 | 主要中文标题使用随 App 打包的 Noto Serif SC 子集；正文采用平台界面字体，金额采用衬线数字（iOS 为 Georgia） |
| 外观偏好 | 两端设置均提供「跟随系统 / 白天 / 黑夜」并保存选择；iOS 分享扩展读取 App Group 偏好，Android 确认浮层沿用相同主题 |
| 表单 | 8～12 点的克制圆角、开放分区和细线；金额、分类、错误说明与提交动作保持明确，键盘打开后仍可滚动完成输入 |
| 动效 | 按压即时反馈；分类选择、展开与进度使用短弹簧过渡；iOS 使用原生 sheet，Android 确认卡采用轻微位移与缩放并等待退出结束后移除窗口 |

### 已完成的环境检查

这些结果仅证明构建、静态检查或核心逻辑，不代表真实支付、系统权限或完整动效验收通过。

- [x] Android `:app:assembleDebug` 与 `:app:testDebugUnitTest` 已通过。现有 XML 报告合计 16 项：`PaymentParseTest` 12 项、`DedupeTest` 4 项；失败、错误、跳过均为 0。
- [x] iOS 模拟器 App 与 ShareExtension 构建通过；`ManualEntryScreen` / `ConfirmScreen` 键盘布局修复版再次 `xcodebuild` 成功，日志为 `/tmp/yuwhisper-ios-orchid-keyboard-build.log`。
- [x] `ios/LedgerCore` 下执行 `swift run LedgerCoreChecks` 通过，解析、去重、账本存储三组检查输出 `ok`。
- [x] `git diff --check` 通过。
- [ ] Android `:app:lintDebug`：本轮仅运行一次，仍被已有 4 条 `MissingPermission` 阻止通过；位置为 `AutoBookkeepingStatusService.kt:57 / 67 / 98` 与 `PendingPaymentNotifier.kt:112`，与此前四条一致。报告另含 41 条 warning、1 条 information；本轮 UI 改版未处理这些捕获与通知权限问题。

构建与检查日志：`/tmp/account-android-orchid-build.log`、`/tmp/yuwhisper-ios-orchid-build.log`、`/tmp/yuwhisper-ios-orchid-keyboard-build.log`、`/tmp/account-orchid-final-ledgercore.log`、`/tmp/account-orchid-final-diff-check.log`、`/tmp/account-orchid-final-lint-debug.log`。单测计数与 lint 摘要分别为 `/tmp/account-orchid-final-unit-test-summary.json`、`/tmp/account-orchid-final-lint-summary.json`；临时目录内容可能随环境清理，正式报告位于 Android `app/build/reports/`。

### 已完成的模拟器画面与操作

证据目录：`/Users/yan/.codex/visualizations/2026/10/07/01a11579-87b9-7243-8656-46e6899bb023/orchid-ui-qa/`。以下只勾选已观察的具体视口与操作，不代表所有页面、字号、失败状态或真机支付通过。

- [x] iOS 最终字体的普通字号首屏：白天流水、统计、手动记账、设置；黑夜流水、设置、分类、确认卡。截图分别为 `ios-ledger-day.png`、`ios-stats-day.png`、`ios-manual-day.png`、`ios-settings-day.png`、`ios-ledger-night.png`、`ios-settings-night.png`、`ios-category-night.png`、`ios-confirm-night.png`。
- [x] iOS 在设置中切换白天 / 黑夜立即生效，重启后仍保留黑夜；「跟随系统」的自动切换尚未验证。
- [x] iOS 黑夜手动记账：新表单直接点金额，首次聚焦即出现原生 decimalPad，输入 ¥1.11；修复后保存按钮完整位于键盘上方，导航栏「完成」可见，无内容漏到保存栏下。保存成功后合计 ¥58.18 → ¥59.29 / 4 笔。证据为 `ios-manual-night-keyboard-before.png`、`ios-manual-night-keyboard-after.png`、`ios-final-keyboard-and-save.mov`。
- [x] iOS 白天手动记账：新表单直接点金额首次聚焦，无 Device 菜单切换即出现原生 decimalPad，键入 ¥2.22；保存按钮与导航栏「完成」完整可见。此草稿未保存。证据为 `ios-manual-day-keyboard-after.png`。
- [x] iOS 黑夜确认备注：硬件键盘状态下通过 AX 点击备注取得焦点并自动滚到备注，随后通过 Device → Toggle Software Keyboard 展开文字软键盘；「确认入账」与「忽略这笔」完整位于键盘上方。键盘展开时确认成功，¥2.50 与 `QA` 备注已入账，合计 ¥59.29 → ¥61.79 / 5 笔。证据为 `ios-confirm-night-keyboard-after.png`；此项不代表确认页首次自动唤起软键盘已验证。
- [x] iOS `accessibility-large` 字号下走查流水与分类，标题只缩放一次；流水截图为 `ios-ledger-accessibility-large.png`。其他辅助字号、横屏键盘与所有表单仍待验证。
- [x] iOS 粘贴「支付成功 / 瑞幸咖啡 / ¥36.50 / 完成」进入待确认；点「稍后」后记录保留，重开确认并入账后共 3 笔，支出合计从 ¥21.68 更新为 ¥58.18，金额正确。截图属于操作前后不同时间：统计白天为 ¥21.68，最终流水白天与黑夜均为 ¥58.18 / 3 笔。
- [x] iOS 流水删除确认弹窗已打开；点击弹窗外部取消后，账本仍为 ¥58.18 / 3 笔。截图为 `ios-delete-dialog-day.png`，实际删除与其他对象的取消流程仍待覆盖。
- [x] `ios-final-controls.mov` 记录最终字体、键盘布局修复前版本的支出 / 收入选择、餐饮 → 交通分类选择、键盘「完成」收起，以及 CUA 对系统 sheet 柄双击后的关闭结果；未成功执行拖动，不能据此勾选下拉退出。
- [x] `final-motion-preview.mp4` 为最终字体和键盘布局修复后的原生录屏转封装，13.855 秒；独立读回并抽帧确认黑夜 ¥1.11 输入状态、键盘收起、保存与账本更新为 ¥59.29 / 4 笔。原始录屏为 `ios-final-keyboard-and-save.mov`。
- [x] `motion-preview.mp4` 已独立核验，20.1 秒录屏中可见系统 sheet 展开、键盘避让、保存后收起与账本数字更新。该录屏早于最终字体与键盘修复，保留为历史动效证据；当前版本以 after 截图及 `final-motion-preview.mp4` 为准。
- [x] Android 原生模拟器权限引导首屏、流水白天 / 黑夜截图已查看：`android-onboarding.png`、`android-ledger-day.png`、`android-ledger-night.png`。其他 Android 页面与完整交互未实测；当前 CUA 无法控制未打包的原生模拟器。

此前 `ios-manual-keyboard.png` 和 `ios-final-controls.mov` 的键盘场景先聚焦再使用 Device 菜单 Toggle Software Keyboard，只证明该路径可操作，不能证明首次直接点击金额通过。后来首次直接聚焦发现保存栏与浮动键盘工具栏重叠；两页改用底部安全区保存栏、裁切滚动区域和导航栏「完成」，以上两条手动记账首次聚焦记录来自修复后的重新走查。三个 after 截图已再次逐一复看。确认备注仍只覆盖菜单切换软键盘的路径。

截图与拼图保留各自采集时的账本状态：¥58.18 / 3 笔的流水截图早于本轮键盘保存操作；随后黑夜手动记账增加 ¥1.11，再确认带 `QA` 备注的 ¥2.50，最新操作结果为 ¥61.79 / 5 笔。白天 ¥2.22 草稿未入账。

`day-night-preview.png` 是白天 / 黑夜现有流水截图的两列静态拼图，保留两张完整截图内容，仅用于并排展示，不是另一次 UI 截图或动效证据。

独立审查指出的 Android 权限进度端点和短信提示文案已修复并通过构建；修正后尚未补充设备复测。以上视频未用于测量帧率或性能，真机性能与「减少动态效果」仍待验证。

### 画面与交互实测清单

未勾选项为尚未完整覆盖的验收范围；其中的窄范围实测已单独列在上一节。静态截图只能证明相应画面的布局；动效必须操作观察或录屏，不以编译和单测代替。

- [ ] 流水、统计、分类、设置、手动记账在白天 / 黑夜下均无文字裁切；标题、朱印、兰花素材与细线可读，长商户、长分类、长备注和大金额可完整查看。
- [ ] 两端切换「跟随系统 / 白天 / 黑夜」后立即生效，退出并重开仍保留；跟随系统正确响应系统外观变化。
- [ ] Android 确认浮层与 iOS 分享扩展采用保存的外观；分享扩展在识别中、成功、重复和失败状态下保持文字对比度。
- [ ] Android 登录、识别场景、权限引导的正常 / 空 / 加载 / 失败状态可读；返回系统设置后权限状态刷新。
- [ ] Android 修正后的权限进度 0% / 100% 端点与短信提示文案重新进行设备走查。
- [ ] iOS 手动记账切换商户 / 备注输入、滚动分类后，保存栏与键盘各自占据正确区域；不同键盘关闭后布局恢复。普通字号的白天 / 黑夜首次金额聚焦已按上一节单独通过。
- [ ] iOS 确认页在没有硬件键盘、没有 Device 菜单切换的情况下，首次直接聚焦备注自动展开软键盘并完成输入与确认。
- [ ] Android 金额 / 商户 / 备注 / 时间草稿在屏幕旋转后保留；两端分类编辑保存失败后保留输入，主操作不会重复提交。
- [ ] 开启大字体（Android 200%、iOS 辅助功能字号），横屏打开键盘，所有输入、错误说明与提交 / 取消操作仍可到达。
- [ ] iOS 相册、粘贴识别、快捷指令和分享扩展继续进入待确认；粘贴 sheet 退出后才显示确认 sheet。
- [ ] 确认浮层空白处不会触发遮罩关闭；「稍后处理」保留待确认记录；保存中无法重复提交或关闭。
- [ ] 浮层收到另一笔付款时不会短暂显示上一笔金额；保存后对应的待入账通知取消。
- [ ] 分类 / 流水删除、退出登录的弹窗说明后果，取消不执行动作；iOS 忽略付款也需要确认。
- [ ] 分享扩展识别中取消，不会在取消后新增待确认或打开 App；识别成功仍需在 App 中确认分类才能入账。

### 原生动效实测清单

- [ ] 主按钮按下时立即反馈，松开恢复；提交中有进度反馈且禁止重复操作，Android 原生涟漪与 iOS 短按压动效没有额外等待。
- [ ] 标签导航、分类选择与外观选择状态连续变化；快捷说明、服务器与诊断展开时内容自然移动，无文字闪烁或控件跳位。
- [ ] iOS sheet 可交互下拉退出；Android 确认卡关闭动画结束后才移除窗口；快速连续操作、取消后重开不会卡在中间状态。
- [ ] Android 系统动画时长设为 0、iOS 开启「减少动态效果」后，全部操作仍可完成，确认退出回调只触发一次。
- [ ] 为按压、选择 / 展开、sheet / 浮层各保留一次录屏或开始 / 中间 / 结束状态证据，再复查浅色与暗色中的文字清晰度。

真实微信 / 支付宝付款、双通道去重、跨设备同步、真机分享取消、Android 200% 字号、iOS 其他辅助字号、横屏和降低动态效果仍按本页前述清单验收。
