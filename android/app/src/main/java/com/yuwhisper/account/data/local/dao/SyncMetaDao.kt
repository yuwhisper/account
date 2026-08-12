package com.yuwhisper.account.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yuwhisper.account.data.local.entity.SyncMetaEntity

@Dao
interface SyncMetaDao {
    @Query("SELECT * FROM sync_meta WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): SyncMetaEntity?

    @Query("SELECT * FROM sync_meta WHERE `key` LIKE :prefix || '%'")
    suspend fun getByPrefix(prefix: String): List<SyncMetaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncMetaEntity)

    @Query("DELETE FROM sync_meta WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM sync_meta")
    suspend fun deleteAll()
}
