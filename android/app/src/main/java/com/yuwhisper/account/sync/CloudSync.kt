package com.yuwhisper.account.sync

import android.content.Context
import android.util.Log
import com.yuwhisper.account.data.LedgerRepository
import com.yuwhisper.account.data.local.AppDatabase
import com.yuwhisper.account.data.local.entity.CategoryEntity
import com.yuwhisper.account.data.local.entity.SyncMetaEntity
import com.yuwhisper.account.data.local.entity.TransactionEntity
import com.yuwhisper.account.data.local.seedIfEmpty
import com.yuwhisper.account.sync.dto.CategoryCreateRequest
import com.yuwhisper.account.sync.dto.CategoryUpdateRequest
import com.yuwhisper.account.sync.dto.TransactionPushItem
import com.yuwhisper.account.sync.dto.TransactionPushRequest
import retrofit2.HttpException
import java.time.Instant
import java.util.UUID

/**
 * Login-gated cloud sync:
 * 1) pull/merge categories (name / client_id)
 * 2) push local category changes
 * 3) push pendingSync transactions (category_id = server id)
 * 4) pull transactions since lastPull; merge by updated_at
 */
class CloudSync(
    private val database: AppDatabase,
    private val apiClient: ApiClient,
    private val tokenStore: TokenStore,
) {
    private val categoryDao get() = database.categoryDao()
    private val transactionDao get() = database.transactionDao()
    private val syncMetaDao get() = database.syncMetaDao()

    suspend fun syncNow(): Result<Unit> {
        if (!tokenStore.isLoggedIn()) {
            return Result.failure(IllegalStateException("not logged in"))
        }
        return runCatching {
            seedIfEmpty(database)
            val api = apiClient.syncApi()
            pushCategoryDeletions(api)
            pushTransactionDeletions(api)
            pullAndMergeCategories(api)
            pushLocalCategories(api)
            pushPendingTransactions(api)
            pullAndMergeTransactions(api)
        }.onFailure { e ->
            Log.w(TAG, "sync failed", e)
        }
    }

    private suspend fun pullAndMergeCategories(api: SyncApi) {
        val remote = api.listCategories()
        for (dto in remote) {
            val byClient = dto.client_id?.takeIf { it.isNotBlank() }
                ?.let { categoryDao.findByClientId(it) }
            val existing = byClient ?: categoryDao.findByName(dto.name)
            val remoteUpdated = SyncTime.parse(dto.updated_at)
            if (existing == null) {
                categoryDao.insert(
                    CategoryEntity(
                        clientId = dto.client_id?.takeIf { it.isNotBlank() }
                            ?: UUID.randomUUID().toString(),
                        name = dto.name,
                        sortOrder = dto.sort_order,
                        updatedAt = remoteUpdated,
                        serverId = dto.id,
                        pendingSync = false,
                    ),
                )
            } else {
                val shouldTakeRemote = !existing.pendingSync ||
                    remoteUpdated >= existing.updatedAt
                categoryDao.update(
                    existing.copy(
                        name = if (shouldTakeRemote) dto.name else existing.name,
                        sortOrder = if (shouldTakeRemote) dto.sort_order else existing.sortOrder,
                        updatedAt = maxOf(existing.updatedAt, remoteUpdated),
                        serverId = dto.id,
                        pendingSync = if (shouldTakeRemote) false else existing.pendingSync,
                    ),
                )
            }
        }
    }

    private suspend fun pushCategoryDeletions(api: SyncApi) {
        val tombstones = syncMetaDao.getByPrefix(LedgerRepository.CATEGORY_DELETE_PREFIX)
        for (tombstone in tombstones) {
            val serverId = tombstone.value.toLongOrNull()
            if (serverId == null) {
                syncMetaDao.delete(tombstone.key)
                continue
            }
            try {
                api.deleteCategory(serverId)
                syncMetaDao.delete(tombstone.key)
            } catch (e: HttpException) {
                // Already deleted remotely is a successful idempotent outcome.
                if (e.code() == 404) {
                    syncMetaDao.delete(tombstone.key)
                } else {
                    throw e
                }
            }
        }
    }

    private suspend fun pushTransactionDeletions(api: SyncApi) {
        val tombstones = syncMetaDao.getByPrefix(LedgerRepository.TRANSACTION_DELETE_SERVER_PREFIX)
        for (tombstone in tombstones) {
            val serverId = tombstone.value.toLongOrNull()
            if (serverId == null) {
                syncMetaDao.delete(tombstone.key)
                continue
            }
            try {
                api.deleteTransaction(serverId)
                syncMetaDao.delete(tombstone.key)
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    syncMetaDao.delete(tombstone.key)
                } else {
                    throw e
                }
            }
        }
    }

    private suspend fun ensureServerCategory(
        api: SyncApi,
        local: CategoryEntity,
    ): CategoryEntity {
        local.serverId?.let { return local }
        return try {
            val created = api.createCategory(
                CategoryCreateRequest(
                    name = local.name,
                    sort_order = local.sortOrder,
                    client_id = local.clientId,
                ),
            )
            val updated = local.copy(
                serverId = created.id,
                updatedAt = SyncTime.parse(created.updated_at),
                pendingSync = false,
            )
            categoryDao.update(updated)
            updated
        } catch (e: HttpException) {
            if (e.code() != 409) throw e
            pullAndMergeCategories(api)
            categoryDao.findByClientId(local.clientId)
                ?: categoryDao.findByName(local.name)
                ?: throw e
        }
    }

    private suspend fun pushLocalCategories(api: SyncApi) {
        val pending = categoryDao.getPendingSync()
        for (local in pending) {
            val serverId = local.serverId
            if (serverId == null) {
                ensureServerCategory(api, local)
            } else {
                val updated = api.updateCategory(
                    serverId,
                    CategoryUpdateRequest(
                        name = local.name,
                        sort_order = local.sortOrder,
                        client_id = local.clientId,
                    ),
                )
                categoryDao.update(
                    local.copy(
                        serverId = updated.id,
                        updatedAt = SyncTime.parse(updated.updated_at),
                        pendingSync = false,
                    ),
                )
            }
        }

        for (cat in categoryDao.getAll()) {
            if (cat.serverId == null) {
                ensureServerCategory(api, cat)
            }
        }
    }

    private suspend fun pushPendingTransactions(api: SyncApi) {
        val pending = transactionDao.getPendingSync()
        if (pending.isEmpty()) return

        val categories = categoryDao.getAll().associateBy { it.localId }
        val items = pending.map { tx ->
            val serverCategoryId = tx.categoryLocalId?.let { categories[it]?.serverId }
            TransactionPushItem(
                client_id = tx.clientId,
                amount_cents = tx.amountCents,
                merchant = tx.merchant,
                source = tx.source,
                category_id = serverCategoryId,
                note = tx.note,
                occurred_at = SyncTime.format(tx.occurredAt),
                updated_at = SyncTime.format(tx.updatedAt),
                type = tx.type,
            )
        }
        api.pushTransactions(TransactionPushRequest(transactions = items))
        for (tx in pending) {
            transactionDao.update(tx.copy(pendingSync = false))
        }
    }

    private suspend fun pullAndMergeTransactions(api: SyncApi) {
        val sinceRaw = syncMetaDao.get(KEY_LAST_PULL)?.value
            ?.takeIf { it.isNotBlank() }
            ?: SyncTime.format(Instant.EPOCH)
        val pull = api.pullTransactions(since = sinceRaw)
        val categoriesByServerId = categoryDao.getAll()
            .mapNotNull { c -> c.serverId?.let { it to c } }
            .toMap()

        for (dto in pull.transactions) {
            val remoteUpdated = SyncTime.parse(dto.updated_at)
            val deleted = syncMetaDao.get(
                LedgerRepository.TRANSACTION_DELETE_CLIENT_PREFIX + dto.client_id,
            )
            if (deleted != null) continue
            val categoryLocalId = dto.category_id?.let { categoriesByServerId[it]?.localId }
            val existing = transactionDao.findByClientId(dto.client_id)
            if (existing == null) {
                transactionDao.insert(
                    TransactionEntity(
                        clientId = dto.client_id,
                        amountCents = dto.amount_cents,
                        merchant = dto.merchant,
                        source = dto.source,
                        categoryLocalId = categoryLocalId,
                        note = dto.note,
                        occurredAt = SyncTime.parse(dto.occurred_at),
                        updatedAt = remoteUpdated,
                        type = dto.type,
                        pendingSync = false,
                        serverId = dto.id,
                    ),
                )
            } else if (remoteUpdated >= existing.updatedAt) {
                transactionDao.update(
                    existing.copy(
                        amountCents = dto.amount_cents,
                        merchant = dto.merchant,
                        source = dto.source,
                        categoryLocalId = if (dto.category_id == null) {
                            null
                        } else {
                            categoryLocalId ?: existing.categoryLocalId
                        },
                        note = dto.note,
                        occurredAt = SyncTime.parse(dto.occurred_at),
                        updatedAt = remoteUpdated,
                        type = dto.type,
                        pendingSync = false,
                        serverId = dto.id,
                    ),
                )
            } else if (existing.serverId == null) {
                transactionDao.update(existing.copy(serverId = dto.id))
            }
        }

        syncMetaDao.upsert(
            SyncMetaEntity(
                key = KEY_LAST_PULL,
                value = SyncTime.format(SyncTime.parse(pull.server_time)),
                updatedAt = Instant.now(),
            ),
        )
    }

    companion object {
        private const val TAG = "CloudSync"
        const val KEY_LAST_PULL = "last_pull"

        fun create(context: Context): CloudSync {
            val appContext = context.applicationContext
            val database = AppDatabase.getInstance(appContext)
            val tokenStore = TokenStore(appContext)
            val apiClient = ApiClient(appContext, tokenStore, SyncPrefs(appContext))
            return CloudSync(database, apiClient, tokenStore)
        }
    }
}
