package com.aistudio.ispbilling.control.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aistudio.ispbilling.control.data.local.AppDatabase
import com.aistudio.ispbilling.control.data.local.SyncAction
import com.aistudio.ispbilling.control.data.local.entity.BillEntity
import com.aistudio.ispbilling.control.data.local.entity.CustomerEntity
import com.aistudio.ispbilling.control.data.local.entity.PackageEntity
import com.aistudio.ispbilling.control.data.network.ApiClient
import com.aistudio.ispbilling.control.data.remote.request.BillRequest
import com.aistudio.ispbilling.control.data.remote.request.CustomerRequest
import com.aistudio.ispbilling.control.data.remote.request.PackageRequest
import java.io.IOException

class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val database =
        AppDatabase.getInstance(applicationContext)

    private val api =
        ApiClient.apiService

    override suspend fun doWork(): Result {
        return try {
            syncCustomers()
            syncPackages()
            syncBills()

            Result.success()
        } catch (e: IOException) {
            Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun syncCustomers() {

        val pending =
            database.customerDao().getUnsyncedCustomers()

        for (customer in pending) {

            val response = when (customer.syncAction) {

                SyncAction.INSERT ->
                    api.addCustomer(customer.toRequest())

                SyncAction.UPDATE ->
                    api.updateCustomer(customer.toRequest())

                SyncAction.DELETE ->
                    api.deleteCustomer(customer.id)

                else -> continue
            }

            if (!response.isSuccessful ||
                response.body()?.status != true
            ) {
                throw IOException(
                    response.body()?.message
                        ?: "Customer synchronization failed"
                )
            }

            if (customer.syncAction == SyncAction.DELETE) {
                database.customerDao()
                    .deleteById(customer.id)
            } else {
                database.customerDao()
                    .updateSyncState(
                        customer.id,
                        true,
                        customer.syncAction
                    )
            }
        }
    }

    private suspend fun syncPackages() {

        val pending =
            database.packageDao().getUnsyncedPackages()

        for (packageEntity in pending) {

            val response = when (packageEntity.syncAction) {

                SyncAction.INSERT ->
                    api.addPackage(packageEntity.toRequest())

                SyncAction.UPDATE ->
                    api.updatePackage(packageEntity.toRequest())

                SyncAction.DELETE ->
                    api.deletePackage(packageEntity.id)

                else -> continue
            }

            if (!response.isSuccessful ||
                response.body()?.status != true
            ) {
                throw IOException(
                    response.body()?.message
                        ?: "Package synchronization failed"
                )
            }

            if (packageEntity.syncAction == SyncAction.DELETE) {
                database.packageDao()
                    .deleteById(packageEntity.id)
            } else {
                database.packageDao()
                    .updateSyncState(
                        packageEntity.id,
                        true,
                        packageEntity.syncAction
                    )
            }
        }
    }

    private suspend fun syncBills() {

        val pending =
            database.billDao().getUnsyncedBills()

        for (bill in pending) {

            val response = when (bill.syncAction) {

                SyncAction.INSERT ->
                    api.addBill(bill.toRequest())

                SyncAction.UPDATE ->
                    api.updateBill(bill.toRequest())

                SyncAction.DELETE ->
                    api.deleteBill(bill.id)

                else -> continue
            }

            if (!response.isSuccessful ||
                response.body()?.status != true
            ) {
                throw IOException(
                    response.body()?.message
                        ?: "Bill synchronization failed"
                )
            }

            if (bill.syncAction == SyncAction.DELETE) {
                database.billDao()
                    .deleteById(bill.id)
            } else {
                database.billDao()
                    .updateSyncState(
                        bill.id,
                        true,
                        bill.syncAction
                    )
            }
        }
    }

    private fun CustomerEntity.toRequest() =
        CustomerRequest(
            id = id,
            userId = userId,
            name = name,
            phone = phone,
            address = address,
            ipAddress = ipAddress,
            packageId = packageId,
            billingCycleDate = billingCycleDate,
            status = status
        )

    private fun PackageEntity.toRequest() =
        PackageRequest(
            id = id,
            userId = userId,
            name = name,
            price = price,
            speed = speed
        )

    private fun BillEntity.toRequest() =
        BillRequest(
            id = id,
            userId = userId,
            customerId = customerId,
            amount = amount,
            billMonth = billMonth,
            dueDate = dueDate,
            status = status
        )

    companion object {

        private const val WORK_NAME =
            "isp_billing_sync"

        fun enqueue(context: Context) {

            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(
                        NetworkType.CONNECTED
                    )
                    .build()

            val request =
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    request
                )
        }
    }
}
