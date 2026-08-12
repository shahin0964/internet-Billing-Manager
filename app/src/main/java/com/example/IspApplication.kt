package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class IspApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Handle GMS and Firebase related background thread crashes gracefully
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val isGmsSecurityException = throwable is SecurityException && 
                (throwable.message?.contains("com.google.android.gms") == true || 
                 throwable.message?.contains("GoogleApiManager") == true)
                 
            if (isGmsSecurityException) {
                Log.e(TAG, "Caught background GMS SecurityException gracefully in thread ${thread.name}: ${throwable.message}")
            } else {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        if (isLoggedIn(this)) {
            ensureFirebaseInitialized(this)
        }
        com.example.util.AutomaticSmsManager.schedulePeriodicSmsWorker(this)
    }

    companion object {
        private const val TAG = "IspApplication"
        private const val FIREBASE_API_KEY = "AIzaSyAeWGj18zHcQXBIhYV_2mA9yeSwWWZ4s1o"
        private const val FIREBASE_APP_ID = "1:5791179901:android:42984c6ab35fa812017d14"
        private const val FIREBASE_PROJECT_ID = "isp-billing-b04b3"
        private const val FIREBASE_SENDER_ID = "5791179901"
        private const val FIREBASE_DB_URL = "https://isp-billing-b04b3-default-rtdb.asia-southeast1.firebasedatabase.app"
        private const val FIREBASE_STORAGE_BUCKET = "isp-billing-b04b3.firebasestorage.app"

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
        fun ensureFirebaseInitialized(context: Context) {
            val appContext = context.applicationContext ?: context
            try {
                if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                    val currentApp = FirebaseApp.getInstance()
                    val currentOptions = currentApp.options
                    // If initialized with wrong key or mismatched App ID, delete and re-init
                    if (currentOptions.applicationId != FIREBASE_APP_ID || 
                        (currentOptions.apiKey.startsWith("AQ") || currentOptions.apiKey.startsWith("YOUR_"))) {
                        Log.w(TAG, "Re-initializing FirebaseApp due to mismatched App ID or invalid key (${currentOptions.applicationId})")
                        currentApp.delete()
                    }
                }

                if (FirebaseApp.getApps(appContext).isEmpty()) {
                    val defaultOptions = try {
                        FirebaseOptions.fromResource(appContext)
                    } catch (e: Exception) {
                        null
                    }

                    if (defaultOptions != null && defaultOptions.applicationId == FIREBASE_APP_ID) {
                        FirebaseApp.initializeApp(appContext, defaultOptions)
                        Log.d(TAG, "FirebaseApp initialized successfully from default resources")
                    } else {
                        val options = FirebaseOptions.Builder()
                            .setApplicationId(FIREBASE_APP_ID)
                            .setApiKey(FIREBASE_API_KEY)
                            .setProjectId(FIREBASE_PROJECT_ID)
                            .setGcmSenderId(FIREBASE_SENDER_ID)
                            .setDatabaseUrl(FIREBASE_DB_URL)
                            .setStorageBucket(FIREBASE_STORAGE_BUCKET)
                            .build()
                        FirebaseApp.initializeApp(appContext, options)
                        Log.d(TAG, "FirebaseApp initialized successfully with explicit Firebase options")
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to initialize FirebaseApp: ${e.message}", e)
            }
        }
    }
}
