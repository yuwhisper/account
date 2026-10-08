package com.yuwhisper.account.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.yuwhisper.account.AccountApp
import com.yuwhisper.account.ui.theme.AccountTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var ready by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AccountApp

        setContent {
            AccountTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Crossfade(targetState = ready, animationSpec = tween(220), label = "账本加载") { loaded ->
                        if (loaded) {
                            AccountRoot(repository = app.ledgerRepository)
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                                    Text("正在打开账本", style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            runCatching { app.ensureSeeded() }
                .onFailure { Log.e(TAG, "ensureSeeded failed before UI ready", it) }
            ready = true
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
