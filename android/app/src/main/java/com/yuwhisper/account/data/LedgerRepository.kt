package com.yuwhisper.account.data

import com.yuwhisper.account.data.local.AppDatabase
import com.yuwhisper.account.data.local.entity.CategoryEntity
import com.yuwhisper.account.data.local.entity.PendingPaymentEntity
import com.yuwhisper.account.data.local.entity.TransactionEntity
import com.yuwhisper.account.data.local.entity.WatchAppEntity
import com.yuwhisper.account.domain.Candidate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Local ledger data access.
 *
 * CSV [exportCsv] writes [amount] as yuan with two decimal places
 * (derived from [TransactionEntity.amountCents] / 100).
 */
class LedgerRepository(
    private val database: AppDatabase,
    private val ensureSeeded: suspend () -> Unit,
) {
    private val transactionDao get() = database.transactionDao()
    private val categoryDao get() = database.categoryDao()
    private val pendingPaymentDao get() = database.pendingPaymentDao()
    private val watchAppDao get() = database.watchAppDao()

    fun observeTransactions(): Flow<List<TransactionEntity>> = flow {
        ensureSeeded()
        emitAll(transactionDao.observeAll())
    }

    fun observeCategories(): Flow<List<CategoryEntity>> = flow {
        ensureSeeded()
        emitAll(categoryDao.observeAll())
    }

    fun observeWatchApps(): Flow<List<WatchAppEntity>> = flow {
        ensureSeeded()
        emitAll(watchAppDao.observeAll())
    }

    suspend fun isWatchAppEnabled(packageName: String): Boolean {
        ensureSeeded()
        return watchAppDao.getAll().any { it.packageName == packageName && it.enabled }
    }

    suspend fun setWatchAppEnabled(packageName: String, enabled: Boolean) {
        ensureSeeded()
        watchAppDao.setEnabled(packageName, enabled)
    }

    suspend fun addManualTransaction(
        amountCents: Long,
        type: String,
        categoryLocalId: Long?,
        merchant: String = "",
        note: String = "",
        occurredAt: Instant = Instant.now(),
        source: String = SOURCE_MANUAL,
    ) {
        ensureSeeded()
        require(type == TYPE_EXPENSE || type == TYPE_INCOME) { "type must be expense|income" }
        require(amountCents >= 0) { "amountCents must be >= 0" }
        val now = Instant.now()
        transactionDao.insert(
            TransactionEntity(
                clientId = UUID.randomUUID().toString(),
                amountCents = amountCents,
                merchant = merchant.trim(),
                source = source,
                categoryLocalId = categoryLocalId,
                note = note.trim(),
                occurredAt = occurredAt,
                updatedAt = now,
                type = type,
                pendingSync = true,
            ),
        )
    }

    suspend fun upsertCategory(
        localId: Long? = null,
        name: String,
        sortOrder: Int = 0,
    ): Long {
        ensureSeeded()
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "category name required" }
        val now = Instant.now()
        return if (localId == null || localId == 0L) {
            categoryDao.insert(
                CategoryEntity(
                    clientId = UUID.randomUUID().toString(),
                    name = trimmed,
                    sortOrder = sortOrder,
                    updatedAt = now,
                    pendingSync = true,
                ),
            )
        } else {
            val existing = categoryDao.getAll().firstOrNull { it.localId == localId }
                ?: error("category not found: $localId")
            categoryDao.update(
                existing.copy(
                    name = trimmed,
                    sortOrder = sortOrder,
                    updatedAt = now,
                    pendingSync = true,
                ),
            )
            localId
        }
    }

    suspend fun deleteCategory(localId: Long) {
        ensureSeeded()
        categoryDao.deleteByLocalId(localId)
    }

    /** Pending + booked candidates for [com.yuwhisper.account.domain.Dedupe]. */
    suspend fun listDedupeCandidates(): List<Candidate> {
        ensureSeeded()
        val fromPending = pendingPaymentDao.getAll().map { it.toCandidate() }
        val fromTx = transactionDao.getAll().map { it.toCandidate() }
        return fromPending + fromTx
    }

    suspend fun insertPendingPayment(entity: PendingPaymentEntity): Long {
        ensureSeeded()
        return pendingPaymentDao.insert(entity)
    }

    suspend fun getPendingPayment(localId: Long): PendingPaymentEntity? {
        ensureSeeded()
        return pendingPaymentDao.getById(localId)
    }

    /**
     * Confirm a pending payment into an expense transaction, then delete the pending row.
     * Category is required.
     */
    suspend fun confirmPendingPayment(
        pendingLocalId: Long,
        amountCents: Long,
        merchant: String,
        categoryLocalId: Long,
        note: String,
    ) {
        ensureSeeded()
        require(amountCents >= 0) { "amountCents must be >= 0" }
        require(categoryLocalId > 0) { "category required" }
        val pending = pendingPaymentDao.getById(pendingLocalId)
            ?: error("pending not found: $pendingLocalId")
        val now = Instant.now()
        transactionDao.insert(
            TransactionEntity(
                clientId = UUID.randomUUID().toString(),
                amountCents = amountCents,
                merchant = merchant.trim(),
                source = pending.source.ifBlank { SOURCE_MANUAL },
                categoryLocalId = categoryLocalId,
                note = note.trim(),
                occurredAt = pending.occurredAt ?: pending.createdAt,
                updatedAt = now,
                type = TYPE_EXPENSE,
                pendingSync = true,
            ),
        )
        pendingPaymentDao.deleteByLocalId(pendingLocalId)
    }

    /**
     * Writes UTF-8 CSV with header:
     * `occurred_at,type,amount,merchant,source,category,note`
     *
     * [amount] is yuan with two decimals (not cents).
     */
    suspend fun exportCsv(file: File) {
        ensureSeeded()
        val categories = categoryDao.getAll().associateBy { it.localId }
        val transactions = transactionDao.getAll()
        val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withZone(ZoneId.systemDefault())

        file.bufferedWriter(Charsets.UTF_8).use { out ->
            out.appendLine(CSV_HEADER)
            for (tx in transactions) {
                val categoryName = tx.categoryLocalId?.let { categories[it]?.name }.orEmpty()
                val amountYuan = String.format(Locale.US, "%.2f", tx.amountCents / 100.0)
                out.appendLine(
                    listOf(
                        formatter.format(tx.occurredAt),
                        tx.type,
                        amountYuan,
                        tx.merchant,
                        tx.source,
                        categoryName,
                        tx.note,
                    ).joinToString(",") { csvEscape(it) },
                )
            }
        }
    }

    companion object {
        const val TYPE_EXPENSE = "expense"
        const val TYPE_INCOME = "income"
        const val SOURCE_MANUAL = "manual"
        const val CSV_HEADER = "occurred_at,type,amount,merchant,source,category,note"

        private fun csvEscape(value: String): String {
            val needsQuote = value.contains(',') ||
                value.contains('"') ||
                value.contains('\n') ||
                value.contains('\r')
            val escaped = value.replace("\"", "\"\"")
            return if (needsQuote) "\"$escaped\"" else escaped
        }
    }
}

private fun PendingPaymentEntity.toCandidate(): Candidate = Candidate(
    source = source,
    amountCents = (amountCents ?: 0).toInt(),
    merchant = merchant,
    occurredAt = occurredAt ?: createdAt,
)

private fun TransactionEntity.toCandidate(): Candidate = Candidate(
    source = source,
    amountCents = amountCents.toInt().coerceAtLeast(0),
    merchant = merchant,
    occurredAt = occurredAt,
)
