package com.yuwhisper.account.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_apps")
data class WatchAppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val enabled: Boolean = true,
)
