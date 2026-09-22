package com.example.data.remote

import android.util.Log
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val BASE_URL = "https://ispbillingmanagement.dev.cv/"

    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    private val errorLoggingInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        
        if (!response.isSuccessful || response.code == 500) {
            val responseBody = response.body
            if (responseBody != null) {
                try {
                    val source = responseBody.source()
                    source.request(Long.MAX_VALUE)
                    val buffer = source.buffer
                    val responseBodyString = buffer.clone().readString(Charsets.UTF_8)
                    
                    Log.e("API_HTTP_ERROR", "==================================================")
                    Log.e("API_HTTP_ERROR", "HTTP ERROR DETECTED ON API CALL")
                    Log.e("API_HTTP_ERROR", "URL: ${request.url}")
                    Log.e("API_HTTP_ERROR", "Method: ${request.method}")
                    Log.e("API_HTTP_ERROR", "Status Code: ${response.code}")
                    Log.e("API_HTTP_ERROR", "Message: ${response.message}")
                    Log.e("API_HTTP_ERROR", "Response Body:\n$responseBodyString")
                    Log.e("API_HTTP_ERROR", "==================================================")
                } catch (e: Exception) {
                    Log.e("API_HTTP_ERROR", "Failed to print error response body: ${e.message}")
                }
            } else {
                Log.e("API_HTTP_ERROR", "HTTP ${response.code} error on ${request.url} but body is null")
            }
        }
        response
    }

    @Volatile
    var authToken: String? = null

    private val authInterceptor = okhttp3.Interceptor { chain ->
        val original = chain.request()
        val token = authToken
        val request = if (!token.isNullOrBlank()) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }
        chain.proceed(request)
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(errorLoggingInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        val gson = GsonBuilder()
            .setLenient()
            .create()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}
