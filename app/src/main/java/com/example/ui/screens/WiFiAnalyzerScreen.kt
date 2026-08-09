package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

enum class AnalyzerTab {
    CHANNEL_GRAPH, TIME_GRAPH, BEST_CHANNELS, ACCESS_POINTS
}

enum class WifiBand(val title: String, val minFreq: Int, val maxFreq: Int) {
    BAND_2_4_GHZ("2.4 GHz", 2400, 2500),
    BAND_5_GHZ("5 GHz", 4900, 5900),
    BAND_6_GHZ("6 GHz", 5925, 7125)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WiFiAnalyzerScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasLocationPermission = isGranted
    }

    var wifiInfo by remember { mutableStateOf<WifiInfo?>(null) }
    var scanResults by remember { mutableStateOf<List<ScanResult>>(emptyList()) }
    var isPaused by remember { mutableStateOf(false) }
    var lastScanTime by remember { mutableStateOf<Long?>(null) }
    
    var currentTab by remember { mutableStateOf(AnalyzerTab.CHANNEL_GRAPH) }
    var currentBand by remember { mutableStateOf(WifiBand.BAND_2_4_GHZ) }

    // History for Time Graph: BSSID -> List of (Timestamp, RSSI)
    val history = remember { mutableStateMapOf<String, MutableList<Pair<Long, Int>>>() }

    val refreshConnection = {
        if (hasLocationPermission) {
            wifiInfo = wifiManager.connectionInfo
        } else {
            wifiInfo = null
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            refreshConnection()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        while (true) {
            delay(5000)
            if (hasLocationPermission) {
                refreshConnection()
            }
        }
    }

    val scanNetworks = {
        if (hasLocationPermission && !isPaused) {
            @Suppress("DEPRECATION")
            wifiManager.startScan()
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                    if (success || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        try {
                            if (!isPaused) {
                                val results = wifiManager.scanResults
                                scanResults = results.sortedByDescending { it.level }
                                val now = System.currentTimeMillis()
                                lastScanTime = now
                                
                                // Update history
                                results.forEach { res ->
                                    val list = history.getOrPut(res.BSSID) { mutableListOf() }
                                    list.add(Pair(now, res.level))
                                    // Keep only last 60 seconds (approx 12 scans if 5s each)
                                    if (list.size > 20) {
                                        list.removeAt(0)
                                    }
                                }
                            }
                        } catch (e: SecurityException) {
                            // Permission missing
                        }
                    }
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    
    LaunchedEffect(isPaused, hasLocationPermission) {
        while (true) {
            if (!isPaused && hasLocationPermission) {
                scanNetworks()
            }
            delay(5000)
        }
    }

    val filteredResults = scanResults.filter { it.frequency in currentBand.minFreq..currentBand.maxFreq }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📡 Wi-Fi Analyzer", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isPaused = !isPaused }) {
                        Icon(
                            if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Resume" else "Pause"
                        )
                    }
                    IconButton(onClick = { 
                        refreshConnection()
                        if (hasLocationPermission) {
                            @Suppress("DEPRECATION")
                            wifiManager.startScan()
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
            if (!hasLocationPermission) {
                PermissionWarningCard(
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                )
                return@Column
            }

            // Tab Row (Icons only)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TabIcon(
                    icon = Icons.Default.BarChart,
                    selected = currentTab == AnalyzerTab.CHANNEL_GRAPH,
                    onClick = { currentTab = AnalyzerTab.CHANNEL_GRAPH }
                )
                TabIcon(
                    icon = Icons.Default.ShowChart,
                    selected = currentTab == AnalyzerTab.TIME_GRAPH,
                    onClick = { currentTab = AnalyzerTab.TIME_GRAPH }
                )
                TabIcon(
                    icon = Icons.Default.Star,
                    selected = currentTab == AnalyzerTab.BEST_CHANNELS,
                    onClick = { currentTab = AnalyzerTab.BEST_CHANNELS }
                )
                TabIcon(
                    icon = Icons.Default.List,
                    selected = currentTab == AnalyzerTab.ACCESS_POINTS,
                    onClick = { currentTab = AnalyzerTab.ACCESS_POINTS }
                )
            }

            // Band Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row {
                        WifiBand.values().forEach { band ->
                            val isSelected = currentBand == band
                            Box(
                                modifier = Modifier
                                    .clickable { currentBand = band }
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(50)
                                    )
                                    .padding(horizontal = 24.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = band.title,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            // Connected Network Info
            if (wifiInfo != null && wifiInfo!!.networkId != -1) {
                ConnectedNetworkMiniCard(wifiInfo!!, wifiManager)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Content Area
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (currentTab) {
                    AnalyzerTab.CHANNEL_GRAPH -> ChannelGraphView(filteredResults, currentBand, wifiInfo)
                    AnalyzerTab.TIME_GRAPH -> TimeGraphView(filteredResults, history, currentBand, wifiInfo)
                    AnalyzerTab.BEST_CHANNELS -> BestChannelsView(filteredResults, currentBand, wifiInfo, wifiManager)
                    AnalyzerTab.ACCESS_POINTS -> AccessPointsView(filteredResults, wifiInfo, wifiManager)
                }
            }
        }
    }
}

@Composable
fun TabIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PermissionWarningCard(onRequestPermission: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Location Permission Required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "Android requires location permission to scan for Wi-Fi networks.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
fun ConnectedNetworkMiniCard(wifiInfo: WifiInfo, wifiManager: WifiManager) {
    val ssid = if (wifiInfo.ssid != null && wifiInfo.ssid != "<unknown ssid>") wifiInfo.ssid.removeSurrounding("\"") else "Unknown"
    val channel = getChannelForFrequency(wifiInfo.frequency)
    
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Connected: $ssid", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("${wifiInfo.frequency} MHz (Ch $channel) • ${wifiInfo.rssi} dBm • ${wifiInfo.linkSpeed} Mbps", 
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
fun ChannelGraphView(results: List<ScanResult>, band: WifiBand, wifiInfo: WifiInfo?) {
    val textMeasurer = rememberTextMeasurer()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    
    if (results.isEmpty()) {
        EmptyState("No networks found on ${band.title}")
        return
    }

    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val width = size.width
        val height = size.height - 40.dp.toPx() // Leave room for X axis labels
        
        // Draw grid
        val ySteps = 5
        val minDbm = -100f
        val maxDbm = -30f
        val dbmRange = maxDbm - minDbm
        
        for (i in 0..ySteps) {
            val y = height - (height * (i.toFloat() / ySteps))
            val dbmValue = minDbm + (dbmRange * (i.toFloat() / ySteps))
            drawLine(
                color = onSurfaceColor.copy(alpha = 0.1f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
            drawText(
                textMeasurer = textMeasurer,
                text = "${dbmValue.toInt()} dBm",
                topLeft = Offset(0f, y - 15.sp.toPx()),
                style = TextStyle(color = onSurfaceColor.copy(alpha = 0.5f), fontSize = 10.sp)
            )
        }

        // Draw Networks
        val minFreq = band.minFreq.toFloat()
        val maxFreq = band.maxFreq.toFloat()
        val freqRange = maxFreq - minFreq

        results.reversed().forEach { result ->
            val isConnected = wifiInfo?.bssid == result.BSSID
            val color = if (isConnected) Color(0xFF4CAF50) else getColorForBssid(result.BSSID)
            
            val centerFreq = result.frequency.toFloat()
            // Approximate width, typically 20MHz for 2.4, maybe 40/80 for 5GHz
            val bw = if (result.channelWidth == ScanResult.CHANNEL_WIDTH_40MHZ) 40f 
                     else if (result.channelWidth == ScanResult.CHANNEL_WIDTH_80MHZ) 80f 
                     else 20f
            
            val startFreq = centerFreq - bw/2
            val endFreq = centerFreq + bw/2
            
            val startX = ((startFreq - minFreq) / freqRange) * width
            val endX = ((endFreq - minFreq) / freqRange) * width
            val centerX = ((centerFreq - minFreq) / freqRange) * width
            
            val level = max(minDbm, result.level.toFloat())
            val peakY = height - (((level - minDbm) / dbmRange) * height)
            
            val path = Path().apply {
                moveTo(startX, height)
                // Curve to peak
                cubicTo(
                    startX + (centerX - startX) / 2, height,
                    centerX - (centerX - startX) / 4, peakY,
                    centerX, peakY
                )
                cubicTo(
                    centerX + (endX - centerX) / 4, peakY,
                    endX - (endX - centerX) / 2, height,
                    endX, height
                )
                close()
            }
            
            drawPath(
                path = path,
                color = color.copy(alpha = if (isConnected) 0.5f else 0.2f),
                style = Fill
            )
            
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = if (isConnected) 5f else 3f)
            )
            
            // Label
            val ssid = if (result.SSID.isNullOrBlank()) "Hidden" else result.SSID
            drawText(
                textMeasurer = textMeasurer,
                text = ssid,
                topLeft = Offset(centerX - 20.dp.toPx(), peakY - 20.dp.toPx()),
                style = TextStyle(color = color, fontSize = 12.sp, fontWeight = if (isConnected) FontWeight.Bold else FontWeight.Normal)
            )
        }
        
        // Draw X axis labels (Channels)
        val channels = if (band == WifiBand.BAND_2_4_GHZ) listOf(1, 6, 11, 14) else listOf(36, 48, 100, 149, 165)
        channels.forEach { ch ->
            val freq = getFrequencyForChannel(ch, band).toFloat()
            if (freq in minFreq..maxFreq) {
                val x = ((freq - minFreq) / freqRange) * width
                drawText(
                    textMeasurer = textMeasurer,
                    text = ch.toString(),
                    topLeft = Offset(x - 5.dp.toPx(), height + 10.dp.toPx()),
                    style = TextStyle(color = onSurfaceColor, fontSize = 12.sp)
                )
            }
        }
    }
}

@Composable
fun TimeGraphView(results: List<ScanResult>, history: Map<String, List<Pair<Long, Int>>>, band: WifiBand, wifiInfo: WifiInfo?) {
    val textMeasurer = rememberTextMeasurer()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    
    val activeBssids = results.map { it.BSSID }.toSet()
    val graphData = history.filterKeys { it in activeBssids }

    if (graphData.isEmpty()) {
        EmptyState("Waiting for data...")
        return
    }

    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val width = size.width
        val height = size.height - 20.dp.toPx()
        
        // Grid
        val ySteps = 5
        val minDbm = -100f
        val maxDbm = -30f
        val dbmRange = maxDbm - minDbm
        
        for (i in 0..ySteps) {
            val y = height - (height * (i.toFloat() / ySteps))
            val dbmValue = minDbm + (dbmRange * (i.toFloat() / ySteps))
            drawLine(
                color = onSurfaceColor.copy(alpha = 0.1f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
            drawText(
                textMeasurer = textMeasurer,
                text = "${dbmValue.toInt()}",
                topLeft = Offset(0f, y - 15.sp.toPx()),
                style = TextStyle(color = onSurfaceColor.copy(alpha = 0.5f), fontSize = 10.sp)
            )
        }

        val now = System.currentTimeMillis()
        val timeWindow = 60000L // 60 seconds

        graphData.forEach { (bssid, points) ->
            if (points.size > 1) {
                val isConnected = wifiInfo?.bssid == bssid
                val color = if (isConnected) Color(0xFF4CAF50) else getColorForBssid(bssid)
                
                val path = Path()
                var started = false
                
                points.forEach { (time, rssi) ->
                    val age = now - time
                    if (age <= timeWindow) {
                        val x = width - ((age.toFloat() / timeWindow) * width)
                        val level = max(minDbm, rssi.toFloat())
                        val y = height - (((level - minDbm) / dbmRange) * height)
                        
                        if (!started) {
                            path.moveTo(x, y)
                            started = true
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                }
                
                if (started) {
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = if (isConnected) 5f else 3f, join = StrokeJoin.Round)
                    )
                }
            }
        }
    }
}

@Composable
fun BestChannelsView(results: List<ScanResult>, band: WifiBand, wifiInfo: WifiInfo?, wifiManager: WifiManager) {
    val channels = if (band == WifiBand.BAND_2_4_GHZ) (1..13).toList() else listOf(36, 40, 44, 48, 149, 153, 157, 161, 165)
    
    val channelScores = channels.map { ch ->
        val chFreq = getFrequencyForChannel(ch, band)
        // Score = sum of power of networks overlapping with this channel
        var score = 0
        var count = 0
        results.forEach { res ->
            val freqDiff = abs(res.frequency - chFreq)
            if (freqDiff <= 20) { // overlap
                score += (100 + res.level) // -30 -> 70, -90 -> 10
                count++
            }
        }
        Triple(ch, score, count)
    }.sortedBy { it.second }
    
    val bestChannel = channelScores.firstOrNull()
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (bestChannel != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("BEST ${band.title} CHANNEL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = bestChannel.first.toString(),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Clear channel • ${bestChannel.third} overlapping networks", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
        
        item {
            Text("ALL CHANNELS", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        items(channelScores) { (ch, score, count) ->
            val quality = max(0, 100 - score)
            val qualityColor = when {
                quality > 80 -> Color(0xFF4CAF50)
                quality > 50 -> Color(0xFFFF9800)
                else -> Color(0xFFF44336)
            }
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(ch.toString(), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (quality > 80) "Excellent" else if (quality > 50) "Fair" else "Congested")
                            Text("$quality%")
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { quality / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = qualityColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$count networks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun AccessPointsView(results: List<ScanResult>, wifiInfo: WifiInfo?, wifiManager: WifiManager) {
    if (results.isEmpty()) {
        EmptyState("No networks found")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(results) { result ->
            NetworkScanResultCard(result, wifiInfo)
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.WifiOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun NetworkScanResultCard(result: ScanResult, wifiInfo: WifiInfo?) {
    val ssid = if (result.SSID.isNullOrBlank()) "Hidden Network" else result.SSID
    val isConnected = wifiInfo?.bssid == result.BSSID
    
    val channel = when {
        result.frequency in 2412..2484 -> ((result.frequency - 2412) / 5) + 1
        result.frequency in 5170..5825 -> ((result.frequency - 5170) / 5) + 34
        else -> "?"
    }

    val wifiStandard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        when (result.wifiStandard) {
            ScanResult.WIFI_STANDARD_LEGACY -> "Legacy"
            ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4"
            ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5"
            ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6"
            ScanResult.WIFI_STANDARD_11BE -> "Wi-Fi 7"
            else -> ""
        }
    } else ""
    
    val security = when {
        result.capabilities.contains("WPA3") -> "WPA3"
        result.capabilities.contains("WPA2") -> "WPA2"
        result.capabilities.contains("WPA") -> "WPA"
        result.capabilities.contains("WEP") -> "WEP"
        else -> "Open"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Signal Meter Circle
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                val quality = max(0f, minOf(1f, 1f - (abs(result.level) - 30) / 70f))
                CircularProgressIndicator(
                    progress = { quality },
                    modifier = Modifier.fillMaxSize(),
                    color = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer else getColorForBssid(result.BSSID),
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
                Text(
                    text = "${result.level}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ssid,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${result.BSSID} • $security",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${result.frequency} MHz (Ch $channel) ${if(wifiStandard.isNotEmpty()) "• $wifiStandard" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun getChannelForFrequency(freq: Int): Int {
    return when {
        freq in 2412..2484 -> ((freq - 2412) / 5) + 1
        freq in 5170..5825 -> ((freq - 5170) / 5) + 34
        else -> 0
    }
}

private fun getFrequencyForChannel(channel: Int, band: WifiBand): Int {
    return if (band == WifiBand.BAND_2_4_GHZ) {
        2412 + (channel - 1) * 5
    } else {
        5170 + (channel - 34) * 5
    }
}

private fun getColorForBssid(bssid: String): Color {
    val colors = listOf(
        Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7), 
        Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF03A9F4),
        Color(0xFF00BCD4), Color(0xFF009688), Color(0xFFFF9800),
        Color(0xFFFF5722), Color(0xFF795548)
    )
    val hash = abs(bssid.hashCode())
    return colors[hash % colors.size]
}
