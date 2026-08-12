package com.yuwhisper.account.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "categories",
    indices = [Index(value = ["clientId"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val clientId: String,
    val name: String,
    val sortOrder: Int = 0,
    val updatedAt: Instant = Instant.EPOCH,
    val serverId: Long? = null,
    val pendingSync: Boolean = false,
)
