package com.yuwhisper.account.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.yuwhisper.account.ui.theme.AccountMutedColor
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("自动记账", style = MaterialTheme.typography.titleMedium)
            Text(
                "付款后优先用无障碍悬浮层弹出确认卡（不跳进本应用）。也可另开系统悬浮窗作备用。默认不常驻前台服务，更省电。",
                color = AccountMutedColor,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("自动记账总开关", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            !masterEnabled -> "状态：关闭"
                            captureReady -> "状态：捕获通道可用"
                            else -> "状态：无障碍/通知监听未开（点下方引导）"
                        },
                        color = AccountMutedColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = masterEnabled,
                    onCheckedChange = { setMasterEnabled(it) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("常驻运行状态通知", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "开启后会以前台服务显示「运行中」，更费电；默认关闭。",
                        color = AccountMutedColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = showStatusNotification,
                    onCheckedChange = {
                        AutoBookkeepingPrefs.setShowStatusNotification(context, it)
                        showStatusNotification = it
                        AutoBookkeepingStatusService.refresh(context)
                    },
                )
            }

            Text(
                when {
                    permissionsReady -> "权限全部就绪"
                    captureReady -> "可捕获付款（电池/悬浮窗建议仍开启，减少被杀）"
                    else -> "捕获通道未开：请开启无障碍或通知使用权"
                },
                color = if (captureReady) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "最近捕获：$captureDebug",
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
                    scope.launch { snackbar.showSnackbar("已复制诊断信息，发给开发者即可") }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("复制诊断信息")
            }
            PermissionStatusList(steps = permissionSteps)
            Button(
                onClick = onOpenPermissionOnboarding,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("权限分步引导")
            }
            Button(
                onClick = onOpenWatchApps,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("识别场景")
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("云同步", style = MaterialTheme.typography.titleMedium)
            Text(
                if (loggedInEmail.isNullOrBlank()) {
                    "未登录：仅本地记账。服务器：$baseUrlPreview"
                } else {
                    "已登录：$loggedInEmail\n服务器：$baseUrlPreview"
                },
                color = AccountMutedColor,
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = onOpenLogin,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (loggedInEmail.isNullOrBlank()) "登录 / 注册" else "账号与服务器")
            }
            if (!loggedInEmail.isNullOrBlank()) {
                OutlinedButton(
                    onClick = {
                        SyncWorker.enqueueNow(context)
                        scope.launch { snackbar.showSnackbar("已开始同步") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("立即同步")
                }
                OutlinedButton(
                    onClick = {
                        auth.logout()
                        loggedInEmail = null
                        scope.launch { snackbar.showSnackbar("已退出登录") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("退出登录")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("数据", style = MaterialTheme.typography.titleMedium)
            Text(
                "CSV header: occurred_at,type,amount,merchant,source,category,note; amount is yuan with 2 decimals.",
                color = AccountMutedColor,
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
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
                                    Intent.createChooser(intent, "Export CSV"),
                                )
                            }.onFailure { e ->
                                scope.launch {
                                    snackbar.showSnackbar(e.message ?: "share failed")
                                }
                            }
                        },
                        { msg -> scope.launch { snackbar.showSnackbar(msg) } },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("导出 CSV")
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("调试", style = MaterialTheme.typography.titleMedium)
            Text(
                "模拟付款会写入待确认队列并弹出居中确认卡（无忽略；返回键保留 pending）。",
                color = AccountMutedColor,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = { simulatePayment() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("模拟一笔付款")
            }
        }
    }
}

@Composable
fun PermissionStatusList(steps: List<PermissionStep>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        steps.forEach { step ->
            Text(
                "${step.title}：${if (step.isGranted) "已开启" else "未开启"}",
                color = if (step.isGranted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
