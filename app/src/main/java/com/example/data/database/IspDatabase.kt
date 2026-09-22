package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import com.example.data.dao.AuditLogDao
import com.example.data.dao.BillDao
import com.example.data.dao.BusinessSettingsDao
import com.example.data.dao.CustomerDao
import com.example.data.dao.ExpenseDao
import com.example.data.dao.IspPackageDao
import com.example.data.dao.NetworkDiagramDao
import com.example.data.dao.PaymentDao
import com.example.data.dao.PendingDeletionDao
import com.example.data.dao.SpecificAdvanceDao
import com.example.data.dao.BandwidthBillDao
import com.example.data.model.AuditLogEntity
import com.example.data.model.BillEntity
import com.example.data.model.BusinessSettingsEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.ExpenseCategoryEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.IspPackageEntity
import com.example.data.model.NetworkConnectionEntity
import com.example.data.model.NetworkDiagramEntity
import com.example.data.model.NetworkNodeEntity
import com.example.data.model.PaymentEntity
import com.example.data.model.PendingDeletionEntity
import com.example.data.model.SpecificAdvanceEntity
import com.example.data.model.BandwidthBillEntity

@Database(
    entities = [
        CustomerEntity::class,
        IspPackageEntity::class,
        BillEntity::class,
        PaymentEntity::class,
        BusinessSettingsEntity::class,
        ExpenseEntity::class,
        ExpenseCategoryEntity::class,
        NetworkDiagramEntity::class,
        NetworkNodeEntity::class,
        NetworkConnectionEntity::class,
        AuditLogEntity::class,
        PendingDeletionEntity::class,
        SpecificAdvanceEntity::class,
        BandwidthBillEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class IspDatabase : RoomDatabase() {

    abstract fun customerDao(): CustomerDao
    abstract fun packageDao(): IspPackageDao
    abstract fun billDao(): BillDao
    abstract fun paymentDao(): PaymentDao
    abstract fun settingsDao(): BusinessSettingsDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun networkDiagramDao(): NetworkDiagramDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun pendingDeletionDao(): PendingDeletionDao
    abstract fun specificAdvanceDao(): SpecificAdvanceDao
    abstract fun bandwidthBillDao(): BandwidthBillDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE business_settings ADD COLUMN logoUri TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `expenses` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `category` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `paymentMethod` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `receiptPath` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `expense_categories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `network_diagrams` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `isDefault` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `network_nodes` (
                        `id` TEXT PRIMARY KEY NOT NULL,
                        `diagramId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `ipAddress` TEXT NOT NULL,
                        `location` TEXT NOT NULL,
                        `areaZone` TEXT NOT NULL,
                        `portInfo` TEXT NOT NULL,
                        `customerRef` TEXT NOT NULL,
                        `customerId` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `positionX` REAL NOT NULL,
                        `positionY` REAL NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `network_connections` (
                        `id` TEXT PRIMARY KEY NOT NULL,
                        `diagramId` INTEGER NOT NULL,
                        `fromNodeId` TEXT NOT NULL,
                        `toNodeId` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `notes` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `audit_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `action` TEXT NOT NULL,
                        `actionType` TEXT NOT NULL,
                        `details` TEXT NOT NULL,
                        `userEmail` TEXT NOT NULL,
                        `userRole` TEXT NOT NULL,
                        `targetEntity` TEXT NOT NULL,
                        `targetId` TEXT NOT NULL,
                        `previousState` TEXT NOT NULL,
                        `newState` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE business_settings ADD COLUMN email TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN area TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN zone TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN latitude REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE customers ADD COLUMN longitude REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE customers ADD COLUMN oltName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN ponPort TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN onuSerial TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN routerName TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add updatedAt and syncStatus (0 = SYNCED) non-destructively to existing tables
                db.execSQL("ALTER TABLE customers ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE customers ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE packages ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE packages ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE bills ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bills ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE payments ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE payments ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE business_settings ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE business_settings ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE expenses ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE expense_categories ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE expense_categories ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE network_diagrams ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE network_nodes ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE network_nodes ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE network_connections ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE network_connections ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE audit_logs ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 0")

                // Create pending_deletions table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_deletions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `collectionName` TEXT NOT NULL,
                        `documentId` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN advanceBalance REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `specific_advances` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `customerId` INTEGER NOT NULL,
                        `billingMonth` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `isConsumed` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bandwidth_bills` (
                        `billingMonth` TEXT NOT NULL PRIMARY KEY,
                        `amount` REAL NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE specific_advances ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bandwidth_bills ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bandwidth_bills ADD COLUMN syncStatus INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val instances = java.util.concurrent.ConcurrentHashMap<String, IspDatabase>()

        fun getDatabaseNameForUser(userId: String?): String {
            if (userId.isNullOrBlank() || userId == "guest" || userId == "authenticated_user") {
                return "isp_control_center_guest.db"
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
            return "isp_user_${safeId}_${hash}.db"
        }

        private fun migrateLegacyDatabaseIfOwned(context: Context, targetDbName: String, targetUserId: String?) {
            try {
                val targetDbFile = context.getDatabasePath(targetDbName)
                if (targetDbFile.exists()) return

                val legacyDbFile = context.getDatabasePath("isp_control_center.db")
                if (!legacyDbFile.exists()) return

                val lastAuthUser = com.example.IspApplication.getLastAuthenticatedUserId(context)
                val isTargetAuthenticated = !targetUserId.isNullOrBlank() && targetUserId != "guest" && targetUserId != "authenticated_user"
                val shouldAdopt = isTargetAuthenticated && lastAuthUser != null && lastAuthUser == targetUserId

                if (shouldAdopt) {
                    targetDbFile.parentFile?.mkdirs()
                    legacyDbFile.copyTo(targetDbFile, overwrite = false)
                    val legacyWal = java.io.File(legacyDbFile.path + "-wal")
                    val targetWal = java.io.File(targetDbFile.path + "-wal")
                    if (legacyWal.exists()) legacyWal.copyTo(targetWal, overwrite = false)
                    val legacyShm = java.io.File(legacyDbFile.path + "-shm")
                    val targetShm = java.io.File(targetDbFile.path + "-shm")
                    if (legacyShm.exists()) legacyShm.copyTo(targetShm, overwrite = false)
                    android.util.Log.i("IspDatabase", "Adopted legacy database isp_control_center.db into $targetDbName (owner: $targetUserId)")
                }
            } catch (e: Throwable) {
                android.util.Log.w("IspDatabase", "Legacy database adoption note: ${e.message}")
            }
        }

        fun getDatabase(context: Context, userId: String? = null): IspDatabase {
            val actualUid = if (userId.isNullOrBlank() || userId == "guest" || userId == "authenticated_user") {
                com.example.IspApplication.getUserId(context)?.takeIf { it.isNotBlank() && it != "guest" && it != "authenticated_user" }
            } else {
                userId
            }
            val dbName = getDatabaseNameForUser(actualUid)

            return instances.computeIfAbsent(dbName) {
                migrateLegacyDatabaseIfOwned(context.applicationContext, dbName, actualUid)
                Room.databaseBuilder(
                    context.applicationContext,
                    IspDatabase::class.java,
                    dbName
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                    .fallbackToDestructiveMigrationOnDowngrade()
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
                        if (db.isOpen) {
                            db.close()
                        }
                    } catch (e: Throwable) {
                        android.util.Log.w("IspDatabase", "Error closing database $dbName: ${e.message}")
                    }
                }
            }
        }

        fun closeAllDatabases() {
            synchronized(this) {
                instances.forEach { (name, db) ->
                    try {
                        if (db.isOpen) {
                            db.close()
                        }
                    } catch (e: Throwable) {
                        android.util.Log.w("IspDatabase", "Error closing database $name: ${e.message}")
                    }
                }
                instances.clear()
            }
        }
    }
}
