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
        @Volatile
        private var INSTANCE: SmsDatabase? = null

        fun getDatabase(context: Context): SmsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmsDatabase::class.java,
                    "isp_sms_features.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
