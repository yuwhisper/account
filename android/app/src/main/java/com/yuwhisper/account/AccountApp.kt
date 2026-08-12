package com.yuwhisper.account

import android.app.Application
import android.util.Log
import com.yuwhisper.account.capture.AutoBookkeepingStatusService
import com.yuwhisper.account.capture.PendingPaymentNotifier
import com.yuwhisper.account.data.LedgerRepository
import com.yuwhisper.account.data.local.AppDatabase
import com.yuwhisper.account.data.local.seedIfEmpty
import com.yuwhisper.account.sync.SyncWorker
import com.yuwhisper.account.sync.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

class AccountApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: AppDatabase
        private set

    lateinit var ledgerRepository: LedgerRepository
        private set

    private val seedDeferred by lazy {
        applicationScope.async(Dispatchers.IO) {
            try {
                seedIfEmpty(database)
            } catch (t: Throwable) {
                Log.e(TAG, "Database seed failed", t)
                throw t
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        ledgerRepository = LedgerRepository(
            database = database,
            ensureSeeded = { ensureSeeded() },
            onLocalDataChanged = {
                if (TokenStore(this).isLoggedIn()) {
                    SyncWorker.enqueueNow(this)
                }
            },
        )
        PendingPaymentNotifier.ensureChannel(this)
        // Do not start sticky FGS by default — only if user opts into status notification.
        AutoBookkeepingStatusService.refresh(this)
        // Warm up seed in background; callers must still await ensureSeeded().
        seedDeferred.start()
        if (TokenStore(this).isLoggedIn()) {
            SyncWorker.ensurePeriodic(this)
            // Debounced; avoid immediate sync storm on every cold start.
            SyncWorker.enqueueNow(this)
        }
    }

    /** Suspend until first-open seed finishes. Re-throws after logging on failure. */
    suspend fun ensureSeeded() {
        seedDeferred.await()
    }

    companion object {
        private const val TAG = "AccountApp"
    }
}
