package com.example.util

import android.content.Context
import com.example.IspApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class SavedAccount(
    val userId: String,
    val name: String,
    val ispName: String = "",
    val email: String,
    val password: String,
    val apiToken: String? = null,
    val lastLoginAt: Long = System.currentTimeMillis()
) {
    val displayName: String
        get() {
            val cleanIsp = ispName.trim()
            val cleanName = name.trim()
            val genericIspNames = setOf("isp billing", "my isp", "isp", "internet billing management", "")
            return when {
                cleanIsp.lowercase() !in genericIspNames -> cleanIsp
                cleanName.isNotBlank() && !cleanName.equals("Admin Account", ignoreCase = true) -> cleanName
                cleanIsp.isNotBlank() -> cleanIsp
                cleanName.isNotBlank() -> cleanName
                else -> email.substringBefore("@")
            }
        }
}

object SavedAccountManager {
    private const val PREFS_NAME = "isp_saved_accounts_prefs"
    private const val KEY_ACCOUNTS_JSON = "saved_accounts_json"

    private val _accountsFlow = MutableStateFlow<List<SavedAccount>>(emptyList())
    val accountsFlow: StateFlow<List<SavedAccount>> = _accountsFlow.asStateFlow()

    fun getSavedAccounts(context: Context): List<SavedAccount> {
        return try {
            val appCtx = context.applicationContext ?: context
            val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val rawJson = prefs.getString(KEY_ACCOUNTS_JSON, null)
            if (rawJson.isNullOrBlank()) {
                _accountsFlow.value = emptyList()
                return emptyList()
            }
            val jsonArray = JSONArray(rawJson)
            val accounts = mutableListOf<SavedAccount>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val email = obj.optString("email", "").trim()
                val password = obj.optString("password", "")
                val userId = obj.optString("userId", "").trim()
                val apiToken = obj.optString("apiToken", "").trim().takeIf { it.isNotBlank() }

                // Valid if we have email AND either password OR (userId + apiToken)
                if (email.isBlank()) continue
                if (password.isBlank() && (userId.isBlank() || apiToken.isNullOrBlank())) continue

                accounts.add(
                    SavedAccount(
                        userId = userId,
                        name = obj.optString("name", "").ifBlank { email.substringBefore("@") },
                        ispName = obj.optString("ispName", ""),
                        email = email,
                        password = password,
                        apiToken = apiToken,
                        lastLoginAt = obj.optLong("lastLoginAt", 0L)
                    )
                )
            }
            val sorted = accounts.sortedByDescending { it.lastLoginAt }
            _accountsFlow.value = sorted
            sorted
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
        apiToken: String? = null,
        ispName: String = ""
    ) {
        try {
            val appCtx = context.applicationContext ?: context
            val normalizedEmail = email.trim()
            if (normalizedEmail.isBlank()) return

            val existing = getSavedAccounts(appCtx).toMutableList()
            val previousMatch = existing.firstOrNull {
                it.email.equals(normalizedEmail, ignoreCase = true) ||
                    (userId.isNotBlank() && it.userId == userId)
            }

            existing.removeAll {
                it.email.equals(normalizedEmail, ignoreCase = true) ||
                    (userId.isNotBlank() && it.userId == userId)
            }

            val resolvedPassword = password.ifBlank { previousMatch?.password ?: "" }
            val resolvedToken = apiToken?.takeIf { it.isNotBlank() } ?: previousMatch?.apiToken
            val resolvedUserId = userId.ifBlank { previousMatch?.userId ?: "" }
            val resolvedName = name.trim().ifBlank { previousMatch?.name ?: normalizedEmail.substringBefore("@") }
            val resolvedIspName = ispName.trim().ifBlank { previousMatch?.ispName ?: "" }

            if (resolvedPassword.isBlank() && (resolvedUserId.isBlank() || resolvedToken.isNullOrBlank())) {
                return
            }

            existing.add(
                0,
                SavedAccount(
                    userId = resolvedUserId,
                    name = resolvedName,
                    ispName = resolvedIspName,
                    email = normalizedEmail,
                    password = resolvedPassword,
                    apiToken = resolvedToken,
                    lastLoginAt = System.currentTimeMillis()
                )
            )

            persistAccounts(appCtx, existing)
        } catch (_: Throwable) {
            // Ignore storage errors safely
        }
    }

    /**
     * Ensures the currently logged-in account is saved/updated in SavedAccountManager
     * even if the user was already logged in prior to this update or is about to log out.
     */
    fun syncActiveSession(context: Context, currentIspName: String = "") {
        try {
            val appCtx = context.applicationContext ?: context
            if (!IspApplication.isLoggedIn(appCtx)) return
            val userId = IspApplication.getUserId(appCtx)?.trim().orEmpty()
            val email = IspApplication.getUserEmail(appCtx)?.trim().orEmpty()
            val name = IspApplication.getUserName(appCtx)?.trim().orEmpty()
            val token = IspApplication.getAuthToken(appCtx)?.trim()

            if (email.isBlank() || userId.isBlank()) return

            saveAccount(
                context = appCtx,
                userId = userId,
                name = name,
                email = email,
                password = "",
                apiToken = token,
                ispName = currentIspName
            )
        } catch (_: Throwable) {
            // Ignore safely
        }
    }

    fun removeAccount(context: Context, email: String) {
        try {
            val appCtx = context.applicationContext ?: context
            val normalizedEmail = email.trim()
            val updated = getSavedAccounts(appCtx).filterNot {
                it.email.equals(normalizedEmail, ignoreCase = true)
            }
            persistAccounts(appCtx, updated)
        } catch (_: Throwable) {
            // Ignore storage errors safely
        }
    }

    private fun persistAccounts(context: Context, accounts: List<SavedAccount>) {
        val appCtx = context.applicationContext ?: context
        val jsonArray = JSONArray()
        for (acc in accounts) {
            val obj = JSONObject().apply {
                put("userId", acc.userId)
                put("name", acc.name)
                put("ispName", acc.ispName)
                put("email", acc.email)
                put("password", acc.password)
                put("apiToken", acc.apiToken ?: "")
                put("lastLoginAt", acc.lastLoginAt)
            }
            jsonArray.put(obj)
        }
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACCOUNTS_JSON, jsonArray.toString()).commit()
        _accountsFlow.value = accounts
    }
}
