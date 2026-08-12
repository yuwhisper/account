package com.yuwhisper.account.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Local-only pending payment snapshot. No ignore path — must confirm into a transaction.
 */
@Entity(tableName = "pending_payments")
data class PendingPaymentEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val amountCents: Long?,
    val merchant: String = "",
    val source: String = "",
    /** notification | accessibility | manual */
    val captureChannel: String = "",
    val rawText: String = "",
    val dedupeKey: String = "",
    val occurredAt: Instant? = null,
    val createdAt: Instant,
)
