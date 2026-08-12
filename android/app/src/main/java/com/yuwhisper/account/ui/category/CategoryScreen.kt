package com.yuwhisper.account.ui.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuwhisper.account.data.local.entity.CategoryEntity
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
    var creating by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("\u5206\u7c7b") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    creating = true
                    editing = null
                    nameInput = ""
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "\u65b0\u589e\u5206\u7c7b")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(categories, key = { it.localId }) { cat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(cat.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "\u6392\u5e8f ${cat.sortOrder}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = {
                            editing = cat
                            creating = false
                            nameInput = cat.name
                        },
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "\u7f16\u8f91")
                    }
                    IconButton(
                        onClick = {
                            onDelete(cat.localId) { msg ->
                                scope.launch { snackbar.showSnackbar(msg) }
                            }
                        },
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "\u5220\u9664")
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (creating || editing != null) {
        val title = if (creating) "\u65b0\u589e\u5206\u7c7b" else "\u7f16\u8f91\u5206\u7c7b"
        AlertDialog(
            onDismissRequest = {
                creating = false
                editing = null
            },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("\u540d\u79f0") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = editing
                        val sort = target?.sortOrder
                            ?: (categories.maxOfOrNull { it.sortOrder }?.plus(1) ?: 0)
                        onUpsert(
                            target?.localId,
                            nameInput,
                            sort,
                            {
                                creating = false
                                editing = null
                            },
                            { msg -> scope.launch { snackbar.showSnackbar(msg) } },
                        )
                    },
                ) {
                    Text("\u4fdd\u5b58")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        creating = false
                        editing = null
                    },
                ) {
                    Text("\u53d6\u6d88")
                }
            },
        )
    }
}
