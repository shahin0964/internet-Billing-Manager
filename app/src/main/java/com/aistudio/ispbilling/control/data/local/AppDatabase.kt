package com.aistudio.ispbilling.control.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aistudio.ispbilling.control.data.local.dao.BillDao
import com.aistudio.ispbilling.control.data.local.dao.CustomerDao
import com.aistudio.ispbilling.control.data.local.dao.PackageDao
import com.aistudio.ispbilling.control.data.local.entity.BillEntity
import com.aistudio.ispbilling.control.data.local.entity.CustomerEntity
import com.aistudio.ispbilling.control.data.local.entity.PackageEntity

@Database(
    entities = [
        CustomerEntity::class,
        PackageEntity::class,
        BillEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun customerDao(): CustomerDao

    abstract fun packageDao(): PackageDao

    abstract fun billDao(): BillDao

    companion object {

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "isp_billing_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
