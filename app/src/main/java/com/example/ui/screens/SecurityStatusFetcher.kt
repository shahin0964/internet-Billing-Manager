package com.example.ui.screens

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.example.util.AppUpdateConfig

enum class SecurityAuditState {
    CHECKING, PASSED, FAILED, UNKNOWN
}

suspend fun fetchSecurityStatus(): SecurityAuditState = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://raw.githubusercontent.com/${AppUpdateConfig.GITHUB_OWNER}/${AppUpdateConfig.GITHUB_REPO}/security-status/security_status.json")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        
        if (connection.responseCode == 200) {
            val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonText)
            val status = json.optString("status", "")
            when (status) {
                "passed" -> SecurityAuditState.PASSED
                "failed" -> SecurityAuditState.FAILED
                else -> SecurityAuditState.UNKNOWN
            }
        } else {
            SecurityAuditState.UNKNOWN
        }
    } catch (e: Exception) {
        SecurityAuditState.UNKNOWN
    }
}
