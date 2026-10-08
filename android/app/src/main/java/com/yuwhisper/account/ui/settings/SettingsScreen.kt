package com.yuwhisper.account.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yuwhisper.account.capture.AutoBookkeepingPrefs
import com.yuwhisper.account.capture.AutoBookkeepingStatusService
import com.yuwhisper.account.capture.AutoLedgerPermissions
import com.yuwhisper.account.capture.CaptureDebug
import com.yuwhisper.account.capture.ConfirmDispatcher
import com.yuwhisper.account.capture.PaymentAccessibilityService
import com.yuwhisper.account.capture.PermissionStep
import com.yuwhisper.account.domain.Candidate
import com.yuwhisper.account.sync.AuthRepository
import com.yuwhisper.account.sync.SyncWorker
import com.yuwhisper.account.ui.theme.AccountAppearanceSelector
import com.yuwhisper.account.ui.theme.AccountPageHeading
import com.yuwhisper.account.ui.theme.AccountCard
import com.yuwhisper.account.ui.theme.AccountDialog
import com.yuwhisper.account.ui.theme.AccountFieldShape
import com.yuwhisper.account.ui.theme.AccountMutedColor
import com.yuwhisper.account.ui.theme.AccountSectionHeader
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onExportCsv: (file: File, onDone: () -> Unit, onError: (String) -> Unit) -> Unit,
    onOpenWatchApps: () -> Unit,
    onOpenPermissionOnboarding: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val auth = remember { AuthRepository(context) }

    var loggedInEmail by remember { mutableStateOf(auth.currentEmail()) }
    var baseUrlPreview by remember { mutableStateOf(auth.getBaseUrl()) }
    var masterEnabled by remember {
        mutableStateOf(AutoBookkeepingPrefs.isMasterEnabled(context))
    }
    var showStatusNotification by remember {
        mutableStateOf(AutoBookkeepingPrefs.isShowStatusNotification(context))
    }
    var permissionSteps by remember {
        mutableStateOf(AutoLedgerPermissions.steps(context))
    }
    var permissionsReady by remember {
        mutableStateOf(AutoLedgerPermissions.isReady(context))
    }
    var captureReady by remember {
        mutableStateOf(AutoLedgerPermissions.isCaptureReady(context))
    }
    var captureDebug by remember { mutableStateOf(CaptureDebug.lastNote) }
    var showLogoutConfirmation by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }

    fun refreshPermissionStatus() {
        permissionSteps = AutoLedgerPermissions.steps(context)
        permissionsReady = AutoLedgerPermissions.isReady(context)
        captureReady = AutoLedgerPermissions.isCaptureReady(context)
        captureDebug = CaptureDebug.lastNote
        AutoBookkeepingStatusService.refresh(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                masterEnabled = AutoBookkeepingPrefs.isMasterEnabled(context)
                showStatusNotification = AutoBookkeepingPrefs.isShowStatusNotification(context)
                loggedInEmail = auth.currentEmail()
                baseUrlPreview = auth.getBaseUrl()
                refreshPermissionStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun setMasterEnabled(enabled: Boolean) {
        AutoBookkeepingPrefs.setMasterEnabled(context, enabled)
        masterEnabled = enabled
        AutoBookkeepingStatusService.refresh(context)
        // Only open wizard when capture channels are missing — not for battery alone.
        if (enabled && !AutoLedgerPermissions.isCaptureReady(context)) {
            onOpenPermissionOnboarding()
            return
        }
        scope.launch {
            snackbar.showSnackbar(
                if (enabled) "已开启自动记账" else "已关闭自动记账",
            )
        }
    }

    fun simulatePayment() {
        ConfirmDispatcher.onPaymentDetected(
            context = context,
            candidate = Candidate(
                source = "wechat",
                amountCents = 100 + (System.currentTimeMillis() % 9_900).toInt(),
                merchant = "瑞幸咖啡",
                occurredAt = Instant.now(),
            ),
            captureChannel = "manual",
            rawText = "调试：模拟一笔付款",
        )
        scope.launch {
            snackbar.showSnackbar("已触发模拟付款确认卡")
        }
    }

    if (showLogoutConfirmation) {
        AccountDialog(
            onDismissRequest = { showLogoutConfirmation = false },
            title = "退出当前账号？",
            description = "退出后仍可使用本地账本，云同步会暂停。",
            confirmButton = {
                Button(onClick = {
                    auth.logout()
                    loggedInEmail = null
                    showLogoutConfirmation = false
                    scope.launch { snackbar.showSnackbar("已退出登录") }
                }, shape = RoundedCornerShape(12.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text("退出登录") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmation = false }) { Text("取消") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            AccountPageHeading(title = "设置", subtitle = "本机记录，按需同步", showSeal = true)
            AccountCard {
                AccountSectionHeader(title = "外观")
                AccountAppearanceSelector()
            }
            AccountCard(modifier = Modifier.animateContentSize(animationSpec = spring(dampingRatio = 1f, stiffness = 600f))) {
                AccountSectionHeader(title = "付款识别")
                SettingSwitchRow(
                    title = "自动识别付款",
                    description = when {
                        !masterEnabled -> "已暂停付款捕获"
                        captureReady -> "捕获通道可用，等待下一笔付款"
                        else -> "请开启无障碍或通知使用权"
                    },
                    checked = masterEnabled,
                    onCheckedChange = { setMasterEnabled(it) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                SettingSwitchRow(
                    title = "运行状态通知",
                    description = "常驻显示运行状态，默认关闭；开启会增加耗电。",
                    checked = showStatusNotification,
                    onCheckedChange = {
                        AutoBookkeepingPrefs.setShowStatusNotification(context, it)
                        showStatusNotification = it
                        AutoBookkeepingStatusService.refresh(context)
                    },
                )
                Text(
                    "付款先进入待确认。浮层确认不会打开应用。",
                    color = AccountMutedColor,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = onOpenWatchApps,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("管理识别场景") }
            }
            AccountCard {
                AccountSectionHeader(title = "权限状态")
                Text(
                    when {
                        permissionsReady -> "权限全部就绪"
                        captureReady -> "可捕获付款，建议补齐电池与悬浮窗设置"
                        else -> "捕获通道未开启"
                    },
                    color = if (captureReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                PermissionStatusList(steps = permissionSteps)
                Button(
                    onClick = onOpenPermissionOnboarding,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text("权限分步引导") }
            }
            AccountCard(modifier = Modifier.animateContentSize(animationSpec = spring(dampingRatio = 1f, stiffness = 600f))) {
                AccountSectionHeader(
                    title = "云同步",
                    subtitle = if (loggedInEmail.isNullOrBlank()) "当前使用本地账本" else "已登录，支持跨设备同步",
                )
                if (!loggedInEmail.isNullOrBlank()) {
                    Text(loggedInEmail.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    "服务器 · $baseUrlPreview",
                    color = AccountMutedColor,
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = onOpenLogin,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(if (loggedInEmail.isNullOrBlank()) "登录 / 注册" else "账号与服务器") }
                AnimatedVisibility(visible = !loggedInEmail.isNullOrBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                SyncWorker.enqueueNow(context)
                                scope.launch { snackbar.showSnackbar("已开始同步") }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text("立即同步") }
                        OutlinedButton(
                            onClick = { showLogoutConfirmation = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text("退出登录") }
                    }
                }
            }
            AccountCard {
                AccountSectionHeader(title = "账本数据", subtitle = "导出完整流水，备份和整理")
                OutlinedButton(
                    onClick = {
                        val stamp = LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                        val file = File(context.cacheDir, "ledger_$stamp.csv")
                        onExportCsv(
                            file,
                            {
                                runCatching {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file,
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(intent, "导出账本"),
                                    )
                                }.onFailure { e ->
                                    scope.launch {
                                        snackbar.showSnackbar(e.message ?: "分享失败")
                                    }
                                }
                            },
                            { msg -> scope.launch { snackbar.showSnackbar(msg) } },
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("导出 CSV") }
            }
            AccountCard(modifier = Modifier.animateContentSize(animationSpec = spring(dampingRatio = 1f, stiffness = 600f))) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("诊断与测试", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showDiagnostics = !showDiagnostics }) {
                        Text(if (showDiagnostics) "收起" else "展开")
                    }
                }
                AnimatedVisibility(visible = showDiagnostics) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            "最近捕获 · $captureDebug",
                            color = AccountMutedColor,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(
                            onClick = {
                                val dump = PaymentAccessibilityService.instance?.dumpForegroundTexts()
                                val report = buildString {
                                    append(CaptureDebug.report())
                                    if (!dump.isNullOrBlank()) {
                                        append("\n当前画面：")
                                        append(dump)
                                    }
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("惜夏记诊断", report))
                                captureDebug = CaptureDebug.lastNote
                                scope.launch { snackbar.showSnackbar("已复制诊断信息") }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text("复制诊断信息") }
                        Text(
                            "模拟付款只加入待确认，关闭确认卡后仍会保留。",
                            color = AccountMutedColor,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(
                            onClick = { simulatePayment() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text("模拟一笔付款") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, color = AccountMutedColor, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun PermissionStatusList(steps: List<PermissionStep>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        steps.forEach { step ->
            val statusColor by animateColorAsState(
                targetValue = if (step.isGranted) MaterialTheme.colorScheme.primary else AccountMutedColor,
                label = "permissionStatusColor",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    if (step.isGranted) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    step.title,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (step.isGranted) "已开启" else "未开启",
                    color = statusColor,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
