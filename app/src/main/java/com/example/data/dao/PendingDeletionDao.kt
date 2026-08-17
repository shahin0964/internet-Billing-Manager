package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.PendingDeletionEntity

@Dao
interface PendingDeletionDao {
    @Query("SELECT * FROM pending_deletions ORDER BY timestamp ASC")
    suspend fun getAllPendingDeletions(): List<PendingDeletionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingDeletion(deletion: PendingDeletionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingDeletions(deletions: List<PendingDeletionEntity>)

    @Query("DELETE FROM pending_deletions WHERE id IN (:ids)")
    suspend fun deletePendingDeletionsByIds(ids: List<Long>)

    @Query("DELETE FROM pending_deletions WHERE collectionName = :collectionName AND documentId = :documentId")
    suspend fun deletePendingDeletion(collectionName: String, documentId: String)

    @Query("DELETE FROM pending_deletions")
    suspend fun clearAllPendingDeletions()
}
