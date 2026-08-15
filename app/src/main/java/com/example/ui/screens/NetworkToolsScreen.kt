package com.example.ui.screens

import androidx.activity.compose.BackHandler
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

enum class NetworkToolType {
    NONE, PING, DNS, IP_CHECK, INTERNET_CONNECTIVITY, GATEWAY

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkToolsScreen(onBackClick: () -> Unit) {
    var activeTool by remember { mutableStateOf(NetworkToolType.NONE) }

    if (activeTool != NetworkToolType.NONE) {
        BackHandler {
            activeTool = NetworkToolType.NONE
        }
        when (activeTool) {
            NetworkToolType.PING -> PingTestScreen(onBackClick = { activeTool = NetworkToolType.NONE })
            NetworkToolType.DNS -> DnsLookupScreen(onBackClick = { activeTool = NetworkToolType.NONE })
            NetworkToolType.IP_CHECK -> IpAddressCheckScreen(onBackClick = { activeTool = NetworkToolType.NONE })
            NetworkToolType.INTERNET_CONNECTIVITY -> InternetConnectivityScreen(onBackClick = { activeTool = NetworkToolType.NONE })
            NetworkToolType.GATEWAY -> GatewayReachabilityScreen(onBackClick = { activeTool = NetworkToolType.NONE })
            else -> {}
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.network_tools),
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
            item {
                ToolCard(
                    title = stringResource(R.string.ping_test),
                    icon = Icons.Default.Public,
                    onClick = { activeTool = NetworkToolType.PING }
                )
            }
            item {
                ToolCard(
                    title = stringResource(R.string.dns_lookup),
                    icon = Icons.Default.Search,
                    onClick = { activeTool = NetworkToolType.DNS }
                )
            }
            item {
                ToolCard(
                    title = stringResource(R.string.ip_address_check),
                    icon = Icons.Default.Wifi,
                    onClick = { activeTool = NetworkToolType.IP_CHECK }
                )
            }
            item {
                ToolCard(
                    title = stringResource(R.string.internet_connectivity_test),
                    icon = Icons.Default.Language,
                    onClick = { activeTool = NetworkToolType.INTERNET_CONNECTIVITY }
                )
            }
            item {
                ToolCard(
                    title = stringResource(R.string.gateway_reachability),
                    icon = Icons.Default.Router,
                    onClick = { activeTool = NetworkToolType.GATEWAY }
                )
            }
        }
    }
}
@Composable
fun ToolCard(title: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
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
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsLookupScreen(onBackClick: () -> Unit) {
    var host by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dns_lookup), fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text(stringResource(R.string.host_ip_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    if (host.isNotBlank()) {
                        isTesting = true
                        result = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTesting && host.isNotBlank()
            ) {
                Text(if (isTesting) stringResource(R.string.testing) else stringResource(R.string.run_test))
            }
            
            LaunchedEffect(isTesting) {
                if (isTesting) {
                    result = withContext(Dispatchers.IO) {
                        try {
                            val addresses = InetAddress.getAllByName(host)
                            val sb = java.lang.StringBuilder()
                            addresses.forEach { addr ->
                                val type = if (addr is Inet6Address) "IPv6" else if (addr is Inet4Address) "IPv4" else "Unknown"
                                sb.append("$type: ${addr.hostAddress}\n")
                            }
                            sb.toString()
                        } catch (e: Exception) {
                            "DNS Resolution Failed: ${e.message}"
                        }
                    }
                    isTesting = false
                }
            }

            if (result.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = result,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpAddressCheckScreen(onBackClick: () -> Unit) {
    var result by remember { mutableStateOf("Checking local network interfaces...") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        result = withContext(Dispatchers.IO) {
            try {
                val sb = java.lang.StringBuilder()
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val intf = interfaces.nextElement()
                    if (intf.isLoopback || !intf.isUp) continue
                    val addresses = intf.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (!addr.isLoopbackAddress) {
                            val type = if (addr is Inet6Address) "IPv6" else if (addr is Inet4Address) "IPv4" else "Unknown"
                            sb.append("Interface: ${intf.name}\n")
                            sb.append("$type: ${addr.hostAddress}\n\n")
                        }
                    }
                }
                if (sb.isEmpty()) "No active local IP addresses found." else sb.toString().trim()
            } catch (e: Exception) {
                "Error retrieving IP: ${e.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ip_address_check), fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = result,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternetConnectivityScreen(onBackClick: () -> Unit) {
    var result by remember { mutableStateOf("Testing connectivity...") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        result = withContext(Dispatchers.IO) {
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val activeNetwork = cm.activeNetwork
                val capabilities = cm.getNetworkCapabilities(activeNetwork)
                
                val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                                  capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                                  
                val type = when {
                    capabilities == null -> "None"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                    else -> "Unknown"
                }

                val sb = java.lang.StringBuilder()
                sb.append("Connection Type: $type\n")
                sb.append("Internet Validated: ${if (hasInternet) "Yes" else "No"}\n\n")

                if (hasInternet) {
                    // Let's do a real reachability check
                    try {
                        val addr = InetAddress.getByName("8.8.8.8")
                        val reachable = addr.isReachable(3000)
                        sb.append("Ping 8.8.8.8: ${if (reachable) "Reachable" else "Unreachable"}")
                    } catch (e: Exception) {
                        sb.append("Reachability test failed: ${e.message}")
                    }
                }

                sb.toString()
            } catch (e: Exception) {
                "Error checking connectivity: ${e.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.internet_connectivity_test), fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = result,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GatewayReachabilityScreen(onBackClick: () -> Unit) {
    var host by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gateway_reachability), fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text(stringResource(R.string.host_ip_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    if (host.isNotBlank()) {
                        isTesting = true
                        result = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTesting && host.isNotBlank()
            ) {
                Text(if (isTesting) stringResource(R.string.testing) else stringResource(R.string.run_test))
            }
            
            LaunchedEffect(isTesting) {
                if (isTesting) {
                    result = withContext(Dispatchers.IO) {
                        try {
                            val addr = InetAddress.getByName(host)
                            val start = System.currentTimeMillis()
                            val reachable = addr.isReachable(5000)
                            val duration = System.currentTimeMillis() - start
                            
                            if (reachable) {
                                "Target: ${addr.hostAddress}\nStatus: Reachable\nResponse time: $duration ms"
                            } else {
                                "Target: ${addr.hostAddress}\nStatus: Unreachable (Timeout 5000ms)"
                            }
                        } catch (e: Exception) {
                            "Error: ${e.message}"
                        }
                    }
                    isTesting = false
                }
            }

            if (result.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = result,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
