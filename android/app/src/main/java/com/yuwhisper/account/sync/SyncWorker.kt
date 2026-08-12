package com.yuwhisper.account.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val outcome = CloudSync.create(applicationContext).syncNow()
        if (outcome.isSuccess) return Result.success()

        val err = outcome.exceptionOrNull()
        if (err is HttpException && (err.code() == 401 || err.code() == 403)) {
            TokenStore(applicationContext).clear()
            cancelAll(applicationContext)
            return Result.failure()
        }
        return Result.retry()
    }

    companion object {
        const val UNIQUE_PERIODIC = "account_cloud_sync_periodic"
        const val UNIQUE_ONCE = "account_cloud_sync_once"

        fun enqueueNow(context: Context) {
            if (!TokenStore(context).isLoggedIn()) return
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                // Coalesce rapid local edits; WorkManager REPLACE keeps latest.
                .setInitialDelay(20, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_ONCE,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun ensurePeriodic(context: Context) {
            if (!TokenStore(context).isLoggedIn()) {
                WorkManager.getInstance(context.applicationContext)
                    .cancelUniqueWork(UNIQUE_PERIODIC)
                return
            }
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancelAll(context: Context) {
            val wm = WorkManager.getInstance(context.applicationContext)
            wm.cancelUniqueWork(UNIQUE_PERIODIC)
            wm.cancelUniqueWork(UNIQUE_ONCE)
        }
    }
}
