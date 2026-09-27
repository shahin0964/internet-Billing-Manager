package com.example.util

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.database.IspDatabase
import com.example.data.database.SmsDatabase
import com.example.data.model.SmsQueueEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/**
 * Safe, sequential, single-flight SMS Dispatcher Engine.
 * Handles SIM routing, Unicode/Bengali multipart SMS, rate-limiting, and error recovery.
 */
object SafeSmsSenderEngine {
    private const val TAG = "SafeSmsSenderEngine"
    private const val INTER_SMS_DELAY_MS = 1200L
    private const val SMS_TIMEOUT_MS = 20000L

    private val queueMutex = Mutex()

    /**
     * Executes single-flight queue processing.
     * Prevents concurrent SMS dispatch loops inside the process.
     */
    suspend fun processQueue(context: Context): Int = withContext(Dispatchers.IO) {
        if (!AutomaticSmsManager.isSmsEnabled(context)) {
            Log.d(TAG, "Queue processing skipped: Automatic SMS feature is disabled")
            return@withContext 0
        }

        if (!AutomaticSmsManager.isSmsPermissionGranted(context)) {
            Log.w(TAG, "Queue processing skipped: SEND_SMS permission is not granted")
            return@withContext 0
        }

        // Try lock or wait cleanly
        queueMutex.withLock {
            try {
                // Ensure data consistency and recover stale SENDING state
                AutomaticSmsManager.migratePendingSms(context)
                AutomaticSmsManager.evaluateDailyWarnings(context)

                val db = SmsDatabase.getDatabase(context)
                val dao = db.smsQueueDao()
                val ispDb = IspDatabase.getDatabase(context)
                val customerDao = ispDb.customerDao()

                // Recover any messages left in SENDING status from previous interruptions
                try {
                    val recoveredCount = dao.recoverStaleSendingSms()
                    if (recoveredCount > 0) {
                        Log.d(TAG, "Recovered $recoveredCount interrupted SENDING messages back to PENDING")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error recovering stale SENDING messages: ${e.message}")
                }

                val pendingList = dao.getSmsByStatus("PENDING")
                if (pendingList.isEmpty()) {
                    Log.d(TAG, "SMS Queue is empty. No pending SMS to process.")
                    return@withLock 0
                }

                Log.d(TAG, "Starting sequential dispatch for ${pendingList.size} pending SMS...")

                val retryEnabled = AutomaticSmsManager.isRetryFailedEnabled(context)
                val maxRetryCount = AutomaticSmsManager.getMaxRetryCount(context)
                var processedCount = 0

                for (sms in pendingList) {
                    // Double check entity status
                    val currentEntity = dao.getSmsById(sms.id) ?: continue
                    if (currentEntity.status != "PENDING") {
                        continue
                    }

                    // Mark as SENDING
                    dao.updateSms(currentEntity.copy(status = "SENDING"))

                    val customerId = sms.customerReferenceId.toLongOrNull()
                    val customer = if (customerId != null && customerId > 0) {
                        customerDao.getCustomerById(customerId).firstOrNull()
                    } else null

                    val rawNumber = customer?.phone?.ifBlank { sms.mobileNumber } ?: sms.mobileNumber
                    val cleanNumber = rawNumber.trim().replace(" ", "").replace("-", "")

                    if (cleanNumber.isBlank()) {
                        Log.e(TAG, "SMS ID ${sms.id} failed: Empty phone number")
                        dao.updateSms(
                            currentEntity.copy(
                                status = "FAILED",
                                lastError = "Invalid / Empty phone number"
                            )
                        )
                        continue
                    }

                    val sendResult = try {
                        sendSingleSms(
                            context = context,
                            mobileNumber = cleanNumber,
                            message = sms.message,
                            smsId = sms.id
                        )
                    } catch (e: Exception) {
                        Result.failure<Unit>(e)
                    }

                    val simLabel = getSimLabel(context)

                    if (sendResult.isSuccess) {
                        Log.d(TAG, "Successfully sent SMS ID ${sms.id} to $cleanNumber")
                        dao.updateSms(
                            currentEntity.copy(
                                status = "SENT",
                                lastError = "Sent via $simLabel",
                                mobileNumber = cleanNumber
                            )
                        )
                        processedCount++
                    } else {
                        val errorMsg = sendResult.exceptionOrNull()?.message ?: "SMS delivery failed"
                        Log.e(TAG, "Failed to send SMS ID ${sms.id} to $cleanNumber: $errorMsg")

                        val currentRetry = sms.retryCount
                        if (retryEnabled && currentRetry < maxRetryCount) {
                            val nextRetry = currentRetry + 1
                            Log.d(TAG, "Re-queuing SMS ID ${sms.id} for retry ($nextRetry / $maxRetryCount)")
                            dao.updateSms(
                                currentEntity.copy(
                                    status = "PENDING",
                                    retryCount = nextRetry,
                                    lastError = errorMsg,
                                    mobileNumber = cleanNumber
                                )
                            )
                        } else {
                            Log.d(TAG, "SMS ID ${sms.id} marked FAILED (Max retries reached or retry disabled)")
                            dao.updateSms(
                                currentEntity.copy(
                                    status = "FAILED",
                                    lastError = errorMsg,
                                    mobileNumber = cleanNumber
                                )
                            )
                        }
                    }

                    // Pacing delay between SMS dispatches to avoid carrier throttling
                    delay(INTER_SMS_DELAY_MS)
                }

                Log.d(TAG, "Finished SMS queue processing. Successfully sent: $processedCount")
                return@withLock processedCount
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error during SMS queue processing: ${e.message}", e)
                return@withLock 0
            }
        }
    }

    /**
     * Sends a single SMS message with robust dual-SIM routing, multipart Unicode support,
     * timeout handling, and BroadcastReceiver safety.
     */
    suspend fun sendSingleSms(
        context: Context,
        mobileNumber: String,
        message: String,
        smsId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!AutomaticSmsManager.isSmsPermissionGranted(context)) {
            return@withContext Result.failure(Exception("SEND_SMS permission not granted"))
        }

        val cleanNumber = mobileNumber.trim().replace(" ", "").replace("-", "")
        if (cleanNumber.isBlank()) {
            return@withContext Result.failure(Exception("Recipient phone number is empty"))
        }

        val selectedSubId = AutomaticSmsManager.getSelectedSim(context)
        val smsManager = try {
            getSmsManagerForSubId(context, selectedSubId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed resolving SmsManager: ${e.message}")
            return@withContext Result.failure(e)
        }

        val parts = try {
            smsManager.divideMessage(message)
        } catch (e: Exception) {
            Log.e(TAG, "Error dividing SMS message: ${e.message}")
            arrayListOf(message)
        }

        val partCount = parts.size
        val sentAction = "SMS_SENT_${System.currentTimeMillis()}_${smsId}"
        val deferred = CompletableDeferred<Int>()
        val remainingParts = AtomicInteger(partCount)
        var firstErrorCode: Int? = null

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val code = resultCode
                if (code != Activity.RESULT_OK && firstErrorCode == null) {
                    firstErrorCode = code
                }
                if (remainingParts.decrementAndGet() <= 0 || code != Activity.RESULT_OK) {
                    deferred.complete(firstErrorCode ?: Activity.RESULT_OK)
                }
            }
        }

        // Register receiver on Main thread safely
        withContext(Dispatchers.Main) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, IntentFilter(sentAction), Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(receiver, IntentFilter(sentAction))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Receiver registration failed: ${e.message}")
            }
        }

        try {
            if (partCount > 1) {
                val sentIntents = ArrayList<PendingIntent>(partCount)
                for (i in 0 until partCount) {
                    val requestCode = ((smsId % 100000L) * 100 + i).toInt()
                    val intent = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        Intent(sentAction),
                        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                    )
                    sentIntents.add(intent)
                }
                smsManager.sendMultipartTextMessage(cleanNumber, null, parts, sentIntents, null)
            } else {
                val requestCode = (smsId % 10000000L).toInt()
                val sentIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    Intent(sentAction),
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )
                smsManager.sendTextMessage(cleanNumber, null, message, sentIntent, null)
            }

            val finalResultCode = withTimeoutOrNull(SMS_TIMEOUT_MS) {
                deferred.await()
            }

            if (finalResultCode == null) {
                Result.failure(Exception("SMS sending timed out after ${SMS_TIMEOUT_MS / 1000}s"))
            } else if (finalResultCode == Activity.RESULT_OK) {
                Result.success(Unit)
            } else {
                val errorString = parseSmsErrorCode(finalResultCode)
                Result.failure(Exception(errorString))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while dispatching SMS: ${e.message}", e)
            Result.failure(e)
        } finally {
            // Safely unregister receiver on Main thread
            withContext(Dispatchers.Main) {
                try {
                    context.unregisterReceiver(receiver)
                } catch (e: Exception) {
                    // Receiver might already be unregistered or intent filter unregistered
                }
            }
        }
    }

    private fun parseSmsErrorCode(code: Int): String {
        return when (code) {
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic Failure (RESULT_ERROR_GENERIC_FAILURE)"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "Radio Off / Airplane Mode (RESULT_ERROR_RADIO_OFF)"
            SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU (RESULT_ERROR_NULL_PDU)"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "No Mobile Network Service (RESULT_ERROR_NO_SERVICE)"
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "SMS Limit Exceeded (RESULT_ERROR_LIMIT_EXCEEDED)"
            SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE -> "FDN Check Failure (RESULT_ERROR_FDN_CHECK_FAILURE)"
            else -> "SMS Error Code: $code"
        }
    }

    /**
     * Resolves appropriate SmsManager for Dual SIM slots.
     */
    fun getSmsManagerForSubId(context: Context, subId: Int): SmsManager {
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        val activeList = try {
            if (AutomaticSmsManager.isSmsPermissionGranted(context)) {
                subscriptionManager?.activeSubscriptionInfoList
            } else null
        } catch (e: SecurityException) {
            null
        }

        if (subId != -1) {
            if (activeList != null) {
                val matchedSub = activeList.firstOrNull { it.subscriptionId == subId }
                if (matchedSub != null) {
                    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val sm = context.getSystemService(SmsManager::class.java)
                        sm?.createForSubscriptionId(matchedSub.subscriptionId) ?: SmsManager.getDefault()
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getSmsManagerForSubscriptionId(matchedSub.subscriptionId)
                    }
                } else {
                    Log.w(TAG, "Selected SIM subId $subId not active. Falling back to default.")
                }
            }
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    fun getSimLabel(context: Context): String {
        val selectedSubId = AutomaticSmsManager.getSelectedSim(context)
        if (selectedSubId == -1) return "Default SIM"
        val availableSims = AutomaticSmsManager.getAvailableSims(context)
        val simInfo = availableSims.find { it.subscriptionId == selectedSubId }
        return if (simInfo != null) "SIM ${simInfo.slotIndex + 1} (${simInfo.carrierName})" else "SIM SubID $selectedSubId"
    }
}
