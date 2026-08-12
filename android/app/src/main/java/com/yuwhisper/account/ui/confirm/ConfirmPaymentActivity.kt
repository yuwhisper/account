package com.yuwhisper.account.ui.confirm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.yuwhisper.account.AccountApp
import com.yuwhisper.account.capture.PendingPaymentNotifier
import com.yuwhisper.account.data.local.entity.CategoryEntity
import com.yuwhisper.account.data.local.entity.PendingPaymentEntity
import com.yuwhisper.account.ui.theme.AccountTheme
import kotlinx.coroutines.launch

/**
 * Centered force-confirm card. Back / dismiss keeps the pending row
 * so the user can reopen via the「待入账」notification.
 */
class ConfirmPaymentActivity : ComponentActivity() {

    private var pending by mutableStateOf<PendingPaymentEntity?>(null)
    private var categories by mutableStateOf<List<CategoryEntity>>(emptyList())
    private var loadError by mutableStateOf<String?>(null)
    private var pendingId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingId = intent.getLongExtra(EXTRA_PENDING_ID, -1L)
        if (pendingId < 0) {
            finish()
            return
        }

        val app = application as AccountApp
        setContent {
            AccountTheme {
                ConfirmPaymentScreen(
                    pending = pending,
                    categories = categories,
                    loadError = loadError,
                    animateIn = true,
                    onConfirm = { amountCents, merchant, categoryLocalId, note, onDone, onError ->
                        lifecycleScope.launch {
                            runCatching {
                                app.ledgerRepository.confirmPendingPayment(
                                    pendingLocalId = pendingId,
                                    amountCents = amountCents,
                                    merchant = merchant,
                                    categoryLocalId = categoryLocalId,
                                    note = note,
                                )
                                PendingPaymentNotifier.cancel(this@ConfirmPaymentActivity, pendingId)
                            }.onSuccess {
                                onDone()
                                finish()
                                overridePendingTransition(0, android.R.anim.fade_out)
                            }.onFailure { e ->
                                Log.e(TAG, "confirm failed", e)
                                onError(e.message ?: "确认失败")
                            }
                        }
                    },
                    onDismissKeepPending = {
                        finish()
                        overridePendingTransition(0, android.R.anim.fade_out)
                    },
                )
            }
        }

        lifecycleScope.launch {
            runCatching {
                app.ensureSeeded()
                val row = app.ledgerRepository.getPendingPayment(pendingId)
                if (row == null) {
                    loadError = "待确认记录不存在或已入账"
                    return@runCatching
                }
                pending = row
                app.ledgerRepository.observeCategories().collect { list ->
                    categories = list
                }
            }.onFailure { e ->
                Log.e(TAG, "load pending failed", e)
                loadError = e.message ?: "加载失败"
            }
        }
    }

    companion object {
        const val EXTRA_PENDING_ID = "pending_id"
        private const val TAG = "ConfirmPaymentActivity"

        fun createIntent(context: Context, pendingId: Long): Intent =
            Intent(context, ConfirmPaymentActivity::class.java).putExtra(EXTRA_PENDING_ID, pendingId)
    }
}
