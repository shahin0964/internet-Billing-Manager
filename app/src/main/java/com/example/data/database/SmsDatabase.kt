package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.SmsQueueDao
import com.example.data.model.SmsQueueEntity

@Database(entities = [SmsQueueEntity::class], version = 1, exportSchema = false)
abstract class SmsDatabase : RoomDatabase() {
    abstract fun smsQueueDao(): SmsQueueDao

    companion object {
        private val instances = java.util.concurrent.ConcurrentHashMap<String, SmsDatabase>()

        fun getDatabaseNameForUser(userId: String?): String {
            if (userId.isNullOrBlank() || userId == "guest" || userId == "authenticated_user") {
                return "isp_sms_features_guest.db"
            }
            val safeId = userId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val hash = try {
                val md = java.security.MessageDigest.getInstance("SHA-256")
                md.digest(userId.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                    .take(12)
            } catch (e: Exception) {
                userId.hashCode().toString().replace("-", "n")
            }
            return "isp_sms_user_${safeId}_${hash}.db"
        }

        fun getDatabase(context: Context, userId: String? = null): SmsDatabase {
            val actualUid = if (userId.isNullOrBlank() || userId == "guest" || userId == "authenticated_user") {
                com.example.IspApplication.getUserId(context)?.takeIf { it.isNotBlank() && it != "guest" && it != "authenticated_user" }
            } else {
                userId
            }
            val dbName = getDatabaseNameForUser(actualUid)

            return instances.computeIfAbsent(dbName) {
                Room.databaseBuilder(
                    context.applicationContext,
                    SmsDatabase::class.java,
                    dbName
                )
                    .fallbackToDestructiveMigration()
                    .build()
            }
        }

        fun closeDatabase(userId: String?) {
            val actualUid = if (userId.isNullOrBlank() || userId == "guest" || userId == "authenticated_user") {
                null
            } else {
                userId
            }
            val dbName = getDatabaseNameForUser(actualUid)
            synchronized(this) {
                instances.remove(dbName)?.let { db ->
                    try {
                        if (db.isOpen) db.close()
                    } catch (e: Throwable) {
                        android.util.Log.w("SmsDatabase", "Error closing sms db $dbName: ${e.message}")
                    }
                }
            }
        }

        fun closeAllDatabases() {
            synchronized(this) {
                instances.forEach { (name, db) ->
                    try {
                        if (db.isOpen) db.close()
                    } catch (e: Throwable) {
                        android.util.Log.w("SmsDatabase", "Error closing sms db $name: ${e.message}")
                    }
                }
                instances.clear()
            }
        }
    }
}
