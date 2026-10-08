package com.yuwhisper.account.ui.onboarding

import com.yuwhisper.account.ui.theme.AccountSerif

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yuwhisper.account.capture.AutoBookkeepingPrefs
import com.yuwhisper.account.capture.AutoLedgerPermissions
import com.yuwhisper.account.capture.PermissionFixAction
import com.yuwhisper.account.capture.PermissionStep
import com.yuwhisper.account.ui.theme.AccountCard
import com.yuwhisper.account.ui.theme.AccountFieldShape
import com.yuwhisper.account.ui.theme.AccountMutedColor
import com.yuwhisper.account.ui.theme.AccountPageHeading
import kotlinx.coroutines.launch

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
    val completed = steps.count { it.isGranted }
    val nextStepIndex = steps.indexOfFirst { !it.isGranted }
    val progress by animateFloatAsState(
        targetValue = if (steps.isEmpty()) 0f else completed.toFloat() / steps.size,
        animationSpec = spring(dampingRatio = 1f, stiffness = 600f),
        label = "permissionProgress",
    )
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
                runCatching { context.startActivity(action.intent) }.onFailure {
                    scope.launch { snackbar.showSnackbar("暂时无法打开系统设置，请稍后重试") }
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = { leave() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountPageHeading(
                        title = if (ready) "权限已就绪" else "开启付款识别",
                        subtitle = "权限设置",
                    )
                    Text(
                        "按步骤授权。付款只进入待确认，短信权限可选。",
                        color = AccountMutedColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                AccountCard(modifier = Modifier.animateContentSize(animationSpec = spring(dampingRatio = 1f, stiffness = 600f))) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (ready) "全部准备完成" else "权限设置进度",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "$completed / ${steps.size}",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        trackColor = MaterialTheme.colorScheme.primaryContainer,
                        drawStopIndicator = {},
                    )
                    Text(
                        if (ready) "以后可随时在设置中调整权限。"
                        else "返回后会自动检查，主要权限齐备后标记为就绪；短信权限可选。",
                        color = AccountMutedColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            itemsIndexed(steps, key = { _, step -> step.id.name }) { index, step ->
                PermissionStepCard(
                    index = index + 1,
                    step = step,
                    isNext = index == nextStepIndex,
                    onOpenSettings = { openFix(step) },
                )
            }
            item {
                if (ready) {
                    Button(
                        onClick = { leave(markCompleted = true) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Text("完成设置") }
                } else {
                    OutlinedButton(
                        onClick = { leave() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("稍后再说") }
                }
            }
        }
    }
}

@Composable
private fun PermissionStepCard(
    index: Int,
    step: PermissionStep,
    isNext: Boolean,
    onOpenSettings: () -> Unit,
) {
    AccountCard(modifier = Modifier.animateContentSize(animationSpec = spring(dampingRatio = 1f, stiffness = 600f))) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (step.isGranted) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(
                        "$index",
                        fontFamily = AccountSerif,
                        fontSize = 22.sp,
                        color = if (isNext) MaterialTheme.colorScheme.primary else AccountMutedColor,
                    )
                }
            }
            Text(
                step.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (step.isGranted) "已开启" else if (isNext) "下一步" else "待开启",
                color = if (step.isGranted || isNext) MaterialTheme.colorScheme.primary else AccountMutedColor,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(
            step.description,
            color = AccountMutedColor,
            style = MaterialTheme.typography.bodyMedium,
        )
        AnimatedVisibility(
            visible = !step.isGranted,
            enter = expandVertically(animationSpec = spring(dampingRatio = 1f, stiffness = 600f)) + fadeIn(),
            exit = shrinkVertically(animationSpec = spring(dampingRatio = 1f, stiffness = 600f)) + fadeOut(),
        ) {
            if (isNext) {
                Button(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text("去开启") }
            } else {
                TextButton(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("去开启") }
            }
        }
    }
}
