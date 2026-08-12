package com.yuwhisper.account.ui.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    var amountText by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(LedgerRepository.TYPE_EXPENSE) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var merchant by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }
    var occurredText by remember {
        mutableStateOf(LocalDateTime.now().format(timeFormatter))
    }
    var saving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("\u8bb0\u4e00\u7b14") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "\u8fd4\u56de")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("\u91d1\u989d\uff08\u5143\uff09") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == LedgerRepository.TYPE_EXPENSE,
                    onClick = { type = LedgerRepository.TYPE_EXPENSE },
                    label = { Text("\u652f\u51fa") },
                )
                FilterChip(
                    selected = type == LedgerRepository.TYPE_INCOME,
                    onClick = { type = LedgerRepository.TYPE_INCOME },
                    label = { Text("\u6536\u5165") },
                )
            }

            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedCategory?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("\u5206\u7c7b") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false },
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            onClick = {
                                selectedCategory = cat
                                categoryExpanded = false
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("\u5546\u6237") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("\u5907\u6ce8") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = occurredText,
                onValueChange = { occurredText = it },
                label = { Text("\u65f6\u95f4\uff08yyyy-MM-dd HH:mm\uff09") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
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
                    val occurredAt = runCatching {
                        LocalDateTime.parse(occurredText.trim(), timeFormatter)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                    }.getOrElse {
                        scope.launch {
                            snackbar.showSnackbar("\u65f6\u95f4\u683c\u5f0f\u5e94\u4e3a yyyy-MM-dd HH:mm")
                        }
                        return@Button
                    }
                    val cents = Math.round(yuan * 100.0)
                    if (cents <= 0L) {
                        scope.launch { snackbar.showSnackbar("请输入有效金额") }
                        return@Button
                    }
                    saving = true
                    onSave(
                        cents,
                        type,
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
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("\u4fdd\u5b58")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
