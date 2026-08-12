package com.yuwhisper.account.ui.confirm

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuwhisper.account.data.local.entity.CategoryEntity
import com.yuwhisper.account.data.local.entity.PendingPaymentEntity
import kotlinx.coroutines.launch
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
    BackHandler(onBack = onDismissKeepPending)
    var visible by remember { mutableStateOf(!animateIn) }
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0x66000000), Color(0x99000000)),
                ),
            )
            .clickable(onClick = onDismissKeepPending),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)) + scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) + slideInVertically { it / 12 },
            exit = fadeOut(tween(120)),
        ) {
            when {
                loadError != null -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp)
                            .clickable(enabled = false) {},
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        shadowElevation = 12.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(loadError, style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = onDismissKeepPending) { Text("稍后处理") }
                        }
                    }
                }
                pending == null -> {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                else -> {
                    ConfirmCard(
                        pending = pending,
                        categories = categories,
                        onConfirm = onConfirm,
                        modifier = Modifier.clickable(enabled = false) {},
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmCard(
    pending: PendingPaymentEntity,
    categories: List<CategoryEntity>,
    onConfirm: (
        amountCents: Long,
        merchant: String,
        categoryLocalId: Long,
        note: String,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var amountText by remember {
        mutableStateOf(
            pending.amountCents?.let { cents ->
                String.format(Locale.US, "%.2f", cents / 100.0)
            }.orEmpty(),
        )
    }
    var merchant by remember { mutableStateOf(pending.merchant) }
    var note by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(categories) {
        if (selectedCategory == null) selectedCategory = categories.firstOrNull()
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 16.dp,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 22.dp, vertical = 26.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = sourceLabel(pending.source),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium,
                )
            }

            Text(
                text = "确认这笔支出",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                text = "¥",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                singleLine = true,
                textStyle = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )

            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("商户") },
                singleLine = true,
                colors = fieldColors,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "消费类型",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    categories.forEach { cat ->
                        val selected = selectedCategory?.localId == cat.localId
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedCategory = cat
                                errorMessage = null
                            },
                            label = { Text(cat.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注（可选）") },
                colors = fieldColors,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            Button(
                enabled = !saving,
                onClick = {
                    val yuan = amountText.toDoubleOrNull()
                    if (yuan == null || yuan < 0) {
                        errorMessage = "请输入有效金额"
                        return@Button
                    }
                    val cat = selectedCategory
                    if (cat == null) {
                        errorMessage = "请选择分类后再入账"
                        return@Button
                    }
                    val cents = Math.round(yuan * 100.0)
                    saving = true
                    errorMessage = null
                    onConfirm(
                        cents,
                        merchant,
                        cat.localId,
                        note,
                        { saving = false },
                        { msg ->
                            saving = false
                            scope.launch { errorMessage = msg }
                        },
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
            ) {
                Text(
                    text = if (saving) "入账中…" else "确认入账",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "稍后可从「待入账」通知继续",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
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
