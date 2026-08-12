package com.yuwhisper.account.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yuwhisper.account.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, localId ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, localId ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT * FROM categories WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE serverId = :serverId LIMIT 1")
    suspend fun findByServerId(serverId: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE clientId = :clientId LIMIT 1")
    suspend fun findByClientId(clientId: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<CategoryEntity>)

    @Update
    suspend fun update(entity: CategoryEntity)

    @Query("DELETE FROM categories WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: Long)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}
