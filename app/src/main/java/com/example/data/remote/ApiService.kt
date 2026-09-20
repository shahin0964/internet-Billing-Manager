package com.example.data.remote

import com.example.data.model.ApiResponse
import com.example.data.model.BillModel
import com.example.data.model.BillRequest
import com.example.data.model.PaymentModel
import com.example.data.model.PaymentRequest
import com.example.data.model.ExpenseModel
import com.example.data.model.ExpenseRequest
import com.example.data.model.ExpenseCategoryModel
import com.example.data.model.ExpenseCategoryRequest
import com.example.data.model.SettingsModel
import com.example.data.model.SettingsRequest
import com.example.data.model.AuditLogModel
import com.example.data.model.AuditLogRequest
import com.example.data.model.BandwidthBillModel
import com.example.data.model.BandwidthBillRequest
import com.example.data.model.SpecificAdvanceModel
import com.example.data.model.SpecificAdvanceRequest
import com.example.data.model.CloudBackupModel
import com.example.data.model.CloudBackupRequest
import com.example.data.model.CloudBackupResponse
import com.example.data.model.Customer
import com.example.data.model.PackageModel
import com.example.data.model.AddCustomerRequest
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {

    @GET("api/customers.php")
    suspend fun getCustomers(
        @Query("user_id") userId: String
    ): ApiResponse<List<Customer>>

    @POST("api/customers.php")
    suspend fun saveCustomer(
        @Body request: AddCustomerRequest
    ): ApiResponse<Unit>

    @DELETE("api/customers.php")
    suspend fun deleteCustomer(
        @Query("id") id: String,
        @Query("user_id") userId: String
    ): ApiResponse<Unit>

    @GET("api/packages.php")
    suspend fun getPackages(
        @Query("user_id") userId: String
    ): ApiResponse<List<PackageModel>>

    @POST("api/packages.php")
    suspend fun savePackage(
        @Body request: PackageRequest
    ): ApiResponse<Unit>

    @DELETE("api/packages.php")
    suspend fun deletePackage(
        @Query("id") id: String,
        @Query("user_id") userId: String
    ): ApiResponse<Unit>

    @GET("api/bills.php")
    suspend fun getBills(
        @Query("user_id") userId: String,
        @Query("customer_id") customerId: String? = null
    ): ApiResponse<List<BillModel>>

    @POST("api/bills.php")
    suspend fun saveBill(
        @Body request: BillRequest
    ): ApiResponse<Unit>

    @DELETE("api/bills.php")
    suspend fun deleteBill(
        @Query("id") id: String,
        @Query("user_id") userId: String
    ): ApiResponse<Unit>

    @GET("api/payments.php")
    suspend fun getPayments(
        @Query("user_id") userId: String,
        @Query("customer_id") customerId: String? = null,
        @Query("bill_id") billId: String? = null
    ): ApiResponse<List<PaymentModel>>

    @POST("api/payments.php")
    suspend fun savePayment(
        @Body request: PaymentRequest
    ): ApiResponse<Unit>

    @DELETE("api/payments.php")
    suspend fun deletePayment(
        @Query("id") id: String,
        @Query("user_id") userId: String
    ): ApiResponse<Unit>

    @GET("api/expenses.php")
    suspend fun getExpenses(
        @Query("user_id") userId: String,
        @Query("category") category: String? = null
    ): ApiResponse<List<ExpenseModel>>

    @POST("api/expenses.php")
    suspend fun saveExpense(
        @Body request: ExpenseRequest
    ): ApiResponse<Unit>

    @DELETE("api/expenses.php")
    suspend fun deleteExpense(
        @Query("id") id: String,
        @Query("user_id") userId: String
    ): ApiResponse<Unit>

    @GET("api/expense_categories.php")
    suspend fun getExpenseCategories(
        @Query("user_id") userId: String
    ): ApiResponse<List<ExpenseCategoryModel>>

    @POST("api/expense_categories.php")
    suspend fun saveExpenseCategory(
        @Body request: ExpenseCategoryRequest
    ): ApiResponse<Unit>

    @DELETE("api/expense_categories.php")
    suspend fun deleteExpenseCategory(
        @Query("id") id: String,
        @Query("user_id") userId: String
    ): ApiResponse<Unit>

    @GET("api/settings.php")
    suspend fun getSettings(
        @Query("user_id") userId: String
    ): ApiResponse<SettingsModel>

    @POST("api/settings.php")
    suspend fun saveSettings(
        @Body request: SettingsRequest
    ): ApiResponse<Unit>

    @GET("api/audit_logs.php")
    suspend fun getAuditLogs(
        @Query("user_id") userId: String,
        @Query("action_type") actionType: String? = null,
        @Query("limit") limit: Int? = null
    ): ApiResponse<List<AuditLogModel>>

    @POST("api/audit_logs.php")
    suspend fun saveAuditLog(
        @Body request: AuditLogRequest
    ): ApiResponse<Unit>

    @GET("api/bandwidth_bills.php")
    suspend fun getBandwidthBills(
        @Query("user_id") userId: String,
        @Query("billing_month") billingMonth: String? = null
    ): ApiResponse<List<BandwidthBillModel>>

    @POST("api/bandwidth_bills.php")
    suspend fun saveBandwidthBill(
        @Body request: BandwidthBillRequest
    ): ApiResponse<Unit>

    @GET("api/specific_advances.php")
    suspend fun getSpecificAdvances(
        @Query("user_id") userId: String,
        @Query("customer_id") customerId: String? = null,
        @Query("billing_month") billingMonth: String? = null
    ): ApiResponse<List<SpecificAdvanceModel>>

    @POST("api/specific_advances.php")
    suspend fun saveSpecificAdvance(
        @Body request: SpecificAdvanceRequest
    ): ApiResponse<Unit>

    @GET("api/backups.php")
    suspend fun getLatestCloudBackup(
        @Query("user_id") userId: String,
        @Query("action") action: String = "latest"
    ): ApiResponse<CloudBackupModel>

    @GET("api/backups.php")
    suspend fun listCloudBackups(
        @Query("user_id") userId: String,
        @Query("action") action: String = "list"
    ): ApiResponse<List<CloudBackupModel>>

    @POST("api/backups.php")
    suspend fun saveCloudBackup(
        @Body request: CloudBackupRequest
    ): CloudBackupResponse

    @POST("api/sync.php")
    suspend fun sync(
        @Body request: com.example.data.model.SyncPushRequest
    ): com.example.data.model.SyncResponse

    @GET("api/sync.php")
    suspend fun getDelta(
        @Query("user_id") userId: String,
        @Query("since") since: Long
    ): com.example.data.model.SyncResponse

    @POST("api/login.php")
    suspend fun login(
        @Body request: LoginRequest
    ): LoginResponse

    @POST("api/signup.php")
    suspend fun signup(
        @Body request: SignupRequest
    ): SignupResponse
}

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginUser(
    val id: String,
    val name: String,
    val email: String
)

data class LoginResponse(
    val status: Boolean,
    val message: String,
    val user: LoginUser?
)

data class SignupRequest(
    val name: String,
    val email: String,
    val password: String,
    val phone: String
)

data class SignupUser(
    val id: String,
    val name: String,
    val email: String
)

data class SignupResponse(
    val status: Boolean,
    val message: String,
    val user: SignupUser?
)

data class PackageRequest(
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    val name: String,
    val price: Double,
    val speed: String? = null
)