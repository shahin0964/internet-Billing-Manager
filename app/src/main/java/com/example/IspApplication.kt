package com.example

import android.app.Application
import android.content.Context
import android.util.Log

class IspApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Handle GMS related background thread crashes gracefully
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val message = throwable.message ?: ""
            val stackTraceStr = Log.getStackTraceString(throwable)
            val isGmsSecurityException = (throwable is SecurityException || stackTraceStr.contains("SecurityException")) && 
                (message.contains("com.google.android.gms") || 
                 message.contains("GoogleApiManager") ||
                 stackTraceStr.contains("com.google.android.gms") ||
                 stackTraceStr.contains("GoogleApiManager"))
                 
            if (isGmsSecurityException) {
                Log.e(TAG, "Caught background GMS SecurityException gracefully in thread ${thread.name}: ${throwable.message}")
            } else {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        com.example.util.AutomaticSmsManager.schedulePeriodicSmsWorker(this)

        val backupRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.util.AutoBackupWorker>(
            24, java.util.concurrent.TimeUnit.HOURS
        ).setConstraints(
            androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()
        ).build()

        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "auto_hosting_backup",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            backupRequest
        )
    }

    companion object {
        private const val TAG = "IspApplication"

        @JvmStatic
        fun isLoggedIn(context: Context): Boolean {
            val prefs = context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
            return prefs.getBoolean("is_logged_in", false)
        }

        @JvmStatic
        fun setLoggedIn(context: Context, value: Boolean) {
            val prefs = context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_logged_in", value).apply()
        }

        @JvmStatic
        fun getUserId(context: Context): String? {
            val prefs = context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
            return prefs.getString("user_id", null)
        }

        @JvmStatic
        fun setUserId(context: Context, id: String?) {
            val prefs = context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("user_id", id).apply()
        }

        @JvmStatic
        fun getUserName(context: Context): String? {
            val prefs = context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
            return prefs.getString("user_name", null)
        }

        @JvmStatic
        fun setUserName(context: Context, name: String?) {
            val prefs = context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("user_name", name).apply()
        }

        @JvmStatic
        fun getUserEmail(context: Context): String? {
            val prefs = context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
            return prefs.getString("user_email", null)
        }

        @JvmStatic
        fun setUserEmail(context: Context, email: String?) {
            val prefs = context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("user_email", email).apply()
        }
    }
}
