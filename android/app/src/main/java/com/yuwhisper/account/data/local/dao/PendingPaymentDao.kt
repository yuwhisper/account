package com.yuwhisper.account.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yuwhisper.account.data.local.entity.PendingPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingPaymentDao {
    @Query("SELECT * FROM pending_payments ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PendingPaymentEntity>>

    @Query("SELECT * FROM pending_payments ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingPaymentEntity>

    @Query("SELECT * FROM pending_payments WHERE localId = :localId LIMIT 1")
    suspend fun getById(localId: Long): PendingPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PendingPaymentEntity): Long

    @Update
    suspend fun update(entity: PendingPaymentEntity)

    @Query("DELETE FROM pending_payments WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: Long)

    @Query("DELETE FROM pending_payments")
    suspend fun deleteAll()
}
