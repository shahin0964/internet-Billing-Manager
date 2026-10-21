package com.example.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

object ApiClient {
    private const val BASE_URL = "https://ispbillingmanagement.dev.cv/"
    private val strictGson = Gson()

    private val loggingInterceptor: HttpLoggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
    }

    /**
     * Checks whether a candidate string is a valid, strict RFC-8259 JSON Object.
     */
    internal fun isStrictJsonObject(candidate: String): Boolean {
        val trimmed = candidate.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return false
        return try {
            val reader = JsonReader(StringReader(trimmed)).apply {
                isLenient = false
            }
            val element = strictGson.getAdapter(JsonElement::class.java).read(reader)
            element != null && element.isJsonObject && reader.peek() == JsonToken.END_DOCUMENT
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Checks whether a candidate string is a valid, strict RFC-8259 JSON Array.
     */
    internal fun isStrictJsonArray(candidate: String): Boolean {
        val trimmed = candidate.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return false
        return try {
            val reader = JsonReader(StringReader(trimmed)).apply {
                isLenient = false
            }
            val element = strictGson.getAdapter(JsonElement::class.java).read(reader)
            element != null && element.isJsonArray && reader.peek() == JsonToken.END_DOCUMENT
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Extracts a valid JSON Object (or Array wrapped into Object) if PHP warnings, notices,
     * HTML snippets, or UTF-8 BOM characters were emitted before/after the JSON payload.
     */
    internal fun extractEmbeddedJsonObject(text: String): String? {
        val lastBrace = text.lastIndexOf('}')
        if (lastBrace > 0) {
            var searchFrom = 0
            while (searchFrom < lastBrace) {
                val openBrace = text.indexOf('{', searchFrom)
                if (openBrace == -1 || openBrace >= lastBrace) break
                val candidate = text.substring(openBrace, lastBrace + 1)
                if (isStrictJsonObject(candidate)) {
                    return candidate
                }
                searchFrom = openBrace + 1
            }

            // Also check if there is trailing HTML/warnings after an earlier '}'
            val firstJsonKeyBrace = text.indexOf("{\"")
            if (firstJsonKeyBrace != -1) {
                var endSearch = lastBrace
                while (endSearch > firstJsonKeyBrace) {
                    val candidate = text.substring(firstJsonKeyBrace, endSearch + 1)
                    if (isStrictJsonObject(candidate)) {
                        return candidate
                    }
                    endSearch = text.lastIndexOf('}', endSearch - 1)
                }
            }
        }

        // Check if response is a valid JSON array and wrap it into an ApiResponse object
        val firstBracket = text.indexOf('[')
        val lastBracket = text.lastIndexOf(']')
        if (firstBracket != -1 && lastBracket > firstBracket) {
            val arrayCandidate = text.substring(firstBracket, lastBracket + 1)
            if (isStrictJsonArray(arrayCandidate)) {
                return """{"status":true,"success":true,"message":"OK","data":$arrayCandidate}"""
            }
        }

        return null
    }

    /**
     * Sanitizes raw HTTP response bytes (handling undecompressed GZIP, UTF-8 BOM,
     * double-encoded JSON strings, PHP warnings/notices, or HTML error pages)
     * so Retrofit's GsonConverterFactory always receives a clean JSON Object.
     */
    internal fun sanitizeResponseBytes(rawBytes: ByteArray): Pair<String, Boolean> {
        if (rawBytes.isEmpty()) {
            return """{"status":false,"success":false,"message":"Empty response from server. Please try again.","user":null,"data":null}""" to false
        }

        // 1. Decompress if raw bytes still have GZIP magic header (0x1f 0x8b)
        val decompressedBytes = if (rawBytes.size >= 2 &&
            rawBytes[0] == 0x1f.toByte() &&
            rawBytes[1] == 0x8b.toByte()
        ) {
            try {
                GZIPInputStream(ByteArrayInputStream(rawBytes)).use { it.readBytes() }
            } catch (_: Throwable) {
                rawBytes
            }
        } else {
            rawBytes
        }

        val rawString = String(decompressedBytes, Charsets.UTF_8)
        return sanitizeJsonString(rawString)
    }

    internal fun sanitizeJsonString(rawString: String): Pair<String, Boolean> {
        var cleaned = rawString
            .replace("\uFEFF", "")
            .replace("\u200B", "")
            .trim { it <= ' ' }

        if (cleaned.isEmpty()) {
            return """{"status":false,"success":false,"message":"Empty response from server. Please try again.","user":null,"data":null}""" to false
        }

        // 1. Unwrap double-encoded JSON strings (e.g. "{\"status\":true,...}")
        var unwrapCount = 0
        while (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length >= 2 && unwrapCount < 2) {
            unwrapCount++
            try {
                val parsed = JsonParser.parseString(cleaned)
                if (parsed.isJsonPrimitive && parsed.asJsonPrimitive.isString) {
                    val inner = parsed.asString
                        .replace("\uFEFF", "")
                        .replace("\u200B", "")
                        .trim { it <= ' ' }
                    if (inner.isNotEmpty()) {
                        cleaned = inner
                    } else {
                        break
                    }
                } else {
                    break
                }
            } catch (_: Throwable) {
                break
            }
        }

        // 2. Fast path: already a valid strict JSON Object
        if (isStrictJsonObject(cleaned)) {
            return cleaned to true
        }

        // 3. Extract embedded JSON Object (e.g. when PHP emits warnings/notices before or after JSON)
        val extracted = extractEmbeddedJsonObject(cleaned)
        if (extracted != null) {
            return extracted to true
        }

        // 4. Fallback: non-JSON response (HTML page, PHP fatal error text, or proxy message)
        val cleanErrorText = cleaned
            .replace(Regex("(?is)<style.*?>.*?</style>"), " ")
            .replace(Regex("(?is)<script.*?>.*?</script>"), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(180)
            .ifBlank { "Server returned an unexpected response. Please try again." }

        val friendlyMessage = if (
            cleanErrorText.contains("403", ignoreCase = true) ||
            cleanErrorText.contains("502", ignoreCase = true) ||
            cleanErrorText.contains("503", ignoreCase = true) ||
            cleanErrorText.contains("Cloudflare", ignoreCase = true) ||
            cleanErrorText.contains("LiteSpeed", ignoreCase = true)
        ) {
            "Server is temporarily busy ($cleanErrorText). Please try again."
        } else {
            cleanErrorText
        }

        val escapedMessage = strictGson.toJson(friendlyMessage)
        return """{"status":false,"success":false,"message":$escapedMessage,"user":null,"data":null}""" to false
    }

    private val jsonSanitizingInterceptor = okhttp3.Interceptor { chain ->
        val request = chain.request()
        var response = chain.proceed(request)
        val body = response.body ?: return@Interceptor response

        val rawBytes = try {
            body.bytes()
        } catch (e: Throwable) {
            Log.w("ApiClient", "Failed to read response bytes: ${e.message}")
            ByteArray(0)
        }

        var (sanitizedJson, isValidJson) = sanitizeResponseBytes(rawBytes)

        // If server returned non-JSON (e.g. transient HTML/WAF/PHP glitch), retry once automatically
        if (!isValidJson) {
            Log.w("API_HTTP_ERROR", "Non-JSON response detected on ${request.url}. Retrying request once...")
            try {
                response.close()
            } catch (_: Throwable) {}

            val retryRequest = request.newBuilder()
                .header("Cache-Control", "no-cache")
                .build()
            response = chain.proceed(retryRequest)
            val retryBytes = try {
                response.body?.bytes() ?: ByteArray(0)
            } catch (_: Throwable) {
                ByteArray(0)
            }
            val retryResult = sanitizeResponseBytes(retryBytes)
            sanitizedJson = retryResult.first
            isValidJson = retryResult.second
        }

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val newBody = sanitizedJson.toResponseBody(mediaType)

        val builder = response.newBuilder()
            .body(newBody)
            .removeHeader("Content-Encoding")
            .removeHeader("Content-Length")

        // Ensure non-JSON error responses converted into JSON objects have 200 status so Retrofit parses the message cleanly
        if (!response.isSuccessful && isStrictJsonObject(sanitizedJson)) {
            builder.code(200).message("OK")
        }

        builder.build()
    }

    @Volatile
    private var appContext: android.content.Context? = null

    @Volatile
    var authToken: String? = null

    fun init(context: android.content.Context) {
        appContext = context.applicationContext
        if (authToken.isNullOrBlank()) {
            authToken = com.example.IspApplication.getAuthToken(context)
        }
    }

    private val authInterceptor = okhttp3.Interceptor { chain ->
        val original = chain.request()
        val token = authToken ?: appContext?.let { com.example.IspApplication.getAuthToken(it) }
        val builder = original.newBuilder()
            .header("Accept", "application/json")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) ISP-Billing-Manager/1.0")
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        chain.proceed(builder.build())
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .addInterceptor(authInterceptor)
            .addInterceptor(jsonSanitizingInterceptor)
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
