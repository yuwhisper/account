package com.yuwhisper.account.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["clientId"], unique = true),
        Index(value = ["occurredAt"]),
        Index(value = ["pendingSync"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val clientId: String,
    val amountCents: Long,
    val merchant: String = "",
    val source: String = "",
    val categoryLocalId: Long? = null,
    val note: String = "",
    val occurredAt: Instant,
    val updatedAt: Instant,
    /** expense | income */
    val type: String,
    val pendingSync: Boolean = true,
    val serverId: Long? = null,
)
