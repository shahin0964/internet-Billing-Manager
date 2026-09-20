package com.aistudio.ispbilling.control.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aistudio.ispbilling.control.data.local.entity.PackageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PackageDao {

    @Query("SELECT * FROM packages ORDER BY id DESC")
    fun observePackages(): Flow<List<PackageEntity>>

    @Query("SELECT * FROM packages WHERE id = :id LIMIT 1")
    suspend fun getPackage(id: Long): PackageEntity?

    @Query("SELECT * FROM packages WHERE isSynced = 0 ORDER BY id ASC")
    suspend fun getUnsyncedPackages(): List<PackageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(packageEntity: PackageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(packages: List<PackageEntity>)

    @Update
    suspend fun update(packageEntity: PackageEntity)

    @Delete
    suspend fun delete(packageEntity: PackageEntity)

    @Query("DELETE FROM packages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("""
        UPDATE packages
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
        DELETE FROM packages
        WHERE id = :id AND isSynced = 1
    """)
    suspend fun deleteSyncedPackage(id: Long)
}
