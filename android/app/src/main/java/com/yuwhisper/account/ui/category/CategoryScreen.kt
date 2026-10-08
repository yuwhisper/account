package com.yuwhisper.account.ui.category

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuwhisper.account.data.local.entity.CategoryEntity
import com.yuwhisper.account.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    categories: List<CategoryEntity>,
    onUpsert: (localId: Long?, name: String, sortOrder: Int, onDone: () -> Unit, onError: (String) -> Unit) -> Unit,
    onDelete: (localId: Long, onError: (String) -> Unit) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var deleting by remember { mutableStateOf<CategoryEntity?>(null) }
    var newName by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    var nameInput by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun closeEditor() {
        if (!saving) { editing = null; error = null }
    }

    fun addCategory() {
        if (adding || newName.isBlank()) return
        adding = true
        addError = null
        val sort = categories.maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
        onUpsert(null, newName, sort,
            { adding = false; newName = "" },
            { message -> adding = false; addError = message })
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp),
        ) {
            item {
                AccountPageHeading("分类", "整理日常收支")
                Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 28.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it; addError = null },
                        label = { Text("新分类名称") },
                        enabled = !adding, singleLine = true, isError = addError != null,
                        supportingText = addError?.let { message -> { Text(message, color = MaterialTheme.colorScheme.error) } },
                        shape = AccountFieldShape, modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(enabled = !adding && newName.isNotBlank(), onClick = ::addCategory) {
                        Text(if (adding) "添加中…" else "添加")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("我的分类", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text("${categories.size} 类", style = MaterialTheme.typography.bodySmall, color = AccountMutedColor)
                }
            }
            if (categories.isEmpty()) {
                item { AccountEmptyState(Icons.Outlined.Category, "创建你的第一个分类", "为餐饮、出行或日常购物设置分类，\n记账时就能快速选择。") }
            }
            items(categories, key = { it.localId }) { cat ->
                Column(Modifier.animateItem()) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(cat.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { editing = cat; nameInput = cat.name; error = null }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑${cat.name}", tint = AccountMutedColor, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { deleting = cat }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "删除${cat.name}", tint = AccountMutedColor, modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                }
            }
        }
    }

    if (editing != null) {
        AccountDialog(
            onDismissRequest = ::closeEditor,
            title = "修改分类",
            confirmButton = {
                Button(enabled = !saving && nameInput.isNotBlank(), onClick = {
                    val target = editing
                    val sort = target?.sortOrder ?: (categories.maxOfOrNull { it.sortOrder }?.plus(1) ?: 0)
                    saving = true
                    error = null
                    onUpsert(target?.localId, nameInput, sort,
                        { saving = false; closeEditor() },
                        { msg -> saving = false; error = msg })
                }) { Text(if (saving) "保存中…" else "保存分类") }
            },
            dismissButton = { TextButton(enabled = !saving, onClick = ::closeEditor) { Text("取消") } },
        ) {
            OutlinedTextField(
                value = nameInput, onValueChange = { nameInput = it; error = null },
                label = { Text("分类名称") }, placeholder = { Text("例如：餐饮") },
                enabled = !saving, singleLine = true, isError = error != null,
                supportingText = error?.let { message -> { Text(message, color = MaterialTheme.colorScheme.error) } },
                shape = AccountFieldShape, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    deleting?.let { cat ->
        AccountDialog(
            onDismissRequest = { deleting = null }, title = "删除「${cat.name}」？",
            description = "已有流水会保留，分类将显示为「未分类」。",
            confirmButton = {
                TextButton(onClick = {
                    onDelete(cat.localId) { msg -> scope.launch { snackbar.showSnackbar(msg) } }
                    deleting = null
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除分类") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("保留") } },
        )
    }
}
