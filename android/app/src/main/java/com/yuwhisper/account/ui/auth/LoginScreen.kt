package com.yuwhisper.account.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.yuwhisper.account.sync.AuthRepository
import com.yuwhisper.account.sync.SyncPrefs
import com.yuwhisper.account.ui.theme.AccountMutedColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onAuthed: () -> Unit,
) {
    val context = LocalContext.current
    val auth = remember { AuthRepository(context) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf(auth.currentEmail().orEmpty()) }
    var password by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf(auth.getBaseUrl()) }
    var busy by remember { mutableStateOf(false) }
    var isRegister by remember { mutableStateOf(false) }

    fun runAuth(register: Boolean) {
        if (busy) return
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || password.length < 6) {
            scope.launch {
                snackbar.showSnackbar("请填写邮箱，密码至少 6 位")
            }
            return
        }
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    auth.setBaseUrl(baseUrl)
                }.fold(
                    onSuccess = {
                        if (register) {
                            auth.register(trimmedEmail, password)
                        } else {
                            auth.login(trimmedEmail, password)
                        }
                    },
                    onFailure = { Result.failure(it) },
                )
            }
            busy = false
            result.onSuccess {
                snackbar.showSnackbar(if (register) "注册并登录成功，正在同步" else "登录成功，正在同步")
                onAuthed()
            }.onFailure { e ->
                snackbar.showSnackbar(e.message ?: "认证失败")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isRegister) "注册" else "登录") },
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
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "未登录时可本地记账；登录后会推送待同步流水并拉取云端数据。",
                color = AccountMutedColor,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("邮箱") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("服务器地址") },
                supportingText = {
                    Text(
                        "模拟器默认 ${SyncPrefs.DEFAULT_BASE_URL}；真机请填电脑局域网 IP，如 http://192.168.1.8:8000",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                singleLine = true,
            )
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            }
            Button(
                onClick = { runAuth(register = isRegister) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isRegister) "注册并登录" else "登录")
            }
            OutlinedButton(
                onClick = { isRegister = !isRegister },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isRegister) "已有账号？去登录" else "没有账号？去注册")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
