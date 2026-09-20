package com.aistudio.ispbilling.control.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aistudio.ispbilling.control.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {

    @Query("SELECT * FROM customers ORDER BY id DESC")
    fun observeCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomer(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE isSynced = 0 ORDER BY id ASC")
    suspend fun getUnsyncedCustomers(): List<CustomerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customer: CustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(customers: List<CustomerEntity>)

    @Update
    suspend fun update(customer: CustomerEntity)

    @Delete
    suspend fun delete(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("""
        UPDATE customers
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
        DELETE FROM customers
        WHERE id = :id AND isSynced = 1
    """)
    suspend fun deleteSyncedCustomer(id: Long)
}
