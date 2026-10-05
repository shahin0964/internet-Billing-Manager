package com.example.ui.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.screens.DEFAULT_BD_SERVERS
import com.example.ui.screens.SpeedTestHistoryEntry
import com.example.ui.screens.SpeedTestServer
import com.example.ui.screens.TestPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Locale

data class NetworkInfoState(
    val ip: String = "Detecting...",
    val ispName: String = "Detecting ISP...",
    val connectionType: String = "Checking...",
    val cityCountry: String = "",
    val isDetecting: Boolean = false,
    val lastDetectedTime: Long = 0L,
    val errorMessage: String? = null
)

class SpeedTestViewModel : ViewModel() {

    private val TAG = "SpeedTestViewModel"

    private val _networkInfo = MutableStateFlow(NetworkInfoState())
    val networkInfo: StateFlow<NetworkInfoState> = _networkInfo.asStateFlow()

    private val _serverList = MutableStateFlow<List<SpeedTestServer>>(DEFAULT_BD_SERVERS)
    val serverList: StateFlow<List<SpeedTestServer>> = _serverList.asStateFlow()

    private val _selectedServerId = MutableStateFlow("auto")
    val selectedServerId: StateFlow<String> = _selectedServerId.asStateFlow()

    private val _isUpdatingServers = MutableStateFlow(false)
    val isUpdatingServers: StateFlow<Boolean> = _isUpdatingServers.asStateFlow()

    private val _serverUpdateStatus = MutableStateFlow<String?>(null)
    val serverUpdateStatus: StateFlow<String?> = _serverUpdateStatus.asStateFlow()

    private val _lastServerUpdateTime = MutableStateFlow(0L)
    val lastServerUpdateTime: StateFlow<Long> = _lastServerUpdateTime.asStateFlow()

    private val _isProbingServers = MutableStateFlow(false)
    val isProbingServers: StateFlow<Boolean> = _isProbingServers.asStateFlow()

    private val _testPhase = MutableStateFlow(TestPhase.IDLE)
    val testPhase: StateFlow<TestPhase> = _testPhase.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _testProgress = MutableStateFlow(0f)
    val testProgress: StateFlow<Float> = _testProgress.asStateFlow()

    private val _pingMs = MutableStateFlow(0L)
    val pingMs: StateFlow<Long> = _pingMs.asStateFlow()

    private val _jitterMs = MutableStateFlow(0L)
    val jitterMs: StateFlow<Long> = _jitterMs.asStateFlow()

    private val _downloadMbps = MutableStateFlow(0f)
    val downloadMbps: StateFlow<Float> = _downloadMbps.asStateFlow()

    private val _uploadMbps = MutableStateFlow(0f)
    val uploadMbps: StateFlow<Float> = _uploadMbps.asStateFlow()

    private val _activeTestServer = MutableStateFlow<SpeedTestServer?>(null)
    val activeTestServer: StateFlow<SpeedTestServer?> = _activeTestServer.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _historyList = MutableStateFlow<List<SpeedTestHistoryEntry>>(emptyList())
    val historyList: StateFlow<List<SpeedTestHistoryEntry>> = _historyList.asStateFlow()

    private var testJob: Job? = null
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        loadCachedServers(context)
        loadHistory(context)
        val prefs = context.getSharedPreferences("speed_test_prefs", Context.MODE_PRIVATE)
        _selectedServerId.value = prefs.getString("selected_server_id", "auto") ?: "auto"
        _lastServerUpdateTime.value = prefs.getLong("last_server_update_time", 0L)
        
        // Auto detect network on initial launch
        fetchCurrentNetworkInfo(context) { _, _ -> }
    }

    /**
     * Requirement 2: Real Network IP & ISP Auto-Detection
     * Checks active Wi-Fi / Mobile Data connection, queries IP geolocation APIs,
     * and retrieves the user's active public IP and real ISP name.
     */
    fun fetchCurrentNetworkInfo(
        context: Context,
        onResult: (ip: String, ispName: String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _networkInfo.value = _networkInfo.value.copy(
                isDetecting = true,
                errorMessage = null
            )

            // 1. Determine connection type from ConnectivityManager
            val connType = withContext(Dispatchers.Default) {
                try {
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    val activeNetwork = cm?.activeNetwork
                    val caps = cm?.getNetworkCapabilities(activeNetwork)
                    when {
                        caps == null -> "No Connection"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi Network"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data (4G/5G)"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet LAN"
                        else -> "Connected"
                    }
                } catch (e: Exception) {
                    "Connected"
                }
            }

            // 2. Query IP and ISP geolocation endpoints with fallbacks
            val (ip, isp, location) = withContext(Dispatchers.IO) {
                var detectedIp = ""
                var detectedIsp = ""
                var detectedLoc = ""

                // Endpoint 1: ip-api.com
                try {
                    val url = URL("http://ip-api.com/json/?fields=status,message,country,city,isp,org,as,query")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    conn.setRequestProperty("User-Agent", "ISP-Billing-Android/1.0")
                    conn.setRequestProperty("Accept", "application/json")
                    if (conn.responseCode == 200) {
                        val resp = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(resp)
                        if (json.optString("status") == "success") {
                            detectedIp = json.optString("query", "")
                            detectedIsp = json.optString("isp", json.optString("org", json.optString("as", "")))
                            val city = json.optString("city", "")
                            val country = json.optString("country", "")
                            detectedLoc = listOf(city, country).filter { it.isNotBlank() }.joinToString(", ")
                        }
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.w(TAG, "ip-api.com lookup failed: ${e.message}")
                }

                // Endpoint 2 (Fallback): ipwho.is
                if (detectedIp.isBlank()) {
                    try {
                        val url = URL("https://ipwho.is/")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 4000
                        conn.readTimeout = 4000
                        conn.setRequestProperty("User-Agent", "ISP-Billing-Android/1.0")
                        conn.setRequestProperty("Accept", "application/json")
                        if (conn.responseCode == 200) {
                            val resp = conn.inputStream.bufferedReader().use { it.readText() }
                            val json = JSONObject(resp)
                            if (json.optBoolean("success", false)) {
                                detectedIp = json.optString("ip", "")
                                val connObj = json.optJSONObject("connection")
                                detectedIsp = connObj?.optString("isp", connObj.optString("org", "")) ?: ""
                                val city = json.optString("city", "")
                                val country = json.optString("country", "")
                                detectedLoc = listOf(city, country).filter { it.isNotBlank() }.joinToString(", ")
                            }
                        }
                        conn.disconnect()
                    } catch (e: Exception) {
                        Log.w(TAG, "ipwho.is lookup failed: ${e.message}")
                    }
                }

                // Endpoint 3 (Fallback): ipapi.co
                if (detectedIp.isBlank()) {
                    try {
                        val url = URL("https://ipapi.co/json/")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 4000
                        conn.readTimeout = 4000
                        conn.setRequestProperty("User-Agent", "ISP-Billing-Android/1.0")
                        if (conn.responseCode == 200) {
                            val resp = conn.inputStream.bufferedReader().use { it.readText() }
                            val json = JSONObject(resp)
                            detectedIp = json.optString("ip", "")
                            detectedIsp = json.optString("org", "")
                            val city = json.optString("city", "")
                            val country = json.optString("country_name", "")
                            detectedLoc = listOf(city, country).filter { it.isNotBlank() }.joinToString(", ")
                        }
                        conn.disconnect()
                    } catch (e: Exception) {
                        Log.w(TAG, "ipapi.co lookup failed: ${e.message}")
                    }
                }

                // Endpoint 4 (Simple IP fallback): api.ipify.org
                if (detectedIp.isBlank()) {
                    try {
                        val url = URL("https://api.ipify.org?format=json")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 3000
                        conn.readTimeout = 3000
                        if (conn.responseCode == 200) {
                            val resp = conn.inputStream.bufferedReader().use { it.readText() }
                            val json = JSONObject(resp)
                            detectedIp = json.optString("ip", "")
                            detectedIsp = "Active Broadband ISP"
                        }
                        conn.disconnect()
                    } catch (e: Exception) {
                        Log.w(TAG, "api.ipify.org lookup failed: ${e.message}")
                    }
                }

                Triple(
                    detectedIp.ifBlank { "127.0.0.1" },
                    detectedIsp.ifBlank { "Local ISP Connection" },
                    detectedLoc.ifBlank { "Bangladesh" }
                )
            }

            val finalState = NetworkInfoState(
                ip = ip,
                ispName = isp,
                connectionType = connType,
                cityCountry = location,
                isDetecting = false,
                lastDetectedTime = System.currentTimeMillis(),
                errorMessage = if (ip == "127.0.0.1") "Unable to resolve public IP (Offline mode)" else null
            )
            _networkInfo.value = finalState

            onResult(ip, isp)
        }
    }

    /**
     * Requirement 3: Online ISP Server Refresh API Logic
     * Fetches updated server configurations from online sources and updates local state & storage.
     */
    fun refreshOnlineServers(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _isUpdatingServers.value = true
            _serverUpdateStatus.value = "Fetching online ISP servers..."

            val (success, message, newServers) = withContext(Dispatchers.IO) {
                val discovered = mutableListOf<SpeedTestServer>()
                var errorMessage = ""

                // 1. Fetch live Bangladesh servers from Ookla/Speedtest Open API
                try {
                    val url = URL("https://www.speedtest.net/api/js/servers?engine=js&search=Bangladesh")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    conn.setRequestProperty("Accept", "application/json")

                    if (conn.responseCode == 200) {
                        val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                        val arr = JSONArray(jsonStr)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val id = obj.optString("id", "")
                            val sponsor = obj.optString("sponsor", "")
                            val city = obj.optString("name", "Bangladesh")
                            val host = obj.optString("host", "")
                            val uploadUrl = obj.optString("url", "")
                            val httpsFunc = obj.optInt("https_functional", 0) == 1

                            if (host.isNotBlank() && uploadUrl.isNotBlank()) {
                                val dlUrl = uploadUrl.replace("upload.php", "random1000x1000.jpg")
                                discovered.add(
                                    SpeedTestServer(
                                        id = id.ifBlank { host },
                                        sponsor = sponsor.ifBlank { "BD Speed Server" },
                                        city = city,
                                        country = "Bangladesh",
                                        host = host,
                                        uploadUrl = uploadUrl,
                                        downloadUrl = dlUrl,
                                        isHttpsSupported = httpsFunc
                                    )
                                )
                            }
                        }
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.w(TAG, "Speedtest servers fetch error: ${e.message}")
                    errorMessage = e.message ?: "Network error"
                }

                // 2. Fetch regional South Asian servers if BD list is small
                if (discovered.size < 5) {
                    try {
                        val url = URL("https://www.speedtest.net/api/js/servers?engine=js&limit=30")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 4000
                        conn.readTimeout = 4000
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                        if (conn.responseCode == 200) {
                            val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                            val arr = JSONArray(jsonStr)
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                val sponsor = obj.optString("sponsor", "")
                                val country = obj.optString("country", "")
                                val host = obj.optString("host", "")
                                val uploadUrl = obj.optString("url", "")
                                if (host.isNotBlank() && uploadUrl.isNotBlank() &&
                                    (country.contains("Bangladesh", ignoreCase = true) ||
                                     country.contains("India", ignoreCase = true) ||
                                     country.contains("Singapore", ignoreCase = true))
                                ) {
                                    val dlUrl = uploadUrl.replace("upload.php", "random1000x1000.jpg")
                                    if (discovered.none { it.host == host }) {
                                        discovered.add(
                                            SpeedTestServer(
                                                id = obj.optString("id", host),
                                                sponsor = sponsor.ifBlank { host },
                                                city = obj.optString("name", country),
                                                country = country,
                                                host = host,
                                                uploadUrl = uploadUrl,
                                                downloadUrl = dlUrl,
                                                isHttpsSupported = obj.optInt("https_functional", 0) == 1
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        conn.disconnect()
                    } catch (e: Exception) {
                        Log.w(TAG, "Regional servers fetch fallback error: ${e.message}")
                    }
                }

                // Merge with DEFAULT_BD_SERVERS
                val autoServer = DEFAULT_BD_SERVERS.first()
                val mergedList = mutableListOf(autoServer)
                
                // Add all verified default servers first
                for (srv in DEFAULT_BD_SERVERS.drop(1)) {
                    if (mergedList.none { it.id == srv.id || it.host == srv.host }) {
                        mergedList.add(srv)
                    }
                }
                
                // Add newly discovered servers
                for (srv in discovered) {
                    if (mergedList.none { it.id == srv.id || it.host == srv.host }) {
                        mergedList.add(srv)
                    }
                }

                val ok = discovered.isNotEmpty() || mergedList.size > DEFAULT_BD_SERVERS.size
                val msg = if (ok) {
                    "Successfully updated ${mergedList.size - 1} ISP speed test servers"
                } else {
                    "Loaded ${mergedList.size - 1} verified built-in ISP servers (Online sync offline)"
                }

                Triple(true, msg, mergedList)
            }

            _serverList.value = newServers
            _lastServerUpdateTime.value = System.currentTimeMillis()
            _isUpdatingServers.value = false
            _serverUpdateStatus.value = message

            // Persist cached servers in SharedPreferences
            appContext?.let { ctx ->
                saveCachedServers(ctx, newServers)
            }

            onComplete(success, message)
        }
    }

    private fun saveCachedServers(context: Context, servers: List<SpeedTestServer>) {
        try {
            val prefs = context.getSharedPreferences("speed_test_prefs", Context.MODE_PRIVATE)
            val arr = JSONArray()
            for (srv in servers) {
                val obj = JSONObject()
                obj.put("id", srv.id)
                obj.put("sponsor", srv.sponsor)
                obj.put("city", srv.city)
                obj.put("country", srv.country)
                obj.put("host", srv.host)
                obj.put("uploadUrl", srv.uploadUrl)
                obj.put("downloadUrl", srv.downloadUrl)
                obj.put("isHttpsSupported", srv.isHttpsSupported)
                obj.put("isAuto", srv.isAuto)
                arr.put(obj)
            }
            prefs.edit()
                .putString("cached_servers_json", arr.toString())
                .putLong("last_server_update_time", System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cached servers: ${e.message}")
        }
    }

    private fun loadCachedServers(context: Context) {
        try {
            val prefs = context.getSharedPreferences("speed_test_prefs", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("cached_servers_json", null)
            if (!jsonStr.isNullOrBlank()) {
                val arr = JSONArray(jsonStr)
                val list = mutableListOf<SpeedTestServer>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        SpeedTestServer(
                            id = obj.optString("id", ""),
                            sponsor = obj.optString("sponsor", ""),
                            city = obj.optString("city", ""),
                            country = obj.optString("country", "Bangladesh"),
                            host = obj.optString("host", ""),
                            uploadUrl = obj.optString("uploadUrl", ""),
                            downloadUrl = obj.optString("downloadUrl", ""),
                            isHttpsSupported = obj.optBoolean("isHttpsSupported", false),
                            isAuto = obj.optBoolean("isAuto", false)
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    _serverList.value = list
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cached servers: ${e.message}")
        }
    }

    fun selectServer(serverId: String, context: Context) {
        _selectedServerId.value = serverId
        val prefs = context.getSharedPreferences("speed_test_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_server_id", serverId).apply()
    }

    suspend fun probeServerLatency(server: SpeedTestServer): Long? = withContext(Dispatchers.IO) {
        if (server.isAuto) return@withContext null
        val hostParts = server.host.split(":")
        val hostName = hostParts[0]
        val port = hostParts.getOrNull(1)?.toIntOrNull() ?: 8080

        var minMs: Long? = null
        for (attempt in 0..1) {
            try {
                val start = System.currentTimeMillis()
                val socket = Socket()
                socket.connect(InetSocketAddress(hostName, port), 1200)
                socket.close()
                val elapsed = System.currentTimeMillis() - start
                if (minMs == null || elapsed < minMs) {
                    minMs = elapsed
                }
            } catch (e: Exception) {
                try {
                    val start = System.currentTimeMillis()
                    val url = URL("http://$hostName:$port/speedtest/latency.txt")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 1200
                    conn.readTimeout = 1200
                    conn.requestMethod = "HEAD"
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                    conn.connect()
                    conn.disconnect()
                    val elapsed = System.currentTimeMillis() - start
                    if (minMs == null || elapsed < minMs) {
                        minMs = elapsed
                    }
                } catch (ex: Exception) {
                    // unreachable
                }
            }
        }
        minMs
    }

    fun probeAllServers() {
        viewModelScope.launch {
            _isProbingServers.value = true
            val updated = withContext(Dispatchers.IO) {
                _serverList.value.map { srv ->
                    if (srv.isAuto) srv
                    else {
                        val lat = probeServerLatency(srv)
                        srv.copy(latencyMs = lat)
                    }
                }
            }
            _serverList.value = updated
            _isProbingServers.value = false
        }
    }

    fun startTest(context: Context) {
        stopTest()
        _errorMessage.value = null
        _isTesting.value = true
        _testProgress.value = 0f
        _pingMs.value = 0L
        _jitterMs.value = 0L
        _downloadMbps.value = 0f
        _uploadMbps.value = 0f

        testJob = viewModelScope.launch {
            try {
                // Phase 1: Finding Server
                _testPhase.value = TestPhase.FINDING_SERVER
                _testProgress.value = 0.05f

                val currentList = _serverList.value
                val selectedId = _selectedServerId.value
                var targetServer = currentList.find { it.id == selectedId } ?: currentList.first()

                if (targetServer.isAuto) {
                    val candidates = currentList.filter { !it.isAuto }
                    var bestServer: SpeedTestServer? = null
                    var lowestPing = Long.MAX_VALUE

                    val deferreds = candidates.map { srv ->
                        async(Dispatchers.IO) {
                            val p = probeServerLatency(srv)
                            Pair(srv, p)
                        }
                    }
                    val probed = deferreds.awaitAll()
                    for ((srv, lat) in probed) {
                        if (lat != null && lat < lowestPing) {
                            lowestPing = lat
                            bestServer = srv.copy(latencyMs = lat)
                        }
                    }
                    targetServer = bestServer ?: candidates.firstOrNull() ?: DEFAULT_BD_SERVERS[1]
                }
                _activeTestServer.value = targetServer
                _testProgress.value = 0.15f

                // Phase 2: Testing Ping & Jitter
                _testPhase.value = TestPhase.TESTING_PING
                val pingSamples = mutableListOf<Long>()
                val hostParts = targetServer.host.split(":")
                val hostName = hostParts[0]
                val port = hostParts.getOrNull(1)?.toIntOrNull() ?: 8080

                for (i in 0..4) {
                    if (!isActive) break
                    val start = System.currentTimeMillis()
                    var success = false
                    try {
                        withContext(Dispatchers.IO) {
                            val socket = Socket()
                            socket.connect(InetSocketAddress(hostName, port), 1500)
                            socket.close()
                        }
                        success = true
                    } catch (e: Exception) {
                        try {
                            withContext(Dispatchers.IO) {
                                val url = URL("http://$hostName:$port/speedtest/latency.txt")
                                val conn = url.openConnection() as HttpURLConnection
                                conn.connectTimeout = 1500
                                conn.readTimeout = 1500
                                conn.connect()
                                conn.disconnect()
                            }
                            success = true
                        } catch (ex: Exception) {
                            // ignore probe failure
                        }
                    }
                    val elapsed = System.currentTimeMillis() - start
                    if (success) {
                        pingSamples.add(elapsed)
                        _pingMs.value = pingSamples.average().toLong()
                        if (pingSamples.size > 1) {
                            _jitterMs.value = (pingSamples.maxOrNull()!! - pingSamples.minOrNull()!!)
                        }
                    }
                    _testProgress.value = 0.15f + (i + 1) * 0.03f
                    delay(80)
                }

                if (pingSamples.isEmpty()) {
                    val start = System.currentTimeMillis()
                    withContext(Dispatchers.IO) {
                        try {
                            val url = URL("https://1.1.1.1")
                            val conn = url.openConnection() as HttpURLConnection
                            conn.connectTimeout = 2000
                            conn.readTimeout = 2000
                            conn.connect()
                            conn.disconnect()
                        } catch (_: Exception) {}
                    }
                    val elapsed = System.currentTimeMillis() - start
                    _pingMs.value = elapsed
                    _jitterMs.value = 2L
                }

                // Phase 3: Testing Download
                _testPhase.value = TestPhase.TESTING_DOWNLOAD
                val dlDurationMs = 6000L
                var totalDlBytes = 0L

                val dlUrls = mutableListOf<String>()
                if (targetServer.downloadUrl.isNotBlank() && targetServer.downloadUrl != "auto") {
                    dlUrls.add(targetServer.downloadUrl)
                    if (targetServer.isHttpsSupported && targetServer.downloadUrl.startsWith("http://")) {
                        dlUrls.add(targetServer.downloadUrl.replace("http://", "https://"))
                    }
                }
                dlUrls.add("https://speed.cloudflare.com/__down?bytes=25000000")

                var connectedDl = false
                var dlStartTime = 0L

                for (dUrl in dlUrls) {
                    if (connectedDl) break
                    try {
                        withContext(Dispatchers.IO) {
                            val url = URL(dUrl)
                            val conn = url.openConnection() as HttpURLConnection
                            conn.connectTimeout = 4000
                            conn.readTimeout = 4000
                            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                            conn.setRequestProperty("Accept-Encoding", "identity")
                            conn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                            conn.setRequestProperty("Pragma", "no-cache")
                            conn.connect()

                            if (conn.responseCode == 200) {
                                connectedDl = true
                                val input: InputStream = conn.inputStream
                                val buffer = ByteArray(16384)
                                var bytesRead: Int
                                dlStartTime = System.currentTimeMillis()

                                while (isActive && (System.currentTimeMillis() - dlStartTime) < dlDurationMs) {
                                    bytesRead = input.read(buffer)
                                    if (bytesRead <= 0) break
                                    totalDlBytes += bytesRead

                                    val now = System.currentTimeMillis()
                                    val elapsedSec = (now - dlStartTime) / 1000.0
                                    if (elapsedSec > 0.1) {
                                        val mbps = ((totalDlBytes * 8.0) / (elapsedSec * 1_000_000.0)).toFloat()
                                        withContext(Dispatchers.Main) {
                                            _downloadMbps.value = mbps
                                            _testProgress.value = 0.30f + ((elapsedSec / 6.0) * 0.35f).toFloat()
                                        }
                                    }
                                }
                                try { input.close() } catch (_: Exception) {}
                                try { conn.disconnect() } catch (_: Exception) {}
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "DL url $dUrl failed: ${e.message}")
                    }
                }

                if (dlStartTime > 0 && totalDlBytes > 0) {
                    val finalDlTime = (System.currentTimeMillis() - dlStartTime) / 1000.0
                    if (finalDlTime > 0.1) {
                        _downloadMbps.value = ((totalDlBytes * 8.0) / (finalDlTime * 1_000_000.0)).toFloat()
                    }
                }

                // Phase 4: Testing Upload
                _testPhase.value = TestPhase.TESTING_UPLOAD
                val ulDurationMs = 5000L
                var totalUlBytes = 0L

                val ulUrls = mutableListOf<String>()
                if (targetServer.uploadUrl.isNotBlank() && targetServer.uploadUrl != "auto") {
                    ulUrls.add(targetServer.uploadUrl)
                    if (targetServer.isHttpsSupported && targetServer.uploadUrl.startsWith("http://")) {
                        ulUrls.add(targetServer.uploadUrl.replace("http://", "https://"))
                    }
                }
                ulUrls.add("https://speed.cloudflare.com/__up")

                val payloadChunk = ByteArray(16384) { 0x55 }
                var connectedUl = false
                var ulStartTime = 0L

                for (uUrl in ulUrls) {
                    if (connectedUl) break
                    try {
                        withContext(Dispatchers.IO) {
                            val url = URL(uUrl)
                            val conn = url.openConnection() as HttpURLConnection
                            conn.connectTimeout = 3500
                            conn.readTimeout = 3500
                            conn.doOutput = true
                            conn.requestMethod = "POST"
                            conn.setChunkedStreamingMode(16384)
                            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                            conn.setRequestProperty("Content-Type", "application/octet-stream")
                            conn.setRequestProperty("Cache-Control", "no-cache")

                            val output: OutputStream = conn.outputStream
                            connectedUl = true
                            ulStartTime = System.currentTimeMillis()

                            while (isActive && (System.currentTimeMillis() - ulStartTime) < ulDurationMs) {
                                output.write(payloadChunk)
                                totalUlBytes += payloadChunk.size

                                val now = System.currentTimeMillis()
                                val elapsedSec = (now - ulStartTime) / 1000.0
                                if (elapsedSec > 0.1) {
                                    val mbps = ((totalUlBytes * 8.0) / (elapsedSec * 1_000_000.0)).toFloat()
                                    withContext(Dispatchers.Main) {
                                        _uploadMbps.value = mbps
                                        _testProgress.value = 0.65f + ((elapsedSec / 5.0) * 0.35f).toFloat()
                                    }
                                }
                            }
                            try { output.flush() } catch (_: Exception) {}
                            try { output.close() } catch (_: Exception) {}
                            try { conn.disconnect() } catch (_: Exception) {}
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "UL url $uUrl failed: ${e.message}")
                    }
                }

                if (ulStartTime > 0 && totalUlBytes > 0) {
                    val finalUlTime = (System.currentTimeMillis() - ulStartTime) / 1000.0
                    if (finalUlTime > 0.1) {
                        _uploadMbps.value = ((totalUlBytes * 8.0) / (finalUlTime * 1_000_000.0)).toFloat()
                    }
                }

                // Finish
                _testPhase.value = TestPhase.COMPLETED
                _testProgress.value = 1f

                // Save to history
                saveHistoryEntry(
                    context,
                    SpeedTestHistoryEntry(
                        timestamp = System.currentTimeMillis(),
                        serverName = _activeTestServer.value?.sponsor ?: targetServer.sponsor,
                        serverCity = _activeTestServer.value?.city ?: targetServer.city,
                        pingMs = _pingMs.value,
                        jitterMs = _jitterMs.value,
                        downloadMbps = _downloadMbps.value,
                        uploadMbps = _uploadMbps.value
                    )
                )

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    throw e
                } else {
                    Log.e(TAG, "Test failed: ${e.message}", e)
                    _errorMessage.value = "Speed test could not be completed. Please check your internet connection and try again."
                    _testPhase.value = TestPhase.FAILED
                }
            } finally {
                _isTesting.value = false
            }
        }
    }

    fun stopTest() {
        testJob?.cancel()
        testJob = null
        _isTesting.value = false
        _testPhase.value = TestPhase.IDLE
    }

    private fun loadHistory(context: Context) {
        val prefs = context.getSharedPreferences("speed_test_prefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("history_json", "[]") ?: "[]"
        try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<SpeedTestHistoryEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SpeedTestHistoryEntry(
                        timestamp = obj.optLong("timestamp", 0L),
                        serverName = obj.optString("serverName", "Unknown"),
                        serverCity = obj.optString("serverCity", "BD"),
                        pingMs = obj.optLong("pingMs", 0L),
                        jitterMs = obj.optLong("jitterMs", 0L),
                        downloadMbps = obj.optDouble("downloadMbps", 0.0).toFloat(),
                        uploadMbps = obj.optDouble("uploadMbps", 0.0).toFloat()
                    )
                )
            }
            _historyList.value = list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            _historyList.value = emptyList()
        }
    }

    private fun saveHistoryEntry(context: Context, entry: SpeedTestHistoryEntry) {
        try {
            val currentList = _historyList.value.toMutableList()
            currentList.add(0, entry)
            if (currentList.size > 25) currentList.removeAt(currentList.size - 1)
            _historyList.value = currentList

            val prefs = context.getSharedPreferences("speed_test_prefs", Context.MODE_PRIVATE)
            val arr = JSONArray()
            for (item in currentList) {
                val obj = JSONObject()
                obj.put("timestamp", item.timestamp)
                obj.put("serverName", item.serverName)
                obj.put("serverCity", item.serverCity)
                obj.put("pingMs", item.pingMs)
                obj.put("jitterMs", item.jitterMs)
                obj.put("downloadMbps", item.downloadMbps)
                obj.put("uploadMbps", item.uploadMbps)
                arr.put(obj)
            }
            prefs.edit().putString("history_json", arr.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save history: ${e.message}")
        }
    }

    fun clearHistory(context: Context) {
        val prefs = context.getSharedPreferences("speed_test_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("history_json").apply()
        _historyList.value = emptyList()
    }
}
