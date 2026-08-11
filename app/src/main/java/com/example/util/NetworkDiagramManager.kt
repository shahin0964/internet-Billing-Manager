package com.example.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

enum class NetworkDeviceType(
    val id: String,
    val displayNameEn: String,
    val displayNameBn: String
) {
    INTERNET("INTERNET", "Internet", "ইন্টারনেট"),
    ROUTER("ROUTER", "Router", "রাউটার"),
    SWITCH("SWITCH", "Switch", "সুইচ"),
    OLT("OLT", "OLT", "ওএলটি"),
    ONU("ONU", "ONU", "ওএনইউ"),
    SERVER("SERVER", "Server", "সার্ভার"),
    ACCESS_POINT("ACCESS_POINT", "Access Point", "এক্সেস পয়েন্ট"),
    CUSTOMER("CUSTOMER", "Customer", "গ্রাহক"),
    OTHER("OTHER", "Other Device", "অন্যান্য ডিভাইস");

    companion object {
        fun fromId(id: String): NetworkDeviceType = values().firstOrNull { it.id == id } ?: OTHER
    }
}

enum class NetworkDeviceStatus(
    val id: String,
    val displayNameEn: String,
    val displayNameBn: String
) {
    ONLINE("ONLINE", "Online", "অনলাইন"),
    WARNING("WARNING", "Warning", "সতর্কবার্তা"),
    OFFLINE("OFFLINE", "Offline", "অফলাইন");

    companion object {
        fun fromId(id: String): NetworkDeviceStatus = values().firstOrNull { it.id == id } ?: ONLINE
    }
}

data class NetworkNode(
    val id: String,
    val name: String,
    val type: NetworkDeviceType,
    val ipAddress: String = "",
    val macAddress: String = "",
    val location: String = "",
    val notes: String = "",
    val status: NetworkDeviceStatus = NetworkDeviceStatus.ONLINE,
    val customerId: Long? = null,
    val customerName: String? = null,
    val x: Float = 100f,
    val y: Float = 100f
)

data class NetworkConnection(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val label: String = ""
)

data class NetworkDiagramData(
    val nodes: List<NetworkNode> = emptyList(),
    val connections: List<NetworkConnection> = emptyList()
)

object NetworkDiagramManager {
    private const val PREFS_NAME = "network_diagram_prefs"
    private const val KEY_NODES_JSON = "nodes_json"
    private const val KEY_CONNECTIONS_JSON = "connections_json"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun loadDiagramData(context: Context): NetworkDiagramData {
        val prefs = getPrefs(context)
        val nodesJson = prefs.getString(KEY_NODES_JSON, null)
        val connJson = prefs.getString(KEY_CONNECTIONS_JSON, null)

        val nodes = mutableListOf<NetworkNode>()
        if (!nodesJson.isNullOrEmpty()) {
            try {
                val array = JSONArray(nodesJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    nodes.add(
                        NetworkNode(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            type = NetworkDeviceType.fromId(obj.optString("type")),
                            ipAddress = obj.optString("ipAddress"),
                            macAddress = obj.optString("macAddress"),
                            location = obj.optString("location"),
                            notes = obj.optString("notes"),
                            status = NetworkDeviceStatus.fromId(obj.optString("status")),
                            customerId = if (obj.has("customerId") && !obj.isNull("customerId")) obj.getLong("customerId") else null,
                            customerName = if (obj.has("customerName") && !obj.isNull("customerName")) obj.optString("customerName").takeIf { it.isNotBlank() } else null,
                            x = obj.optDouble("x", 100.0).toFloat(),
                            y = obj.optDouble("y", 100.0).toFloat()
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val connections = mutableListOf<NetworkConnection>()
        if (!connJson.isNullOrEmpty()) {
            try {
                val array = JSONArray(connJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    connections.add(
                        NetworkConnection(
                            id = obj.optString("id"),
                            fromNodeId = obj.optString("fromNodeId"),
                            toNodeId = obj.optString("toNodeId"),
                            label = obj.optString("label")
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return NetworkDiagramData(nodes = nodes, connections = connections)
    }

    fun saveDiagramData(context: Context, data: NetworkDiagramData) {
        val prefs = getPrefs(context)

        val nodesArray = JSONArray()
        for (node in data.nodes) {
            val obj = JSONObject()
            obj.put("id", node.id)
            obj.put("name", node.name)
            obj.put("type", node.type.id)
            obj.put("ipAddress", node.ipAddress)
            obj.put("macAddress", node.macAddress)
            obj.put("location", node.location)
            obj.put("notes", node.notes)
            obj.put("status", node.status.id)
            node.customerId?.let { obj.put("customerId", it) }
            node.customerName?.let { obj.put("customerName", it) }
            obj.put("x", node.x.toDouble())
            obj.put("y", node.y.toDouble())
            nodesArray.put(obj)
        }

        val connArray = JSONArray()
        for (conn in data.connections) {
            val obj = JSONObject()
            obj.put("id", conn.id)
            obj.put("fromNodeId", conn.fromNodeId)
            obj.put("toNodeId", conn.toNodeId)
            obj.put("label", conn.label)
            connArray.put(obj)
        }

        prefs.edit()
            .putString(KEY_NODES_JSON, nodesArray.toString())
            .putString(KEY_CONNECTIONS_JSON, connArray.toString())
            .apply()
    }
}
