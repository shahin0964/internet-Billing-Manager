package com.example

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class IspApplication : Application() {
    private var networkCallbackRegistered = false
    private var activeActivityCount = 0
    private var isAppInForeground = false
    private var lastForegroundSyncTimestamp = 0L

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
            com.example.data.remote.ApiClient.init(this)
            com.example.util.PinLockManager.init(this)
            com.example.util.PrivacyModeManager.init(this)
            com.example.util.AutomaticSmsManager.schedulePeriodicSmsWorker(this)
            com.example.util.AutoBackupWorker.scheduleDailyAutoBackup(this)
            com.example.util.MonthlyAutoBillingWorker.scheduleMonthlyAutoBilling(this)
            com.example.util.SyncWorker.schedulePeriodicSync(this)

            if (isLoggedIn(this)) {
                com.example.util.SyncWorker.enqueueSync(this, forceExpedited = true)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val uid = getUserId(this@IspApplication)
                        if (!uid.isNullOrBlank()) {
                            // 1. Seamlessly restore/sync latest cloud data
                            com.example.util.HostingSyncManager.restoreOrSyncSession(this@IspApplication, uid)

                            // 2. Ensure monthly billing check
                            val repo = com.example.data.repository.IspRepository.create(this@IspApplication, uid)
                            val currentMonth = com.example.util.BillingMonthUtils.formatStandardMonth()
                            val dueDate = com.example.util.BillingMonthUtils.formatStandardDueDate()
                            repo.generateMonthlyBills(currentMonth, dueDate, isAutoGeneration = true)
                        }
                    } catch (e: Throwable) {
                        Log.d(TAG, "Startup session restore & auto-billing note: ${e.message}")
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "WorkManager initialization/scheduling deferred or unavailable: ${e.message}")
        }

        registerAppLifecycleCallbacks()
        registerNetworkSyncCallback()

        if (isLoggedIn(this)) {
            com.example.util.HostingSyncManager.startPeriodicForegroundPolling(this, 35_000L)
        }
    }

    private fun registerAppLifecycleCallbacks() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                activeActivityCount++
                if (activeActivityCount == 1) {
                    isAppInForeground = true
                    Log.i(TAG, "App entered foreground: Triggering automatic full remote data pull and starting 30-60s polling")
                    triggerAutoSyncIfLoggedIn(forceImmediate = true)
                    com.example.util.HostingSyncManager.startPeriodicForegroundPolling(this@IspApplication, 35_000L)
                }
            }

            override fun onActivityResumed(activity: Activity) {
                val now = System.currentTimeMillis()
                if (now - lastForegroundSyncTimestamp > 5000L) { // 5s throttle on rapid tab/activity switching
                    lastForegroundSyncTimestamp = now
                    Log.d(TAG, "Activity resumed: Refreshing server updates...")
                    triggerAutoSyncIfLoggedIn(forceImmediate = false)
                }
                com.example.util.HostingSyncManager.startPeriodicForegroundPolling(this@IspApplication, 35_000L)
            }

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {
                activeActivityCount = (activeActivityCount - 1).coerceAtLeast(0)
                if (activeActivityCount == 0) {
                    isAppInForeground = false
                    Log.i(TAG, "App entered background: Pausing foreground periodic polling to save resources")
                    com.example.util.HostingSyncManager.stopPeriodicForegroundPolling()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
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
                    Log.i(TAG, "Network became available: Triggering immediate full remote data pull")
                    triggerAutoSyncIfLoggedIn(forceImmediate = true)
                    if (isAppInForeground) {
                        com.example.util.HostingSyncManager.startPeriodicForegroundPolling(this@IspApplication, 35_000L)
                    }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                        Log.i(TAG, "Network validated: Triggering auto sync")
                        triggerAutoSyncIfLoggedIn(forceImmediate = true)
                        if (isAppInForeground) {
                            com.example.util.HostingSyncManager.startPeriodicForegroundPolling(this@IspApplication, 35_000L)
                        }
                    }
                }
            })
            networkCallbackRegistered = true
        } catch (e: Throwable) {
            Log.w(TAG, "Network sync callback registration deferred: ${e.message}")
        }
    }

    fun triggerAutoSyncIfLoggedIn(forceImmediate: Boolean = false) {
        val uid = getUserId(this)
        if (!uid.isNullOrBlank() && isLoggedIn(this) && com.example.util.HostingSyncManager.isSessionValid(this, uid)) {
            // 1. Enqueue guaranteed background sync worker
            com.example.util.SyncWorker.enqueueSync(this@IspApplication, forceExpedited = true)
            // 2. Also trigger immediate full remote data pull in coroutine scope
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    com.example.util.HostingSyncManager.performFullRemoteDataPull(this@IspApplication, forceDeepFallback = forceImmediate)
                } catch (e: Throwable) {
                    Log.w(TAG, "Auto sync remote pull note: ${e.message}")
                }
            }
        }
    }

    companion object {
        private const val TAG = "IspApplication"

        @JvmStatic
        fun triggerAutoSync(context: Context, forceImmediate: Boolean = false) {
            val app = context.applicationContext as? IspApplication
            if (app != null) {
                app.triggerAutoSyncIfLoggedIn(forceImmediate)
            } else {
                val uid = getUserId(context)
                if (!uid.isNullOrBlank() && isLoggedIn(context)) {
                    com.example.util.SyncWorker.enqueueSync(context, forceExpedited = true)
                    CoroutineScope(Dispatchers.IO).launch {
                        com.example.util.HostingSyncManager.performFullRemoteDataPull(context, forceDeepFallback = forceImmediate)
                    }
                }
            }
        }

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
