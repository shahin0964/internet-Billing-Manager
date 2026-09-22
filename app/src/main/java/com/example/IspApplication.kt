package com.example

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class IspApplication : Application() {
    private var networkCallbackRegistered = false

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

        try {
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
        } catch (e: Throwable) {
            Log.w(TAG, "WorkManager initialization/scheduling deferred or unavailable: ${e.message}")
        }

        com.example.data.remote.ApiClient.authToken = getAuthToken(this)

        registerNetworkSyncCallback()
    }

    private fun registerNetworkSyncCallback() {
        if (networkCallbackRegistered) return
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    triggerAutoSyncIfLoggedIn()
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                        triggerAutoSyncIfLoggedIn()
                    }
                }
            })
            networkCallbackRegistered = true
        } catch (e: Throwable) {
            Log.w(TAG, "Network sync callback registration deferred: ${e.message}")
        }
    }

    private fun triggerAutoSyncIfLoggedIn() {
        val uid = getUserId(this)
        if (!uid.isNullOrBlank() && isLoggedIn(this) && com.example.util.HostingSyncManager.isSessionValid(this, uid)) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    com.example.util.HostingSyncManager.syncLocalToHosting(this@IspApplication)
                } catch (e: Throwable) {
                    Log.w(TAG, "Auto sync on network available note: ${e.message}")
                }
            }
        }
    }

    companion object {
        private const val TAG = "IspApplication"

        @JvmStatic
        fun getAuthToken(context: Context): String? {
            val prefs = context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
            return prefs.getString("auth_token", null)
        }

        @JvmStatic
        fun setAuthToken(context: Context, token: String?) {
            val prefs = context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("auth_token", token).apply()
            com.example.data.remote.ApiClient.authToken = token
        }

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

        @JvmStatic
        fun getLastAuthenticatedUserId(context: Context): String? {
            val prefs = context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
            return prefs.getString("last_authenticated_user_id", null)
        }

        @JvmStatic
        fun setLastAuthenticatedUserId(context: Context, id: String?) {
            val prefs = context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("last_authenticated_user_id", id).apply()
        }
    }
}
