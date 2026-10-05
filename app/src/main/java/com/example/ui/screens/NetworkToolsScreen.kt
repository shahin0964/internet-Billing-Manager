package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.DhcpInfo
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
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
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class NetworkToolType {
    NONE, PING, DNS, IP_CHECK, GATEWAY, PORT_CHECK
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkToolsScreen(onBackClick: () -> Unit) {
    var activeTool by remember { mutableStateOf(NetworkToolType.NONE) }
    val context = LocalContext.current

    if (activeTool != NetworkToolType.NONE) {
        BackHandler {
            activeTool = NetworkToolType.NONE
        }
        when (activeTool) {
            NetworkToolType.PING -> PingTestScreen(onBackClick = { activeTool = NetworkToolType.NONE })
            NetworkToolType.DNS -> DnsLookupScreen(onBackClick = { activeTool = NetworkToolType.NONE })
            NetworkToolType.IP_CHECK -> IpAddressCheckScreen(onBackClick = { activeTool = NetworkToolType.NONE })
            NetworkToolType.GATEWAY -> GatewayReachabilityScreen(onBackClick = { activeTool = NetworkToolType.NONE })
            NetworkToolType.PORT_CHECK -> PortCheckScreen(onBackClick = { activeTool = NetworkToolType.NONE })
            else -> {}
        }
        return
    }

    // Live Network Summary State
    var networkSummary by remember {
        mutableStateOf(NetworkSummaryState(connectionType = "Detecting...", isOnline = false))
    }

    LaunchedEffect(Unit) {
        networkSummary = fetchNetworkSummary(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🛠️ " + stringResource(R.string.network_tools),
                            fontWeight = FontWeight.Bold
                        )
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
                actions = {
                    IconButton(onClick = {
                        networkSummary = NetworkSummaryState(connectionType = "Detecting...", isOnline = false)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Live Network Overview Card
            item {
                NetworkOverviewHeroCard(
                    summary = networkSummary,
                    onRefresh = {
                        networkSummary = fetchNetworkSummary(context)
                    }
                )
            }

            item {
                Text(
                    text = "DIAGNOSTIC & TESTING SUITE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                ModernToolCard(
                    title = stringResource(R.string.ping_test),
                    subtitle = "Real-time ICMP ping, packet loss & round-trip time (RTT)",
                    icon = Icons.Default.Public,
                    accentColor = Color(0xFF2979FF),
                    onClick = { activeTool = NetworkToolType.PING }
                )
            }

            item {
                ModernToolCard(
                    title = stringResource(R.string.dns_lookup),
                    subtitle = "Resolve domain names to IPv4 & IPv6 records with response time",
                    icon = Icons.Default.Search,
                    accentColor = Color(0xFF00B0FF),
                    onClick = { activeTool = NetworkToolType.DNS }
                )
            }

            item {
                ModernToolCard(
                    title = stringResource(R.string.ip_address_check),
                    subtitle = "Inspect local network interfaces, gateway, and public ISP IP",
                    icon = Icons.Default.Wifi,
                    accentColor = Color(0xFF00E676),
                    onClick = { activeTool = NetworkToolType.IP_CHECK }
                )
            }

            item {
                ModernToolCard(
                    title = stringResource(R.string.gateway_reachability),
                    subtitle = "Auto-detect router default gateway and test socket reachability",
                    icon = Icons.Default.Router,
                    accentColor = Color(0xFFFF9100),
                    onClick = { activeTool = NetworkToolType.GATEWAY }
                )
            }

            item {
                ModernToolCard(
                    title = "TCP Port Checker",
                    subtitle = "Test open ports (80, 443, 8080, 22) on local or remote servers",
                    icon = Icons.Default.Lan,
                    accentColor = Color(0xFFD500F9),
                    onClick = { activeTool = NetworkToolType.PORT_CHECK }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

data class NetworkSummaryState(
    val connectionType: String,
    val isOnline: Boolean,
    val localIp: String = "127.0.0.1",
    val gatewayIp: String = "Unknown",
    val publicIp: String = "Fetching...",
    val ispName: String = "Broadband Network"
)

private fun fetchNetworkSummary(context: Context): NetworkSummaryState {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val activeNet = cm?.activeNetwork
    val caps = cm?.getNetworkCapabilities(activeNet)

    val isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    val connType = when {
        caps == null -> "No Connection"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi Network"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet LAN"
        else -> "Connected"
    }

    // Local IP
    var localIp = "127.0.0.1"
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val intf = interfaces.nextElement()
            if (intf.isLoopback || !intf.isUp) continue
            val addrs = intf.inetAddresses
            while (addrs.hasMoreElements()) {
                val addr = addrs.nextElement()
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    localIp = addr.hostAddress ?: localIp
                    break
                }
            }
        }
    } catch (_: Exception) {}

    // Gateway
    var gatewayIp = "192.168.1.1"
    try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val dhcp: DhcpInfo? = wm?.dhcpInfo
        if (dhcp != null && dhcp.gateway != 0) {
            val g = dhcp.gateway
            gatewayIp = String.format("%d.%d.%d.%d", g and 0xff, (g shr 8) and 0xff, (g shr 16) and 0xff, (g shr 24) and 0xff)
        }
    } catch (_: Exception) {}

    return NetworkSummaryState(
        connectionType = connType,
        isOnline = isOnline,
        localIp = localIp,
        gatewayIp = gatewayIp
    )
}

@Composable
fun NetworkOverviewHeroCard(
    summary: NetworkSummaryState,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        shadowElevation = 2.dp
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
                    Surface(
                        shape = CircleShape,
                        color = if (summary.isOnline) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (summary.isOnline) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = if (summary.isOnline) Color(0xFF2E7D32) else Color(0xFFC62828),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = summary.connectionType,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (summary.isOnline) "Internet Connected • Online" else "No Active Internet Access",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (summary.isOnline) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                        )
                    }
                }

                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LOCAL IPv4",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = summary.localIp,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "DEFAULT GATEWAY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = summary.gatewayIp,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun ModernToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
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
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.padding(11.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * 2. DNS Lookup Tool Screen (Real DNS with java.net.InetAddress)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsLookupScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    var host by remember { mutableStateOf("google.com") }
    var records by remember { mutableStateOf<List<DnsRecordItem>>(emptyList()) }
    var responseTimeMs by remember { mutableLongStateOf(0L) }
    var isTesting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun runDnsQuery(targetHost: String) {
        val clean = targetHost.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore("/")
        if (clean.isBlank()) return

        isTesting = true
        errorMessage = null
        records = emptyList()

        scope.launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            try {
                val addresses = InetAddress.getAllByName(clean)
                val elapsed = System.currentTimeMillis() - start
                val list = addresses.map { addr ->
                    val type = if (addr is Inet6Address) "IPv6 (AAAA)" else "IPv4 (A)"
                    val canonical = try { addr.canonicalHostName } catch (_: Exception) { clean }
                    DnsRecordItem(
                        recordType = type,
                        ipAddress = addr.hostAddress ?: "",
                        canonicalName = canonical
                    )
                }
                withContext(Dispatchers.Main) {
                    records = list
                    responseTimeMs = elapsed
                    isTesting = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "DNS Lookup Failed: ${e.message ?: "Host unreachable"}"
                    isTesting = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔎 " + stringResource(R.string.dns_lookup), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            item {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.host_ip_hint)) },
                    placeholder = { Text("e.g. google.com or cloudflare.com") },
                    leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                    trailingIcon = {
                        if (host.isNotEmpty()) {
                            IconButton(onClick = { host = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            // Quick Preset Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("google.com", "cloudflare.com", "facebook.com", "youtube.com", "bdix.net").forEach { preset ->
                        AssistChip(
                            onClick = {
                                host = preset
                                runDnsQuery(preset)
                            },
                            label = { Text(preset) },
                            leadingIcon = {
                                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = { runDnsQuery(host) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isTesting && host.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Resolving DNS...")
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.run_test), fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (errorMessage != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            if (records.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RESOLVED RECORDS (${records.size}) • $responseTimeMs ms",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(
                            onClick = {
                                val text = records.joinToString("\n") { "${it.recordType}: ${it.ipAddress}" }
                                copyToClipboard(context, "DNS Records", text)
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy All")
                        }
                    }
                }

                items(records) { rec ->
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
                                .padding(14.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (rec.recordType.contains("IPv6")) Color(0xFFE1F5FE) else Color(0xFFE8F5E9)
                                ) {
                                    Text(
                                        text = rec.recordType,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (rec.recordType.contains("IPv6")) Color(0xFF0288D1) else Color(0xFF2E7D32),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = rec.ipAddress,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Host: ${rec.canonicalName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { copyToClipboard(context, "IP Address", rec.ipAddress) }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

data class DnsRecordItem(
    val recordType: String,
    val ipAddress: String,
    val canonicalName: String
)

/**
 * 3. IP Address & Network Interface Inspector Screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpAddressCheckScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    var publicIpInfo by remember { mutableStateOf<PublicIpDetails?>(null) }
    var interfaceList by remember { mutableStateOf<List<NetworkInterfaceItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun loadAllIpInfo() {
        isLoading = true
        scope.launch(Dispatchers.IO) {
            // 1. Fetch Local Network Interfaces
            val ifList = mutableListOf<NetworkInterfaceItem>()
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val intf = interfaces.nextElement()
                    if (intf.isLoopback || !intf.isUp) continue

                    val ipList = mutableListOf<String>()
                    val addrs = intf.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val a = addrs.nextElement()
                        if (!a.isLoopbackAddress) {
                            val prefix = if (a is Inet6Address) "IPv6" else "IPv4"
                            ipList.add("$prefix: ${a.hostAddress}")
                        }
                    }

                    val macBytes = try { intf.hardwareAddress } catch (_: Exception) { null }
                    val macStr = macBytes?.joinToString(":") { String.format("%02X", it) } ?: "N/A"

                    if (ipList.isNotEmpty()) {
                        ifList.add(
                            NetworkInterfaceItem(
                                name = intf.name,
                                displayName = intf.displayName,
                                ipAddresses = ipList,
                                macAddress = macStr,
                                isUp = intf.isUp,
                                mtu = intf.mtu
                            )
                        )
                    }
                }
            } catch (_: Exception) {}

            // 2. Fetch Public IP
            var pub: PublicIpDetails? = null
            try {
                val url = URL("http://ip-api.com/json/?fields=status,query,country,city,regionName,isp,org,as")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                if (conn.responseCode == 200) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(resp)
                    if (json.optString("status") == "success") {
                        pub = PublicIpDetails(
                            ip = json.optString("query", "Unknown"),
                            isp = json.optString("isp", json.optString("org", "")),
                            location = listOf(json.optString("city"), json.optString("country")).filter { it.isNotBlank() }.joinToString(", ")
                        )
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {}

            if (pub == null) {
                try {
                    val url = URL("https://api.ipify.org?format=json")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    if (conn.responseCode == 200) {
                        val resp = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(resp)
                        pub = PublicIpDetails(
                            ip = json.optString("ip", "Unknown"),
                            isp = "Broadband ISP",
                            location = "Bangladesh"
                        )
                    }
                    conn.disconnect()
                } catch (_: Exception) {}
            }

            withContext(Dispatchers.Main) {
                interfaceList = ifList
                publicIpInfo = pub
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadAllIpInfo()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🌐 " + stringResource(R.string.ip_address_check), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { loadAllIpInfo() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
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
            // Public IP Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PUBLIC INTERNET IP",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (publicIpInfo != null) {
                                IconButton(
                                    onClick = { copyToClipboard(context, "Public IP", publicIpInfo!!.ip) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Text(
                            text = publicIpInfo?.ip ?: (if (isLoading) "Detecting..." else "Unavailable"),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (publicIpInfo != null) {
                            Text(
                                text = "ISP: ${publicIpInfo!!.isp} • ${publicIpInfo!!.location}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "ACTIVE NETWORK INTERFACES (${interfaceList.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (interfaceList.isEmpty() && !isLoading) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "No active network interfaces found.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            items(interfaceList) { intf ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SettingsEthernet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${intf.name} (${intf.displayName})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF4CAF50).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "MTU ${intf.mtu}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        intf.ipAddresses.forEach { addr ->
                            Text(
                                text = addr,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (intf.macAddress != "N/A") {
                            Text(
                                text = "MAC: ${intf.macAddress}",
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

data class PublicIpDetails(val ip: String, val isp: String, val location: String)
data class NetworkInterfaceItem(
    val name: String,
    val displayName: String,
    val ipAddresses: List<String>,
    val macAddress: String,
    val isUp: Boolean,
    val mtu: Int
)

/**
 * 4. Gateway Reachability & Router Test Screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GatewayReachabilityScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    var gatewayHost by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var reachabilityResult by remember { mutableStateOf<GatewayTestResult?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // Auto-detect gateway
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val dhcp: DhcpInfo? = wm?.dhcpInfo
            if (dhcp != null && dhcp.gateway != 0) {
                val g = dhcp.gateway
                gatewayHost = String.format("%d.%d.%d.%d", g and 0xff, (g shr 8) and 0xff, (g shr 16) and 0xff, (g shr 24) and 0xff)
            } else {
                gatewayHost = "192.168.1.1"
            }
        } catch (_: Exception) {
            gatewayHost = "192.168.1.1"
        }
    }

    fun testGateway(target: String) {
        val clean = target.trim()
        if (clean.isBlank()) return
        isTesting = true
        reachabilityResult = null

        scope.launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            var isReachable = false
            var openPorts = mutableListOf<Int>()
            var dnsServer = "Unknown"
            var errorMessage: String? = null

            try {
                val addr = InetAddress.getByName(clean)
                
                // 1. Try Socket probes on router management ports
                val portsToTest = listOf(80, 443, 53, 8080)
                for (p in portsToTest) {
                    try {
                        val s = Socket()
                        s.connect(InetSocketAddress(addr, p), 1200)
                        s.close()
                        openPorts.add(p)
                        isReachable = true
                    } catch (_: Exception) {}
                }

                // 2. Fallback ICMP reachability
                if (!isReachable) {
                    isReachable = addr.isReachable(2000)
                }

                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val dhcp = wm?.dhcpInfo
                if (dhcp != null && dhcp.dns1 != 0) {
                    val d = dhcp.dns1
                    dnsServer = String.format("%d.%d.%d.%d", d and 0xff, (d shr 8) and 0xff, (d shr 16) and 0xff, (d shr 24) and 0xff)
                }

            } catch (e: Exception) {
                errorMessage = e.message
            }

            val rtt = System.currentTimeMillis() - start
            withContext(Dispatchers.Main) {
                reachabilityResult = GatewayTestResult(
                    gatewayIp = clean,
                    isReachable = isReachable,
                    latencyMs = rtt,
                    openPorts = openPorts,
                    dnsServer = dnsServer,
                    error = errorMessage
                )
                isTesting = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📡 " + stringResource(R.string.gateway_reachability), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            item {
                OutlinedTextField(
                    value = gatewayHost,
                    onValueChange = { gatewayHost = it },
                    label = { Text("Router Gateway IP") },
                    placeholder = { Text("e.g. 192.168.1.1") },
                    leadingIcon = { Icon(Icons.Default.Router, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            // Quick Preset Gateway IPs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("192.168.1.1", "192.168.0.1", "10.0.0.1", "172.16.0.1").forEach { ip ->
                        AssistChip(
                            onClick = {
                                gatewayHost = ip
                                testGateway(ip)
                            },
                            label = { Text(ip) }
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = { testGateway(gatewayHost) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isTesting && gatewayHost.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Probing Gateway...")
                    } else {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Reachability Now", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (reachabilityResult != null) {
                val res = reachabilityResult!!
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (res.isReachable) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (res.isReachable) Color(0xFF4CAF50) else Color(0xFFE57373)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (res.isReachable) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (res.isReachable) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (res.isReachable) "Gateway Reachable & Online" else "Gateway Unreachable / Timed Out",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (res.isReachable) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                            Text(
                                text = "Target: ${res.gatewayIp} • Round Trip: ${res.latencyMs} ms",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (res.openPorts.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF4CAF50).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "Active Ports: ${res.openPorts.joinToString(", ")}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class GatewayTestResult(
    val gatewayIp: String,
    val isReachable: Boolean,
    val latencyMs: Long,
    val openPorts: List<Int>,
    val dnsServer: String,
    val error: String?
)

/**
 * 5. TCP Port Checker Screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortCheckScreen(onBackClick: () -> Unit) {
    var host by remember { mutableStateOf("google.com") }
    var portStr by remember { mutableStateOf("443") }
    var isChecking by remember { mutableStateOf(false) }
    var portResult by remember { mutableStateOf<PortScanResult?>(null) }
    val scope = rememberCoroutineScope()

    fun checkPort(h: String, p: String) {
        val port = p.toIntOrNull() ?: 80
        val target = h.trim()
        if (target.isBlank()) return

        isChecking = true
        portResult = null

        scope.launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            var isOpen = false
            var msg = ""
            try {
                val s = Socket()
                s.connect(InetSocketAddress(target, port), 2500)
                s.close()
                isOpen = true
                msg = "Port $port is OPEN & accepting TCP connections"
            } catch (e: Exception) {
                isOpen = false
                msg = "Port $port is CLOSED / Unreachable (${e.message ?: "Connection timed out"})"
            }
            val elapsed = System.currentTimeMillis() - start
            withContext(Dispatchers.Main) {
                portResult = PortScanResult(
                    host = target,
                    port = port,
                    isOpen = isOpen,
                    latencyMs = elapsed,
                    message = msg
                )
                isChecking = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔌 TCP Port Checker", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Host / IP Address") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = portStr,
                onValueChange = { portStr = it },
                label = { Text("Port Number") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Common Port Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(80 to "HTTP", 443 to "HTTPS", 53 to "DNS", 22 to "SSH", 8080 to "HTTP-Alt", 3306 to "MySQL").forEach { (p, name) ->
                    AssistChip(
                        onClick = {
                            portStr = p.toString()
                            checkPort(host, p.toString())
                        },
                        label = { Text("$p ($name)") }
                    )
                }
            }

            Button(
                onClick = { checkPort(host, portStr) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isChecking && host.isNotBlank() && portStr.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Testing Port Connection...")
                } else {
                    Icon(Icons.Default.Lan, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Check Port Now", fontWeight = FontWeight.Bold)
                }
            }

            if (portResult != null) {
                val res = portResult!!
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (res.isOpen) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (res.isOpen) Color(0xFF4CAF50) else Color(0xFFE57373)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (res.isOpen) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (res.isOpen) Color(0xFF2E7D32) else Color(0xFFC62828),
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = if (res.isOpen) "Port ${res.port} OPEN" else "Port ${res.port} CLOSED",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (res.isOpen) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                        Text(
                            text = res.message,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Target: ${res.host}:${res.port} • Latency: ${res.latencyMs} ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

data class PortScanResult(
    val host: String,
    val port: Int,
    val isOpen: Boolean,
    val latencyMs: Long,
    val message: String
)

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
