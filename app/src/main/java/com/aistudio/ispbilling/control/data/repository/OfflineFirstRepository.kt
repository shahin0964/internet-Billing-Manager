package com.aistudio.ispbilling.control.data.repository

import com.aistudio.ispbilling.control.data.local.SyncAction
import com.aistudio.ispbilling.control.data.local.dao.BillDao
import com.aistudio.ispbilling.control.data.local.dao.CustomerDao
import com.aistudio.ispbilling.control.data.local.dao.PackageDao
import com.aistudio.ispbilling.control.data.local.entity.BillEntity
import com.aistudio.ispbilling.control.data.local.entity.CustomerEntity
import com.aistudio.ispbilling.control.data.local.entity.PackageEntity
import com.aistudio.ispbilling.control.data.network.ApiService
import com.aistudio.ispbilling.control.data.remote.request.BillRequest
import com.aistudio.ispbilling.control.data.remote.request.CustomerRequest
import com.aistudio.ispbilling.control.data.remote.request.PackageRequest
import kotlinx.coroutines.flow.Flow

class OfflineFirstRepository(
    private val apiService: ApiService,
    private val customerDao: CustomerDao,
    private val packageDao: PackageDao,
    private val billDao: BillDao
) {

    fun observeCustomers(): Flow<List<CustomerEntity>> =
        customerDao.observeCustomers()

    fun observePackages(): Flow<List<PackageEntity>> =
        packageDao.observePackages()

    fun observeBills(): Flow<List<BillEntity>> =
        billDao.observeBills()

    suspend fun saveCustomer(customer: CustomerEntity) {
        customerDao.insert(
            customer.copy(
                isSynced = false,
                syncAction = SyncAction.INSERT
            )
        )
    }

    suspend fun updateCustomer(customer: CustomerEntity) {
        customerDao.insert(
            customer.copy(
                isSynced = false,
                syncAction = SyncAction.UPDATE
            )
        )
    }

    suspend fun deleteCustomer(id: Long) {
        customerDao.getCustomer(id)?.let {
            customerDao.insert(
                it.copy(
                    isSynced = false,
                    syncAction = SyncAction.DELETE
                )
            )
        }
    }

    suspend fun savePackage(packageEntity: PackageEntity) {
        packageDao.insert(
            packageEntity.copy(
                isSynced = false,
                syncAction = SyncAction.INSERT
            )
        )
    }

    suspend fun updatePackage(packageEntity: PackageEntity) {
        packageDao.insert(
            packageEntity.copy(
                isSynced = false,
                syncAction = SyncAction.UPDATE
            )
        )
    }

    suspend fun deletePackage(id: Long) {
        packageDao.getPackage(id)?.let {
            packageDao.insert(
                it.copy(
                    isSynced = false,
                    syncAction = SyncAction.DELETE
                )
            )
        }
    }

    suspend fun saveBill(bill: BillEntity) {
        billDao.insert(
            bill.copy(
                isSynced = false,
                syncAction = SyncAction.INSERT
            )
        )
    }

    suspend fun updateBill(bill: BillEntity) {
        billDao.insert(
            bill.copy(
                isSynced = false,
                syncAction = SyncAction.UPDATE
            )
        )
    }

    suspend fun deleteBill(id: Long) {
        billDao.getBill(id)?.let {
            billDao.insert(
                it.copy(
                    isSynced = false,
                    syncAction = SyncAction.DELETE
                )
            )
        }
    }

    suspend fun refreshCustomers() {
        val response = apiService.getCustomers()

        if (!response.isSuccessful) return

        val body = response.body() ?: return

        if (!body.status || body.data == null) return

        val pendingIds =
            customerDao.getUnsyncedCustomers()
                .map { it.id }
                .toSet()

        val serverEntities = body.data
            .filterNot { it.id in pendingIds }
            .map { customer ->
                CustomerEntity(
                    id = customer.id,
                    userId = customer.userId,
                    name = customer.name,
                    phone = customer.phone,
                    address = customer.address,
                    ipAddress = customer.ipAddress,
                    packageId = customer.packageId,
                    billingCycleDate = customer.billingCycleDate,
                    status = customer.status,
                    isSynced = true,
                    syncAction = SyncAction.INSERT
                )
            }

        if (serverEntities.isNotEmpty()) {
            customerDao.insertAll(serverEntities)
        }
    }

    suspend fun refreshPackages() {
        val response = apiService.getPackages()

        if (!response.isSuccessful) return

        val body = response.body() ?: return

        if (!body.status || body.data == null) return

        val pendingIds =
            packageDao.getUnsyncedPackages()
                .map { it.id }
                .toSet()

        val serverEntities = body.data
            .filterNot { it.id in pendingIds }
            .map { packageModel ->
                PackageEntity(
                    id = packageModel.id,
                    userId = packageModel.userId,
                    name = packageModel.name,
                    price = packageModel.price,
                    speed = packageModel.speed,
                    isSynced = true,
                    syncAction = SyncAction.INSERT
                )
            }

        if (serverEntities.isNotEmpty()) {
            packageDao.insertAll(serverEntities)
        }
    }

    suspend fun refreshBills() {
        val response = apiService.getBills()

        if (!response.isSuccessful) return

        val body = response.body() ?: return

        if (!body.status || body.data == null) return

        val pendingIds =
            billDao.getUnsyncedBills()
                .map { it.id }
                .toSet()

        val serverEntities = body.data
            .filterNot { it.id in pendingIds }
            .map { bill ->
                BillEntity(
                    id = bill.id,
                    userId = bill.userId,
                    customerId = bill.customerId,
                    amount = bill.amount,
                    billMonth = bill.billMonth,
                    dueDate = bill.dueDate,
                    status = bill.status,
                    isSynced = true,
                    syncAction = SyncAction.INSERT
                )
            }

        if (serverEntities.isNotEmpty()) {
            billDao.insertAll(serverEntities)
        }
    }
}
