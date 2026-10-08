package com.yuwhisper.account.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Wallet
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yuwhisper.account.data.local.entity.WatchAppEntity
import com.yuwhisper.account.ui.theme.AccountCard
import com.yuwhisper.account.ui.theme.AccountEmptyState
import com.yuwhisper.account.ui.theme.AccountFieldShape
import com.yuwhisper.account.ui.theme.AccountMutedColor
import com.yuwhisper.account.ui.theme.AccountSectionHeader
import com.yuwhisper.account.ui.theme.AccountPageHeading

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchAppsScreen(
    apps: List<WatchAppEntity>,
    onToggle: (packageName: String, enabled: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var listenerEnabled by remember {
        mutableStateOf(
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName),
        )
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountPageHeading(title = "识别场景", subtitle = "选择付款来源")
                    Text(
                        "识别付款通知与成功页，核对之后再入账。",
                        color = AccountMutedColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                AccountCard(modifier = Modifier.animateContentSize(animationSpec = spring(dampingRatio = 1f, stiffness = 600f))) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("通知使用权", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (listenerEnabled) "已开启，可读取付款通知" else "未开启，请到系统设置授权",
                                color = if (listenerEnabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Text(
                        "系统设置 → 通知使用权 / 通知访问权限 → 惜夏记",
                        color = AccountMutedColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(if (listenerEnabled) "管理通知使用权" else "开启通知使用权") }
                }
            }
            item {
                AccountSectionHeader(
                    title = "付款应用",
                    subtitle = "已开启 ${apps.count { it.enabled }} / ${apps.size} 个来源",
                )
            }
            if (apps.isEmpty()) {
                item {
                    AccountEmptyState(
                        icon = Icons.Outlined.Wallet,
                        title = "暂无识别来源",
                        description = "付款应用列表准备好后会显示在这里。",
                    )
                }
            }
            items(apps, key = { it.packageName }) { app ->
                WatchAppRow(app = app, onToggle = { enabled -> onToggle(app.packageName, enabled) })
            }
        }
    }
}

@Composable
private fun WatchAppRow(app: WatchAppEntity, onToggle: (Boolean) -> Unit) {
    AccountCard {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .toggleable(value = app.enabled, role = Role.Switch, onValueChange = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    app.packageName,
                    color = AccountMutedColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = app.enabled, onCheckedChange = null)
        }
    }
}
