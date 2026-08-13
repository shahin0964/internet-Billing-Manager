package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.ui.graphics.vector.ImageVector

enum class NodeType(
    val displayName: String,
    val iconName: String,
    val defaultBadge: String
) {
    INTERNET("Internet / Upstream", "Language", "🌐"),
    CORE_ROUTER("Core Router", "Whatshot", "🔥"),
    MIKROTIK("MikroTik Router", "CellTower", "📡"),
    SERVER("Server", "Dns", "🖥️"),
    SWITCH("Switch", "Hub", "🔌"),
    OLT("OLT", "Router", "📶"),
    ONU_ONT("ONU / ONT", "Inbox", "📦"),
    CUSTOMER("Customer", "Home", "🏠"),
    AREA_ZONE("Area / Zone", "Place", "📍");

    companion object {
        fun fromName(name: String): NodeType {
            return try {
                valueOf(name)
            } catch (e: Exception) {
                MIKROTIK
            }
        }
    }
}

@Entity(tableName = "network_diagrams")
data class NetworkDiagramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "network_nodes")
data class NetworkNodeEntity(
    @PrimaryKey val id: String, // UUID
    val diagramId: Long,
    val name: String,
    val type: String, // Enum name of NodeType
    val ipAddress: String = "",
    val location: String = "",
    val areaZone: String = "",
    val portInfo: String = "",
    val customerRef: String = "",
    val customerId: String = "",
    val notes: String = "",
    val positionX: Float = 0f,
    val positionY: Float = 0f
)

@Entity(tableName = "network_connections")
data class NetworkConnectionEntity(
    @PrimaryKey val id: String, // UUID
    val diagramId: Long,
    val fromNodeId: String,
    val toNodeId: String,
    val label: String = "",
    val notes: String = ""
)

data class FullNetworkDiagram(
    val diagram: NetworkDiagramEntity,
    val nodes: List<NetworkNodeEntity>,
    val connections: List<NetworkConnectionEntity>
)
