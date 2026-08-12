package com.yuwhisper.account.ui.settings

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
import com.yuwhisper.account.capture.ConfirmDispatcher
import com.yuwhisper.account.capture.PermissionStep
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
    onOpenPermissionOnboarding: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var masterEnabled by remember {
        mutableStateOf(AutoBookkeepingPrefs.isMasterEnabled(context))
    }
    var permissionSteps by remember {
        mutableStateOf(AutoLedgerPermissions.steps(context))
    }
    var permissionsReady by remember {
        mutableStateOf(AutoLedgerPermissions.isReady(context))
    }

    fun refreshPermissionStatus() {
        permissionSteps = AutoLedgerPermissions.steps(context)
        permissionsReady = AutoLedgerPermissions.isReady(context)
        AutoBookkeepingStatusService.refresh(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                masterEnabled = AutoBookkeepingPrefs.isMasterEnabled(context)
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
        if (enabled && !AutoLedgerPermissions.isReady(context)) {
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("自动记账", style = MaterialTheme.typography.titleMedium)
            Text(
                "总开关开启且核心权限齐全时，通知栏显示「自动记账运行中」。",
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
                            permissionsReady -> "状态：运行中"
                            else -> "状态：权限未齐（请完成引导）"
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
                if (permissionsReady) "自动记账已就绪" else "权限未完成，不可标为已就绪",
                color = if (permissionsReady) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.bodyMedium,
            )
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
