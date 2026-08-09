package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class IspApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ensureFirebaseInitialized(this)
    }

    companion object {
        private const val TAG = "IspApplication"

        // Official Firebase Web/Android Client API Key for project isp-billing-b04b3 (from google-services.json)
        private const val FIREBASE_CLIENT_API_KEY = "AIzaSyAeWGj18zHcQXBIhYV_2mA9yeSwWWZ4s1o"
        private const val FIREBASE_APP_ID = "1:5791179901:android:e7eaaa4cbf2e26f3017d14"
        private const val FIREBASE_PROJECT_ID = "isp-billing-b04b3"
        private const val FIREBASE_GCM_SENDER_ID = "5791179901"
        private const val FIREBASE_DB_URL = "https://isp-billing-b04b3-default-rtdb.asia-southeast1.firebasedatabase.app"
        private const val FIREBASE_STORAGE_BUCKET = "isp-billing-b04b3.firebasestorage.app"

        @JvmStatic
        fun ensureFirebaseInitialized(context: Context) {
            val appContext = context.applicationContext ?: context
            try {
                // Check if Google API Key (Gemini key) was loaded into BuildConfig
                val googleApiKey = try {
                    val field = BuildConfig::class.java.getField("GOOGLE_API_KEY")
                    (field.get(null) as? String)?.trim()
                } catch (e: Throwable) {
                    null
                }

                if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                    val currentApp = FirebaseApp.getInstance()
                    val currentApiKey = currentApp.options.apiKey
                    // If FirebaseApp was misconfigured with Gemini GOOGLE_API_KEY, delete and re-initialize with Firebase API Key
                    if (!googleApiKey.isNullOrBlank() && currentApiKey == googleApiKey) {
                        Log.w(TAG, "FirebaseApp was initialized with Gemini GOOGLE_API_KEY. Re-initializing with correct Firebase Client API Key.")
                        currentApp.delete()
                    } else {
                        return
                    }
                }

                // Attempt to load from google-services.json generated resources first
                val defaultOptions = try {
                    FirebaseOptions.fromResource(appContext)
                } catch (e: Exception) {
                    null
                }

                if (defaultOptions != null && (googleApiKey.isNullOrBlank() || defaultOptions.apiKey != googleApiKey)) {
                    FirebaseApp.initializeApp(appContext)
                    Log.d(TAG, "FirebaseApp initialized successfully from default resources")
                } else {
                    // Programmatic fallback using official Firebase Client configuration
                    val options = FirebaseOptions.Builder()
                        .setApplicationId(FIREBASE_APP_ID)
                        .setApiKey(FIREBASE_CLIENT_API_KEY)
                        .setProjectId(FIREBASE_PROJECT_ID)
                        .setGcmSenderId(FIREBASE_GCM_SENDER_ID)
                        .setDatabaseUrl(FIREBASE_DB_URL)
                        .setStorageBucket(FIREBASE_STORAGE_BUCKET)
                        .build()

                    FirebaseApp.initializeApp(appContext, options)
                    Log.d(TAG, "FirebaseApp initialized successfully with valid Firebase options")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to initialize FirebaseApp: ${e.message}", e)
            }
        }
    }
}
