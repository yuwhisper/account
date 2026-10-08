package com.yuwhisper.account.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yuwhisper.account.sync.AuthRepository
import com.yuwhisper.account.sync.SyncPrefs
import com.yuwhisper.account.ui.theme.AccountCard
import com.yuwhisper.account.ui.theme.AccountFieldShape
import com.yuwhisper.account.ui.theme.AccountMutedColor
import com.yuwhisper.account.ui.theme.AccountPageHeading
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
    var passwordVisible by remember { mutableStateOf(false) }
    var showServer by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    fun runAuth(register: Boolean) {
        if (busy) return
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || password.length < 6) {
            authError = "请填写邮箱，密码至少 6 位"
            scope.launch {
                snackbar.showSnackbar("请填写邮箱，密码至少 6 位")
            }
            return
        }
        authError = null
        focusManager.clearFocus()
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
                authError = e.message ?: "认证失败"
                snackbar.showSnackbar(authError.orEmpty())
            }
        }
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
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimatedContent(targetState = isRegister, label = "authHeading") { register ->
                    AccountPageHeading(
                        title = if (register) "创建账号" else "登录同步",
                        subtitle = "账本随你同行",
                    )
                }
                Text(
                    "账本保存在本机，登录后与云端同步。",
                    color = AccountMutedColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            AccountCard(modifier = Modifier.animateContentSize(animationSpec = spring(dampingRatio = 1f, stiffness = 600f))) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; authError = null },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AccountFieldShape,
                    label = { Text("邮箱") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; authError = null },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = AccountFieldShape,
                    label = { Text("密码") },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                            )
                        }
                    },
                    supportingText = { Text("至少 6 位") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { runAuth(register = isRegister) }),
                )
                AnimatedVisibility(visible = authError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        Text(
                            authError.orEmpty(),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                Button(
                    onClick = { runAuth(register = isRegister) },
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        Text(
                            if (busy) "正在连接…" else if (isRegister) "注册并登录" else "登录并同步",
                        )
                    }
                }
                TextButton(
                    onClick = { isRegister = !isRegister; authError = null },
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(if (isRegister) "已有账号？去登录" else "创建一个账号")
                }
            }
            AccountCard(modifier = Modifier.animateContentSize(animationSpec = spring(dampingRatio = 1f, stiffness = 600f))) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("同步服务器", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showServer = !showServer }) {
                        Text(if (showServer) "收起" else "更改")
                    }
                }
                if (!showServer) {
                    Text(baseUrl, color = AccountMutedColor, style = MaterialTheme.typography.bodySmall)
                }
                AnimatedVisibility(visible = showServer) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it; authError = null },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        label = { Text("服务器地址") },
                        supportingText = {
                            Text(
                                "默认 ${SyncPrefs.DEFAULT_BASE_URL}\n本地调试可填 http://10.0.2.2:8000",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                }
            }
        }
    }
}
