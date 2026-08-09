package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.io.BufferedReader
import java.io.InputStreamReader

data class PingResult(
    val seq: Int,
    val bytes: Int,
    val host: String,
    val ip: String,
    val ttl: Int,
    val timeMs: Float,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingTestScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ping_settings", Context.MODE_PRIVATE) }
    
    var host by remember { mutableStateOf(prefs.getString("last_host", "") ?: "") }
    var results by remember { mutableStateOf<List<PingResult>>(emptyList()) }
    var stats by remember { mutableStateOf<PingStats?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var showSettings by remember { mutableStateOf(false) }
    
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

    fun startPing() {
        if (host.isBlank()) return
        
        // Save host to prefs
        prefs.edit().putString("last_host", host).apply()
        
        results = emptyList()
        stats = null
        errorMessage = null
        isTesting = true
        
        pingJob = coroutineScope.launch(Dispatchers.IO) {
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
                if (settings.ttl.isNotBlank()) {
                    cmdList.add("-t")
                    cmdList.add(settings.ttl)
                }
                if (settings.interval.isNotBlank()) {
                    cmdList.add("-i")
                    cmdList.add(settings.interval)
                }
                if (settings.timeout.isNotBlank()) {
                    cmdList.add("-W")
                    cmdList.add(settings.timeout)
                }
                if (settings.overallTimeout.isNotBlank()) {
                    cmdList.add("-w")
                    cmdList.add(settings.overallTimeout)
                }
                cmdList.add(host)

                val process = ProcessBuilder(cmdList).redirectErrorStream(true).start()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                
                var line: String?
                var sent = 0
                var received = 0
                val times = mutableListOf<Float>()
                
                while (isActive) {
                    line = reader.readLine()
                    if (line == null) break
                    
                    val match = regex.find(line)
                    if (match != null) {
                        val (bytes, h, ip, seq, ttl, time) = match.destructured
                        val actualHost = if (settings.resolveHostname) h else ""
                        val actualIp = if (ip.isBlank()) h else ip
                        val timeFloat = time.toFloatOrNull() ?: 0f
                        val result = PingResult(
                            seq = seq.toInt(),
                            bytes = bytes.toInt(),
                            host = actualHost,
                            ip = actualIp,
                            ttl = ttl.toInt(),
                            timeMs = timeFloat
                        )
                        withContext(Dispatchers.Main) {
                            results = results + result
                        }
                        received++
                        times.add(timeFloat)
                    } else if (line.contains("bytes of data")) {
                        // Header line, ignore
                    } else if (line.contains("ping statistics")) {
                        // End of ping output, next lines are stats
                        break
                    } else if (line.isNotBlank() && !line.startsWith("---")) {
                        if (line.contains("Unreachable", ignoreCase = true) || line.contains("Timeout", ignoreCase = true) || line.contains("exceeded", ignoreCase = true)) {
                            val seqMatch = Regex("""seq=(\d+)""").find(line)
                            val seq = seqMatch?.groupValues?.get(1)?.toIntOrNull() ?: (results.size + 1)
                            val result = PingResult(
                                seq = seq,
                                bytes = 0,
                                host = "",
                                ip = "",
                                ttl = 0,
                                timeMs = 0f,
                                error = line.trim()
                            )
                            withContext(Dispatchers.Main) {
                                results = results + result
                            }
                        } else if (line.contains("unknown host", ignoreCase = true) || line.contains("bad address", ignoreCase = true) || line.contains("Name or service not known", ignoreCase = true)) {
                            withContext(Dispatchers.Main) {
                                errorMessage = "DNS resolution failed or unknown host."
                            }
                            break
                        }
                    }
                }
                
                process.destroy()
                
                sent = settings.count.toIntOrNull() ?: (if (results.isEmpty()) 0 else results.last().seq)
                if (sent < received) sent = received
                val loss = if (sent > 0) ((sent - received).toFloat() / sent) * 100f else 0f
                val min = times.minOrNull() ?: 0f
                val max = times.maxOrNull() ?: 0f
                val avg = if (times.isNotEmpty()) times.average().toFloat() else 0f
                
                withContext(Dispatchers.Main) {
                    if (errorMessage == null && sent > 0) {
                        stats = PingStats(
                            transmitted = sent,
                            received = received,
                            lossPercent = loss,
                            minMs = min,
                            maxMs = max,
                            avgMs = avg
                        )
                    }
                    isTesting = false
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Error: ${e.message}"
                    isTesting = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ping Test", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { 
                        stopPing()
                        onBackClick() 
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Hostname or IP Address") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isTesting
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (isTesting) stopPing() else startPing()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTesting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(if (isTesting) "STOP" else "PING")
                    }
                }
            }

            if (errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results) { result ->
                        PingResultRow(result)
                    }
                }
                
                if (stats != null || isTesting) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 8.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (isTesting) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Pinging...", style = MaterialTheme.typography.labelLarge)
                            } else if (stats != null) {
                                Text("Ping Statistics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Sent: ${stats!!.transmitted}")
                                    Text("Received: ${stats!!.received}")
                                    Text("Loss: ${String.format("%.1f", stats!!.lossPercent)}%")
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Min: ${String.format("%.1f", stats!!.minMs)} ms")
                                    Text("Avg: ${String.format("%.1f", stats!!.avgMs)} ms")
                                    Text("Max: ${String.format("%.1f", stats!!.maxMs)} ms")
                                }
                            }
                        }
                    }
                }
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
fun PingResultRow(result: PingResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Seq #${result.seq}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                if (result.error != null) {
                    Text(
                        text = "Failed",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "${result.timeMs} ms",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (result.error != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(result.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (result.host.isNotBlank() && result.host != result.ip) "${result.host} (${result.ip})" else result.ip,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${result.bytes} bytes, TTL: ${result.ttl}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
        title = { Text("Ping Settings", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("IP Version", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("Auto", "IPv4", "IPv6").forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = ipVersion == option,
                                onClick = { ipVersion = option }
                            )
                            Text(option, style = MaterialTheme.typography.bodyMedium)
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
                OutlinedTextField(
                    value = overallTimeout,
                    onValueChange = { overallTimeout = it },
                    label = { Text("Overall Timeout (seconds)") },
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
