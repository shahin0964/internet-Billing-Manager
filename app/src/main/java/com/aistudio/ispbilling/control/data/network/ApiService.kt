package com.aistudio.ispbilling.control.data.network

import com.aistudio.ispbilling.control.data.model.ApiResponse
import com.aistudio.ispbilling.control.data.model.BillModel
import com.aistudio.ispbilling.control.data.model.Customer
import com.aistudio.ispbilling.control.data.model.PackageModel
import com.aistudio.ispbilling.control.data.remote.request.BillRequest
import com.aistudio.ispbilling.control.data.remote.request.CustomerRequest
import com.aistudio.ispbilling.control.data.remote.request.PackageRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ApiService {

    @GET("api/customers.php")
    suspend fun getCustomers(): Response<ApiResponse<List<Customer>>>

    @POST("api/customers.php")
    suspend fun addCustomer(
        @Body request: CustomerRequest
    ): Response<ApiResponse<Unit>>

    @PUT("api/customers.php")
    suspend fun updateCustomer(
        @Body request: CustomerRequest
    ): Response<ApiResponse<Unit>>

    @DELETE("api/customers.php/{id}")
    suspend fun deleteCustomer(
        @Path("id") id: Long
    ): Response<ApiResponse<Unit>>

    @GET("api/packages.php")
    suspend fun getPackages(): Response<ApiResponse<List<PackageModel>>>

    @POST("api/packages.php")
    suspend fun addPackage(
        @Body request: PackageRequest
    ): Response<ApiResponse<Unit>>

    @PUT("api/packages.php")
    suspend fun updatePackage(
        @Body request: PackageRequest
    ): Response<ApiResponse<Unit>>

    @DELETE("api/packages.php/{id}")
    suspend fun deletePackage(
        @Path("id") id: Long
    ): Response<ApiResponse<Unit>>

    @GET("api/bills.php")
    suspend fun getBills(): Response<ApiResponse<List<BillModel>>>

    @POST("api/bills.php")
    suspend fun addBill(
        @Body request: BillRequest
    ): Response<ApiResponse<Unit>>

    @PUT("api/bills.php")
    suspend fun updateBill(
        @Body request: BillRequest
    ): Response<ApiResponse<Unit>>

    @DELETE("api/bills.php/{id}")
    suspend fun deleteBill(
        @Path("id") id: Long
    ): Response<ApiResponse<Unit>>
}
