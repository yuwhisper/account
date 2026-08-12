package com.yuwhisper.account.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* optional; simulate still works via Activity */ }

    fun simulatePayment() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
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

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "自动记账权限与云同步设置将在后续任务接入。",
                color = AccountMutedColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
