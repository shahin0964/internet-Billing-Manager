package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Locale
import java.util.UUID

data class HostGeoInfo(
    val host: String = "",
    val ip: String = "",
    val countryCode: String = "",
    val countryName: String = "",
    val city: String = "",
    val flagEmoji: String = "🌐",
    val ispOrOrg: String = "",
    val isLocal: Boolean = false
)

data class PingResult(
    val seq: Int,
    val bytes: Int,
    val host: String,
    val ip: String,
    val ttl: Int,
    val timeMs: Float,
    val flagEmoji: String = "🌐",
    val error: String? = null
)

data class PingStats(
    val transmitted: Int = 0,
    val received: Int = 0,
    val lossPercent: Float = 0f,
    val minMs: Float = 0f,
    val maxMs: Float = 0f,
    val avgMs: Float = 0f
)

data class PingSettings(
    val ipVersion: String = "Auto",
    val count: String = "4",
    val packetSize: String = "56",
    val ttl: String = "",
    val interval: String = "1",
    val timeout: String = "3",
    val overallTimeout: String = "",
    val resolveHostname: Boolean = true
)

data class PingBookmark(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val flagEmoji: String = "🌐"
)

val DEFAULT_PING_BOOKMARKS = listOf(
    PingBookmark(id = "1", name = "Google DNS", host = "8.8.8.8", flagEmoji = "🇺🇸"),
    PingBookmark(id = "2", name = "Cloudflare", host = "1.1.1.1", flagEmoji = "🇺🇸"),
    PingBookmark(id = "3", name = "Router Gateway", host = "192.168.1.1", flagEmoji = "🏠"),
    PingBookmark(id = "4", name = "BDIX Core", host = "bdix.net", flagEmoji = "🇧🇩"),
    PingBookmark(id = "5", name = "Google Web", host = "google.com", flagEmoji = "🇺🇸")
)

fun countryCodeToFlagEmoji(countryCode: String?): String {
    if (countryCode.isNullOrBlank() || countryCode.length != 2) return "🌐"
    return try {
        val code = countryCode.uppercase(Locale.US)
        val firstChar = Character.codePointAt(code, 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(code, 1) - 0x41 + 0x1F1E6
        String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    } catch (_: Exception) {
        "🌐"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingTestScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ping_settings", Context.MODE_PRIVATE) }

    var host by remember { mutableStateOf(prefs.getString("last_host", "8.8.8.8") ?: "8.8.8.8") }
    var results by remember { mutableStateOf<List<PingResult>>(emptyList()) }
    var rawLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var stats by remember { mutableStateOf<PingStats?>(null) }
    var hostGeoInfo by remember { mutableStateOf<HostGeoInfo?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    // Bookmark Dialogs State
    var showSaveBookmarkDialog by remember { mutableStateOf(false) }
    var showManageBookmarksModal by remember { mutableStateOf(false) }
    var bookmarksList by remember { mutableStateOf<List<PingBookmark>>(emptyList()) }

    // Load Bookmarks
    fun loadBookmarks() {
        val jsonStr = prefs.getString("ping_bookmarks_json", null)
        if (jsonStr.isNullOrBlank()) {
            bookmarksList = DEFAULT_PING_BOOKMARKS
        } else {
            try {
                val arr = JSONArray(jsonStr)
                val list = mutableListOf<PingBookmark>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        PingBookmark(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            name = obj.optString("name", "Target"),
                            host = obj.optString("host", ""),
                            flagEmoji = obj.optString("flagEmoji", "🌐")
                        )
                    )
                }
                bookmarksList = if (list.isEmpty()) DEFAULT_PING_BOOKMARKS else list
            } catch (_: Exception) {
                bookmarksList = DEFAULT_PING_BOOKMARKS
            }
        }
    }

    fun saveBookmarks(list: List<PingBookmark>) {
        bookmarksList = list
        try {
            val arr = JSONArray()
            for (b in list) {
                val obj = JSONObject()
                obj.put("id", b.id)
                obj.put("name", b.name)
                obj.put("host", b.host)
                obj.put("flagEmoji", b.flagEmoji)
                arr.put(obj)
            }
            prefs.edit().putString("ping_bookmarks_json", arr.toString()).apply()
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) {
        loadBookmarks()
    }

    var settings by remember {
        mutableStateOf(
            PingSettings(
                ipVersion = prefs.getString("ipVersion", "Auto") ?: "Auto",
                count = prefs.getString("count", "4") ?: "4",
                packetSize = prefs.getString("packetSize", "56") ?: "56",
                ttl = prefs.getString("ttl", "") ?: "",
                interval = prefs.getString("interval", "1") ?: "1",
                timeout = prefs.getString("timeout", "3") ?: "3",
                overallTimeout = prefs.getString("overallTimeout", "") ?: "",
                resolveHostname = prefs.getBoolean("resolveHostname", true)
            )
        )
    }

    val coroutineScope = rememberCoroutineScope()
    var pingJob by remember { mutableStateOf<Job?>(null) }

    val regex = Regex("""(\d+)\s+bytes from\s+(\S+?)(?:\s+\(([^)]+)\))?:.*?icmp_seq=(\d+).*?ttl=(\d+).*?time=([\d.]+)\s*ms""")

    fun stopPing() {
        pingJob?.cancel()
        isTesting = false
    }

    fun startPing(targetHost: String = host) {
        val cleanHost = targetHost.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore("/")
        if (cleanHost.isBlank()) return

        host = cleanHost
        prefs.edit().putString("last_host", cleanHost).apply()

        results = emptyList()
        rawLogs = listOf("PING $cleanHost (${settings.packetSize} bytes of data)...")
        stats = null
        hostGeoInfo = null
        errorMessage = null
        isTesting = true

        pingJob = coroutineScope.launch(Dispatchers.IO) {
            var sent = 0
            var received = 0
            val times = mutableListOf<Float>()
            var executedViaProcess = false

            // 1. Resolve IP and Geo/Flag Info Asynchronously
            var resolvedIp = cleanHost
            var detectedFlag = "🌐"
            var geo = HostGeoInfo(host = cleanHost, ip = cleanHost)

            try {
                val inetAddr = InetAddress.getByName(cleanHost)
                resolvedIp = inetAddr.hostAddress ?: cleanHost

                val isLocal = inetAddr.isLoopbackAddress || inetAddr.isSiteLocalAddress ||
                        cleanHost.startsWith("192.168.") || cleanHost.startsWith("10.") ||
                        cleanHost.startsWith("172.16.") || cleanHost.startsWith("127.")

                if (isLocal) {
                    detectedFlag = "🏠"
                    geo = HostGeoInfo(
                        host = cleanHost,
                        ip = resolvedIp,
                        countryCode = "LAN",
                        countryName = "Local Network",
                        city = "Gateway / Private Subnet",
                        flagEmoji = "🏠",
                        ispOrOrg = "Local Router / LAN Device",
                        isLocal = true
                    )
                } else if (cleanHost == "8.8.8.8" || cleanHost == "8.8.4.4") {
                    detectedFlag = "🇺🇸"
                    geo = HostGeoInfo(
                        host = cleanHost,
                        ip = resolvedIp,
                        countryCode = "US",
                        countryName = "United States",
                        city = "Mountain View",
                        flagEmoji = "🇺🇸",
                        ispOrOrg = "Google Public DNS",
                        isLocal = false
                    )
                } else if (cleanHost == "1.1.1.1" || cleanHost == "1.0.0.1") {
                    detectedFlag = "🇺🇸"
                    geo = HostGeoInfo(
                        host = cleanHost,
                        ip = resolvedIp,
                        countryCode = "US",
                        countryName = "United States",
                        city = "Anycast Edge",
                        flagEmoji = "🇺🇸",
                        ispOrOrg = "Cloudflare DNS",
                        isLocal = false
                    )
                } else {
                    // Query IP Geolocation API
                    try {
                        val url = URL("http://ip-api.com/json/$resolvedIp?fields=status,country,countryCode,city,isp,org")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 3000
                        conn.readTimeout = 3000
                        if (conn.responseCode == 200) {
                            val resp = conn.inputStream.bufferedReader().use { it.readText() }
                            val json = JSONObject(resp)
                            if (json.optString("status") == "success") {
                                val cc = json.optString("countryCode", "")
                                val cName = json.optString("country", "")
                                val city = json.optString("city", "")
                                val org = json.optString("org", json.optString("isp", ""))
                                val emoji = countryCodeToFlagEmoji(cc)
                                detectedFlag = emoji
                                geo = HostGeoInfo(
                                    host = cleanHost,
                                    ip = resolvedIp,
                                    countryCode = cc,
                                    countryName = cName,
                                    city = city,
                                    flagEmoji = emoji,
                                    ispOrOrg = org,
                                    isLocal = false
                                )
                            }
                        }
                        conn.disconnect()
                    } catch (_: Exception) {
                        detectedFlag = "🌐"
                        geo = HostGeoInfo(
                            host = cleanHost,
                            ip = resolvedIp,
                            countryCode = "GLOBAL",
                            countryName = "Global Endpoint",
                            flagEmoji = "🌐",
                            ispOrOrg = "Remote Host",
                            isLocal = false
                        )
                    }
                }
            } catch (_: Exception) {}

            withContext(Dispatchers.Main) {
                hostGeoInfo = geo
            }

            // 2. Try Runtime Process Ping
            try {
                val cmdList = mutableListOf<String>()
                if (settings.ipVersion == "IPv6") {
                    cmdList.add("ping6")
                } else {
                    cmdList.add("ping")
                    if (settings.ipVersion == "IPv4") cmdList.add("-4")
                }

                if (settings.count.isNotBlank()) {
                    cmdList.add("-c")
                    cmdList.add(settings.count)
                }
                if (settings.packetSize.isNotBlank()) {
                    cmdList.add("-s")
                    cmdList.add(settings.packetSize)
                }
                if (settings.interval.isNotBlank()) {
                    cmdList.add("-i")
                    cmdList.add(settings.interval)
                }
                if (settings.timeout.isNotBlank()) {
                    cmdList.add("-W")
                    cmdList.add(settings.timeout)
                }
                cmdList.add(cleanHost)

                val process = ProcessBuilder(cmdList).redirectErrorStream(true).start()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?

                while (isActive) {
                    line = reader.readLine()
                    if (line == null) break

                    withContext(Dispatchers.Main) {
                        rawLogs = rawLogs + line!!
                    }

                    val match = regex.find(line)
                    if (match != null) {
                        executedViaProcess = true
                        val (bytes, h, ip, seq, ttl, time) = match.destructured
                        val actualHost = if (settings.resolveHostname) h else ""
                        val actualIp = if (ip.isBlank()) h else ip
                        val timeFloat = time.toFloatOrNull() ?: 0f
                        val result = PingResult(
                            seq = seq.toIntOrNull() ?: (results.size + 1),
                            bytes = bytes.toIntOrNull() ?: 56,
                            host = actualHost,
                            ip = actualIp,
                            ttl = ttl.toIntOrNull() ?: 64,
                            timeMs = timeFloat,
                            flagEmoji = detectedFlag
                        )
                        withContext(Dispatchers.Main) {
                            results = results + result
                        }
                        received++
                        times.add(timeFloat)
                    }
                }
                process.destroy()
            } catch (_: Exception) {}

            // 3. Try Socket-based fallback if process ping didn't yield results
            if (!executedViaProcess || results.isEmpty()) {
                val totalCount = settings.count.toIntOrNull() ?: 4
                sent = 0
                received = 0
                times.clear()

                try {
                    val inetAddr = InetAddress.getByName(cleanHost)
                    val ipStr = inetAddr.hostAddress ?: cleanHost

                    withContext(Dispatchers.Main) {
                        rawLogs = rawLogs + "Probing $detectedFlag $cleanHost [$ipStr] via socket..."
                    }

                    for (i in 1..totalCount) {
                        if (!isActive) break
                        sent++
                        val start = System.currentTimeMillis()
                        var success = false
                        var rtt = 0f

                        try {
                            val socket = Socket()
                            socket.connect(InetSocketAddress(inetAddr, 443), 2000)
                            socket.close()
                            success = true
                            rtt = (System.currentTimeMillis() - start).toFloat()
                        } catch (ex: Exception) {
                            try {
                                val s2 = Socket()
                                s2.connect(InetSocketAddress(inetAddr, 80), 2000)
                                s2.close()
                                success = true
                                rtt = (System.currentTimeMillis() - start).toFloat()
                            } catch (_: Exception) {
                                val s3 = System.currentTimeMillis()
                                val reachable = inetAddr.isReachable(2000)
                                if (reachable) {
                                    success = true
                                    rtt = (System.currentTimeMillis() - s3).toFloat()
                                }
                            }
                        }

                        if (success) {
                            received++
                            times.add(rtt)
                            val res = PingResult(
                                seq = i,
                                bytes = 64,
                                host = cleanHost,
                                ip = ipStr,
                                ttl = 56,
                                timeMs = rtt,
                                flagEmoji = detectedFlag
                            )
                            withContext(Dispatchers.Main) {
                                results = results + res
                                rawLogs = rawLogs + "$detectedFlag 64 bytes from $ipStr: seq=$i time=${String.format(Locale.US, "%.1f", rtt)} ms"
                            }
                        } else {
                            val res = PingResult(
                                seq = i,
                                bytes = 0,
                                host = cleanHost,
                                ip = ipStr,
                                ttl = 0,
                                timeMs = 0f,
                                flagEmoji = detectedFlag,
                                error = "Request timed out"
                            )
                            withContext(Dispatchers.Main) {
                                results = results + res
                                rawLogs = rawLogs + "Request timeout for icmp_seq $i ($detectedFlag $cleanHost)"
                            }
                        }
                        delay(600)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Host Resolution Failed: ${e.message ?: "Unknown host"}"
                        rawLogs = rawLogs + "Error: ${e.message}"
                    }
                }
            }

            val finalSent = if (sent > 0) sent else (settings.count.toIntOrNull() ?: results.size)
            val loss = if (finalSent > 0) (((finalSent - received).toFloat() / finalSent) * 100f).coerceIn(0f, 100f) else 0f
            val minMs = times.minOrNull() ?: 0f
            val maxMs = times.maxOrNull() ?: 0f
            val avgMs = if (times.isNotEmpty()) times.average().toFloat() else 0f

            withContext(Dispatchers.Main) {
                if (finalSent > 0 && errorMessage == null) {
                    stats = PingStats(
                        transmitted = finalSent,
                        received = received,
                        lossPercent = loss,
                        minMs = minMs,
                        maxMs = maxMs,
                        avgMs = avgMs
                    )
                    rawLogs = rawLogs + "--- $detectedFlag $cleanHost ping statistics ---"
                    rawLogs = rawLogs + "$finalSent packets transmitted, $received received, ${String.format(Locale.US, "%.1f", loss)}% packet loss"
                    rawLogs = rawLogs + "rtt min/avg/max = ${String.format(Locale.US, "%.1f/%.1f/%.1f", minMs, avgMs, maxMs)} ms"
                }
                isTesting = false
            }
        }
    }

    val isCurrentHostBookmarked = remember(host, bookmarksList) {
        bookmarksList.any { it.host.equals(host.trim(), ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚡ Ping Test", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        stopPing()
                        onBackClick()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showManageBookmarksModal = true }) {
                        Icon(
                            imageVector = Icons.Default.Bookmarks,
                            contentDescription = "Saved Bookmarks",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Target Input Bar with Bookmark / Favorite Action
            item {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Target Hostname or IP") },
                    placeholder = { Text("e.g. 8.8.8.8, 1.1.1.1, google.com") },
                    leadingIcon = {
                        Text(
                            text = hostGeoInfo?.flagEmoji ?: "🌐",
                            fontSize = 20.sp,
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (host.isNotEmpty() && !isTesting) {
                                IconButton(onClick = { host = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (host.isNotBlank()) {
                                        showSaveBookmarkDialog = true
                                    } else {
                                        Toast.makeText(context, "Please enter an IP or hostname first", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isCurrentHostBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Save IP Bookmark",
                                    tint = if (isCurrentHostBookmarked) Color(0xFFFFB300) else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    enabled = !isTesting
                )
            }

            // Saved IP Bookmarks Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bookmarks,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SAVED TARGET BOOKMARKS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    TextButton(
                        onClick = { showManageBookmarksModal = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Manage", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Saved Bookmark Chips Row (Instant Click-to-Ping)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    bookmarksList.forEach { bm ->
                        val isSelected = bm.host.equals(host.trim(), ignoreCase = true)
                        Surface(
                            modifier = Modifier
                                .clickable {
                                    host = bm.host
                                    startPing(bm.host)
                                    Toast.makeText(context, "Pinging ${bm.name} (${bm.host})...", Toast.LENGTH_SHORT).show()
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(bm.flagEmoji, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = bm.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = bm.host,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Add Custom Bookmark Chip
                    AssistChip(
                        onClick = { showSaveBookmarkDialog = true },
                        label = { Text("+ Bookmark") },
                        leadingIcon = {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                }
            }

            // Target Country & Geo Banner
            if (hostGeoInfo != null) {
                val geo = hostGeoInfo!!
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = geo.flagEmoji,
                                fontSize = 32.sp,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = geo.host,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (geo.countryCode.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = geo.countryCode,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${geo.countryName}${if (geo.city.isNotBlank()) " • " + geo.city else ""} (${geo.ip})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (geo.ispOrOrg.isNotBlank()) {
                                    Text(
                                        text = "ISP / Org: ${geo.ispOrOrg}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Action Button
            item {
                Button(
                    onClick = {
                        if (isTesting) stopPing() else startPing(host)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTesting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onError, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop Ping Test", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Ping", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Statistics Hero Banner
            if (stats != null) {
                item {
                    val s = stats!!
                    val lossColor = when {
                        s.lossPercent == 0f -> Color(0xFF2E7D32)
                        s.lossPercent < 25f -> Color(0xFFF57C00)
                        else -> Color(0xFFD32F2F)
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        shadowElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = hostGeoInfo?.flagEmoji ?: "🌐",
                                        fontSize = 18.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "PING STATISTICS",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", s.lossPercent)}% Packet Loss",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = lossColor
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MetricStatItem("SENT", "${s.transmitted} pkts")
                                MetricStatItem("RECEIVED", "${s.received} pkts")
                                MetricStatItem("AVG RTT", "${String.format(Locale.US, "%.1f", s.avgMs)} ms")
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MetricStatItem("MIN RTT", "${String.format(Locale.US, "%.1f", s.minMs)} ms")
                                MetricStatItem("MAX RTT", "${String.format(Locale.US, "%.1f", s.maxMs)} ms")
                                MetricStatItem("COUNTRY", hostGeoInfo?.countryCode ?: "Global")
                            }
                        }
                    }
                }
            }

            // Results Terminal / Log Window
            if (rawLogs.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CONSOLE OUTPUT",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                val text = rawLogs.joinToString("\n")
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Ping Logs", text))
                                Toast.makeText(context, "Copied console output", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Output", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E1E1E)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            rawLogs.forEach { logLine ->
                                val color = when {
                                    logLine.contains("timeout", ignoreCase = true) || logLine.contains("failed", ignoreCase = true) || logLine.contains("Error", ignoreCase = true) -> Color(0xFFFF5252)
                                    logLine.contains("bytes from", ignoreCase = true) -> Color(0xFF69F0AE)
                                    logLine.contains("statistics", ignoreCase = true) -> Color(0xFFFFD740)
                                    else -> Color(0xFFE0E0E0)
                                }
                                Text(
                                    text = logLine,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = color
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Dialog 1: Save IP Bookmark Dialog
    if (showSaveBookmarkDialog) {
        var bookmarkName by remember { mutableStateOf(if (host.isNotBlank()) "Target $host" else "") }
        var targetHostInput by remember { mutableStateOf(host) }

        AlertDialog(
            onDismissRequest = { showSaveBookmarkDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save IP Bookmark", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Save this target IP/hostname with a custom label for instant 1-tap ping tests.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = bookmarkName,
                        onValueChange = { bookmarkName = it },
                        label = { Text("Custom Name / Label") },
                        placeholder = { Text("e.g. Office Core Router, DNS 1") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = targetHostInput,
                        onValueChange = { targetHostInput = it },
                        label = { Text("Target IP / Hostname") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = targetHostInput.trim()
                        val name = bookmarkName.trim().ifBlank { clean }
                        if (clean.isNotBlank()) {
                            val flag = hostGeoInfo?.flagEmoji ?: "🌐"
                            val newBm = PingBookmark(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                host = clean,
                                flagEmoji = flag
                            )
                            val updated = bookmarksList.filter { !it.host.equals(clean, ignoreCase = true) } + newBm
                            saveBookmarks(updated)
                            showSaveBookmarkDialog = false
                            Toast.makeText(context, "Saved bookmark: $name", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = targetHostInput.isNotBlank()
                ) {
                    Text("Save Bookmark")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveBookmarkDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog 2: Manage Bookmarks Modal Bottom Sheet
    if (showManageBookmarksModal) {
        ModalBottomSheet(
            onDismissRequest = { showManageBookmarksModal = false },
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bookmarks, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Manage Saved Targets",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TextButton(
                        onClick = {
                            showManageBookmarksModal = false
                            showSaveBookmarkDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add New")
                    }
                }

                if (bookmarksList.isEmpty()) {
                    Text(
                        text = "No saved bookmarks. Add your frequently tested servers or IP addresses!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(bookmarksList, key = { it.id }) { bm ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        host = bm.host
                                        showManageBookmarksModal = false
                                        startPing(bm.host)
                                    },
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
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(bm.flagEmoji, fontSize = 24.sp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = bm.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = bm.host,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Button(
                                            onClick = {
                                                host = bm.host
                                                showManageBookmarksModal = false
                                                startPing(bm.host)
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("Ping", style = MaterialTheme.typography.labelSmall)
                                        }

                                        IconButton(
                                            onClick = {
                                                val updated = bookmarksList.filter { it.id != bm.id }
                                                saveBookmarks(updated)
                                                Toast.makeText(context, "Removed ${bm.name}", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
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

    if (showSettings) {
        PingSettingsDialog(
            settings = settings,
            onDismiss = { showSettings = false },
            onSave = { newSettings ->
                settings = newSettings
                prefs.edit().apply {
                    putString("ipVersion", newSettings.ipVersion)
                    putString("count", newSettings.count)
                    putString("packetSize", newSettings.packetSize)
                    putString("ttl", newSettings.ttl)
                    putString("interval", newSettings.interval)
                    putString("timeout", newSettings.timeout)
                    putString("overallTimeout", newSettings.overallTimeout)
                    putBoolean("resolveHostname", newSettings.resolveHostname)
                }.apply()
                showSettings = false
            }
        )
    }
}

@Composable
fun MetricStatItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun PingSettingsDialog(
    settings: PingSettings,
    onDismiss: () -> Unit,
    onSave: (PingSettings) -> Unit
) {
    var ipVersion by remember { mutableStateOf(settings.ipVersion) }
    var count by remember { mutableStateOf(settings.count) }
    var packetSize by remember { mutableStateOf(settings.packetSize) }
    var ttl by remember { mutableStateOf(settings.ttl) }
    var interval by remember { mutableStateOf(settings.interval) }
    var timeout by remember { mutableStateOf(settings.timeout) }
    var overallTimeout by remember { mutableStateOf(settings.overallTimeout) }
    var resolveHostname by remember { mutableStateOf(settings.resolveHostname) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ping Configuration", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("IP Version", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    listOf("Auto", "IPv4", "IPv6").forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { ipVersion = option }) {
                            RadioButton(
                                selected = ipVersion == option,
                                onClick = { ipVersion = option },
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(option, style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
                        }
                    }
                }

                OutlinedTextField(
                    value = count,
                    onValueChange = { count = it },
                    label = { Text("Pings Count") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = packetSize,
                    onValueChange = { packetSize = it },
                    label = { Text("Packet Size (bytes)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ttl,
                    onValueChange = { ttl = it },
                    label = { Text("Time To Live (TTL)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it },
                    label = { Text("Ping Interval (seconds)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = timeout,
                    onValueChange = { timeout = it },
                    label = { Text("Packet Timeout (seconds)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = !resolveHostname,
                        onCheckedChange = { resolveHostname = !it }
                    )
                    Text("Do not resolve host names", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    PingSettings(
                        ipVersion = ipVersion,
                        count = count,
                        packetSize = packetSize,
                        ttl = ttl,
                        interval = interval,
                        timeout = timeout,
                        overallTimeout = overallTimeout,
                        resolveHostname = resolveHostname
                    )
                )
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
