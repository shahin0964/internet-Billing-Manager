package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PrivacyModeManager {
    private const val PREFS_NAME = "isp_prefs"
    private const val KEY_PRIVACY_MODE = "privacy_mode"

    private val _privacyModeFlow = MutableStateFlow(false)
    val privacyModeFlow: StateFlow<Boolean> = _privacyModeFlow.asStateFlow()

    private var isInitialized = false

    fun init(context: Context) {
        val enabled = getPrefs(context).getBoolean(KEY_PRIVACY_MODE, false)
        _privacyModeFlow.value = enabled
        isInitialized = true
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isPrivacyModeEnabled(context: Context): Boolean {
        val enabled = getPrefs(context).getBoolean(KEY_PRIVACY_MODE, false)
        if (_privacyModeFlow.value != enabled) {
            _privacyModeFlow.value = enabled
        }
        return enabled
    }

    fun setPrivacyModeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PRIVACY_MODE, enabled).commit()
        _privacyModeFlow.value = enabled
    }

    fun maskAmount(amountFormatted: String, isPrivacy: Boolean): String {
        return if (isPrivacy) "••••••" else amountFormatted
    }

    fun maskCurrencyAmount(currencySymbol: String, amountFormatted: String, isPrivacy: Boolean): String {
        return if (isPrivacy) "$currencySymbol••••••" else "$currencySymbol$amountFormatted"
    }

    fun maskPhone(phone: String, isPrivacy: Boolean): String {
        if (!isPrivacy || phone.length < 5) return phone
        val trimmed = phone.trim()
        if (trimmed.length <= 6) return "••••••"
        val start = trimmed.take(3)
        val end = trimmed.takeLast(2)
        val dots = "•".repeat(maxOf(4, trimmed.length - 5))
        return "$start$dots$end"
    }

    fun maskGeneric(text: String, isPrivacy: Boolean): String {
        return if (isPrivacy) "••••••" else text
    }
}
