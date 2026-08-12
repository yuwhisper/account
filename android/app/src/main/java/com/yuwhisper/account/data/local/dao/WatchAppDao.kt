package com.yuwhisper.account.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yuwhisper.account.data.local.entity.WatchAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchAppDao {
    @Query("SELECT * FROM watch_apps ORDER BY label ASC")
    fun observeAll(): Flow<List<WatchAppEntity>>

    @Query("SELECT * FROM watch_apps ORDER BY label ASC")
    suspend fun getAll(): List<WatchAppEntity>

    @Query("SELECT COUNT(*) FROM watch_apps")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<WatchAppEntity>)

    @Update
    suspend fun update(entity: WatchAppEntity)

    @Query("UPDATE watch_apps SET enabled = :enabled WHERE packageName = :packageName")
    suspend fun setEnabled(packageName: String, enabled: Boolean)
}
