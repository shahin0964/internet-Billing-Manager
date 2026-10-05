package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinLockManager {
    private const val PREFS_NAME = "secure_pin_app_lock"
    private const val KEY_PIN_ENABLED = "pin_lock_enabled"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"

    private const val SALT_SIZE_BYTES = 16
    private const val HASH_KEY_LENGTH = 256
    private const val ITERATION_COUNT = 10000

    private val _pinLockEnabledFlow = MutableStateFlow(false)
    val pinLockEnabledFlow: StateFlow<Boolean> = _pinLockEnabledFlow.asStateFlow()

    private val _hasPinSetFlow = MutableStateFlow(false)
    val hasPinSetFlow: StateFlow<Boolean> = _hasPinSetFlow.asStateFlow()

    private var isInitialized = false

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun init(context: Context) {
        val prefs = getPrefs(context)
        val hasPin = !prefs.getString(KEY_PIN_HASH, null).isNullOrBlank() &&
                !prefs.getString(KEY_PIN_SALT, null).isNullOrBlank()
        val isEnabled = prefs.getBoolean(KEY_PIN_ENABLED, false) && hasPin
        _hasPinSetFlow.value = hasPin
        _pinLockEnabledFlow.value = isEnabled
        isInitialized = true
    }

    fun isPinLockEnabled(context: Context): Boolean {
        val prefs = getPrefs(context)
        val hasPin = hasPinSet(context)
        val enabled = prefs.getBoolean(KEY_PIN_ENABLED, false) && hasPin
        if (_pinLockEnabledFlow.value != enabled) {
            _pinLockEnabledFlow.value = enabled
        }
        return enabled
    }

    fun hasPinSet(context: Context): Boolean {
        val prefs = getPrefs(context)
        val hasPin = !prefs.getString(KEY_PIN_HASH, null).isNullOrBlank() &&
                !prefs.getString(KEY_PIN_SALT, null).isNullOrBlank()
        if (_hasPinSetFlow.value != hasPin) {
            _hasPinSetFlow.value = hasPin
        }
        return hasPin
    }

    fun savePin(context: Context, pin: String): Boolean {
        if (!pin.matches(Regex("^[0-9]{4,6}$"))) {
            return false
        }
        val secureRandom = SecureRandom()
        val salt = ByteArray(SALT_SIZE_BYTES)
        secureRandom.nextBytes(salt)

        val hash = deriveHash(pin, salt) ?: return false

        val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP)

        val success = getPrefs(context).edit()
            .putString(KEY_PIN_SALT, saltBase64)
            .putString(KEY_PIN_HASH, hashBase64)
            .putBoolean(KEY_PIN_ENABLED, true)
            .commit()

        if (success) {
            _hasPinSetFlow.value = true
            _pinLockEnabledFlow.value = true
        }

        return success
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        if (!pin.matches(Regex("^[0-9]{4,6}$"))) {
            return false
        }
        val prefs = getPrefs(context)
        val saltBase64 = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val storedHashBase64 = prefs.getString(KEY_PIN_HASH, null) ?: return false

        val salt = try {
            Base64.decode(saltBase64, Base64.NO_WRAP)
        } catch (e: Exception) {
            return false
        }

        val storedHash = try {
            Base64.decode(storedHashBase64, Base64.NO_WRAP)
        } catch (e: Exception) {
            return false
        }

        val computedHash = deriveHash(pin, salt) ?: return false

        return MessageDigest.isEqual(storedHash, computedHash)
    }

    fun setPinLockEnabled(context: Context, enabled: Boolean) {
        val hasPin = hasPinSet(context)
        getPrefs(context).edit().putBoolean(KEY_PIN_ENABLED, enabled).commit()
        _pinLockEnabledFlow.value = enabled && hasPin
        _hasPinSetFlow.value = hasPin
    }

    fun removePin(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .putBoolean(KEY_PIN_ENABLED, false)
            .commit()
        _hasPinSetFlow.value = false
        _pinLockEnabledFlow.value = false
    }

    private fun deriveHash(pin: String, salt: ByteArray): ByteArray? {
        return try {
            val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATION_COUNT, HASH_KEY_LENGTH)
            val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            skf.generateSecret(spec).encoded
        } catch (e: Exception) {
            null
        }
    }
}
