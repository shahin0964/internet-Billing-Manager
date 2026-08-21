package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.BillEntity
import com.example.data.model.BusinessSettingsEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.ExpenseCategoryEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.IspPackageEntity
import com.example.data.model.PaymentEntity
import com.example.data.model.SpecificAdvanceEntity
import com.example.data.model.BandwidthBillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY id DESC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id")
    fun getCustomerById(id: Long): Flow<CustomerEntity?>

    @Query("SELECT * FROM customers")
    suspend fun getAllCustomersList(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE syncStatus = 1")
    suspend fun getDirtyCustomers(): List<CustomerEntity>

    @Query("UPDATE customers SET syncStatus = 0 WHERE id IN (:ids)")
    suspend fun markCustomersSynced(ids: List<Long>)

    @Query("UPDATE customers SET syncStatus = :status WHERE id = :id")
    suspend fun updateCustomerSyncStatus(id: Long, status: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<CustomerEntity>)

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)

    @Query("UPDATE customers SET status = :status WHERE id = :id")
    suspend fun updateCustomerStatus(id: Long, status: String)

    @Query("DELETE FROM customers")
    suspend fun deleteAllCustomers()
}

@Dao
interface IspPackageDao {
    @Query("SELECT * FROM packages ORDER BY speedMbps ASC")
    fun getAllPackages(): Flow<List<IspPackageEntity>>

    @Query("SELECT * FROM packages")
    suspend fun getAllPackagesList(): List<IspPackageEntity>

    @Query("SELECT * FROM packages WHERE syncStatus = 1")
    suspend fun getDirtyPackages(): List<IspPackageEntity>

    @Query("UPDATE packages SET syncStatus = 0 WHERE id IN (:ids)")
    suspend fun markPackagesSynced(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackage(pkg: IspPackageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackages(packages: List<IspPackageEntity>)

    @Update
    suspend fun updatePackage(pkg: IspPackageEntity)

    @Delete
    suspend fun deletePackage(pkg: IspPackageEntity)

    @Query("DELETE FROM packages")
    suspend fun deleteAllPackages()
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY id DESC")
    fun getAllBills(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE customerId = :customerId ORDER BY id DESC")
    fun getBillsForCustomer(customerId: Long): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills")
    suspend fun getAllBillsList(): List<BillEntity>

    @Query("SELECT * FROM bills WHERE syncStatus = 1")
    suspend fun getDirtyBills(): List<BillEntity>

    @Query("UPDATE bills SET syncStatus = 0 WHERE id IN (:ids)")
    suspend fun markBillsSynced(ids: List<Long>)

    @Query("SELECT * FROM bills WHERE customerId = :customerId ORDER BY id DESC")
    suspend fun getBillsListForCustomer(customerId: Long): List<BillEntity>

    @Query("SELECT * FROM bills WHERE id = :id")
    fun getBillById(id: Long): Flow<BillEntity?>

    @Query("SELECT * FROM bills WHERE (customerId = :customerId OR (:customerCode != '' AND LOWER(TRIM(customerCode)) = LOWER(TRIM(:customerCode)))) AND LOWER(TRIM(billingMonth)) = LOWER(TRIM(:billingMonth)) LIMIT 1")
    suspend fun findBillForCustomerAndMonth(customerId: Long, customerCode: String, billingMonth: String): BillEntity?

    @Query("SELECT COUNT(*) FROM bills WHERE (customerId = :customerId OR (:customerCode != '' AND LOWER(TRIM(customerCode)) = LOWER(TRIM(:customerCode)))) AND LOWER(TRIM(billingMonth)) = LOWER(TRIM(:billingMonth))")
    suspend fun getBillCountForCustomerAndMonth(customerId: Long, customerCode: String, billingMonth: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: BillEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBills(bills: List<BillEntity>)

    @Query("DELETE FROM bills WHERE customerId = :customerId")
    suspend fun deleteBillsForCustomer(customerId: Long)

    @Update
    suspend fun updateBill(bill: BillEntity)

    @Delete
    suspend fun deleteBill(bill: BillEntity)

    @Query("UPDATE bills SET customerName = :customerName WHERE customerId = :customerId")
    suspend fun updateCustomerNameInBills(customerId: Long, customerName: String)

    @Query("DELETE FROM bills")
    suspend fun deleteAllBills()
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY id DESC")
    fun getAllPayments(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments")
    suspend fun getAllPaymentsList(): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE syncStatus = 1")
    suspend fun getDirtyPayments(): List<PaymentEntity>

    @Query("UPDATE payments SET syncStatus = 0 WHERE id IN (:ids)")
    suspend fun markPaymentsSynced(ids: List<Long>)

    @Query("SELECT * FROM payments WHERE customerId = :customerId ORDER BY id DESC")
    fun getPaymentsForCustomer(customerId: Long): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE customerId = :customerId ORDER BY id DESC")
    suspend fun getPaymentsListForCustomer(customerId: Long): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE id = :id LIMIT 1")
    suspend fun getPaymentById(id: Long): PaymentEntity?

    @Query("SELECT * FROM payments WHERE billId = :billId ORDER BY id DESC")
    fun getPaymentsForBill(billId: Long): Flow<List<PaymentEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM payments WHERE paymentDate = :date")
    fun getCollectedAmountForDate(date: String): Flow<Double>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(payments: List<PaymentEntity>)

    @Delete
    suspend fun deletePayment(payment: PaymentEntity)

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun deletePaymentById(id: Long)

    @Query("DELETE FROM payments WHERE customerId = :customerId")
    suspend fun deletePaymentsForCustomer(customerId: Long)

    @Query("UPDATE payments SET customerName = :customerName WHERE customerId = :customerId")
    suspend fun updateCustomerNameInPayments(customerId: Long, customerName: String)

    @Query("DELETE FROM payments")
    suspend fun deleteAllPayments()
}

@Dao
interface BusinessSettingsDao {
    @Query("SELECT * FROM business_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<BusinessSettingsEntity?>

    @Query("SELECT * FROM business_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsSingle(): BusinessSettingsEntity?

    @Query("SELECT * FROM business_settings WHERE id = 1 AND syncStatus = 1 LIMIT 1")
    suspend fun getDirtySettings(): BusinessSettingsEntity?

    @Query("UPDATE business_settings SET syncStatus = 0 WHERE id = 1")
    suspend fun markSettingsSynced()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: BusinessSettingsEntity)

    @Query("DELETE FROM business_settings")
    suspend fun deleteSettings()
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC, id DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses")
    suspend fun getAllExpensesList(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE syncStatus = 1")
    suspend fun getDirtyExpenses(): List<ExpenseEntity>

    @Query("UPDATE expenses SET syncStatus = 0 WHERE id IN (:ids)")
    suspend fun markExpensesSynced(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()

    @Query("SELECT * FROM expense_categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<ExpenseCategoryEntity>>

    @Query("SELECT * FROM expense_categories")
    suspend fun getAllCategoriesList(): List<ExpenseCategoryEntity>

    @Query("SELECT * FROM expense_categories WHERE syncStatus = 1")
    suspend fun getDirtyCategories(): List<ExpenseCategoryEntity>

    @Query("UPDATE expense_categories SET syncStatus = 0 WHERE id IN (:ids)")
    suspend fun markCategoriesSynced(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: ExpenseCategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<ExpenseCategoryEntity>)

    @Query("DELETE FROM expense_categories")
    suspend fun deleteAllCategories()
}

@Dao
interface SpecificAdvanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpecificAdvance(advance: SpecificAdvanceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpecificAdvances(advances: List<SpecificAdvanceEntity>)

    @Query("SELECT * FROM specific_advances WHERE customerId = :customerId AND billingMonth = :billingMonth AND isConsumed = 0 LIMIT 1")
    suspend fun getUnconsumedSpecificAdvance(customerId: Long, billingMonth: String): SpecificAdvanceEntity?

    @Query("SELECT * FROM specific_advances")
    suspend fun getAllSpecificAdvancesList(): List<SpecificAdvanceEntity>

    @Query("SELECT * FROM specific_advances WHERE syncStatus = 1")
    suspend fun getDirtySpecificAdvances(): List<SpecificAdvanceEntity>

    @Query("UPDATE specific_advances SET syncStatus = 0 WHERE id IN (:ids)")
    suspend fun markSpecificAdvancesSynced(ids: List<Long>)

    @Query("UPDATE specific_advances SET isConsumed = 1, updatedAt = :updatedAt, syncStatus = 1 WHERE id = :id")
    suspend fun markConsumed(id: Long, updatedAt: Long)

    @Query("DELETE FROM specific_advances")
    suspend fun deleteAllSpecificAdvances()
}

@Dao
interface BandwidthBillDao {
    @Query("SELECT * FROM bandwidth_bills WHERE billingMonth = :billingMonth LIMIT 1")
    suspend fun getBandwidthBillSingle(billingMonth: String): BandwidthBillEntity?

    @Query("SELECT * FROM bandwidth_bills")
    fun getAllBandwidthBills(): Flow<List<BandwidthBillEntity>>

    @Query("SELECT * FROM bandwidth_bills")
    suspend fun getAllBandwidthBillsList(): List<BandwidthBillEntity>

    @Query("SELECT * FROM bandwidth_bills WHERE syncStatus = 1")
    suspend fun getDirtyBandwidthBills(): List<BandwidthBillEntity>

    @Query("UPDATE bandwidth_bills SET syncStatus = 0 WHERE billingMonth IN (:months)")
    suspend fun markBandwidthBillsSynced(months: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBandwidthBill(bill: BandwidthBillEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBandwidthBills(bills: List<BandwidthBillEntity>)

    @Query("DELETE FROM bandwidth_bills")
    suspend fun deleteAllBandwidthBills()
}

