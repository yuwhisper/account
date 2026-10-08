package com.yuwhisper.account.ui.manual

import com.yuwhisper.account.ui.theme.AccountSerif

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuwhisper.account.ui.theme.AccountCard
import com.yuwhisper.account.ui.theme.AccountFieldShape
import com.yuwhisper.account.ui.theme.AccountPageHeading
import com.yuwhisper.account.data.LedgerRepository
import com.yuwhisper.account.data.local.entity.CategoryEntity
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryScreen(
    categories: List<CategoryEntity>,
    onBack: () -> Unit,
    onSave: (
        amountCents: Long,
        type: String,
        categoryLocalId: Long?,
        merchant: String,
        note: String,
        occurredAt: Instant,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var amountText by rememberSaveable { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var selectedCategoryLocalId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedCategory = categories.firstOrNull { it.localId == selectedCategoryLocalId }
    var merchant by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }
    var occurredText by rememberSaveable {
        mutableStateOf(LocalDateTime.now().format(timeFormatter))
    }
    var saving by remember { mutableStateOf(false) }
    val fieldShape = AccountFieldShape

    BackHandler(enabled = saving) { }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !saving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.navigationBarsPadding().imePadding(),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(
                        enabled = !saving,
                        onClick = {
                            val yuan = amountText.toDoubleOrNull()
                            if (yuan == null || !yuan.isFinite() || yuan <= 0.0 || yuan > 1_000_000.0) {
                                scope.launch { snackbar.showSnackbar("请输入 0.01～1000000 的有效金额") }
                                return@Button
                            }
                            val parts = amountText.trim().split('.')
                            if (parts.size > 1 && parts[1].length > 2) {
                                scope.launch { snackbar.showSnackbar("金额最多两位小数") }
                                return@Button
                            }
                            val parsedTime = runCatching {
                                LocalDateTime.parse(occurredText.trim(), timeFormatter)
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                            }
                            if (parsedTime.isFailure) {
                                scope.launch { snackbar.showSnackbar("时间格式应为 yyyy-MM-dd HH:mm") }
                                return@Button
                            }
                            val occurredAt = parsedTime.getOrThrow()
                            val cents = Math.round(yuan * 100.0)
                            if (cents <= 0L) {
                                scope.launch { snackbar.showSnackbar("请输入有效金额") }
                                return@Button
                            }
                            saving = true
                            onSave(
                                cents,
                                LedgerRepository.TYPE_EXPENSE,
                                selectedCategory?.localId,
                                merchant,
                                note,
                                occurredAt,
                                {
                                    saving = false
                                    onBack()
                                },
                                { msg ->
                                    saving = false
                                    scope.launch { snackbar.showSnackbar(msg) }
                                },
                            )
                        },
                        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(min = 56.dp),
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
                            if (saving) "保存中…" else "保存支出",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                AccountPageHeading(title = "记一笔", subtitle = "手动记录支出")
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    enabled = !saving,
                    label = { Text("金额 · 元") },
                    prefix = { Text("¥", style = MaterialTheme.typography.headlineSmall) },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = AccountSerif,
                        fontWeight = FontWeight.Normal,
                        fontSize = 40.sp,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                AccountCard {
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { if (!saving) categoryExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedCategory?.name ?: "",
                            onValueChange = {},
                            enabled = !saving,
                            readOnly = true,
                            label = { Text("分类 · 可选") },
                            placeholder = { Text("选择分类") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                            shape = fieldShape,
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false },
                        ) {
                            if (categories.isEmpty()) {
                                DropdownMenuItem(text = { Text("还没有分类") }, onClick = {}, enabled = false)
                            }
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        selectedCategoryLocalId = cat.localId
                                        categoryExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = merchant,
                        onValueChange = { merchant = it },
                        enabled = !saving,
                        label = { Text("商户 · 可选") },
                        singleLine = true,
                        shape = fieldShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = occurredText,
                        onValueChange = { occurredText = it },
                        enabled = !saving,
                        label = { Text("消费时间") },
                        supportingText = { Text("格式：yyyy-MM-dd HH:mm") },
                        singleLine = true,
                        shape = fieldShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        enabled = !saving,
                        label = { Text("备注 · 可选") },
                        minLines = 2,
                        maxLines = 4,
                        shape = fieldShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
