package com.yuwhisper.account.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yuwhisper.account.capture.AutoBookkeepingPrefs
import com.yuwhisper.account.capture.AutoBookkeepingStatusService
import com.yuwhisper.account.capture.CaptureAvailability
import com.yuwhisper.account.capture.ConfirmDispatcher
import com.yuwhisper.account.domain.Candidate
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
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var masterEnabled by remember {
        mutableStateOf(AutoBookkeepingPrefs.isMasterEnabled(context))
    }
    var notificationListenerOn by remember {
        mutableStateOf(CaptureAvailability.isNotificationListenerEnabled(context))
    }
    var accessibilityOn by remember {
        mutableStateOf(CaptureAvailability.isAccessibilityEnabled(context))
    }

    fun refreshChannelStatus() {
        notificationListenerOn = CaptureAvailability.isNotificationListenerEnabled(context)
        accessibilityOn = CaptureAvailability.isAccessibilityEnabled(context)
        AutoBookkeepingStatusService.refresh(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                masterEnabled = AutoBookkeepingPrefs.isMasterEnabled(context)
                refreshChannelStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* optional; simulate still works via Activity */ }

    fun ensurePostNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun setMasterEnabled(enabled: Boolean) {
        AutoBookkeepingPrefs.setMasterEnabled(context, enabled)
        masterEnabled = enabled
        if (enabled) {
            ensurePostNotifications()
        }
        AutoBookkeepingStatusService.refresh(context)
        scope.launch {
            snackbar.showSnackbar(
                if (enabled) "已开启自动记账（需通知监听或无障碍其一可用）" else "已关闭自动记账",
            )
        }
    }

    fun simulatePayment() {
        ensurePostNotifications()
        ConfirmDispatcher.onPaymentDetected(
            context = context,
            candidate = Candidate(
                source = "wechat",
                amountCents = 3650,
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("自动记账", style = MaterialTheme.typography.titleMedium)
            Text(
                "总开关开启后，在通知监听或无障碍可用时，通知栏显示「自动记账运行中」。",
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
                        if (masterEnabled) {
                            if (notificationListenerOn || accessibilityOn) {
                                "状态：运行中"
                            } else {
                                "状态：已停止（请开启通知监听或无障碍）"
                            }
                        } else {
                            "状态：关闭"
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

            Text(
                "通知监听：${if (notificationListenerOn) "已开启" else "未开启"}  ·  无障碍：${if (accessibilityOn) "已开启" else "未开启"}",
                color = if (notificationListenerOn || accessibilityOn) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("通知使用权设置")
            }
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("无障碍设置")
            }
            Button(
                onClick = onOpenWatchApps,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("识别场景")
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
