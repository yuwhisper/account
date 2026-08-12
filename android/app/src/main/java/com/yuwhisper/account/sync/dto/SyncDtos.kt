package com.yuwhisper.account.sync.dto

data class CategoryDto(
    val id: Long,
    val name: String,
    val sort_order: Int = 0,
    val updated_at: String,
    val client_id: String? = null,
)

data class CategoryCreateRequest(
    val name: String,
    val sort_order: Int = 0,
    val client_id: String? = null,
)

data class CategoryUpdateRequest(
    val name: String? = null,
    val sort_order: Int? = null,
    val client_id: String? = null,
)

data class TransactionDto(
    val id: Long,
    val client_id: String,
    val amount_cents: Long,
    val merchant: String = "",
    val source: String = "",
    val category_id: Long? = null,
    val note: String = "",
    val occurred_at: String,
    val updated_at: String,
    val type: String,
)

data class TransactionPushItem(
    val client_id: String,
    val amount_cents: Long,
    val merchant: String = "",
    val source: String = "",
    val category_id: Long? = null,
    val note: String = "",
    val occurred_at: String,
    val updated_at: String,
    val type: String,
)

data class TransactionPushRequest(
    val transactions: List<TransactionPushItem>,
)

data class PushOkResponse(
    val ok: Boolean = true,
)

data class TransactionPullResponse(
    val transactions: List<TransactionDto>,
    val server_time: String,
)
