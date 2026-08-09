package com.example.util

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object AppUpdateConfig {
    val GITHUB_OWNER: String
        get() = BuildConfig.GITHUB_OWNER.ifBlank { "isp-app" }

    val GITHUB_REPO: String
        get() = BuildConfig.GITHUB_REPO.ifBlank { "isp-billing-app" }
}

data class GitHubReleaseInfo(
    val tagName: String,
    val version: String,
    val releaseNotes: String,
    val apkDownloadUrl: String?,
    val isNewer: Boolean
)

sealed class UpdateException(message: String, val errCode: String) : Exception(message) {
    class NoInternet : UpdateException("No active internet connection available", "NO_INTERNET")
    class ConnectionFailed(msg: String) : UpdateException(msg, "CONNECTION_FAILED")
    class Timeout(msg: String) : UpdateException(msg, "TIMEOUT")
    class SslError(msg: String) : UpdateException(msg, "SSL_ERROR")
    class HttpError(val httpCode: Int, msg: String) : UpdateException(msg, "HTTP_$httpCode")
    class ReleaseNotFound(msg: String) : UpdateException(msg, "RELEASE_NOT_FOUND")
    class RateLimited(msg: String) : UpdateException(msg, "RATE_LIMITED")
    class ServerError(val httpCode: Int, msg: String) : UpdateException(msg, "SERVER_ERROR")
    class InvalidJson(msg: String) : UpdateException(msg, "INVALID_JSON")
    class ApkAssetNotFound(msg: String) : UpdateException(msg, "APK_ASSET_NOT_FOUND")
}

object AppUpdateManager {

    private const val TAG = "AppUpdateManager"
    private const val PREFS_NAME = "app_update_prefs"
    private const val KEY_CACHED_TAG = "cached_tag_name"
    private const val KEY_CACHED_VERSION = "cached_version"
    private const val KEY_CACHED_NOTES = "cached_release_notes"
    private const val KEY_CACHED_APK_URL = "cached_apk_url"
    private const val KEY_CACHED_TIMESTAMP = "cached_timestamp"
    private const val KEY_RATE_LIMIT_RESET = "rate_limit_reset_timestamp"
    private const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version"

    private const val AUTO_CHECK_CACHE_DURATION_MS = 60 * 60 * 1000L // 1 hour TTL for auto check
    private val checkMutex = Mutex()

    fun isVersionAlreadyNotified(context: Context, version: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastNotified = prefs.getString(KEY_LAST_NOTIFIED_VERSION, null)
        return lastNotified == version
    }

    fun saveVersionNotified(context: Context, version: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_NOTIFIED_VERSION, version).apply()
    }

    private fun getCachedReleaseInfo(context: Context): GitHubReleaseInfo? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val version = prefs.getString(KEY_CACHED_VERSION, null) ?: return null
        val tagName = prefs.getString(KEY_CACHED_TAG, "") ?: ""
        val notes = prefs.getString(KEY_CACHED_NOTES, "") ?: ""
        val apkUrl = prefs.getString(KEY_CACHED_APK_URL, null) ?: return null
        val installedVersion = getInstalledVersion(context)
        val isNewer = isVersionNewer(installedVersion, version)

        return GitHubReleaseInfo(
            tagName = tagName,
            version = version,
            releaseNotes = notes,
            apkDownloadUrl = apkUrl,
            isNewer = isNewer
        )
    }

    private fun saveCachedReleaseInfo(context: Context, info: GitHubReleaseInfo) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_CACHED_TAG, info.tagName)
            .putString(KEY_CACHED_VERSION, info.version)
            .putString(KEY_CACHED_NOTES, info.releaseNotes)
            .putString(KEY_CACHED_APK_URL, info.apkDownloadUrl)
            .putLong(KEY_CACHED_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    private fun getCacheTimestamp(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_CACHED_TIMESTAMP, 0L)
    }

    private fun getRateLimitResetTimestamp(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_RATE_LIMIT_RESET, 0L)
    }

    private fun saveRateLimitResetTimestamp(context: Context, resetEpochMs: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_RATE_LIMIT_RESET, resetEpochMs).apply()
    }

    private fun clearRateLimitResetTimestamp(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_RATE_LIMIT_RESET).apply()
    }

    /**
     * Check if network connectivity is available.
     */
    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                val activeNetworkInfo = connectivityManager.activeNetworkInfo
                activeNetworkInfo != null && activeNetworkInfo.isConnected
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error checking network availability: ${e.message}")
            true
        }
    }

    /**
     * Get installed app version name safely from PackageManager or BuildConfig.
     */
    fun getInstalledVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName?.takeIf { it.isNotBlank() } ?: BuildConfig.VERSION_NAME
        } catch (e: Exception) {
            BuildConfig.VERSION_NAME
        }
    }

    /**
     * Get installed app version code safely from PackageManager or BuildConfig.
     */
    fun getInstalledVersionCode(context: Context): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            BuildConfig.VERSION_CODE.toLong()
        }
    }

    /**
     * Compare semantic version strings (e.g. "1.0.9" vs "1.0.10").
     */
    fun isVersionNewer(installedVersion: String, latestVersion: String): Boolean {
        val cleanInstalled = installedVersion.trim().removePrefix("v").removePrefix("V")
        val cleanLatest = latestVersion.trim().removePrefix("v").removePrefix("V")

        if (cleanInstalled.equals(cleanLatest, ignoreCase = true)) return false

        val installedParts = cleanInstalled.split(".", "-", "+", "_").mapNotNull { it.toIntOrNull() }
        val latestParts = cleanLatest.split(".", "-", "+", "_").mapNotNull { it.toIntOrNull() }

        if (installedParts.isEmpty() || latestParts.isEmpty()) {
            return false
        }

        val maxLen = maxOf(installedParts.size, latestParts.size)
        for (i in 0 until maxLen) {
            val inst = installedParts.getOrElse(i) { 0 }
            val lat = latestParts.getOrElse(i) { 0 }
            if (lat > inst) return true
            if (lat < inst) return false
        }
        return false
    }

    /**
     * Check GitHub API for the latest release with caching and rate limit handling.
     */
    suspend fun checkForUpdates(
        context: Context,
        force: Boolean = false,
        owner: String = AppUpdateConfig.GITHUB_OWNER,
        repo: String = AppUpdateConfig.GITHUB_REPO
    ): Result<GitHubReleaseInfo> = withContext(Dispatchers.IO) {
        val installedVersion = getInstalledVersion(context)

        // Offline check
        if (!isNetworkAvailable(context)) {
            Log.w(TAG, "Network check failed: No active internet connection")
            val cached = getCachedReleaseInfo(context)
            if (cached != null) {
                return@withContext Result.success(cached)
            }
            return@withContext Result.failure(UpdateException.NoInternet())
        }

        // Deduplicate simultaneous check requests
        checkMutex.withLock {
            val now = System.currentTimeMillis()

            // 1. Check Rate Limit status
            val rateLimitReset = getRateLimitResetTimestamp(context)
            if (now < rateLimitReset) {
                val remainingMins = maxOf(1L, (rateLimitReset - now) / 60000L)
                Log.w(TAG, "Rate limit currently active. Resets in $remainingMins min.")

                val cached = getCachedReleaseInfo(context)
                if (cached != null) {
                    return@withContext Result.success(cached)
                }
                return@withContext Result.failure(
                    UpdateException.RateLimited("GitHub API rate limit exceeded. Resets in $remainingMins min.")
                )
            }

            // 2. Check Cache freshness for automatic checks (force == false)
            if (!force) {
                val cachedTimestamp = getCacheTimestamp(context)
                if (now - cachedTimestamp < AUTO_CHECK_CACHE_DURATION_MS) {
                    val cached = getCachedReleaseInfo(context)
                    if (cached != null) {
                        Log.d(TAG, "Returning cached release info (age: ${(now - cachedTimestamp) / 1000}s)")
                        return@withContext Result.success(cached)
                    }
                }
            }

            // 3. Make HTTP request to GitHub API
            val urlString = "https://api.github.com/repos/$owner/$repo/releases/latest"
            Log.d(TAG, "Checking updates for repo: $owner/$repo (installed: $installedVersion, force=$force)")

            var connection: HttpURLConnection? = null
            try {
                val url = URL(urlString)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "Android-ISP-Billing-App")
                }

                val responseCode = connection.responseCode
                val remainingHeader = connection.getHeaderField("X-RateLimit-Remaining")
                val resetHeader = connection.getHeaderField("X-RateLimit-Reset")
                val retryAfterHeader = connection.getHeaderField("Retry-After")

                Log.d(TAG, "HTTP Response Code: $responseCode, RateLimit-Remaining: $remainingHeader, Reset: $resetHeader")

                if (responseCode == HttpURLConnection.HTTP_FORBIDDEN || responseCode == 429 || (remainingHeader == "0" && responseCode != HttpURLConnection.HTTP_OK)) {
                    val resetEpochMs = when {
                        resetHeader != null -> (resetHeader.toLongOrNull() ?: 0L) * 1000L
                        retryAfterHeader != null -> now + ((retryAfterHeader.toLongOrNull() ?: 1800L) * 1000L)
                        else -> now + (30 * 60 * 1000L)
                    }
                    val validReset = maxOf(now + 60000L, resetEpochMs)
                    saveRateLimitResetTimestamp(context, validReset)

                    val minsLeft = maxOf(1L, (validReset - now) / 60000L)
                    Log.w(TAG, "GitHub API rate limit exceeded (HTTP $responseCode). Resets in $minsLeft min.")

                    val cached = getCachedReleaseInfo(context)
                    if (cached != null) {
                        return@withContext Result.success(cached)
                    }
                    return@withContext Result.failure(
                        UpdateException.RateLimited("GitHub API rate limit exceeded. Try again in $minsLeft min.")
                    )
                }

                when (responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        // OK
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        Log.w(TAG, "GitHub release not found for repo $owner/$repo (HTTP 404)")
                        return@withContext Result.failure(
                            UpdateException.ReleaseNotFound("No release found for repository $owner/$repo")
                        )
                    }
                    in 500..599 -> {
                        Log.w(TAG, "GitHub server error (HTTP $responseCode)")
                        return@withContext Result.failure(
                            UpdateException.ServerError(responseCode, "GitHub server error (HTTP $responseCode)")
                        )
                    }
                    else -> {
                        Log.w(TAG, "HTTP request failed with status $responseCode")
                        return@withContext Result.failure(
                            UpdateException.HttpError(responseCode, "HTTP Error: $responseCode")
                        )
                    }
                }

                clearRateLimitResetTimestamp(context)

                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                val json = try {
                    JSONObject(jsonString)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse release JSON", e)
                    return@withContext Result.failure(UpdateException.InvalidJson("Malformed JSON from release server"))
                }

                val tagName = json.optString("tag_name", "")
                val releaseName = json.optString("name", "")
                val cleanVersion = tagName.removePrefix("v").removePrefix("V")
                    .ifBlank { releaseName.removePrefix("v").removePrefix("V") }
                val rawBody = if (json.isNull("body")) "" else json.optString("body", "")
                val releaseNotes = if (rawBody.isBlank() || rawBody.trim().equals("null", ignoreCase = true)) {
                    context.getString(com.example.R.string.update_notes_fallback)
                } else {
                    rawBody.trim()
                }

                Log.d(TAG, "Release tag received: $tagName (clean: $cleanVersion)")

                var apkUrl: String? = null
                var apkFilename: String? = null

                if (json.has("assets")) {
                    val assets = json.getJSONArray("assets")
                    Log.d(TAG, "Number of assets received: ${assets.length()}")

                    val apkAssets = mutableListOf<Pair<String, String>>()
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val assetName = asset.optString("name", "")
                        val downloadUrl = asset.optString("browser_download_url", "")
                        if (assetName.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                            apkAssets.add(Pair(assetName, downloadUrl))
                        }
                    }

                    if (apkAssets.isNotEmpty()) {
                        val matched = apkAssets.firstOrNull { (name, _) ->
                            name.equals("ISP-Billing-Release.apk", ignoreCase = true) ||
                            name.equals("InternetBillManagement.apk", ignoreCase = true)
                        } ?: apkAssets.first()

                        apkFilename = matched.first
                        apkUrl = matched.second
                    }
                }

                Log.d(TAG, "Selected APK filename: $apkFilename, download URL: $apkUrl")

                if (apkUrl.isNullOrBlank()) {
                    return@withContext Result.failure(
                        UpdateException.ApkAssetNotFound("No APK asset found in release $tagName")
                    )
                }

                val isNewer = isVersionNewer(installedVersion, cleanVersion)
                Log.d(TAG, "Version comparison: installed=$installedVersion, latest=$cleanVersion, isNewer=$isNewer")

                val releaseInfo = GitHubReleaseInfo(
                    tagName = tagName,
                    version = cleanVersion,
                    releaseNotes = releaseNotes,
                    apkDownloadUrl = apkUrl,
                    isNewer = isNewer
                )

                saveCachedReleaseInfo(context, releaseInfo)

                Result.success(releaseInfo)
            } catch (e: UnknownHostException) {
                Log.w(TAG, "DNS failure / unknown host: ${e.message}")
                val cached = getCachedReleaseInfo(context)
                if (cached != null) {
                    return@withContext Result.success(cached)
                }
                Result.failure(UpdateException.ConnectionFailed("Unable to resolve GitHub host"))
            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "Connection timeout: ${e.message}")
                val cached = getCachedReleaseInfo(context)
                if (cached != null) {
                    return@withContext Result.success(cached)
                }
                Result.failure(UpdateException.Timeout("Connection timed out"))
            } catch (e: SSLException) {
                Log.w(TAG, "SSL error: ${e.message}")
                Result.failure(UpdateException.SslError("Secure connection error"))
            } catch (e: IOException) {
                Log.w(TAG, "I/O error contacting GitHub API: ${e.message}")
                val cached = getCachedReleaseInfo(context)
                if (cached != null) {
                    return@withContext Result.success(cached)
                }
                Result.failure(UpdateException.ConnectionFailed(e.message ?: "Network error"))
            } catch (e: Exception) {
                Log.w(TAG, "Unexpected error checking updates: ${e.message}", e)
                Result.failure(e)
            } finally {
                connection?.disconnect()
            }
        }
    }

    /**
     * Download the APK file from the release asset URL.
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        expectedVersionName: String? = null,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable(context)) {
            return@withContext Result.failure(UpdateException.NoInternet())
        }

        try {
            Log.d(TAG, "Starting APK download from URL: $downloadUrl")
            var currentUrl = downloadUrl
            var connection: HttpURLConnection
            var redirectCount = 0
            val maxRedirects = 5

            while (true) {
                val url = URL(currentUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Android-ISP-Billing-App")

                val status = connection.responseCode
                Log.d(TAG, "Download HTTP status: $status")

                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 || status == 308
                ) {
                    if (redirectCount >= maxRedirects) {
                        return@withContext Result.failure(Exception("Too many redirects downloading APK"))
                    }
                    val loc = connection.getHeaderField("Location")
                    if (loc.isNullOrBlank()) {
                        return@withContext Result.failure(Exception("Redirect location missing"))
                    }
                    currentUrl = loc
                    redirectCount++
                    connection.disconnect()
                } else if (status == HttpURLConnection.HTTP_OK) {
                    break
                } else {
                    return@withContext Result.failure(Exception("Failed to download APK: HTTP $status"))
                }
            }

            val fileLength = connection.contentLength
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir

            // Clean up any stale APK files in download directory
            try {
                downloadDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("app-update", ignoreCase = true) || file.name.endsWith(".apk", ignoreCase = true)) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clean old update APK cache: ${e.message}")
            }

            val safeVersion = expectedVersionName?.trim()?.removePrefix("v")?.removePrefix("V")?.replace("[^a-zA-Z0-9.-_]".toRegex(), "") ?: "latest"
            val outputFile = File(downloadDir, "app-update-$safeVersion.apk")

            if (outputFile.exists()) {
                outputFile.delete()
            }

            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val data = ByteArray(8192)
                    var total: Long = 0
                    var count: Int
                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        if (fileLength > 0) {
                            val progress = ((total * 100) / fileLength).toInt()
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                        }
                        output.write(data, 0, count)
                    }
                    output.flush()
                }
            }

            if (!outputFile.exists() || outputFile.length() == 0L) {
                return@withContext Result.failure(Exception("Downloaded APK file is missing or empty"))
            }

            // Verify header: valid APK must be a ZIP archive starting with PK\x03\x04
            val header = ByteArray(4)
            outputFile.inputStream().use { it.read(header) }
            if (header[0] != 0x50.toByte() || header[1] != 0x4B.toByte() || header[2] != 0x03.toByte() || header[3] != 0x04.toByte()) {
                outputFile.delete()
                return@withContext Result.failure(Exception("Downloaded file is corrupt or not a valid APK package"))
            }

            Log.d(TAG, "APK download completed: ${outputFile.absolutePath} (${outputFile.length()} bytes)")

            // Verify package name, version code and version name before returning
            val verifyRes = verifyDownloadedApk(context, outputFile, expectedVersionName)
            if (verifyRes.isFailure) {
                val err = verifyRes.exceptionOrNull() ?: Exception("Downloaded APK failed verification")
                Log.e(TAG, "APK verification failed: ${err.message}")
                outputFile.delete()
                return@withContext Result.failure(err)
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Verify package identity, version code, and version name of a downloaded APK file before installation.
     */
    fun verifyDownloadedApk(
        context: Context,
        apkFile: File,
        expectedVersionName: String? = null
    ): Result<Boolean> {
        try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                return Result.failure(Exception("Downloaded APK file is missing or empty"))
            }

            val pInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                ?: return Result.failure(Exception("Downloaded file is not a valid Android package"))

            val currentPkg = context.packageName
            val apkPkg = pInfo.packageName ?: pInfo.applicationInfo?.packageName

            if (!apkPkg.isNullOrBlank() && apkPkg != currentPkg) {
                return Result.failure(
                    Exception("Downloaded APK package ($apkPkg) does not match current app ($currentPkg)")
                )
            }

            val installedCode = getInstalledVersionCode(context)
            val apkCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }

            val apkVersionName = pInfo.versionName ?: ""
            val installedVersion = getInstalledVersion(context)

            Log.d(
                TAG,
                "APK Verification: pkg=$apkPkg, apkVersionCode=$apkCode (installedCode=$installedCode), apkVersionName=$apkVersionName (installedVer=$installedVersion, expectedVer=$expectedVersionName)"
            )

            if (apkCode < installedCode) {
                return Result.failure(
                    Exception("Downloaded APK version code ($apkCode) is older than currently installed version code ($installedCode). Update aborted.")
                )
            }

            if (!expectedVersionName.isNullOrBlank()) {
                val cleanExpected = expectedVersionName.trim().removePrefix("v").removePrefix("V")
                val cleanApkVer = apkVersionName.trim().removePrefix("v").removePrefix("V")

                if (cleanApkVer.isNotBlank() && isVersionNewer(cleanApkVer, cleanExpected)) {
                    return Result.failure(
                        Exception("Downloaded APK version ($cleanApkVer) does not match expected target release ($cleanExpected). Update aborted.")
                    )
                }
            }

            return Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying downloaded APK: ${e.message}", e)
            return Result.failure(e)
        }
    }

    /**
     * Create automatic pre-update safety backup of all customer, bill, payment, and settings records.
     */
    suspend fun createPreUpdateSafetyBackup(context: Context): Result<File> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating pre-update safety backup...")
            val db = com.example.data.database.IspDatabase.getDatabase(context)
            val repository = com.example.data.repository.IspRepository(
                db.customerDao(),
                db.packageDao(),
                db.billDao(),
                db.paymentDao(),
                db.settingsDao(),
                db.expenseDao(),
                db,
                context.applicationContext
            )
            repository.createAutomaticPreUpdateBackup(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create pre-update safety backup: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Trigger Android package installer for downloaded APK.
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                Toast.makeText(context, "APK file is invalid or missing", Toast.LENGTH_SHORT).show()
                return
            }

            val verification = verifyDownloadedApk(context, apkFile)
            if (verification.isFailure) {
                val msg = verification.exceptionOrNull()?.message ?: "APK verification failed"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(settingsIntent)
                Toast.makeText(context, "Please allow installation from this source", Toast.LENGTH_LONG).show()
                return
            }

            val intent = Intent(Intent.ACTION_VIEW)
            val apkUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }

            intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK

            val resInfoList = context.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(packageName, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to launch package installer", e)
            Toast.makeText(context, "Unable to launch installer: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
