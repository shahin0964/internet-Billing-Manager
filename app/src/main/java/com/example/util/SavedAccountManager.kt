package com.example.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedAccount(
    val userId: String,
    val name: String,
    val email: String,
    val password: String,
    val apiToken: String? = null,
    val lastLoginAt: Long = System.currentTimeMillis()
)

object SavedAccountManager {
    private const val PREFS_NAME = "isp_saved_accounts_prefs"
    private const val KEY_ACCOUNTS_JSON = "saved_accounts_json"

    fun getSavedAccounts(context: Context): List<SavedAccount> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val rawJson = prefs.getString(KEY_ACCOUNTS_JSON, null) ?: return emptyList()
            val jsonArray = JSONArray(rawJson)
            val accounts = mutableListOf<SavedAccount>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val email = obj.optString("email", "").trim()
                val password = obj.optString("password", "")
                if (email.isBlank() || password.isBlank()) continue
                accounts.add(
                    SavedAccount(
                        userId = obj.optString("userId", ""),
                        name = obj.optString("name", "").ifBlank { email.substringBefore("@") },
                        email = email,
                        password = password,
                        apiToken = obj.optString("apiToken", "").takeIf { it.isNotBlank() },
                        lastLoginAt = obj.optLong("lastLoginAt", 0L)
                    )
                )
            }
            accounts.sortedByDescending { it.lastLoginAt }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun saveAccount(
        context: Context,
        userId: String,
        name: String,
        email: String,
        password: String,
        apiToken: String? = null
    ) {
        try {
            val normalizedEmail = email.trim()
            if (normalizedEmail.isBlank() || password.isBlank()) return

            val existing = getSavedAccounts(context).toMutableList()
            existing.removeAll {
                it.email.equals(normalizedEmail, ignoreCase = true) ||
                    (userId.isNotBlank() && it.userId == userId)
            }

            existing.add(
                0,
                SavedAccount(
                    userId = userId,
                    name = name.trim().ifBlank { normalizedEmail.substringBefore("@") },
                    email = normalizedEmail,
                    password = password,
                    apiToken = apiToken,
                    lastLoginAt = System.currentTimeMillis()
                )
            )

            persistAccounts(context, existing)
        } catch (_: Throwable) {
            // Ignore storage errors safely
        }
    }

    fun removeAccount(context: Context, email: String) {
        try {
            val normalizedEmail = email.trim()
            val updated = getSavedAccounts(context).filterNot {
                it.email.equals(normalizedEmail, ignoreCase = true)
            }
            persistAccounts(context, updated)
        } catch (_: Throwable) {
            // Ignore storage errors safely
        }
    }

    private fun persistAccounts(context: Context, accounts: List<SavedAccount>) {
        val jsonArray = JSONArray()
        for (acc in accounts) {
            val obj = JSONObject().apply {
                put("userId", acc.userId)
                put("name", acc.name)
                put("email", acc.email)
                put("password", acc.password)
                put("apiToken", acc.apiToken ?: "")
                put("lastLoginAt", acc.lastLoginAt)
            }
            jsonArray.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACCOUNTS_JSON, jsonArray.toString()).apply()
    }
}
