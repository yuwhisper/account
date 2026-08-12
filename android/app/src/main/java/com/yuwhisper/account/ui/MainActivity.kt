package com.yuwhisper.account.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (ready) {
                        AccountRoot(repository = app.ledgerRepository)
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
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
