package com.example.data.remote

import com.example.data.model.ApiResponse
import com.example.data.model.BillModel
import com.example.data.model.Customer
import com.example.data.model.PackageModel
import com.example.data.model.AddCustomerRequest
import retrofit2.http.Body
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

    @GET("api/packages.php")
    suspend fun getPackages(
        @Query("user_id") userId: String
    ): ApiResponse<List<PackageModel>>

    @GET("api/bills.php")
    suspend fun getBills(
        @Query("user_id") userId: String,
        @Query("customer_id") customerId: String? = null
    ): ApiResponse<List<BillModel>>
}
