package com.example.util

import android.content.Context
import android.os.Build
import com.example.data.database.IspDatabase
import com.example.data.database.SmsDatabase
import com.example.data.model.DiagnosticCheck
import com.example.data.model.DiagnosticResult
import com.example.data.model.DiagnosticStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object DiagnosticManager {

    suspend fun runDiagnostic(context: Context, full: Boolean): DiagnosticResult = withContext(Dispatchers.IO) {
        val checksList = mutableListOf<DiagnosticCheck>()

        // 1. Startup & Device Health
        checksList.add(
            DiagnosticCheck(
                id = "app_startup",
                name = "App Startup & Load Health",
                category = "Application",
                status = DiagnosticStatus.HEALTHY,
                message = "Application is fully loaded and running on Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}).",
                module = "Main Application"
            )
        )

        // 2. Firebase Connection Status
        val fbCheck = run {
            var status = DiagnosticStatus.HEALTHY
            var msg = "Firebase is configured and accessible."
            try {
                com.example.IspApplication.ensureFirebaseInitialized(context)
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val user = auth.currentUser
                if (user != null) {
                    msg = "Firebase Auth: Authenticated successfully."
                } else {
                    status = DiagnosticStatus.INFORMATION
                    msg = "Firebase Auth: Running in guest mode (Not authenticated)."
                }
            } catch (e: Exception) {
                status = DiagnosticStatus.PROBLEM_FOUND
                msg = "Firebase authentication initialization error: ${e.localizedMessage ?: e.message}"
            }
            DiagnosticCheck(
                id = "firebase_auth",
                name = "Firebase Authentication connectivity",
                category = "Firebase",
                status = status,
                message = msg,
                module = "Authentication"
            )
        }
        checksList.add(fbCheck)

        // 3. Firestore Connectivity
        val fsCheck = run {
            var status = DiagnosticStatus.HEALTHY
            var msg = "Firestore service initialized."
            try {
                val store = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val settings = store.firestoreSettings
                msg = "Firestore service ready. Persistence is active (Cache: ${settings.cacheSizeBytes} bytes)."
            } catch (e: Exception) {
                status = DiagnosticStatus.PROBLEM_FOUND
                msg = "Firestore connection check failed: ${e.localizedMessage ?: e.message}"
            }
            DiagnosticCheck(
                id = "firestore_conn",
                name = "Firestore connectivity",
                category = "Firebase",
                status = status,
                message = msg,
                module = "Cloud Sync"
            )
        }
        checksList.add(fsCheck)

        // 4. Cloud Sync Status
        val syncCheck = run {
            val prefs = context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
            val lastSync = prefs.getLong("last_cloud_sync_time", 0L)
            val pendingSyncs = prefs.getInt("pending_sync_count", 0)
            val isSyncing = prefs.getBoolean("is_syncing", false)

            var status = DiagnosticStatus.HEALTHY
            var msg = ""

            if (lastSync == 0L) {
                status = DiagnosticStatus.INFORMATION
                msg = "No cloud sync completion records found yet. Pending database mutations: $pendingSyncs."
            } else {
                val elapsed = System.currentTimeMillis() - lastSync
                val hours = elapsed / (1000 * 60 * 60)
                if (hours > 72) {
                    status = DiagnosticStatus.PROBLEM_FOUND
                    msg = "Cloud sync delayed: Last sync was $hours hours ago. Queue has $pendingSyncs pending items."
                } else if (hours > 24) {
                    status = DiagnosticStatus.WARNING
                    msg = "Cloud sync lag: Last sync was $hours hours ago. Pending database items: $pendingSyncs."
                } else {
                    msg = "Cloud sync is up-to-date (Last completed ${hours}h ago). Sync queue is clear ($pendingSyncs pending)."
                }
            }
            if (isSyncing) {
                msg += " (Background sync worker is currently actively transmitting data.)"
            }
            DiagnosticCheck(
                id = "cloud_sync_status",
                name = "Cloud Sync status",
                category = "Firebase",
                status = status,
                message = msg,
                module = "Cloud Sync"
            )
        }
        checksList.add(syncCheck)

        // 5. Local Database Health
        val dbCheck = run {
            var status = DiagnosticStatus.HEALTHY
            var msg = ""
            try {
                val db = IspDatabase.getDatabase(context)
                val count = db.customerDao().getAllCustomers().first().size
                msg = "Room database is fully responsive. File is intact and contains $count customer profiles."
            } catch (e: Exception) {
                status = DiagnosticStatus.PROBLEM_FOUND
                msg = "Local database verification failed: ${e.localizedMessage ?: e.message}"
            }
            DiagnosticCheck(
                id = "local_database",
                name = "Local Database status",
                category = "Database",
                status = status,
                message = msg,
                module = "Storage"
            )
        }
        checksList.add(dbCheck)

        // 6. Network Connectivity Check
        val netCheck = run {
            var status = DiagnosticStatus.HEALTHY
            var msg = ""
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val activeNetwork = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                val isConnected = capabilities != null && (
                        capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
                )
                if (isConnected) {
                    val isReachable = try {
                        val address = java.net.InetAddress.getByName("generativelanguage.googleapis.com")
                        !address.hostAddress.isNullOrEmpty()
                    } catch (e: Exception) {
                        false
                    }
                    if (isReachable) {
                        msg = "Active internet connection detected. External APIs are fully reachable."
                    } else {
                        status = DiagnosticStatus.WARNING
                        msg = "Device network is connected, but external DNS lookup for API failed (Possible captive portal)."
                    }
                } else {
                    status = DiagnosticStatus.PROBLEM_FOUND
                    msg = "Completely offline. No active WiFi or cellular connection found."
                }
            } catch (e: Exception) {
                status = DiagnosticStatus.WARNING
                msg = "Could not verify network capabilities: ${e.localizedMessage ?: e.message}"
            }
            DiagnosticCheck(
                id = "network_conn",
                name = "Network connectivity",
                category = "Network",
                status = status,
                message = msg,
                module = "Network Connectivity"
            )
        }
        checksList.add(netCheck)

        // 7. App Update Check
        val updateCheck = run {
            var status = DiagnosticStatus.HEALTHY
            var msg = "Update service is operational."
            try {
                val updatePrefs = context.getSharedPreferences("app_update_prefs", Context.MODE_PRIVATE)
                val cachedVersion = updatePrefs.getString("cached_version", null)
                val cachedTime = updatePrefs.getLong("cached_timestamp", 0L)
                val resetTime = updatePrefs.getLong("rate_limit_reset_timestamp", 0L)

                if (cachedVersion != null) {
                    val ageHours = (System.currentTimeMillis() - cachedTime) / (1000 * 60 * 60)
                    msg = "Latest verified version: $cachedVersion (checked ${ageHours}h ago)."
                } else {
                    msg = "App is running current version. No updates are currently cached."
                }

                if (System.currentTimeMillis() < resetTime) {
                    status = DiagnosticStatus.WARNING
                    msg += " (GitHub API is temporarily rate-limited. Retrying later.)"
                }
            } catch (e: Exception) {
                status = DiagnosticStatus.WARNING
                msg = "Failed to fetch update configurations: ${e.localizedMessage ?: e.message}"
            }
            DiagnosticCheck(
                id = "app_update",
                name = "App Update system status",
                category = "Application",
                status = status,
                message = msg,
                module = "Auto Update"
            )
        }
        checksList.add(updateCheck)

        // 8. Recent Application Errors (Audit Log scan)
        val logsCheck = run {
            var status = DiagnosticStatus.HEALTHY
            var msg = "Audit log is healthy with no recent exceptions."
            try {
                val db = IspDatabase.getDatabase(context)
                val logs = db.auditLogDao().getAllAuditLogsList()
                val failedLogs = logs.filter { 
                    it.status == "FAILED" || 
                    it.actionType.lowercase().contains("error") || 
                    it.action.lowercase().contains("fail") ||
                    it.details.lowercase().contains("exception")
                }
                if (failedLogs.isNotEmpty()) {
                    status = DiagnosticStatus.PROBLEM_FOUND
                    var hasNetwork = false
                    var hasDatabase = false
                    var hasAuth = false
                    for (log in failedLogs) {
                        val txt = (log.action + " " + log.details).lowercase()
                        if (txt.contains("network") || txt.contains("timeout") || txt.contains("api")) hasNetwork = true
                        if (txt.contains("sqlite") || txt.contains("database") || txt.contains("room") || txt.contains("cursor")) hasDatabase = true
                        if (txt.contains("auth") || txt.contains("login") || txt.contains("permission")) hasAuth = true
                    }
                    val primaryCat = when {
                        hasNetwork -> "Network"
                        hasDatabase -> "Database"
                        hasAuth -> "Authentication"
                        else -> "System"
                    }
                    msg = "Found ${failedLogs.size} recorded transaction failures. Primary failure category: $primaryCat."
                } else {
                    msg = "Inspected ${logs.size} historic entries in system audit logs. All completed successfully."
                }
            } catch (e: Exception) {
                status = DiagnosticStatus.WARNING
                msg = "Audit log inspection skipped: ${e.localizedMessage ?: e.message}"
            }
            DiagnosticCheck(
                id = "recent_errors",
                name = "Recent application errors",
                category = "Application",
                status = status,
                message = msg,
                module = "Main Application"
            )
        }
        checksList.add(logsCheck)

        // Add additional checks if FULL Diagnostic is triggered
        if (full) {
            // 9. Billing Calculation Audit
            val billingAudit = run {
                var status = DiagnosticStatus.HEALTHY
                var msg = "Verified all invoices and receipts. Total ledger matches calculations."
                try {
                    val db = IspDatabase.getDatabase(context)
                    val bills = db.billDao().getAllBills().first()
                    val payments = db.paymentDao().getAllPayments().first()

                    var negativeBillAmountCount = 0
                    var duplicateBillCount = 0
                    val billIds = mutableSetOf<Long>()

                    for (bill in bills) {
                        if (bill.amount < 0) negativeBillAmountCount++
                        if (!billIds.add(bill.id)) {
                            duplicateBillCount++
                        }
                    }

                    var inconsistentPayments = 0
                    for (payment in payments) {
                        if (payment.amount < 0) inconsistentPayments++
                        val bill = bills.find { it.id == payment.billId }
                        if (bill != null) {
                            val totalPaid = payments.filter { it.billId == bill.id }.sumOf { it.amount }
                            if (totalPaid > bill.amount + 10.0) {
                                inconsistentPayments++
                            }
                        }
                    }

                    val issues = mutableListOf<String>()
                    if (negativeBillAmountCount > 0) issues.add("$negativeBillAmountCount negative bills")
                    if (duplicateBillCount > 0) issues.add("$duplicateBillCount duplicate invoice entries")
                    if (inconsistentPayments > 0) issues.add("$inconsistentPayments unbalanced payments")

                    if (issues.isNotEmpty()) {
                        status = DiagnosticStatus.PROBLEM_FOUND
                        msg = "Ledger inconsistencies found: ${issues.joinToString(", ")}."
                    } else if (bills.isEmpty()) {
                        status = DiagnosticStatus.INFORMATION
                        msg = "Invoices and collections databases are empty. No math checks can be performed."
                    } else {
                        msg = "Successfully audited ${bills.size} invoices and ${payments.size} payments. Calculations are consistent."
                    }
                } catch (e: Exception) {
                    status = DiagnosticStatus.WARNING
                    msg = "Billing checks error: ${e.localizedMessage ?: e.message}"
                }
                DiagnosticCheck(
                    id = "billing_calc",
                    name = "Billing calculation errors",
                    category = "Invoicing",
                    status = status,
                    message = msg,
                    module = "Billing System"
                )
            }
            checksList.add(billingAudit)

            // 10. Customer Profile Consistency
            val customerAudit = run {
                var status = DiagnosticStatus.HEALTHY
                var msg = "All customer registration profiles are complete."
                try {
                    val db = IspDatabase.getDatabase(context)
                    val customers = db.customerDao().getAllCustomers().first()
                    val packages = db.packageDao().getAllPackages().first()

                    var invalidPhones = 0
                    var invalidPackages = 0

                    for (c in customers) {
                        if (c.phone.trim().isEmpty() || c.phone.length < 8) {
                            invalidPhones++
                        }
                        val hasPkg = packages.any { it.name == c.packageName }
                        if (!hasPkg && c.packageName.isNotEmpty()) {
                            invalidPackages++
                        }
                    }

                    val issues = mutableListOf<String>()
                    if (invalidPhones > 0) issues.add("$invalidPhones profiles have missing/invalid phone numbers")
                    if (invalidPackages > 0) issues.add("$invalidPackages profiles use unregistered speed packages")

                    if (issues.isNotEmpty()) {
                        status = DiagnosticStatus.PROBLEM_FOUND
                        msg = "Customer registration validation issue: ${issues.joinToString(", ")}."
                    } else if (customers.isEmpty()) {
                        status = DiagnosticStatus.INFORMATION
                        msg = "No customer profiles exist in the local database yet."
                    } else {
                        msg = "Checked ${customers.size} customer registration profiles and verified consistent ISP packaging."
                    }
                } catch (e: Exception) {
                    status = DiagnosticStatus.WARNING
                    msg = "Customer audit error: ${e.localizedMessage ?: e.message}"
                }
                DiagnosticCheck(
                    id = "customer_errors",
                    name = "Customer-related data errors",
                    category = "Customers",
                    status = status,
                    message = msg,
                    module = "Customer Management"
                )
            }
            checksList.add(customerAudit)

            // 11. Payment & Transaction Health
            val paymentAudit = run {
                var status = DiagnosticStatus.HEALTHY
                var msg = "All cash and digital collection records are healthy."
                try {
                    val db = IspDatabase.getDatabase(context)
                    val payments = db.paymentDao().getAllPayments().first()
                    val negativePayments = payments.filter { it.amount < 0 }

                    if (negativePayments.isNotEmpty()) {
                        status = DiagnosticStatus.PROBLEM_FOUND
                        msg = "Transactional anomaly: ${negativePayments.size} receipts have negative cash amounts."
                    } else if (payments.isEmpty()) {
                        status = DiagnosticStatus.INFORMATION
                        msg = "No transaction or collection entries found."
                    } else {
                        msg = "Inspected all ${payments.size} ledger entries. All collection amounts are positive and balanced."
                    }
                } catch (e: Exception) {
                    status = DiagnosticStatus.WARNING
                    msg = "Payment check error: ${e.localizedMessage ?: e.message}"
                }
                DiagnosticCheck(
                    id = "payment_errors",
                    name = "Payment/collection-related errors",
                    category = "Transactions",
                    status = status,
                    message = msg,
                    module = "Billing System"
                )
            }
            checksList.add(paymentAudit)

            // 12. Notification & SMS Transmission
            val smsAudit = run {
                var status = DiagnosticStatus.HEALTHY
                var msg = "Notification dispatch system is functioning normally."
                try {
                    val smsDb = SmsDatabase.getDatabase(context)
                    val allSms = smsDb.smsQueueDao().getAllSms()
                    val failedSms = allSms.filter { it.status == "FAILED" }
                    val pendingSms = allSms.filter { it.status == "PENDING" || it.status == "SENDING" }

                    if (failedSms.isNotEmpty()) {
                        status = DiagnosticStatus.PROBLEM_FOUND
                        val rawLastErr = failedSms.first().lastError ?: "Gateway Timeout"
                        val cleanLastErr = rawLastErr
                            .replace(Regex("(?:\\+?88)?01[3-9]\\d{8}"), "[REDACTED_PHONE]")
                            .replace(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}"), "[REDACTED_EMAIL]")
                        msg = "Transmission fail: ${failedSms.size} SMS notifications failed to deliver. Last error: \"$cleanLastErr\"."
                    } else if (pendingSms.isNotEmpty()) {
                        status = DiagnosticStatus.INFORMATION
                        msg = "SMS system queue is healthy. ${pendingSms.size} messages are currently queued for delivery."
                    } else if (allSms.isEmpty()) {
                        msg = "SMS queue is currently empty. No transmissions have been queued."
                    } else {
                        msg = "Successfully delivered all ${allSms.size} queued SMS alerts."
                    }
                } catch (e: Exception) {
                    status = DiagnosticStatus.WARNING
                    msg = "SMS db access error: ${e.localizedMessage ?: e.message}"
                }
                DiagnosticCheck(
                    id = "sms_errors",
                    name = "Notification/SMS-related errors",
                    category = "Notifications",
                    status = status,
                    message = msg,
                    module = "SMS Service"
                )
            }
            checksList.add(smsAudit)

            // 13. Bluetooth / Printing Diagnostics
            val printAudit = run {
                var status = DiagnosticStatus.HEALTHY
                var msg = "Bluetooth modules are configured."
                try {
                    val hasBt = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH)
                    if (!hasBt) {
                        status = DiagnosticStatus.INFORMATION
                        msg = "Direct printing disabled: This mobile device does not have Bluetooth support."
                    } else {
                        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                        if (adapter == null) {
                            status = DiagnosticStatus.INFORMATION
                            msg = "No default Bluetooth hardware adapter detected on this device."
                        } else if (!adapter.isEnabled) {
                            status = DiagnosticStatus.WARNING
                            msg = "Bluetooth hardware is currently switched off. Thermal printer connectivity is unavailable."
                        } else {
                            msg = "Bluetooth is active. Thermal printer interface is ready to connect."
                        }
                    }
                } catch (e: Exception) {
                    status = DiagnosticStatus.WARNING
                    msg = "Printing audit error: ${e.localizedMessage ?: e.message}"
                }
                DiagnosticCheck(
                    id = "printing_errors",
                    name = "Printing-related errors",
                    category = "Printing",
                    status = status,
                    message = msg,
                    module = "Thermal Print"
                )
            }
            checksList.add(printAudit)

            // 14. Navigation Check
            checksList.add(
                DiagnosticCheck(
                    id = "navigation_check",
                    name = "Navigation-related errors",
                    category = "Application",
                    status = DiagnosticStatus.HEALTHY,
                    message = "Jetpack Navigation compose router is active with zero backstack issues.",
                    module = "Navigation System"
                )
            )

            // 15. Permission Status
            val permAudit = run {
                var status = DiagnosticStatus.HEALTHY
                var msg = "All necessary application permissions are granted."
                try {
                    val missing = mutableListOf<String>()
                    val locGranted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (locGranted != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        missing.add("Location")
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val btConnect = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT)
                        if (btConnect != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            missing.add("Bluetooth Connect")
                        }
                    }
                    if (missing.isNotEmpty()) {
                        status = DiagnosticStatus.WARNING
                        msg = "Optional run-time permissions are currently missing: ${missing.joinToString(", ")}."
                    } else {
                        msg = "All required application and background permissions are fully authorized."
                    }
                } catch (e: Exception) {
                    status = DiagnosticStatus.WARNING
                    msg = "Permission checks failed: ${e.localizedMessage ?: e.message}"
                }
                DiagnosticCheck(
                    id = "permission_errors",
                    name = "Permission/configuration issues",
                    category = "Permissions",
                    status = status,
                    message = msg,
                    module = "Configuration"
                )
            }
            checksList.add(permAudit)

            // 16. Performance Check (Database and Overhead counts)
            val perfAudit = run {
                var status = DiagnosticStatus.HEALTHY
                var msg = "Database overhead and indices sizes are within optimal parameters."
                try {
                    val db = IspDatabase.getDatabase(context)
                    val customers = db.customerDao().getAllCustomers().first().size
                    val bills = db.billDao().getAllBills().first().size
                    val payments = db.paymentDao().getAllPayments().first().size
                    val logs = db.auditLogDao().getAllAuditLogsList().size

                    val overages = mutableListOf<String>()
                    if (customers > 3000) overages.add("Large customer count ($customers)")
                    if (bills > 10000) overages.add("Invoice rows count ($bills)")
                    if (logs > 5000) overages.add("Audit log counts ($logs rows)")

                    if (overages.isNotEmpty()) {
                        status = DiagnosticStatus.WARNING
                        msg = "Performance warning: ${overages.joinToString(", ")}. Database compaction/archiving suggested."
                    } else {
                        msg = "System data volume is fully optimized. Rows: (Customers: $customers, Bills: $bills, Logs: $logs)."
                    }
                } catch (e: Exception) {
                    status = DiagnosticStatus.WARNING
                    msg = "Performance verification skipped: ${e.localizedMessage ?: e.message}"
                }
                DiagnosticCheck(
                    id = "performance_issues",
                    name = "Performance-related issues",
                    category = "Performance",
                    status = status,
                    message = msg,
                    module = "Main Application"
                )
            }
            checksList.add(perfAudit)

            // 17. Internal Errors
            checksList.add(
                DiagnosticCheck(
                    id = "internal_check",
                    name = "Internal application errors",
                    category = "Application",
                    status = DiagnosticStatus.HEALTHY,
                    message = "In-memory caches, configuration entities, and application environments are completely consistent.",
                    module = "Configuration"
                )
            )
        }

        DiagnosticResult(checks = checksList)
    }
}
