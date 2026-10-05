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
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Locale
import kotlin.math.pow

enum class AdvNetworkTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    PORT_SCAN("Port Scanner", Icons.Default.Dns),
    SUBNET_CALC("Subnet / CIDR", Icons.Default.Build),
    TRACEROUTE("Traceroute", Icons.Default.LocationOn),
    MAC_LOOKUP("MAC Vendor", Icons.Default.Search),
    WHOIS("Whois / Domain", Icons.Default.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedNetworkScreen(onBackClick: () -> Unit) {
    var currentTab by remember { mutableStateOf(AdvNetworkTab.PORT_SCAN) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Network 🛜", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Tools",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            // Horizontal Tab Navigation
            ScrollableTabRow(
                selectedTabIndex = currentTab.ordinal,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) }
            ) {
                AdvNetworkTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    Tab(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(tab.title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    )
                }
            }

            // Tab Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentTab) {
                    AdvNetworkTab.PORT_SCAN -> PortScannerView()
                    AdvNetworkTab.SUBNET_CALC -> SubnetCalculatorView()
                    AdvNetworkTab.TRACEROUTE -> TracerouteView()
                    AdvNetworkTab.MAC_LOOKUP -> MacVendorLookupView()
                    AdvNetworkTab.WHOIS -> WhoisLookupView()
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 1. Port Scanner Tool View
// -------------------------------------------------------------
data class ScannedPort(val port: Int, val serviceName: String, val isOpen: Boolean, val latencyMs: Long)

@Composable
fun PortScannerView() {
    val context = LocalContext.current
    var host by remember { mutableStateOf("192.168.1.1") }
    var selectedPreset by remember { mutableStateOf("ISP & Router Ports") }
    var customPortsInput by remember { mutableStateOf("80, 443, 8291, 8728, 22, 53") }
    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableFloatStateOf(0f) }
    var scannedList by remember { mutableStateOf<List<ScannedPort>>(emptyList()) }
    val scope = rememberCoroutineScope()
    var scanJob by remember { mutableStateOf<Job?>(null) }

    val presetMap = mapOf(
        "ISP & Router Ports" to listOf(80 to "HTTP Web", 443 to "HTTPS Web", 8291 to "MikroTik Winbox", 8728 to "MikroTik API", 8729 to "MikroTik API-SSL", 22 to "SSH", 23 to "Telnet", 21 to "FTP", 53 to "DNS", 161 to "SNMP", 8080 to "HTTP Proxy"),
        "Web & Cloud Ports" to listOf(80 to "HTTP", 443 to "HTTPS", 8080 to "HTTP-Alt", 8443 to "HTTPS-Alt", 3000 to "NodeJS", 5000 to "Flask", 8000 to "Django", 9000 to "Portainer"),
        "Database & Services" to listOf(3306 to "MySQL/MariaDB", 5432 to "PostgreSQL", 1433 to "MS-SQL", 6379 to "Redis", 27017 to "MongoDB", 11211 to "Memcached")
    )

    fun startPortScan() {
        val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").substringBefore("/")
        if (cleanHost.isBlank()) return

        val portsToScan = if (selectedPreset == "Custom Ports") {
            customPortsInput.split(",").mapNotNull { it.trim().toIntOrNull() }.distinct().map { it to "Port $it" }
        } else {
            presetMap[selectedPreset] ?: presetMap.values.first()
        }

        if (portsToScan.isEmpty()) return

        scanJob?.cancel()
        isScanning = true
        scanProgress = 0f
        scannedList = emptyList()

        scanJob = scope.launch(Dispatchers.IO) {
            val total = portsToScan.size
            val results = mutableListOf<ScannedPort>()

            portsToScan.forEachIndexed { index, (port, service) ->
                if (!isActive) return@forEachIndexed
                val start = System.currentTimeMillis()
                var isOpen = false

                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(cleanHost, port), 800)
                    socket.close()
                    isOpen = true
                } catch (_: Exception) {}

                val latency = System.currentTimeMillis() - start
                val item = ScannedPort(port = port, serviceName = service, isOpen = isOpen, latencyMs = latency)
                results.add(item)

                withContext(Dispatchers.Main) {
                    scannedList = results.toList()
                    scanProgress = (index + 1).toFloat() / total.toFloat()
                }
            }

            withContext(Dispatchers.Main) {
                isScanning = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Target IP / Router Hostname") },
                placeholder = { Text("e.g. 192.168.1.1 or mikrotik.local") },
                leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                enabled = !isScanning
            )
        }

        // Preset Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ISP & Router Ports", "Web & Cloud Ports", "Database & Services", "Custom Ports").forEach { p ->
                    val isSelected = selectedPreset == p
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedPreset = p },
                        label = { Text(p) }
                    )
                }
            }
        }

        if (selectedPreset == "Custom Ports") {
            item {
                OutlinedTextField(
                    value = customPortsInput,
                    onValueChange = { customPortsInput = it },
                    label = { Text("Comma-separated Ports") },
                    placeholder = { Text("80, 443, 8291, 8728, 22") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        }

        item {
            Button(
                onClick = { if (isScanning) { scanJob?.cancel(); isScanning = false } else startPortScan() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onError, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop Scanning", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Port Scan", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (isScanning) {
            item {
                LinearProgressIndicator(
                    progress = { scanProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }

        if (scannedList.isNotEmpty()) {
            val openCount = scannedList.count { it.isOpen }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RESULTS ($openCount OPEN / ${scannedList.size} TOTAL)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    TextButton(onClick = {
                        val text = scannedList.joinToString("\n") { "Port ${it.port} (${it.serviceName}): ${if (it.isOpen) "OPEN (${it.latencyMs}ms)" else "CLOSED"}" }
                        copyToClipboard(context, "Port Scan Results", text)
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            items(scannedList) { p ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (p.isOpen) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (p.isOpen) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (p.isOpen) Icons.Default.CheckCircle else Icons.Default.Close,
                                contentDescription = null,
                                tint = if (p.isOpen) Color(0xFF2E7D32) else Color(0xFF9E9E9E),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Port ${p.port} • ${p.serviceName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (p.isOpen) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (p.isOpen) "Response time: ${p.latencyMs} ms" else "Filtered / No response",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (p.isOpen) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = if (p.isOpen) "OPEN" else "CLOSED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (p.isOpen) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 2. Subnet / CIDR Calculator Tool View
// -------------------------------------------------------------
data class SubnetCalcResult(
    val ip: String,
    val cidr: Int,
    val subnetMask: String,
    val wildcardMask: String,
    val networkAddress: String,
    val broadcastAddress: String,
    val firstUsableIp: String,
    val lastUsableIp: String,
    val totalHosts: Long,
    val usableHosts: Long,
    val ipClass: String,
    val isPrivate: Boolean
)

@Composable
fun SubnetCalculatorView() {
    val context = LocalContext.current
    var ipInput by remember { mutableStateOf("192.168.1.100") }
    var selectedCidr by remember { mutableIntStateOf(24) }
    var result by remember { mutableStateOf<SubnetCalcResult?>(null) }

    fun calculateSubnet() {
        try {
            val cleanIp = ipInput.trim()
            val parts = cleanIp.split(".")
            if (parts.size != 4) return
            val octets = parts.map { it.toInt() }
            if (octets.any { it !in 0..255 }) return

            val ipLong = (octets[0].toLong() shl 24) or (octets[1].toLong() shl 16) or (octets[2].toLong() shl 8) or octets[3].toLong()
            val maskLong = if (selectedCidr == 0) 0L else (0xFFFFFFFFL shl (32 - selectedCidr)) and 0xFFFFFFFFL
            val wildcardLong = maskLong.inv() and 0xFFFFFFFFL

            val networkLong = ipLong and maskLong
            val broadcastLong = networkLong or wildcardLong

            fun longToIp(l: Long): String = String.format("%d.%d.%d.%d", (l shr 24) and 0xff, (l shr 16) and 0xff, (l shr 8) and 0xff, l and 0xff)

            val totalHosts = (2.0.pow((32 - selectedCidr).toDouble())).toLong()
            val usableHosts = if (selectedCidr >= 31) 0L else maxOf(0L, totalHosts - 2)

            val firstUsable = if (selectedCidr >= 31) longToIp(networkLong) else longToIp(networkLong + 1)
            val lastUsable = if (selectedCidr >= 31) longToIp(broadcastLong) else longToIp(broadcastLong - 1)

            val firstOct = octets[0]
            val ipClass = when {
                firstOct in 1..126 -> "Class A"
                firstOct in 128..191 -> "Class B"
                firstOct in 192..223 -> "Class C"
                firstOct in 224..239 -> "Class D (Multicast)"
                else -> "Class E"
            }

            val isPrivate = (firstOct == 10) || (firstOct == 172 && octets[1] in 16..31) || (firstOct == 192 && octets[1] == 168)

            result = SubnetCalcResult(
                ip = cleanIp,
                cidr = selectedCidr,
                subnetMask = longToIp(maskLong),
                wildcardMask = longToIp(wildcardLong),
                networkAddress = longToIp(networkLong),
                broadcastAddress = longToIp(broadcastLong),
                firstUsableIp = firstUsable,
                lastUsableIp = lastUsable,
                totalHosts = totalHosts,
                usableHosts = usableHosts,
                ipClass = ipClass,
                isPrivate = isPrivate
            )
        } catch (_: Exception) {}
    }

    LaunchedEffect(ipInput, selectedCidr) {
        calculateSubnet()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            OutlinedTextField(
                value = ipInput,
                onValueChange = { ipInput = it },
                label = { Text("IPv4 Address") },
                placeholder = { Text("192.168.1.1") },
                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }

        // CIDR Prefix Selector
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "CIDR Prefix: /$selectedCidr (${if (result != null) result!!.subnetMask else ""})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(8, 16, 20, 22, 24, 25, 26, 27, 28, 29, 30, 32).forEach { cidr ->
                        val isSelected = selectedCidr == cidr
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCidr = cidr },
                            label = { Text("/$cidr") }
                        )
                    }
                }
            }
        }

        if (result != null) {
            val r = result!!
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
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
                            Text(
                                text = "CALCULATION SUMMARY",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (r.isPrivate) Color(0xFFE8F5E9) else Color(0xFFE3F2FD)
                            ) {
                                Text(
                                    text = if (r.isPrivate) "Private IP" else "Public IP",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (r.isPrivate) Color(0xFF2E7D32) else Color(0xFF1565C0),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        SubnetDetailRow("Network Address", "${r.networkAddress}/${r.cidr}")
                        SubnetDetailRow("Subnet Mask", r.subnetMask)
                        SubnetDetailRow("Wildcard Mask", r.wildcardMask)
                        SubnetDetailRow("Usable Host Range", "${r.firstUsableIp} - ${r.lastUsableIp}")
                        SubnetDetailRow("Broadcast Address", r.broadcastAddress)
                        SubnetDetailRow("Usable Hosts", "${r.usableHosts} IPs (${r.totalHosts} total)")
                        SubnetDetailRow("IP Class", r.ipClass)
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val text = """
                            IP: ${r.ip}/${r.cidr}
                            Network: ${r.networkAddress}
                            Netmask: ${r.subnetMask}
                            Wildcard: ${r.wildcardMask}
                            Range: ${r.firstUsableIp} - ${r.lastUsableIp}
                            Broadcast: ${r.broadcastAddress}
                            Usable Hosts: ${r.usableHosts}
                        """.trimIndent()
                        copyToClipboard(context, "Subnet Calculation", text)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy Subnet Details", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SubnetDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

// -------------------------------------------------------------
// 3. Traceroute Tool View
// -------------------------------------------------------------
data class TraceHop(val hopNumber: Int, val host: String, val ip: String, val rttMs: Float, val isSuccess: Boolean)

@Composable
fun TracerouteView() {
    val context = LocalContext.current
    var host by remember { mutableStateOf("8.8.8.8") }
    var isTracing by remember { mutableStateOf(false) }
    var hopsList by remember { mutableStateOf<List<TraceHop>>(emptyList()) }
    var traceLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()
    var traceJob by remember { mutableStateOf<Job?>(null) }

    fun startTraceroute() {
        val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").substringBefore("/")
        if (cleanHost.isBlank()) return

        traceJob?.cancel()
        isTracing = true
        hopsList = emptyList()
        traceLogs = listOf("traceroute to $cleanHost, 15 hops max...")

        traceJob = scope.launch(Dispatchers.IO) {
            var targetAddr: InetAddress? = null
            try {
                targetAddr = InetAddress.getByName(cleanHost)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    traceLogs = traceLogs + "Error: Failed to resolve $cleanHost"
                    isTracing = false
                }
                return@launch
            }

            val maxHops = 15
            for (ttl in 1..maxHops) {
                if (!isActive) break

                var hopIp = "*"
                var hopHost = "*"
                var rtt = 0f
                var success = false

                // Try ping with TTL
                try {
                    val cmd = listOf("ping", "-c", "1", "-t", ttl.toString(), "-W", "1", cleanHost)
                    val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
                    val reader = BufferedReader(InputStreamReader(proc.inputStream))
                    var line: String?
                    val start = System.currentTimeMillis()

                    while (proc.isAlive || reader.ready()) {
                        line = reader.readLine() ?: break
                        if (line.contains("From", ignoreCase = true) || line.contains("bytes from", ignoreCase = true)) {
                            val ipMatch = Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b""").find(line)
                            if (ipMatch != null) {
                                hopIp = ipMatch.value
                                hopHost = hopIp
                                success = true
                                rtt = (System.currentTimeMillis() - start).toFloat()
                                break
                            }
                        }
                    }
                    proc.destroy()
                } catch (_: Exception) {}

                // Fallback socket probe for final destination
                if (!success && ttl == maxHops) {
                    val s = System.currentTimeMillis()
                    try {
                        val sock = Socket()
                        sock.connect(InetSocketAddress(cleanHost, 80), 1000)
                        sock.close()
                        hopIp = targetAddr.hostAddress ?: cleanHost
                        hopHost = cleanHost
                        success = true
                        rtt = (System.currentTimeMillis() - s).toFloat()
                    } catch (_: Exception) {}
                }

                val hop = TraceHop(
                    hopNumber = ttl,
                    host = hopHost,
                    ip = hopIp,
                    rttMs = rtt,
                    isSuccess = success
                )

                withContext(Dispatchers.Main) {
                    hopsList = hopsList + hop
                    traceLogs = traceLogs + if (success) "$ttl  $hopIp  ${String.format(Locale.US, "%.1f", rtt)} ms" else "$ttl  * * * (Request timed out)"
                }

                if (hopIp == targetAddr.hostAddress) {
                    withContext(Dispatchers.Main) {
                        traceLogs = traceLogs + "Destination $cleanHost reached in $ttl hops."
                    }
                    break
                }
                delay(200)
            }

            withContext(Dispatchers.Main) {
                isTracing = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Target Host / Gateway") },
                placeholder = { Text("e.g. 8.8.8.8, 1.1.1.1, google.com") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                enabled = !isTracing
            )
        }

        item {
            Button(
                onClick = { if (isTracing) { traceJob?.cancel(); isTracing = false } else startTraceroute() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTracing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                if (isTracing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onError, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop Traceroute", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Trace Route Now", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (traceLogs.isNotEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E1E1E)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        traceLogs.forEach { line ->
                            Text(
                                text = line,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = if (line.contains("Error") || line.contains("*")) Color(0xFFFF8A80) else Color(0xFF69F0AE)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 4. MAC Address Vendor Lookup Tool View
// -------------------------------------------------------------
data class MacVendorResult(
    val mac: String,
    val vendor: String,
    val country: String,
    val prefix: String,
    val isMulticast: Boolean,
    val isLocal: Boolean
)

@Composable
fun MacVendorLookupView() {
    val context = LocalContext.current
    var macInput by remember { mutableStateOf("CC:2D:E0:12:34:56") }
    var result by remember { mutableStateOf<MacVendorResult?>(null) }
    var isLookingUp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Curated top ISP & networking vendor OUI database
    val builtInOuiMap = mapOf(
        "CC:2D:E0" to ("MikroTik / Routerboard" to "Latvia"),
        "D4:CA:6D" to ("MikroTik / Routerboard" to "Latvia"),
        "48:8F:5A" to ("MikroTik / Routerboard" to "Latvia"),
        "00:0C:42" to ("MikroTik / Routerboard" to "Latvia"),
        "D8:0D:17" to ("TP-Link Corporation" to "China"),
        "50:D4:F7" to ("TP-Link Corporation" to "China"),
        "C0:25:67" to ("TP-Link Corporation" to "China"),
        "E4:AA:EC" to ("Ubiquiti Networks" to "USA"),
        "78:8A:20" to ("Ubiquiti Networks" to "USA"),
        "00:1A:2B" to ("Huawei Technologies" to "China"),
        "20:0B:C7" to ("Huawei Technologies" to "China"),
        "F4:F2:6D" to ("Cisco Systems" to "USA"),
        "00:1B:54" to ("Cisco Systems" to "USA"),
        "FC:EC:DA" to ("Ubiquiti Inc" to "USA"),
        "70:4F:57" to ("ZTE Corporation" to "China"),
        "00:19:E0" to ("Nokia / Alcatel-Lucent" to "Finland")
    )

    fun lookupMac(targetMac: String) {
        val clean = targetMac.trim().uppercase(Locale.US).replace("-", ":").replace(".", ":")
        if (clean.length < 6) return

        isLookingUp = true
        result = null

        scope.launch(Dispatchers.IO) {
            val prefix = clean.take(8)
            var vendorName = "Unknown Vendor"
            var country = "Global"

            val match = builtInOuiMap.entries.find { clean.startsWith(it.key) }
            if (match != null) {
                vendorName = match.value.first
                country = match.value.second
            } else {
                // Query macvendors API
                try {
                    val url = URL("https://api.macvendors.com/${clean.replace(":", "")}")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    if (conn.responseCode == 200) {
                        val resp = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                        if (resp.isNotBlank()) {
                            vendorName = resp
                        }
                    }
                    conn.disconnect()
                } catch (_: Exception) {}
            }

            val firstByte = try { clean.take(2).toInt(16) } catch (_: Exception) { 0 }
            val isMulticast = (firstByte and 1) == 1
            val isLocal = (firstByte and 2) == 2

            withContext(Dispatchers.Main) {
                result = MacVendorResult(
                    mac = clean,
                    vendor = vendorName,
                    country = country,
                    prefix = prefix,
                    isMulticast = isMulticast,
                    isLocal = isLocal
                )
                isLookingUp = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            OutlinedTextField(
                value = macInput,
                onValueChange = { macInput = it },
                label = { Text("MAC Address / OUI Prefix") },
                placeholder = { Text("e.g. CC:2D:E0:12:34:56") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }

        // Quick Preset Vendor MACs
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("CC:2D:E0" to "MikroTik", "D8:0D:17" to "TP-Link", "E4:AA:EC" to "Ubiquiti", "00:1A:2B" to "Huawei", "F4:F2:6D" to "Cisco").forEach { (oui, label) ->
                    AssistChip(
                        onClick = {
                            macInput = "$oui:11:22:33"
                            lookupMac(macInput)
                        },
                        label = { Text("$oui ($label)") }
                    )
                }
            }
        }

        item {
            Button(
                onClick = { lookupMac(macInput) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLookingUp && macInput.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLookingUp) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Searching Vendor Database...")
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Lookup MAC Vendor", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (result != null) {
            val r = result!!
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(42.dp)) {
                                Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(r.vendor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("OUI: ${r.prefix} • ${r.country}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        SubnetDetailRow("MAC Address", r.mac)
                        SubnetDetailRow("Organization / Vendor", r.vendor)
                        SubnetDetailRow("Country of Origin", r.country)
                        SubnetDetailRow("Address Type", if (r.isLocal) "Locally Administered (Randomized)" else "Universally Administered (Real Hardware)")
                        SubnetDetailRow("Cast Mode", if (r.isMulticast) "Multicast MAC" else "Unicast MAC")
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 5. Whois & Domain / IP Registry Lookup Tool View
// -------------------------------------------------------------
data class WhoisResult(
    val query: String,
    val ip: String,
    val asn: String,
    val org: String,
    val country: String,
    val city: String,
    val timeZone: String,
    val status: String
)

@Composable
fun WhoisLookupView() {
    val context = LocalContext.current
    var domainInput by remember { mutableStateOf("bdix.net") }
    var whoisData by remember { mutableStateOf<WhoisResult?>(null) }
    var isQuerying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun runWhoisQuery(target: String) {
        val clean = target.trim().removePrefix("http://").removePrefix("https://").substringBefore("/")
        if (clean.isBlank()) return

        isQuerying = true
        errorMessage = null
        whoisData = null

        scope.launch(Dispatchers.IO) {
            try {
                val inet = InetAddress.getByName(clean)
                val ip = inet.hostAddress ?: clean

                val url = URL("http://ip-api.com/json/$ip?fields=status,message,country,regionName,city,timezone,isp,org,as,query")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                if (conn.responseCode == 200) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(resp)
                    if (json.optString("status") == "success") {
                        val result = WhoisResult(
                            query = clean,
                            ip = json.optString("query", ip),
                            asn = json.optString("as", "N/A"),
                            org = json.optString("org", json.optString("isp", "N/A")),
                            country = json.optString("country", "Unknown"),
                            city = json.optString("city", "Unknown"),
                            timeZone = json.optString("timezone", "UTC"),
                            status = "Active / Registered"
                        )
                        withContext(Dispatchers.Main) {
                            whoisData = result
                            isQuerying = false
                        }
                        return@launch
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Whois Lookup Failed: ${e.message}"
                    isQuerying = false
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            OutlinedTextField(
                value = domainInput,
                onValueChange = { domainInput = it },
                label = { Text("Domain Name or IP Address") },
                placeholder = { Text("e.g. bdix.net, google.com, 8.8.8.8") },
                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }

        // Preset Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("bdix.net", "google.com", "cloudflare.com", "btcl.com.bd").forEach { d ->
                    AssistChip(
                        onClick = {
                            domainInput = d
                            runWhoisQuery(d)
                        },
                        label = { Text(d) }
                    )
                }
            }
        }

        item {
            Button(
                onClick = { runWhoisQuery(domainInput) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isQuerying && domainInput.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isQuerying) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Fetching Whois Records...")
                } else {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Lookup Whois Info", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (whoisData != null) {
            val w = whoisData!!
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "WHOIS REGISTRY INFO",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF4CAF50).copy(alpha = 0.2f)) {
                                Text(
                                    text = w.status,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        SubnetDetailRow("Target Domain", w.query)
                        SubnetDetailRow("Resolved IP", w.ip)
                        SubnetDetailRow("Autonomous System (ASN)", w.asn)
                        SubnetDetailRow("Registrant / ISP", w.org)
                        SubnetDetailRow("Country / Region", "${w.country}, ${w.city}")
                        SubnetDetailRow("Timezone", w.timeZone)
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
