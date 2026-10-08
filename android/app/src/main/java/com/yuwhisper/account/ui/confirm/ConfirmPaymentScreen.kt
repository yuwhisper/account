package com.yuwhisper.account.ui.confirm

import com.yuwhisper.account.ui.theme.AccountSerif

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuwhisper.account.ui.theme.AccountFieldShape
import com.yuwhisper.account.ui.theme.AccountPageHeading
import com.yuwhisper.account.data.local.entity.CategoryEntity
import com.yuwhisper.account.data.local.entity.PendingPaymentEntity
import java.util.Locale

@Composable
fun ConfirmPaymentScreen(
    pending: PendingPaymentEntity?,
    categories: List<CategoryEntity>,
    loadError: String?,
    animateIn: Boolean = true,
    onConfirm: (
        amountCents: Long,
        merchant: String,
        categoryLocalId: Long,
        note: String,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) -> Unit,
    onDismissKeepPending: () -> Unit,
) {
    // Overlay hosts start visible, even when their first composition cannot launch effects.
    val visibility = remember { MutableTransitionState(!animateIn) }
    var saving by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var dismissalDelivered by remember { mutableStateOf(false) }
    val dismissAfterExit by rememberUpdatedState(onDismissKeepPending)
    val requestDismiss = {
        if (!saving && !closing) {
            closing = true
            visibility.targetState = false
        }
    }

    LaunchedEffect(Unit) {
        if (!closing) visibility.targetState = true
    }
    // Removing a service window or finishing the Activity happens only after the exit settles.
    // Compose's system duration scale also makes this settle immediately when motion is disabled.
    LaunchedEffect(visibility.isIdle, visibility.currentState, closing) {
        if (closing && visibility.isIdle && !visibility.currentState && !dismissalDelivered) {
            dismissalDelivered = true
            dismissAfterExit()
        }
    }

    // Overlay ComposeView may have no Activity dispatcher; keep that host safe as well.
    if (LocalOnBackPressedDispatcherOwner.current != null) {
        BackHandler { requestDismiss() }
    }

    AnimatedVisibility(
        visibleState = visibility,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(140)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // The scrim is a sibling behind the card. Card padding must consume taps too.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.38f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !saving && !closing,
                        onClickLabel = "稍后处理",
                        onClick = requestDismiss,
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                val cardModifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .animateEnterExit(
                        enter = scaleIn(initialScale = 0.98f, animationSpec = spring(dampingRatio = 0.9f, stiffness = 600f)) +
                            slideInVertically(spring(dampingRatio = 0.9f, stiffness = 600f)) { it / 24 },
                        exit = scaleOut(targetScale = 0.98f, animationSpec = tween(140)) +
                            slideOutVertically(tween(140)) { it / 24 },
                    )
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }

                when {
                    loadError != null -> {
                        Surface(
                            modifier = cardModifier,
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 4.dp,
                        ) {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text("暂时无法打开", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    loadError,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = requestDismiss, enabled = !closing) {
                                    Text("稍后处理")
                                }
                            }
                        }
                    }
                    pending == null -> {
                        Surface(
                            modifier = cardModifier,
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 4.dp,
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                                Text("正在读取付款信息", style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = requestDismiss, enabled = !closing) {
                                    Text("稍后处理")
                                }
                            }
                        }
                    }
                    else -> {
                        ConfirmCard(
                            pending = pending,
                            categories = categories,
                            saving = saving,
                            enabled = !saving && !closing,
                            onConfirm = { amountCents, merchant, categoryLocalId, note, onDone, onError ->
                                saving = true
                                val onSaved: () -> Unit = {
                                    saving = false
                                    onDone()
                                    requestDismiss()
                                }
                                val onSaveError: (String) -> Unit = { message ->
                                    saving = false
                                    onError(message)
                                }
                                onConfirm(
                                    amountCents,
                                    merchant,
                                    categoryLocalId,
                                    note,
                                    onSaved,
                                    onSaveError,
                                )
                            },
                            onDismiss = requestDismiss,
                            modifier = cardModifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmCard(
    pending: PendingPaymentEntity,
    categories: List<CategoryEntity>,
    saving: Boolean,
    enabled: Boolean,
    onConfirm: (
        amountCents: Long,
        merchant: String,
        categoryLocalId: Long,
        note: String,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var amountText by rememberSaveable(pending.localId) {
        mutableStateOf(
            pending.amountCents?.let { cents ->
                String.format(Locale.US, "%.2f", cents / 100.0)
            }.orEmpty(),
        )
    }
    var merchant by rememberSaveable(pending.localId) { mutableStateOf(pending.merchant) }
    var note by rememberSaveable(pending.localId) { mutableStateOf("") }
    var selectedCategoryLocalId by rememberSaveable(pending.localId) {
        mutableStateOf(categories.firstOrNull()?.localId)
    }
    val selectedCategory = categories.firstOrNull { it.localId == selectedCategoryLocalId }
    var errorMessage by remember(pending.localId) { mutableStateOf<String?>(null) }

    LaunchedEffect(categories) {
        if (selectedCategoryLocalId == null) selectedCategoryLocalId = categories.firstOrNull()?.localId
    }

    val fieldShape = AccountFieldShape
    val haptic = LocalHapticFeedback.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        disabledContainerColor = MaterialTheme.colorScheme.surface,
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AccountPageHeading(title = "确认入账", subtitle = sourceLabel(pending.source))
            if (pending.amountCents == null) {
                Text(
                    "尚未识别金额，请补充后入账。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                enabled = enabled,
                singleLine = true,
                label = { Text("金额 · 元") },
                prefix = { Text("¥", style = MaterialTheme.typography.headlineSmall) },
                placeholder = { Text("0.00") },
                textStyle = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = AccountSerif,
                    fontWeight = FontWeight.Normal,
                    fontSize = 40.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
                shape = fieldShape,
            )

            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                enabled = enabled,
                label = { Text("商户") },
                singleLine = true,
                colors = fieldColors,
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "分类",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (categories.isEmpty()) {
                    Text(
                        "暂无分类，请先在账本中添加分类。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory?.localId == cat.localId,
                                enabled = enabled,
                                onClick = {
                                    selectedCategoryLocalId = cat.localId
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    errorMessage = null
                                },
                                label = { Text(cat.name) },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                enabled = enabled,
                label = { Text("备注 · 可选") },
                minLines = 1,
                maxLines = 3,
                colors = fieldColors,
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth(),
            )

            AnimatedVisibility(visible = errorMessage != null, enter = fadeIn(), exit = fadeOut()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Text(
                        errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Button(
                enabled = enabled,
                onClick = {
                    val yuan = amountText.toDoubleOrNull()
                    if (yuan == null || !yuan.isFinite() || yuan <= 0.0 || yuan > 1_000_000.0) {
                        errorMessage = "请输入 0.01～1000000 的有效金额"
                        return@Button
                    }
                    val parts = amountText.trim().split('.')
                    if (parts.size > 1 && parts[1].length > 2) {
                        errorMessage = "金额最多两位小数"
                        return@Button
                    }
                    val cat = selectedCategory
                    if (cat == null) {
                        errorMessage = "请选择分类后再入账"
                        return@Button
                    }
                    val cents = Math.round(yuan * 100.0)
                    if (cents <= 0L) {
                        errorMessage = "请输入有效金额"
                        return@Button
                    }
                    errorMessage = null
                    onConfirm(
                        cents,
                        merchant,
                        cat.localId,
                        note,
                        {},
                        { message -> errorMessage = message },
                    )
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(10.dp))
                }
                Text(
                    if (saving) "入账中…" else "确认入账",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            TextButton(
                onClick = onDismiss,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("稍后处理")
            }
        }
    }
}

private fun sourceLabel(source: String): String = when (source) {
    "wechat" -> "微信支付"
    "alipay" -> "支付宝"
    "manual" -> "模拟付款"
    "unionpay" -> "云闪付"
    else -> source.ifBlank { "付款" }
}
