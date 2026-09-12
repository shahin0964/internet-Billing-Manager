package com.example.util

import android.content.Context
import android.content.SharedPreferences

data class ReceiptCustomizationConfig(
    val autoPopupEnabled: Boolean = true,
    val receiptTitle: String = "পেমেন্ট রশিদ (PAYMENT RECEIPT)",
    val footerMessage: String = "আমাদের ইন্টারনেট সেবা ব্যবহার করার জন্য আপনাকে ধন্যবাদ!",
    val customNotes: String = "যেকোনো সেবা বা বিল সংক্রান্ত তথ্যের জন্য আমাদের হটলাইনে যোগাযোগ করুন।",
    val showCustomerPhone: Boolean = true,
    val showCustomerPppoe: Boolean = true,
    val showPackageName: Boolean = true,
    val showCustomerAddress: Boolean = true,
    val showRemainingDue: Boolean = true,
    val showPaymentMethod: Boolean = true,
    val paperSize: String = "A4" // "A4" or "THERMAL_80MM"
)

object ReceiptCustomizationManager {
    private const val PREFS_NAME = "receipt_customization_prefs"

    private const val KEY_AUTO_POPUP = "key_receipt_auto_popup"
    private const val KEY_RECEIPT_TITLE = "key_receipt_title"
    private const val KEY_FOOTER_MESSAGE = "key_receipt_footer_msg"
    private const val KEY_CUSTOM_NOTES = "key_receipt_custom_notes"
    private const val KEY_SHOW_PHONE = "key_show_customer_phone"
    private const val KEY_SHOW_PPPOE = "key_show_customer_pppoe"
    private const val KEY_SHOW_PACKAGE = "key_show_package_name"
    private const val KEY_SHOW_ADDRESS = "key_show_customer_address"
    private const val KEY_SHOW_REMAINING_DUE = "key_show_remaining_due"
    private const val KEY_SHOW_PAYMENT_METHOD = "key_show_payment_method"
    private const val KEY_PAPER_SIZE = "key_receipt_paper_size"

    val DEFAULT_CONFIG = ReceiptCustomizationConfig()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isAutoPopupEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_POPUP, DEFAULT_CONFIG.autoPopupEnabled)
    }

    fun setAutoPopupEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_POPUP, enabled).apply()
    }

    fun getConfig(context: Context): ReceiptCustomizationConfig {
        val prefs = getPrefs(context)
        return ReceiptCustomizationConfig(
            autoPopupEnabled = prefs.getBoolean(KEY_AUTO_POPUP, DEFAULT_CONFIG.autoPopupEnabled),
            receiptTitle = prefs.getString(KEY_RECEIPT_TITLE, DEFAULT_CONFIG.receiptTitle) ?: DEFAULT_CONFIG.receiptTitle,
            footerMessage = prefs.getString(KEY_FOOTER_MESSAGE, DEFAULT_CONFIG.footerMessage) ?: DEFAULT_CONFIG.footerMessage,
            customNotes = prefs.getString(KEY_CUSTOM_NOTES, DEFAULT_CONFIG.customNotes) ?: DEFAULT_CONFIG.customNotes,
            showCustomerPhone = prefs.getBoolean(KEY_SHOW_PHONE, DEFAULT_CONFIG.showCustomerPhone),
            showCustomerPppoe = prefs.getBoolean(KEY_SHOW_PPPOE, DEFAULT_CONFIG.showCustomerPppoe),
            showPackageName = prefs.getBoolean(KEY_SHOW_PACKAGE, DEFAULT_CONFIG.showPackageName),
            showCustomerAddress = prefs.getBoolean(KEY_SHOW_ADDRESS, DEFAULT_CONFIG.showCustomerAddress),
            showRemainingDue = prefs.getBoolean(KEY_SHOW_REMAINING_DUE, DEFAULT_CONFIG.showRemainingDue),
            showPaymentMethod = prefs.getBoolean(KEY_SHOW_PAYMENT_METHOD, DEFAULT_CONFIG.showPaymentMethod),
            paperSize = prefs.getString(KEY_PAPER_SIZE, DEFAULT_CONFIG.paperSize) ?: DEFAULT_CONFIG.paperSize
        )
    }

    fun saveConfig(context: Context, config: ReceiptCustomizationConfig) {
        getPrefs(context).edit()
            .putBoolean(KEY_AUTO_POPUP, config.autoPopupEnabled)
            .putString(KEY_RECEIPT_TITLE, config.receiptTitle)
            .putString(KEY_FOOTER_MESSAGE, config.footerMessage)
            .putString(KEY_CUSTOM_NOTES, config.customNotes)
            .putBoolean(KEY_SHOW_PHONE, config.showCustomerPhone)
            .putBoolean(KEY_SHOW_PPPOE, config.showCustomerPppoe)
            .putBoolean(KEY_SHOW_PACKAGE, config.showPackageName)
            .putBoolean(KEY_SHOW_ADDRESS, config.showCustomerAddress)
            .putBoolean(KEY_SHOW_REMAINING_DUE, config.showRemainingDue)
            .putBoolean(KEY_SHOW_PAYMENT_METHOD, config.showPaymentMethod)
            .putString(KEY_PAPER_SIZE, config.paperSize)
            .apply()
    }

    fun resetToDefaults(context: Context) {
        saveConfig(context, DEFAULT_CONFIG)
    }
}
