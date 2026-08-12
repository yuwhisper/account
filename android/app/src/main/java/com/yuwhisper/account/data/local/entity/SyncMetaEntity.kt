package com.yuwhisper.account.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val key: String,
    val value: String = "",
    val updatedAt: Instant = Instant.EPOCH,
)
