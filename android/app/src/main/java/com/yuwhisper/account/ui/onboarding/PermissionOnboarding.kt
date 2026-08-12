package com.yuwhisper.account.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yuwhisper.account.capture.AutoBookkeepingPrefs
import com.yuwhisper.account.capture.AutoLedgerPermissions
import com.yuwhisper.account.capture.PermissionFixAction
import com.yuwhisper.account.capture.PermissionStep
import com.yuwhisper.account.ui.theme.AccountMutedColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionOnboardingScreen(
    onFinished: () -> Unit,
    showBack: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var steps by remember { mutableStateOf(AutoLedgerPermissions.steps(context)) }
    val ready = steps.all { it.isGranted }

    fun refresh() {
        steps = AutoLedgerPermissions.steps(context)
        if (AutoLedgerPermissions.isReady(context)) {
            AutoBookkeepingPrefs.setOnboardingCompleted(context, true)
        }
    }

    fun leave(markCompleted: Boolean = false) {
        if (markCompleted || AutoLedgerPermissions.isReady(context)) {
            AutoBookkeepingPrefs.setOnboardingCompleted(context, true)
        } else {
            AutoBookkeepingPrefs.setOnboardingSeen(context)
        }
        onFinished()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh() }

    fun openFix(step: PermissionStep) {
        when (val action = AutoLedgerPermissions.fixAction(context, step.id)) {
            is PermissionFixAction.AlreadyGranted -> refresh()
            is PermissionFixAction.RequestPermission ->
                notificationPermissionLauncher.launch(action.permission)
            is PermissionFixAction.StartActivity ->
                runCatching { context.startActivity(action.intent) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自动记账权限引导") },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = { leave() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
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
                    if (ready) {
                        "自动记账已就绪"
                    } else {
                        "请按顺序完成下列权限。全部开启后才会标记为「自动记账已就绪」。短信权限为可选项，不在必做步骤中。"
                    },
                    color = if (ready) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        AccountMutedColor
                    },
                    style = if (ready) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.bodySmall
                    },
                )
            }
            itemsIndexed(steps, key = { _, step -> step.id.name }) { index, step ->
                PermissionStepCard(
                    index = index + 1,
                    step = step,
                    onOpenSettings = { openFix(step) },
                )
            }
            item {
                if (ready) {
                    Button(
                        onClick = { leave(markCompleted = true) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("完成")
                    }
                } else {
                    OutlinedButton(
                        onClick = { leave() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("稍后再说")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionStepCard(
    index: Int,
    step: PermissionStep,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (step.isGranted) {
                    Icons.Filled.CheckCircle
                } else {
                    Icons.Outlined.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = if (step.isGranted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    AccountMutedColor
                },
            )
            Text(
                "$index. ${step.title}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (step.isGranted) "已开启" else "未开启",
                color = if (step.isGranted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            step.description,
            color = AccountMutedColor,
            style = MaterialTheme.typography.bodySmall,
        )
        if (!step.isGranted) {
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("去开启")
            }
        }
    }
}
