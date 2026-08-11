package com.example.util

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.database.IspDatabase
import com.example.data.database.SmsDatabase
import com.example.data.model.BillEntity
import com.example.data.model.PaymentEntity
import com.example.data.model.SmsQueueEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object AutomaticSmsManager {
    private const val TAG = "AutomaticSmsManager"
    private const val PREFS_NAME = "automatic_sms_prefs"

    // Settings Keys
    private const val KEY_SMS_ENABLED = "sms_enabled"
    private const val KEY_SELECTED_SIM = "selected_sim" // 0: Default, 1: SIM 1, 2: SIM 2
    private const val KEY_DEFAULT_SENDING_TIME = "default_sending_time"
    private const val KEY_RETRY_FAILED = "retry_failed"
    private const val KEY_MAX_RETRY_COUNT = "max_retry_count"

    // Rules Keys
    private const val KEY_RULE_BILL_GENERATED = "rule_bill_generated"
    private const val KEY_RULE_DUE_REMINDER = "rule_due_reminder"
    private const val KEY_RULE_OVERDUE = "rule_overdue"
    private const val KEY_RULE_PAYMENT_CONFIRMATION = "rule_payment_confirmation"
    private const val KEY_RULE_GENERAL_NOTICE = "rule_general_notice"

    private const val KEY_RULE_WARNING_1 = "rule_warning_1"
    private const val KEY_RULE_WARNING_2 = "rule_warning_2"
    private const val KEY_RULE_WARNING_3 = "rule_warning_3"
    private const val KEY_DATE_WARNING_1 = "date_warning_1"
    private const val KEY_DATE_WARNING_2 = "date_warning_2"
    private const val KEY_DATE_WARNING_3 = "date_warning_3"

    // Due Reminder Offset Key
    private const val KEY_DUE_REMINDER_OFFSET = "due_reminder_offset" // e.g. -3, -1, 0, 1, 3

    // Template Keys
    private const val KEY_TEMPLATE_BILL_GENERATED = "template_bill_generated"
    private const val KEY_TEMPLATE_DUE_REMINDER = "template_due_reminder"
    private const val KEY_TEMPLATE_OVERDUE = "template_overdue"
    private const val KEY_TEMPLATE_PAYMENT_CONFIRMATION = "template_payment_confirmation"
    private const val KEY_TEMPLATE_GENERAL_NOTICE = "template_general_notice"

    private const val KEY_TEMPLATE_WARNING_1 = "template_warning_1"
    private const val KEY_TEMPLATE_WARNING_2 = "template_warning_2"
    private const val KEY_TEMPLATE_WARNING_3 = "template_warning_3"

    // Defaults (Bengali and English fallback default templates)
    private const val DEFAULT_TEMPLATE_BILL_GENERATED = "প্রিয় {customer_name},\nআপনার {bill_month} মাসের বিল তৈরি করা হয়েছে। বিল: ৳{bill_amount}। পরিশোধের শেষ সময়: {due_date}। ধন্যবাদ।"
    private const val DEFAULT_TEMPLATE_DUE_REMINDER = "প্রিয় {customer_name},\nআপনার {bill_month} মাসের বিল এখনো বকেয়া রয়েছে। বকেয়া বিল: ৳{due_amount}।"
    private const val DEFAULT_TEMPLATE_OVERDUE = "প্রিয় {customer_name},\nআপনার ইন্টারনেট বিল ৳{due_amount} বকেয়া রয়েছে। সংযোগ বিচ্ছিন্ন হওয়া এড়াতে দয়া করে এখনই বিল পরিশোধ করুন।"
    private const val DEFAULT_TEMPLATE_PAYMENT_CONFIRMATION = "প্রিয় {customer_name},\nআপনার {bill_month} মাসের বিল পরিশোধ সম্পন্ন হয়েছে। ধন্যবাদ।"
    private const val DEFAULT_TEMPLATE_GENERAL_NOTICE = "প্রিয় {customer_name},\nআমাদের সেবা সাময়িক বিঘ্নিত হতে পারে। সাময়িক অসুবিধার জন্য আমরা আন্তরিকভাবে দুঃখিত।"

    private const val DEFAULT_TEMPLATE_WARNING_1 = "প্রিয় {customer_name},\nআপনার {bill_month} মাসের বিল এখনো বকেয়া রয়েছে। বকেয়া বিল: ৳{due_amount}।"
    private const val DEFAULT_TEMPLATE_WARNING_2 = "প্রিয় {customer_name},\nআপনার {bill_month} মাসের বিল এখনো বকেয়া রয়েছে। সংযোগ সচল রাখতে দ্রুত ৳{due_amount} পরিশোধ করুন।"
    private const val DEFAULT_TEMPLATE_WARNING_3 = "জরুরী নোটিশ: প্রিয় {customer_name},\nআপনার {bill_month} মাসের বিল ৳{due_amount} পরিশোধ না করায় সংযোগ বিচ্ছিন্ন করা হতে পারে।"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Settings Getters & Setters
    fun isSmsEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_SMS_ENABLED, false)
    fun setSmsEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_SMS_ENABLED, enabled).apply()

    fun getSelectedSim(context: Context): Int = getPrefs(context).getInt("key_selected_sub_id", -1)
    fun setSelectedSim(context: Context, subId: Int) = getPrefs(context).edit().putInt("key_selected_sub_id", subId).apply()

    fun getDefaultSendingTime(context: Context): String = getPrefs(context).getString(KEY_DEFAULT_SENDING_TIME, "10:00") ?: "10:00"
    fun setDefaultSendingTime(context: Context, time: String) = getPrefs(context).edit().putString(KEY_DEFAULT_SENDING_TIME, time).apply()

    data class SimInfo(
        val subscriptionId: Int,
        val slotIndex: Int,
        val carrierName: String,
        val number: String
    )

    fun getAvailableSims(context: Context): List<SimInfo> {
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            ?: return emptyList()
        val list = mutableListOf<SimInfo>()
        try {
            val activeList = subscriptionManager.activeSubscriptionInfoList
            if (activeList != null) {
                for (info in activeList) {
                    list.add(
                        SimInfo(
                            subscriptionId = info.subscriptionId,
                            slotIndex = info.simSlotIndex,
                            carrierName = info.carrierName?.toString() ?: "Unknown",
                            number = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                subscriptionManager.getPhoneNumber(info.subscriptionId) ?: info.number ?: ""
                            } else {
                                info.number ?: ""
                            }
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot access SubscriptionManager due to security limits: ${e.message}")
        }
        return list
    }


    fun isRetryFailedEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_RETRY_FAILED, true)
    fun setRetryFailedEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_RETRY_FAILED, enabled).apply()

    fun getMaxRetryCount(context: Context): Int = getPrefs(context).getInt(KEY_MAX_RETRY_COUNT, 3)
    fun setMaxRetryCount(context: Context, count: Int) = getPrefs(context).edit().putInt(KEY_MAX_RETRY_COUNT, count).apply()

    // Rules Getters & Setters
    fun isRuleBillGeneratedEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_RULE_BILL_GENERATED, true)
    fun setRuleBillGeneratedEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_RULE_BILL_GENERATED, enabled).apply()

    fun isRuleDueReminderEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_RULE_DUE_REMINDER, true)
    fun setRuleDueReminderEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_RULE_DUE_REMINDER, enabled).apply()

    fun isRuleOverdueEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_RULE_OVERDUE, true)
    fun setRuleOverdueEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_RULE_OVERDUE, enabled).apply()

    fun isRulePaymentConfirmationEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_RULE_PAYMENT_CONFIRMATION, true)
    fun setRulePaymentConfirmationEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_RULE_PAYMENT_CONFIRMATION, enabled).apply()

    fun isRuleGeneralNoticeEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_RULE_GENERAL_NOTICE, true)
    fun setRuleGeneralNoticeEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_RULE_GENERAL_NOTICE, enabled).apply()

    fun isRuleWarning1Enabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_RULE_WARNING_1, false)
    fun setRuleWarning1Enabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_RULE_WARNING_1, enabled).apply()
    fun getDateWarning1(context: Context): Int = getPrefs(context).getInt(KEY_DATE_WARNING_1, 5)
    fun setDateWarning1(context: Context, date: Int) = getPrefs(context).edit().putInt(KEY_DATE_WARNING_1, date).apply()

    fun isRuleWarning2Enabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_RULE_WARNING_2, false)
    fun setRuleWarning2Enabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_RULE_WARNING_2, enabled).apply()
    fun getDateWarning2(context: Context): Int = getPrefs(context).getInt(KEY_DATE_WARNING_2, 10)
    fun setDateWarning2(context: Context, date: Int) = getPrefs(context).edit().putInt(KEY_DATE_WARNING_2, date).apply()

    fun isRuleWarning3Enabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_RULE_WARNING_3, false)
    fun setRuleWarning3Enabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_RULE_WARNING_3, enabled).apply()
    fun getDateWarning3(context: Context): Int = getPrefs(context).getInt(KEY_DATE_WARNING_3, 15)
    fun setDateWarning3(context: Context, date: Int) = getPrefs(context).edit().putInt(KEY_DATE_WARNING_3, date).apply()

    // Offset Getters & Setters
    fun getDueReminderOffset(context: Context): Int = getPrefs(context).getInt(KEY_DUE_REMINDER_OFFSET, 0)
    fun setDueReminderOffset(context: Context, offset: Int) = getPrefs(context).edit().putInt(KEY_DUE_REMINDER_OFFSET, offset).apply()

    // Templates Getters & Setters
    fun getTemplateBillGenerated(context: Context): String = getPrefs(context).getString(KEY_TEMPLATE_BILL_GENERATED, DEFAULT_TEMPLATE_BILL_GENERATED) ?: DEFAULT_TEMPLATE_BILL_GENERATED
    fun setTemplateBillGenerated(context: Context, t: String) = getPrefs(context).edit().putString(KEY_TEMPLATE_BILL_GENERATED, t).apply()

    fun getTemplateDueReminder(context: Context): String = getPrefs(context).getString(KEY_TEMPLATE_DUE_REMINDER, DEFAULT_TEMPLATE_DUE_REMINDER) ?: DEFAULT_TEMPLATE_DUE_REMINDER
    fun setTemplateDueReminder(context: Context, t: String) = getPrefs(context).edit().putString(KEY_TEMPLATE_DUE_REMINDER, t).apply()

    fun getTemplateOverdue(context: Context): String = getPrefs(context).getString(KEY_TEMPLATE_OVERDUE, DEFAULT_TEMPLATE_OVERDUE) ?: DEFAULT_TEMPLATE_OVERDUE
    fun setTemplateOverdue(context: Context, t: String) = getPrefs(context).edit().putString(KEY_TEMPLATE_OVERDUE, t).apply()

    fun getTemplatePaymentConfirmation(context: Context): String = getPrefs(context).getString(KEY_TEMPLATE_PAYMENT_CONFIRMATION, DEFAULT_TEMPLATE_PAYMENT_CONFIRMATION) ?: DEFAULT_TEMPLATE_PAYMENT_CONFIRMATION
    fun setTemplatePaymentConfirmation(context: Context, t: String) = getPrefs(context).edit().putString(KEY_TEMPLATE_PAYMENT_CONFIRMATION, t).apply()

    fun getTemplateGeneralNotice(context: Context): String = getPrefs(context).getString(KEY_TEMPLATE_GENERAL_NOTICE, DEFAULT_TEMPLATE_GENERAL_NOTICE) ?: DEFAULT_TEMPLATE_GENERAL_NOTICE
    fun setTemplateGeneralNotice(context: Context, t: String) = getPrefs(context).edit().putString(KEY_TEMPLATE_GENERAL_NOTICE, t).apply()

    fun getTemplateWarning1(context: Context): String = getPrefs(context).getString(KEY_TEMPLATE_WARNING_1, DEFAULT_TEMPLATE_WARNING_1) ?: DEFAULT_TEMPLATE_WARNING_1
    fun setTemplateWarning1(context: Context, t: String) = getPrefs(context).edit().putString(KEY_TEMPLATE_WARNING_1, t).apply()

    fun getTemplateWarning2(context: Context): String = getPrefs(context).getString(KEY_TEMPLATE_WARNING_2, DEFAULT_TEMPLATE_WARNING_2) ?: DEFAULT_TEMPLATE_WARNING_2
    fun setTemplateWarning2(context: Context, t: String) = getPrefs(context).edit().putString(KEY_TEMPLATE_WARNING_2, t).apply()

    fun getTemplateWarning3(context: Context): String = getPrefs(context).getString(KEY_TEMPLATE_WARNING_3, DEFAULT_TEMPLATE_WARNING_3) ?: DEFAULT_TEMPLATE_WARNING_3
    fun setTemplateWarning3(context: Context, t: String) = getPrefs(context).edit().putString(KEY_TEMPLATE_WARNING_3, t).apply()

    // Permission check
    fun isSmsPermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Trigger background queue processor worker.
     */
    fun triggerSmsWorker(context: Context) {
        try {
            val oneTimeWork = OneTimeWorkRequestBuilder<SmsQueueWorker>()
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "sms_queue_one_time",
                ExistingWorkPolicy.REPLACE,
                oneTimeWork
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue SmsQueueWorker: ${e.message}")
        }
    }

    /**
     * Schedules periodic SMS queue processor (every 1 hour).
     */
    fun schedulePeriodicSmsWorker(context: Context) {
        try {
            val periodicWork = PeriodicWorkRequestBuilder<SmsQueueWorker>(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "sms_queue_periodic",
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWork
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule periodic SmsQueueWorker: ${e.message}")
        }
    }

    /**
     * Dynamic Template replacement engine.
     */
    fun processTemplate(
        template: String,
        customerName: String = "",
        monthlyFee: String = "",
        dueAmount: String = "",
        dueDate: String = "",
        paymentAmount: String = "",
        paymentDate: String = "",
        packageSpeed: String = "",
        ispName: String = "",
        billMonth: String = "",
        customerId: String = ""
    ): String {
        return template
            // New user requested placeholders
            .replace("{customer_name}", customerName)
            .replace("{billing_month}", billMonth)
            .replace("{bill_month}", billMonth)
            .replace("{bill_amount}", monthlyFee)
            .replace("{monthly_bill}", monthlyFee)
            .replace("{due_amount}", dueAmount)
            .replace("{due_date}", dueDate)
            .replace("{payment_date}", paymentDate)
            .replace("{customer_id}", customerId)
            .replace("{company_name}", ispName)
            // Legacy placeholders
            .replace("[Customer Name]", customerName)
            .replace("[Customer]", customerName)
            .replace("[Bill Amount]", monthlyFee)
            .replace("[Monthly Fee]", monthlyFee)
            .replace("[Due Amount]", dueAmount)
            .replace("[Due Date]", dueDate)
            .replace("[Payment Amount]", paymentAmount)
            .replace("[Payment Date]", paymentDate)
            .replace("[Package/Speed]", packageSpeed)
            .replace("[ISP Name]", ispName)
    }

    /**
     * Migrate old pending SMS records to use current phone numbers
     */
    suspend fun migratePendingSms(context: Context) = withContext(Dispatchers.IO) {
        try {
            val db = SmsDatabase.getDatabase(context)
            val dao = db.smsQueueDao()
            val ispDb = IspDatabase.getDatabase(context)
            val customerDao = ispDb.customerDao()

            val pendingList = dao.getSmsByStatus("PENDING") + dao.getSmsByStatus("FAILED")
            for (sms in pendingList) {
                var newNumber = sms.mobileNumber
                var customerId = sms.customerReferenceId.toLongOrNull()

                // Try to recover customer ID by matching name and phone if ID is missing or invalid
                if (customerId == null) {
                    val allCustomers = customerDao.getAllCustomers().first()
                    val matchedCustomer = allCustomers.firstOrNull { it.name == sms.customerName && it.phone.trim().replace(" ", "") == sms.mobileNumber }
                    if (matchedCustomer != null) {
                        customerId = matchedCustomer.id
                    }
                }

                if (customerId != null) {
                    val customer = customerDao.getCustomerById(customerId).first()
                    if (customer != null && customer.phone.isNotBlank()) {
                        val cleanNumber = customer.phone.trim().replace(" ", "").replace("-", "")
                        if (cleanNumber != sms.mobileNumber || sms.customerReferenceId != customerId.toString()) {
                            Log.d(TAG, "Migrating SMS ID ${sms.id} for customer $customerId to new number $cleanNumber")
                            dao.updateSms(
                                sms.copy(
                                    mobileNumber = cleanNumber,
                                    customerReferenceId = customerId.toString()
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate pending SMS: ${e.message}")
        }
    }

    /**
     * Safe queueing with duplicate prevention using idempotency key.
     */
    private suspend fun enqueueSms(
        context: Context,
        customerId: Long,
        customerName: String,
        mobileNumber: String,
        message: String,
        smsType: String,
        idempotencyKey: String
    ) {
        val cleanNumber = mobileNumber.trim().replace(" ", "").replace("-", "")
        if (cleanNumber.isBlank()) {
            Log.w(TAG, "Skipping SMS queuing for customer $customerName: Mobile number is empty")
            return
        }

        val db = SmsDatabase.getDatabase(context)
        val dao = db.smsQueueDao()

        // 1. Prevent duplicate checking idempotencyKey
        val count = dao.countByIdempotencyKey(idempotencyKey)
        if (count > 0) {
            Log.d(TAG, "Skipping duplicate SMS queue: Idempotency key '$idempotencyKey' already exists")
            return
        }

        // 2. Insert to queue
        val entity = SmsQueueEntity(
            customerReferenceId = customerId.toString(),
            customerName = customerName,
            mobileNumber = cleanNumber,
            message = message,
            smsType = smsType,
            createdTime = System.currentTimeMillis(),
            scheduledTime = System.currentTimeMillis(),
            status = "PENDING",
            idempotencyKey = idempotencyKey
        )
        dao.insertSms(entity)
        Log.d(TAG, "Queued automatic SMS for $customerName ($smsType, idempotencyKey: $idempotencyKey)")

        // 3. Fire immediate sending worker
        triggerSmsWorker(context)
    }

    /**
     * Hook to evaluate daily warnings for unpaid bills
     */
    suspend fun evaluateDailyWarnings(context: Context) {
        if (!isSmsEnabled(context)) return

        val ispDb = IspDatabase.getDatabase(context)
        val billDao = ispDb.billDao()
        val customerDao = ispDb.customerDao()
        val settingsDao = ispDb.settingsDao()
        
        val settings = settingsDao.getSettings().first()
        val ispName = settings?.ispName ?: "ISP"

        val sendWarning1 = isRuleWarning1Enabled(context)
        val dateWarning1 = getDateWarning1(context)
        
        val sendWarning2 = isRuleWarning2Enabled(context)
        val dateWarning2 = getDateWarning2(context)
        
        val sendWarning3 = isRuleWarning3Enabled(context)
        val dateWarning3 = getDateWarning3(context)

        // Only evaluate if at least one is enabled
        if (!sendWarning1 && !sendWarning2 && !sendWarning3) return

        // Get unpaid bills
        val unpaidBills = billDao.getAllBills().first().filter { it.dueAmount > 0.0 }
        
        val calendar = java.util.Calendar.getInstance()
        val todayDate = calendar.get(java.util.Calendar.DAY_OF_MONTH)

        for (bill in unpaidBills) {
            val customer = customerDao.getCustomerById(bill.customerId).first() ?: continue
            
            // Check Warning 1
            if (sendWarning1 && todayDate == dateWarning1) {
                val template = getTemplateWarning1(context)
                val msg = processTemplate(
                    template = template,
                    customerName = customer.name,
                    monthlyFee = bill.amount.toString(),
                    dueAmount = bill.dueAmount.toString(),
                    dueDate = bill.dueDate,
                    packageSpeed = customer.packageName,
                    ispName = ispName,
                    billMonth = bill.billingMonth,
                    customerId = customer.id.toString()
                )
                val idKey = "bill_${bill.id}_warning_1"
                enqueueSms(context, customer.id, customer.name, customer.phone, msg, "warning_1", idKey)
            }
            
            // Check Warning 2
            if (sendWarning2 && todayDate == dateWarning2) {
                val template = getTemplateWarning2(context)
                val msg = processTemplate(
                    template = template,
                    customerName = customer.name,
                    monthlyFee = bill.amount.toString(),
                    dueAmount = bill.dueAmount.toString(),
                    dueDate = bill.dueDate,
                    packageSpeed = customer.packageName,
                    ispName = ispName,
                    billMonth = bill.billingMonth,
                    customerId = customer.id.toString()
                )
                val idKey = "bill_${bill.id}_warning_2"
                enqueueSms(context, customer.id, customer.name, customer.phone, msg, "warning_2", idKey)
            }
            
            // Check Warning 3
            if (sendWarning3 && todayDate == dateWarning3) {
                val template = getTemplateWarning3(context)
                val msg = processTemplate(
                    template = template,
                    customerName = customer.name,
                    monthlyFee = bill.amount.toString(),
                    dueAmount = bill.dueAmount.toString(),
                    dueDate = bill.dueDate,
                    packageSpeed = customer.packageName,
                    ispName = ispName,
                    billMonth = bill.billingMonth,
                    customerId = customer.id.toString()
                )
                val idKey = "bill_${bill.id}_warning_3"
                enqueueSms(context, customer.id, customer.name, customer.phone, msg, "warning_3", idKey)
            }
        }
    }
    /**
     * Hooks for Bill Generation Event
     */
    suspend fun onBillsGenerated(context: Context, bills: List<BillEntity>) {
        if (!isSmsEnabled(context)) return

        val ispDb = IspDatabase.getDatabase(context)
        val customerDao = ispDb.customerDao()
        val settingsDao = ispDb.settingsDao()
        val settings = settingsDao.getSettings().first()
        val ispName = settings?.ispName ?: "ISP"

        val sendBillGen = isRuleBillGeneratedEnabled(context)
        val sendDueRem = isRuleDueReminderEnabled(context)

        for (bill in bills) {
            val customer = customerDao.getCustomerById(bill.customerId).first() ?: continue

            // 1. Queue Bill Generated SMS
            if (sendBillGen) {
                val template = getTemplateBillGenerated(context)
                val msg = processTemplate(
                    template = template,
                    customerName = customer.name,
                    monthlyFee = bill.amount.toString(),
                    dueDate = bill.dueDate,
                    packageSpeed = customer.packageName,
                    ispName = ispName
                )
                val idKey = "bill_${bill.id}_generated"
                enqueueSms(context, customer.id, customer.name, customer.phone, msg, "bill_generated", idKey)
            }

            // 2. Queue Due Reminder SMS (if offset is configured to be on/before due date)
            if (sendDueRem) {
                val offset = getDueReminderOffset(context)
                if (offset <= 0) { // e.g. -3, -1, 0
                    val template = getTemplateDueReminder(context)
                    val msg = processTemplate(
                        template = template,
                        customerName = customer.name,
                        dueAmount = bill.dueAmount.toString(),
                        dueDate = bill.dueDate,
                        ispName = ispName
                    )
                    val idKey = "bill_${bill.id}_due_$offset"
                    enqueueSms(context, customer.id, customer.name, customer.phone, msg, "due_reminder", idKey)
                }
            }
        }
    }

    /**
     * Hooks for Payment Confirmation Event
     */
    suspend fun onPaymentRecorded(context: Context, payment: PaymentEntity) {
        if (!isSmsEnabled(context)) return
        if (!isRulePaymentConfirmationEnabled(context)) return

        val ispDb = IspDatabase.getDatabase(context)
        val customerDao = ispDb.customerDao()
        val settingsDao = ispDb.settingsDao()
        val settings = settingsDao.getSettings().first()
        val ispName = settings?.ispName ?: "ISP"

        val customer = customerDao.getCustomerById(payment.customerId).first() ?: return

        val template = getTemplatePaymentConfirmation(context)
        val msg = processTemplate(
            template = template,
            customerName = customer.name,
            paymentAmount = payment.amount.toString(),
            paymentDate = payment.paymentDate,
            ispName = ispName
        )
        val idKey = "payment_${payment.id}_confirmed"
        enqueueSms(context, customer.id, customer.name, customer.phone, msg, "payment_confirmation", idKey)
    }

    /**
     * Manual Send SMS (doesn't trigger rules, queues directly)
     */
    suspend fun queueManualSms(
        context: Context,
        customerId: Long,
        customerName: String,
        mobileNumber: String,
        message: String
    ) {
        val ispDb = com.example.data.database.IspDatabase.getDatabase(context)
        val customer = ispDb.customerDao().getCustomerById(customerId).firstOrNull()
        val bills = ispDb.billDao().getBillsForCustomer(customerId).firstOrNull() ?: emptyList()
        val settings = ispDb.settingsDao().getSettings().firstOrNull()
        
        val ispName = settings?.ispName ?: "ISP Net"
        val totalDue = bills.sumOf { it.dueAmount }
        val packageName = customer?.packageName ?: ""
        val monthlyFee = customer?.monthlyFee?.toString() ?: "0"
        
        val currentBill = bills.firstOrNull()
        val billMonth = currentBill?.billingMonth ?: java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale("bn", "BD")).format(java.util.Date())
        val dueDate = currentBill?.dueDate ?: ""
        
        val processedMessage = processTemplate(
            template = message,
            customerName = customerName,
            monthlyFee = monthlyFee,
            dueAmount = totalDue.toString(),
            dueDate = dueDate,
            packageSpeed = packageName,
            ispName = ispName,
            billMonth = billMonth,
            customerId = customerId.toString()
        )

        val idKey = "manual_${System.currentTimeMillis()}_${customerId}"
        enqueueSms(context, customerId, customerName, mobileNumber, processedMessage, "general_notice", idKey)
    }

    /**
     * Synchronous single SMS sender with transient receiver
     */
    suspend fun sendSingleSms(
        context: Context,
        mobileNumber: String,
        message: String,
        smsId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isSmsPermissionGranted(context)) {
            return@withContext Result.failure(Exception("Permission denied"))
        }

        val selectedSubId = getSelectedSim(context) // -1: Default, >0: specific subId
        val smsManager = try {
            getSmsManagerForSubId(context, selectedSubId)
        } catch (e: Exception) {
            Log.e(TAG, "Error selecting SIM subscription: ${e.message}")
            return@withContext Result.failure(e)
        }

        val sentAction = "SMS_SENT_${System.currentTimeMillis()}_${smsId}"
        val sentIntent = PendingIntent.getBroadcast(
            context,
            smsId.toInt(),
            Intent(sentAction),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val deferred = CompletableDeferred<Int>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                deferred.complete(resultCode)
                try {
                    context.unregisterReceiver(this)
                } catch (e: Exception) {
                    Log.e(TAG, "Unregister error: ${e.message}")
                }
            }
        }

        // Register receiver with appropriate safety flags
        withContext(Dispatchers.Main) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, IntentFilter(sentAction), Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, IntentFilter(sentAction))
            }
        }

        try {
            // Send the message text
            val msgList = smsManager.divideMessage(message)
            if (msgList.size > 1) {
                val sentIntents = ArrayList<PendingIntent>()
                sentIntents.add(sentIntent)
                for (k in 1 until msgList.size) {
                    sentIntents.add(
                        PendingIntent.getBroadcast(
                            context,
                            (smsId + k).toInt(),
                            Intent(sentAction),
                            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                }
                smsManager.sendMultipartTextMessage(mobileNumber, null, msgList, sentIntents, null)
            } else {
                smsManager.sendTextMessage(mobileNumber, null, message, sentIntent, null)
            }

            // Wait with a 15-second timeout
            val resultCode = withTimeoutOrNull(15000L) {
                deferred.await()
            } ?: SmsManager.RESULT_ERROR_GENERIC_FAILURE

            if (resultCode == Activity.RESULT_OK) {
                Result.success(Unit)
            } else {
                val errText = when (resultCode) {
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic Failure"
                    SmsManager.RESULT_ERROR_NO_SERVICE -> "No Mobile Service"
                    SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU"
                    SmsManager.RESULT_ERROR_RADIO_OFF -> "Airplane Mode / Radio Off"
                    else -> "SMS Code: $resultCode"
                }
                Result.failure(Exception(errText))
            }
        } catch (e: Exception) {
            // Unregister to prevent leaks
            try {
                context.unregisterReceiver(receiver)
            } catch (ex: Exception) {}
            Result.failure(e)
        }
    }

    /**
     * Resolves appropriate SmsManager for Dual SIM slots
     */
    private fun getSmsManagerForSubId(context: Context, subId: Int): SmsManager {
        if (subId == -1) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
        }

        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        if (subscriptionManager != null) {
            try {
                val activeList = subscriptionManager.activeSubscriptionInfoList
                if (activeList != null) {
                    val matchedSub = activeList.firstOrNull { it.subscriptionId == subId }
                    if (matchedSub == null) {
                        throw IllegalStateException("Selected SIM (SubID: $subId) is not available.")
                    }
                    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.getSystemService(SmsManager::class.java).createForSubscriptionId(matchedSub.subscriptionId)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getSmsManagerForSubscriptionId(matchedSub.subscriptionId)
                    }
                } else {
                    throw IllegalStateException("No active SIM cards found.")
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot access SubscriptionManager due to security limits: ${e.message}")
                throw e
            }
        }

        throw IllegalStateException("SubscriptionManager is not available on this device.")
    }
}
