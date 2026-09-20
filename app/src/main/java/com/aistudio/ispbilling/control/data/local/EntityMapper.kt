package com.aistudio.ispbilling.control.data.local

import com.aistudio.ispbilling.control.data.local.entity.BillEntity
import com.aistudio.ispbilling.control.data.local.entity.CustomerEntity
import com.aistudio.ispbilling.control.data.local.entity.PackageEntity
import com.aistudio.ispbilling.control.data.model.BillModel
import com.aistudio.ispbilling.control.data.model.Customer
import com.aistudio.ispbilling.control.data.model.PackageModel

fun Customer.toEntity(
    synced: Boolean = true,
    syncAction: String = SyncAction.INSERT
): CustomerEntity {
    return CustomerEntity(
        id = id,
        userId = userId,
        name = name,
        phone = phone,
        address = address,
        ipAddress = ipAddress,
        packageId = packageId,
        billingCycleDate = billingCycleDate,
        status = status,
        isSynced = synced,
        syncAction = syncAction
    )
}

fun CustomerEntity.toModel(): Customer {
    return Customer(
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
}

fun PackageModel.toEntity(
    synced: Boolean = true,
    syncAction: String = SyncAction.INSERT
): PackageEntity {
    return PackageEntity(
        id = id,
        userId = userId,
        name = name,
        price = price,
        speed = speed,
        isSynced = synced,
        syncAction = syncAction
    )
}

fun PackageEntity.toModel(): PackageModel {
    return PackageModel(
        id = id,
        userId = userId,
        name = name,
        price = price,
        speed = speed
    )
}

fun BillModel.toEntity(
    synced: Boolean = true,
    syncAction: String = SyncAction.INSERT
): BillEntity {
    return BillEntity(
        id = id,
        userId = userId,
        customerId = customerId,
        amount = amount,
        billMonth = billMonth,
        dueDate = dueDate,
        status = status,
        isSynced = synced,
        syncAction = syncAction
    )
}

fun BillEntity.toModel(): BillModel {
    return BillModel(
        id = id,
        userId = userId,
        customerId = customerId,
        amount = amount,
        billMonth = billMonth,
        dueDate = dueDate,
        status = status
    )
}
