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
            val appContext = context.applicationContext ?: context
            try {
                if (FirebaseApp.getApps(appContext).isEmpty()) {
                    val defaultOptions = try {
                        FirebaseOptions.fromResource(appContext)
                    } catch (e: Exception) {
                        null
                    }

                    if (defaultOptions != null) {
                        FirebaseApp.initializeApp(appContext)
                        Log.d(TAG, "FirebaseApp initialized successfully from default resources")
                    } else {
                        val envApiKey = try {
                            val field = BuildConfig::class.java.getField("GOOGLE_API_KEY")
                            (field.get(null) as? String)?.takeIf {
                                it.isNotBlank() && !it.startsWith("YOUR_")
                            }
                        } catch (e: Throwable) {
                            null
                        }

                        if (!envApiKey.isNullOrBlank()) {
                            val options = FirebaseOptions.Builder()
                                .setApplicationId("1:5791179901:android:e7eaaa4cbf2e26f3017d14")
                                .setApiKey(envApiKey)
                                .setProjectId("isp-billing-b04b3")
                                .setGcmSenderId("5791179901")
                                .setDatabaseUrl("https://isp-billing-b04b3-default-rtdb.asia-southeast1.firebasedatabase.app")
                                .setStorageBucket("isp-billing-b04b3.firebasestorage.app")
                                .build()
                            FirebaseApp.initializeApp(appContext, options)
                            Log.d(TAG, "FirebaseApp initialized successfully with secure options")
                        } else {
                            Log.e(TAG, "Failed to initialize FirebaseApp: default options and API key missing")
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to initialize FirebaseApp: ${e.message}", e)
            }
        }
    }
}
