package com.yuwhisper.account

import android.app.Application
import com.yuwhisper.account.data.local.AppDatabase
import com.yuwhisper.account.data.local.seedIfEmpty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AccountApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        applicationScope.launch(Dispatchers.IO) {
            seedIfEmpty(database)
        }
    }
}
