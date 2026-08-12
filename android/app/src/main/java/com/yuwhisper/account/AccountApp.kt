package com.yuwhisper.account

import android.app.Application
import android.util.Log
import com.yuwhisper.account.data.LedgerRepository
import com.yuwhisper.account.data.local.AppDatabase
import com.yuwhisper.account.data.local.seedIfEmpty
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
        )
        // Warm up seed in background; callers must still await ensureSeeded().
        seedDeferred.start()
    }

    /** Suspend until first-open seed finishes. Re-throws after logging on failure. */
    suspend fun ensureSeeded() {
        seedDeferred.await()
    }

    companion object {
        private const val TAG = "AccountApp"
    }
}
