package com.example.data.remote

import com.example.data.model.ApiResponse
import com.example.data.model.BillModel
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