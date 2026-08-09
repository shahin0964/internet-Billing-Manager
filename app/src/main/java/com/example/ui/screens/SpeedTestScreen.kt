package com.example.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SpeedTestServer(
    val id: String,
    val sponsor: String,
    val city: String,
    val country: String = "Bangladesh",
    val host: String,
    val uploadUrl: String,
    val downloadUrl: String,
    val latencyMs: Long? = null,
    val isHttpsSupported: Boolean = false,
    val isAuto: Boolean = false
)

data class SpeedTestHistoryEntry(
    val timestamp: Long,
    val serverName: String,
    val serverCity: String,
    val pingMs: Long,
    val jitterMs: Long,
    val downloadMbps: Float,
    val uploadMbps: Float
)

enum class TestPhase {
    IDLE,
    FINDING_SERVER,
    TESTING_PING,
    TESTING_DOWNLOAD,
    TESTING_UPLOAD,
    COMPLETED,
    FAILED
}

// Default verified Bangladesh Speed Test Servers (Ookla BD Network & Fallbacks)
val DEFAULT_BD_SERVERS = listOf(
    SpeedTestServer(
        id = "auto",
        sponsor = "Best Server (Auto)",
        city = "Bangladesh",
        host = "auto",
        uploadUrl = "auto",
        downloadUrl = "auto",
        isAuto = true
    ),
    SpeedTestServer(
        id = "32227",
        sponsor = "Alpha Networks Limited",
        city = "Chattogram",
        host = "speedtest.alphanetwork.com.bd:8080",
        uploadUrl = "http://speedtest.alphanetwork.com.bd:8080/speedtest/upload.php",
        downloadUrl = "http://speedtest.alphanetwork.com.bd:8080/speedtest/random1000x1000.jpg",
        isHttpsSupported = true
    ),
    SpeedTestServer(
        id = "AmberIT",
        sponsor = "AmberIT Ltd",
        city = "Dhaka",
        host = "speedtest.amberit.com.bd:8080",
        uploadUrl = "http://speedtest.amberit.com.bd:8080/speedtest/upload.php",
        downloadUrl = "http://speedtest.amberit.com.bd:8080/speedtest/random1000x1000.jpg",
        isHttpsSupported = true
    ),
    SpeedTestServer(
        id = "55670",
        sponsor = "CNCBD",
        city = "Chittagong",
        host = "speedtest.cncbd.info:8080",
        uploadUrl = "http://speedtest.cncbd.info:8080/speedtest/upload.php",
        downloadUrl = "http://speedtest.cncbd.info:8080/speedtest/random1000x1000.jpg",
        isHttpsSupported = true
    ),
    SpeedTestServer(
        id = "34040",
        sponsor = "BDconnect",
        city = "Chattogram",
        host = "speedtest.bdconnectctg.net:8080",
        uploadUrl = "http://speedtest.bdconnectctg.net:8080/speedtest/upload.php",
        downloadUrl = "http://speedtest.bdconnectctg.net:8080/speedtest/random1000x1000.jpg",
        isHttpsSupported = true
    ),
    SpeedTestServer(
        id = "35480",
        sponsor = "Mux Technologies",
        city = "Chattogram",
        host = "sp1.muxtechnologies.net:8080",
        uploadUrl = "http://sp1.muxtechnologies.net:8080/speedtest/upload.php",
        downloadUrl = "http://sp1.muxtechnologies.net:8080/speedtest/random1000x1000.jpg",
        isHttpsSupported = true
    ),
    SpeedTestServer(
        id = "71483",
        sponsor = "Summit Communications Ltd",
        city = "Chattogram",
        host = "speedtest.ctg.summitiig.net:8080",
        uploadUrl = "http://speedtest.ctg.summitiig.net:8080/speedtest/upload.php",
        downloadUrl = "http://speedtest.ctg.summitiig.net:8080/speedtest/random1000x1000.jpg"
    ),
    SpeedTestServer(
        id = "44366",
        sponsor = "AMR NET",
        city = "Chattogram",
        host = "speedtest.amrnetbd.com:8080",
        uploadUrl = "http://speedtest.amrnetbd.com:8080/speedtest/upload.php",
        downloadUrl = "http://speedtest.amrnetbd.com:8080/speedtest/random1000x1000.jpg",
        isHttpsSupported = true
    ),
    SpeedTestServer(
        id = "53118",
        sponsor = "Connect-3",
        city = "Chattogram",
        host = "ns3.connect3.net.bd:8080",
        uploadUrl = "http://ns3.connect3.net.bd:8080/speedtest/upload.php",
        downloadUrl = "http://ns3.connect3.net.bd:8080/speedtest/random1000x1000.jpg",
        isHttpsSupported = true
    ),
    SpeedTestServer(
        id = "68492",
        sponsor = "TS_Network",
        city = "Chattogram",
        host = "speedtest.tsnetwork.net.bd:8080",
        uploadUrl = "http://speedtest.tsnetwork.net.bd:8080/speedtest/upload.php",
        downloadUrl = "http://speedtest.tsnetwork.net.bd:8080/speedtest/random1000x1000.jpg"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedTestScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("speed_test_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var serverList by remember { mutableStateOf(DEFAULT_BD_SERVERS) }
    var selectedServerId by remember { mutableStateOf(prefs.getString("selected_server_id", "auto") ?: "auto") }
    
    val selectedServer = remember(selectedServerId, serverList) {
        serverList.find { it.id == selectedServerId } ?: serverList.first()
    }

    var showServerSelector by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isProbingServers by remember { mutableStateOf(false) }

    var testPhase by remember { mutableStateOf(TestPhase.IDLE) }
    var isTesting by remember { mutableStateOf(false) }
    var testProgress by remember { mutableFloatStateOf(0f) }

    var pingMs by remember { mutableLongStateOf(0L) }
    var jitterMs by remember { mutableLongStateOf(0L) }
    var downloadMbps by remember { mutableFloatStateOf(0f) }
    var uploadMbps by remember { mutableFloatStateOf(0f) }

    var activeTestServer by remember { mutableStateOf<SpeedTestServer?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var historyList by remember { mutableStateOf<List<SpeedTestHistoryEntry>>(emptyList()) }
    var testJob by remember { mutableStateOf<Job?>(null) }

    // Load History
    fun loadHistory() {
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
            historyList = list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            historyList = emptyList()
        }
    }

    fun saveHistoryEntry(entry: SpeedTestHistoryEntry) {
        try {
            val currentList = historyList.toMutableList()
            currentList.add(0, entry)
            if (currentList.size > 20) currentList.removeAt(currentList.size - 1)
            historyList = currentList

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
            Log.e("SpeedTest", "Failed to save history: ${e.message}")
        }
    }

    fun clearHistory() {
        prefs.edit().remove("history_json").apply()
        historyList = emptyList()
    }

    fun stopTest() {
        testJob?.cancel()
        testJob = null
        isTesting = false
        testPhase = TestPhase.IDLE
    }

    DisposableEffect(Unit) {
        onDispose {
            stopTest()
        }
    }

    LaunchedEffect(Unit) {
        loadHistory()
        // Discover verified servers online
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://www.speedtest.net/api/js/servers?engine=js&search=Bangladesh")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                conn.setRequestProperty("Accept", "application/json")

                if (conn.responseCode == 200) {
                    val stream = conn.inputStream
                    val jsonStr = stream.bufferedReader().use { it.readText() }
                    conn.disconnect()

                    val arr = JSONArray(jsonStr)
                    val discovered = mutableListOf<SpeedTestServer>()
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
                                    host = host,
                                    uploadUrl = uploadUrl,
                                    downloadUrl = dlUrl,
                                    isHttpsSupported = httpsFunc
                                )
                            )
                        }
                    }

                    if (discovered.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            val autoServer = DEFAULT_BD_SERVERS.first()
                            val combined = mutableListOf(autoServer)
                            for (d in discovered) {
                                if (combined.none { it.id == d.id }) {
                                    combined.add(d)
                                }
                            }
                            serverList = combined
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("SpeedTest", "Failed to fetch live server list: ${e.message}")
            }
        }
    }

    // Measure latency to a server via TCP Socket probe
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
                // Try HTTP HEAD request
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
        scope.launch {
            isProbingServers = true
            val updated = withContext(Dispatchers.IO) {
                serverList.map { srv ->
                    if (srv.isAuto) srv
                    else {
                        val lat = probeServerLatency(srv)
                        srv.copy(latencyMs = lat)
                    }
                }
            }
            serverList = updated
            isProbingServers = false
        }
    }

    fun startTest() {
        stopTest()
        errorMessage = null
        isTesting = true
        testProgress = 0f
        pingMs = 0L
        jitterMs = 0L
        downloadMbps = 0f
        uploadMbps = 0f

        testJob = scope.launch {
            try {
                // Phase 1: Finding Server
                testPhase = TestPhase.FINDING_SERVER
                testProgress = 0.05f

                var targetServer = selectedServer
                if (targetServer.isAuto) {
                    val candidates = serverList.filter { !it.isAuto }
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
                activeTestServer = targetServer
                testProgress = 0.15f

                // Phase 2: Testing Ping & Jitter
                testPhase = TestPhase.TESTING_PING
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
                        pingMs = pingSamples.average().toLong()
                        if (pingSamples.size > 1) {
                            jitterMs = (pingSamples.maxOrNull()!! - pingSamples.minOrNull()!!)
                        }
                    }
                    testProgress = 0.15f + (i + 1) * 0.03f
                    delay(80)
                }

                if (pingSamples.isEmpty()) {
                    // Fallback to Cloudflare edge ping
                    val start = System.currentTimeMillis()
                    withContext(Dispatchers.IO) {
                        val url = URL("https://1.1.1.1")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 2000
                        conn.readTimeout = 2000
                        conn.connect()
                        conn.disconnect()
                    }
                    val elapsed = System.currentTimeMillis() - start
                    pingMs = elapsed
                    jitterMs = 2L
                }

                // Phase 3: Testing Download
                testPhase = TestPhase.TESTING_DOWNLOAD
                val dlDurationMs = 6000L
                var totalDlBytes = 0L

                val dlUrls = mutableListOf<String>()
                if (targetServer.downloadUrl.isNotBlank() && targetServer.downloadUrl != "auto") {
                    dlUrls.add(targetServer.downloadUrl)
                    if (targetServer.isHttpsSupported && targetServer.downloadUrl.startsWith("http://")) {
                        dlUrls.add(targetServer.downloadUrl.replace("http://", "https://"))
                    }
                }
                // High performance HTTPS fallback endpoints
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
                            conn.setRequestProperty("Accept-Encoding", "identity") // Disable GZIP compression for accurate byte count
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
                                            downloadMbps = mbps
                                            testProgress = 0.30f + ((elapsedSec / 6.0) * 0.35f).toFloat()
                                        }
                                    }
                                }
                                try { input.close() } catch (_: Exception) {}
                                try { conn.disconnect() } catch (_: Exception) {}
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("SpeedTest", "DL url $dUrl failed: ${e.message}")
                    }
                }

                if (dlStartTime > 0 && totalDlBytes > 0) {
                    val finalDlTime = (System.currentTimeMillis() - dlStartTime) / 1000.0
                    if (finalDlTime > 0.1) {
                        downloadMbps = ((totalDlBytes * 8.0) / (finalDlTime * 1_000_000.0)).toFloat()
                    }
                }

                // Phase 4: Testing Upload
                testPhase = TestPhase.TESTING_UPLOAD
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
                            // CRITICAL: Chunked streaming mode prevents in-memory byte buffering
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
                                        uploadMbps = mbps
                                        testProgress = 0.65f + ((elapsedSec / 5.0) * 0.35f).toFloat()
                                    }
                                }
                            }
                            try { output.flush() } catch (_: Exception) {}
                            try { output.close() } catch (_: Exception) {}
                            try { conn.disconnect() } catch (_: Exception) {}
                        }
                    } catch (e: Exception) {
                        Log.w("SpeedTest", "UL url $uUrl failed: ${e.message}")
                    }
                }

                if (ulStartTime > 0 && totalUlBytes > 0) {
                    val finalUlTime = (System.currentTimeMillis() - ulStartTime) / 1000.0
                    if (finalUlTime > 0.1) {
                        uploadMbps = ((totalUlBytes * 8.0) / (finalUlTime * 1_000_000.0)).toFloat()
                    }
                }

                // Finish
                testPhase = TestPhase.COMPLETED
                testProgress = 1f

                // Save to history
                saveHistoryEntry(
                    SpeedTestHistoryEntry(
                        timestamp = System.currentTimeMillis(),
                        serverName = activeTestServer?.sponsor ?: targetServer.sponsor,
                        serverCity = activeTestServer?.city ?: targetServer.city,
                        pingMs = pingMs,
                        jitterMs = jitterMs,
                        downloadMbps = downloadMbps,
                        uploadMbps = uploadMbps
                    )
                )

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    throw e
                } else {
                    Log.e("SpeedTest", "Test failed: ${e.message}", e)
                    errorMessage = "Speed test could not be completed. Please check your internet connection and try again."
                    testPhase = TestPhase.FAILED
                }
            } finally {
                isTesting = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "⚡ Speed Test",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showServerSelector = true }) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = "Select Server",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Server Selector Card
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showServerSelector = true },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    ),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Selected Server",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = selectedServer.sponsor,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (selectedServer.isAuto) "Automatic Server Discovery (BD)" else "${selectedServer.city} • ${selectedServer.host}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Button(
                            onClick = { showServerSelector = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("Change", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Speed Gauge & Real-Time Test Display Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Display Phase Banner
                        val phaseText = when (testPhase) {
                            TestPhase.IDLE -> "Ready to test"
                            TestPhase.FINDING_SERVER -> "Finding lowest-latency server..."
                            TestPhase.TESTING_PING -> "Testing Ping & Jitter..."
                            TestPhase.TESTING_DOWNLOAD -> "Testing Download Speed..."
                            TestPhase.TESTING_UPLOAD -> "Testing Upload Speed..."
                            TestPhase.COMPLETED -> "Test Complete!"
                            TestPhase.FAILED -> "Test Failed"
                        }

                        Text(
                            text = phaseText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (testPhase == TestPhase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )

                        // Big Speed Indicator Circle
                        val displaySpeed = when (testPhase) {
                            TestPhase.TESTING_UPLOAD -> uploadMbps
                            else -> downloadMbps
                        }
                        val speedLabel = when (testPhase) {
                            TestPhase.TESTING_UPLOAD -> "UPLOAD Mbps"
                            else -> "DOWNLOAD Mbps"
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(180.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            MaterialTheme.colorScheme.surface
                                        )
                                    )
                                )
                                .border(
                                    width = 4.dp,
                                    brush = Brush.sweepGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary,
                                            MaterialTheme.colorScheme.primary
                                        )
                                    ),
                                    shape = CircleShape
                                )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = String.format(Locale.US, "%.1f", displaySpeed),
                                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 38.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = speedLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isTesting) {
                            LinearProgressIndicator(
                                progress = { testProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }

                        // Metrics Cards Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricBox(
                                label = "PING",
                                value = if (pingMs > 0) "$pingMs ms" else "--",
                                icon = Icons.Default.Speed,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            MetricBox(
                                label = "JITTER",
                                value = if (jitterMs > 0) "$jitterMs ms" else "--",
                                icon = Icons.Default.NetworkCheck,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricBox(
                                label = "DOWNLOAD",
                                value = String.format(Locale.US, "%.1f Mbps", downloadMbps),
                                icon = Icons.Default.ArrowDownward,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            MetricBox(
                                label = "UPLOAD",
                                value = String.format(Locale.US, "%.1f Mbps", uploadMbps),
                                icon = Icons.Default.ArrowUpward,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (errorMessage != null) {
                            Text(
                                text = "⚠️ $errorMessage",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Start / Stop Button
                        if (isTesting) {
                            Button(
                                onClick = { stopTest() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Stop Speed Test", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { startTest() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Start Speed Test", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Test History Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📜 Test History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (historyList.isNotEmpty()) {
                        TextButton(onClick = { clearHistory() }) {
                            Text("Clear", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Test History List
            if (historyList.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "No speed test history yet. Start a test above!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(historyList) { item ->
                    val dateStr = remember(item.timestamp) {
                        SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.US).format(Date(item.timestamp))
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.serverName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$dateStr • ${item.serverCity}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "DL: ${String.format(Locale.US, "%.1f", item.downloadMbps)} M",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "UL: ${String.format(Locale.US, "%.1f", item.uploadMbps)} M",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${item.pingMs} ms",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Ping",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Server Selector Bottom Sheet / Dialog
    if (showServerSelector) {
        ModalBottomSheet(
            onDismissRequest = { showServerSelector = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🌐 Select Speed Test Server",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = { probeAllServers() },
                        enabled = !isProbingServers
                    ) {
                        if (isProbingServers) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing Ping...")
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test All Ping")
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by ISP, City, or Host...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                val filteredServers = remember(serverList, searchQuery) {
                    if (searchQuery.isBlank()) serverList
                    else {
                        serverList.filter { s ->
                            s.isAuto ||
                            s.sponsor.contains(searchQuery, ignoreCase = true) ||
                            s.city.contains(searchQuery, ignoreCase = true) ||
                            s.host.contains(searchQuery, ignoreCase = true)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredServers) { srv ->
                        val isSelected = srv.id == selectedServerId
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedServerId = srv.id
                                    prefs.edit().putString("selected_server_id", srv.id).apply()
                                    showServerSelector = false
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(14.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            selectedServerId = srv.id
                                            prefs.edit().putString("selected_server_id", srv.id).apply()
                                            showServerSelector = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = srv.sponsor,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (srv.isAuto) "Selects lowest latency server automatically"
                                                   else "${srv.city} • ${srv.host}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (!srv.isAuto) {
                                    if (srv.latencyMs != null) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (srv.latencyMs < 100) Color(0xFF4CAF50).copy(alpha = 0.2f)
                                                    else if (srv.latencyMs < 200) Color(0xFFFF9800).copy(alpha = 0.2f)
                                                    else MaterialTheme.colorScheme.errorContainer
                                        ) {
                                            Text(
                                                text = "${srv.latencyMs} ms",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (srv.latencyMs < 100) Color(0xFF2E7D32)
                                                        else if (srv.latencyMs < 200) Color(0xFFE65100)
                                                        else MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "BD",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun MetricBox(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
