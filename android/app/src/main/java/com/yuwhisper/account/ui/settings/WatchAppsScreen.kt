package com.yuwhisper.account.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.yuwhisper.account.data.local.entity.WatchAppEntity
import com.yuwhisper.account.ui.theme.AccountMutedColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchAppsScreen(
    apps: List<WatchAppEntity>,
    onToggle: (packageName: String, enabled: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("识别场景") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "开启后，将监听对应 App 的付款通知与付款成功页（无障碍），并弹出确认入账卡。",
                    color = AccountMutedColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (listenerEnabled) "通知使用权：已开启" else "通知使用权：未开启",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (listenerEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Text(
                        "系统设置 → 通知使用权 / 通知访问权限 → 语声记账",
                        color = AccountMutedColor,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (listenerEnabled) "管理通知使用权" else "开启通知使用权")
                    }
                }
            }
            items(apps, key = { it.packageName }) { app ->
                WatchAppRow(
                    app = app,
                    onToggle = { enabled -> onToggle(app.packageName, enabled) },
                )
            }
        }
    }
}

@Composable
private fun WatchAppRow(
    app: WatchAppEntity,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(app.label, style = MaterialTheme.typography.titleMedium)
            Text(
                app.packageName,
                color = AccountMutedColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(
            checked = app.enabled,
            onCheckedChange = onToggle,
        )
    }
}
