package com.aistudio.ispbilling.control.data.network

import android.util.Log
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val BASE_URL =
        "https://ispbillingmanagement.dev.cv/"

    private val loggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
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

    private val jsonSanitizingInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) ISP-Billing-Manager/1.0")
            .build()
        val response = chain.proceed(request)
        val body = response.body ?: return@Interceptor response
        val rawBytes = try {
            body.bytes()
        } catch (_: Throwable) {
            ByteArray(0)
        }
        val (sanitizedJson, _) = com.example.data.remote.ApiClient.sanitizeResponseBytes(rawBytes)
        val mediaType = okhttp3.MediaType.Companion.run { "application/json; charset=utf-8".toMediaTypeOrNull() }
        val newBody = okhttp3.ResponseBody.Companion.run { sanitizedJson.toResponseBody(mediaType) }
        response.newBuilder()
            .body(newBody)
            .removeHeader("Content-Encoding")
            .removeHeader("Content-Length")
            .build()
    }

    private val okHttpClient =
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(jsonSanitizingInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()

    private val retrofit = {
        val gson = GsonBuilder()
            .setLenient()
            .create()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(
                GsonConverterFactory.create(gson)
            )
            .build()
    }()

    val apiService: ApiService =
        retrofit.create(ApiService::class.java)
}
