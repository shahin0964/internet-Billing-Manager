package com.aistudio.ispbilling.control.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aistudio.ispbilling.control.data.local.entity.BillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {

    @Query("SELECT * FROM bills ORDER BY id DESC")
    fun observeBills(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE id = :id LIMIT 1")
    suspend fun getBill(id: Long): BillEntity?

    @Query("SELECT * FROM bills WHERE isSynced = 0 ORDER BY id ASC")
    suspend fun getUnsyncedBills(): List<BillEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bill: BillEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bills: List<BillEntity>)

    @Update
    suspend fun update(bill: BillEntity)

    @Delete
    suspend fun delete(bill: BillEntity)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("""
        UPDATE bills
        SET isSynced = :isSynced,
            syncAction = :syncAction
        WHERE id = :id
    """)
    suspend fun updateSyncState(
        id: Long,
        isSynced: Boolean,
        syncAction: String
    )

    @Query("""
        DELETE FROM bills
        WHERE id = :id AND isSynced = 1
    """)
    suspend fun deleteSyncedBill(id: Long)
}
