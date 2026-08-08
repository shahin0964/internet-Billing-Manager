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

        @JvmStatic
        fun ensureFirebaseInitialized(context: Context) {
            try {
                if (FirebaseApp.getApps(context).isNotEmpty()) {
                    return
                }

                // 1. Try standard initialization from google-services.json resources
                try {
                    FirebaseApp.initializeApp(context)
                } catch (e: Throwable) {
                    Log.w(TAG, "Standard FirebaseApp.initializeApp failed: ${e.message}")
                }

                if (FirebaseApp.getApps(context).isNotEmpty()) {
                    Log.d(TAG, "FirebaseApp initialized via standard provider/resources")
                    return
                }

                // 2. Explicit options fallback from app/google-services.json
                val optionsFromResource = try {
                    FirebaseOptions.fromResource(context)
                } catch (e: Throwable) {
                    null
                }

                val optionsToUse = optionsFromResource ?: FirebaseOptions.Builder()
                    .setApplicationId("1:5791179901:android:e7eaaa4cbf2e26f3017d14")
                    .setApiKey("AIzaSyAeWGj18zHcQXBIhYV_2mA9yeSwWWZ4s1o")
                    .setProjectId("isp-billing-b04b3")
                    .setGcmSenderId("5791179901")
                    .setDatabaseUrl("https://isp-billing-b04b3-default-rtdb.asia-southeast1.firebasedatabase.app")
                    .setStorageBucket("isp-billing-b04b3.firebasestorage.app")
                    .build()

                FirebaseApp.initializeApp(context, optionsToUse)
                Log.d(TAG, "FirebaseApp initialized successfully with explicit options")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to initialize FirebaseApp: ${e.message}", e)
            }
        }
    }
}

