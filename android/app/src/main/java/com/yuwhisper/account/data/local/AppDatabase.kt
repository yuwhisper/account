package com.yuwhisper.account.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.yuwhisper.account.data.local.converter.InstantConverters
import com.yuwhisper.account.data.local.dao.CategoryDao
import com.yuwhisper.account.data.local.dao.PendingPaymentDao
import com.yuwhisper.account.data.local.dao.SyncMetaDao
import com.yuwhisper.account.data.local.dao.TransactionDao
import com.yuwhisper.account.data.local.dao.WatchAppDao
import com.yuwhisper.account.data.local.entity.CategoryEntity
import com.yuwhisper.account.data.local.entity.PendingPaymentEntity
import com.yuwhisper.account.data.local.entity.SyncMetaEntity
import com.yuwhisper.account.data.local.entity.TransactionEntity
import com.yuwhisper.account.data.local.entity.WatchAppEntity
import com.yuwhisper.account.domain.DefaultWatchApps
import com.yuwhisper.account.domain.SeedCategories
import java.time.Instant
import java.util.UUID

@Database(
    entities = [
        CategoryEntity::class,
        TransactionEntity::class,
        PendingPaymentEntity::class,
        WatchAppEntity::class,
        SyncMetaEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(InstantConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun pendingPaymentDao(): PendingPaymentDao
    abstract fun watchAppDao(): WatchAppDao
    abstract fun syncMetaDao(): SyncMetaDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }
        }

        private fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "yuwhisper_account.db",
            ).build()
        }
    }
}

/** Idempotent seed for first open (categories + default watch apps). */
suspend fun seedIfEmpty(database: AppDatabase) {
    val categoryDao = database.categoryDao()
    val watchAppDao = database.watchAppDao()

    if (categoryDao.count() == 0) {
        val now = Instant.now()
        val categories = SeedCategories.NAMES.mapIndexed { index, name ->
            CategoryEntity(
                clientId = UUID.randomUUID().toString(),
                name = name,
                sortOrder = index,
                updatedAt = now,
                pendingSync = false,
            )
        }
        categoryDao.insertAll(categories)
    }

    if (watchAppDao.count() == 0) {
        val apps = DefaultWatchApps.ALL.map {
            WatchAppEntity(
                packageName = it.packageName,
                label = it.label,
                enabled = it.enabled,
            )
        }
        watchAppDao.upsertAll(apps)
    }
}
