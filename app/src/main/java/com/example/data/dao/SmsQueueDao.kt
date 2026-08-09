package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.SmsQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsQueueDao {
    @Query("SELECT * FROM sms_queue ORDER BY id DESC")
    fun getAllSmsFlow(): Flow<List<SmsQueueEntity>>

    @Query("SELECT * FROM sms_queue ORDER BY id DESC")
    suspend fun getAllSms(): List<SmsQueueEntity>

    @Query("SELECT * FROM sms_queue WHERE status = :status ORDER BY id ASC")
    suspend fun getSmsByStatus(status: String): List<SmsQueueEntity>

    @Query("SELECT * FROM sms_queue WHERE id = :id")
    suspend fun getSmsById(id: Long): SmsQueueEntity?

    @Query("SELECT COUNT(*) FROM sms_queue WHERE idempotencyKey = :idempotencyKey")
    suspend fun countByIdempotencyKey(idempotencyKey: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSms(sms: SmsQueueEntity): Long

    @Update
    suspend fun updateSms(sms: SmsQueueEntity)

    @Delete
    suspend fun deleteSms(sms: SmsQueueEntity)

    @Query("DELETE FROM sms_queue")
    suspend fun clearAll()
}
