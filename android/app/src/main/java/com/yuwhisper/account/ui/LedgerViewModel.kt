package com.yuwhisper.account.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuwhisper.account.data.LedgerRepository
import com.yuwhisper.account.data.local.entity.CategoryEntity
import com.yuwhisper.account.data.local.entity.TransactionEntity
import com.yuwhisper.account.data.local.entity.WatchAppEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class LedgerViewModel(
    private val repository: LedgerRepository,
) : ViewModel() {

    val categories: StateFlow<List<CategoryEntity>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val transactions: StateFlow<List<TransactionEntity>> = repository.observeTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val watchApps: StateFlow<List<WatchAppEntity>> = repository.observeWatchApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ledgerDays: StateFlow<List<LedgerDayGroup>> = combine(transactions, categories) { txs, cats ->
        val catMap = cats.associateBy { it.localId }
        val zone = ZoneId.systemDefault()
        txs.groupBy { it.occurredAt.atZone(zone).toLocalDate() }
            .toSortedMap(compareByDescending { it })
            .map { (date, dayTxs) ->
                val expenseCents = dayTxs
                    .filter { it.type == LedgerRepository.TYPE_EXPENSE }
                    .sumOf { it.amountCents }
                LedgerDayGroup(
                    date = date,
                    expenseCents = expenseCents,
                    items = dayTxs.map { tx ->
                        LedgerItemUi(
                            localId = tx.localId,
                            type = tx.type,
                            amountCents = tx.amountCents,
                            merchant = tx.merchant.ifBlank { "\u672a\u586b\u5199\u5546\u6237" },
                            categoryName = tx.categoryLocalId?.let { catMap[it]?.name }
                                ?: "\u672a\u5206\u7c7b",
                            note = tx.note,
                            occurredAt = tx.occurredAt,
                        )
                    },
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monthStats: StateFlow<MonthStatsUi> = combine(transactions, categories) { txs, cats ->
        val catMap = cats.associateBy { it.localId }
        val zone = ZoneId.systemDefault()
        val month = YearMonth.now(zone)
        val monthTxs = txs.filter {
            YearMonth.from(it.occurredAt.atZone(zone).toLocalDate()) == month &&
                it.type == LedgerRepository.TYPE_EXPENSE
        }
        val total = monthTxs.sumOf { it.amountCents }
        val byCategory = monthTxs
            .groupBy {
                it.categoryLocalId?.let { id -> catMap[id]?.name } ?: "\u672a\u5206\u7c7b"
            }
            .map { (name, list) -> CategorySpendUi(name, list.sumOf { it.amountCents }) }
            .sortedByDescending { it.amountCents }
        MonthStatsUi(
            yearMonth = month,
            totalExpenseCents = total,
            byCategory = byCategory,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MonthStatsUi(YearMonth.now(), 0, emptyList()),
    )

    fun addManual(
        amountCents: Long,
        type: String,
        categoryLocalId: Long?,
        merchant: String,
        note: String,
        occurredAt: Instant,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.addManualTransaction(
                    amountCents = amountCents,
                    type = type,
                    categoryLocalId = categoryLocalId,
                    merchant = merchant,
                    note = note,
                    occurredAt = occurredAt,
                )
            }.onSuccess { onDone() }
                .onFailure { onError(it.message ?: "\u4fdd\u5b58\u5931\u8d25") }
        }
    }

    fun upsertCategory(
        localId: Long?,
        name: String,
        sortOrder: Int,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { repository.upsertCategory(localId, name, sortOrder) }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "\u4fdd\u5b58\u5931\u8d25") }
        }
    }

    fun deleteCategory(localId: Long, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.deleteCategory(localId) }
                .onFailure { onError(it.message ?: "\u5220\u9664\u5931\u8d25") }
        }
    }

    fun deleteTransaction(localId: Long, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.deleteTransaction(localId) }
                .onFailure { onError(it.message ?: "\u5220\u9664\u5931\u8d25") }
        }
    }

    fun exportCsv(file: File, onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.exportCsv(file) }
                .onSuccess { onDone() }
                .onFailure { onError(it.message ?: "\u5bfc\u51fa\u5931\u8d25") }
        }
    }

    fun setWatchAppEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setWatchAppEnabled(packageName, enabled) }
                .onFailure { /* UI already optimistic via Flow */ }
        }
    }

    companion object {
        fun factory(repository: LedgerRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LedgerViewModel(repository) as T
                }
            }
    }
}

data class LedgerItemUi(
    val localId: Long,
    val type: String,
    val amountCents: Long,
    val merchant: String,
    val categoryName: String,
    val note: String,
    val occurredAt: Instant,
)

data class LedgerDayGroup(
    val date: LocalDate,
    val expenseCents: Long,
    val items: List<LedgerItemUi>,
)

data class CategorySpendUi(
    val name: String,
    val amountCents: Long,
)

data class MonthStatsUi(
    val yearMonth: YearMonth,
    val totalExpenseCents: Long,
    val byCategory: List<CategorySpendUi>,
)

fun Long.centsToYuanText(): String = String.format(java.util.Locale.CHINA, "%.2f", this / 100.0)
