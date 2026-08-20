package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.NetworkConnectionEntity
import com.example.data.model.NetworkDiagramEntity
import com.example.data.model.NetworkNodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkDiagramDao {
    @Query("SELECT * FROM network_diagrams ORDER BY id ASC")
    fun getAllDiagrams(): Flow<List<NetworkDiagramEntity>>

    @Query("SELECT * FROM network_diagrams WHERE isDefault = 1 LIMIT 1")
    fun getDefaultDiagram(): Flow<NetworkDiagramEntity?>

    @Query("SELECT * FROM network_nodes WHERE diagramId = :diagramId")
    fun getNodesForDiagram(diagramId: Long): Flow<List<NetworkNodeEntity>>

    @Query("SELECT * FROM network_connections WHERE diagramId = :diagramId")
    fun getConnectionsForDiagram(diagramId: Long): Flow<List<NetworkConnectionEntity>>

    @Query("SELECT * FROM network_nodes WHERE diagramId = :diagramId")
    suspend fun getNodesListForDiagram(diagramId: Long): List<NetworkNodeEntity>

    @Query("SELECT * FROM network_connections WHERE diagramId = :diagramId")
    suspend fun getConnectionsListForDiagram(diagramId: Long): List<NetworkConnectionEntity>

    @Query("SELECT * FROM network_diagrams WHERE id = :id")
    suspend fun getDiagramById(id: Long): NetworkDiagramEntity?

    @Query("SELECT * FROM network_diagrams")
    suspend fun getAllDiagramsList(): List<NetworkDiagramEntity>

    @Query("SELECT * FROM network_nodes")
    suspend fun getAllNodesList(): List<NetworkNodeEntity>

    @Query("SELECT * FROM network_connections")
    suspend fun getAllConnectionsList(): List<NetworkConnectionEntity>

    @Query("SELECT * FROM network_diagrams WHERE syncStatus = 1")
    suspend fun getDirtyDiagrams(): List<NetworkDiagramEntity>

    @Query("UPDATE network_diagrams SET syncStatus = 0 WHERE id IN (:ids)")
    suspend fun markDiagramsSynced(ids: List<Long>)

    @Query("SELECT * FROM network_nodes WHERE syncStatus = 1")
    suspend fun getDirtyNodes(): List<NetworkNodeEntity>

    @Query("UPDATE network_nodes SET syncStatus = 0 WHERE id IN (:ids)")
    suspend fun markNodesSynced(ids: List<String>)

    @Query("SELECT * FROM network_connections WHERE syncStatus = 1")
    suspend fun getDirtyConnections(): List<NetworkConnectionEntity>

    @Query("UPDATE network_connections SET syncStatus = 0 WHERE id IN (:ids)")
    suspend fun markConnectionsSynced(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiagram(diagram: NetworkDiagramEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: NetworkNodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<NetworkNodeEntity>)

    @Update
    suspend fun updateNode(node: NetworkNodeEntity)

    @Query("UPDATE network_nodes SET positionX = :x, positionY = :y WHERE id = :nodeId")
    suspend fun updateNodePosition(nodeId: String, x: Float, y: Float)

    @Query("UPDATE network_nodes SET syncStatus = :syncStatus, updatedAt = :updatedAt WHERE id = :nodeId")
    suspend fun updateNodeSyncStatus(nodeId: String, syncStatus: Int, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteNode(node: NetworkNodeEntity)

    @Query("DELETE FROM network_nodes WHERE id = :nodeId")
    suspend fun deleteNodeById(nodeId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: NetworkConnectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnections(connections: List<NetworkConnectionEntity>)

    @Delete
    suspend fun deleteConnection(connection: NetworkConnectionEntity)

    @Query("DELETE FROM network_connections WHERE id = :connectionId")
    suspend fun deleteConnectionById(connectionId: String)

    @Query("DELETE FROM network_connections WHERE fromNodeId = :nodeId OR toNodeId = :nodeId")
    suspend fun deleteConnectionsForNode(nodeId: String)

    @Query("DELETE FROM network_nodes WHERE diagramId = :diagramId")
    suspend fun clearNodesForDiagram(diagramId: Long)

    @Query("DELETE FROM network_connections WHERE diagramId = :diagramId")
    suspend fun clearConnectionsForDiagram(diagramId: Long)

    @Transaction
    suspend fun clearDiagram(diagramId: Long) {
        clearConnectionsForDiagram(diagramId)
        clearNodesForDiagram(diagramId)
    }

    @Delete
    suspend fun deleteDiagram(diagram: NetworkDiagramEntity)

    @Query("DELETE FROM network_diagrams")
    suspend fun deleteAllDiagrams()

    @Query("DELETE FROM network_nodes")
    suspend fun deleteAllNodes()

    @Query("DELETE FROM network_connections")
    suspend fun deleteAllConnections()
}
