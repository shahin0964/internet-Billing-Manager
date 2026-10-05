package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.viewmodel.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WiFiAnalyzerScreen(
    onBackClick: () -> Unit,
    viewModel: WiFiAnalyzerViewModel = viewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()
    val lastScanTime by viewModel.lastScanTime.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val currentBand by viewModel.currentBand.collectAsStateWithLifecycle()
    val connectedWifi by viewModel.connectedWifi.collectAsStateWithLifecycle()
    val allScanResults by viewModel.allScanResults.collectAsStateWithLifecycle()
    val filteredResults by viewModel.filteredScanResults.collectAsStateWithLifecycle()
    val bandCounts by viewModel.bandCounts.collectAsStateWithLifecycle()
    val rssiHistory by viewModel.rssiHistory.collectAsStateWithLifecycle()
    val channelRatings by viewModel.channelRatings.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        viewModel.setPermissionGranted(isGranted)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "📡 Wi-Fi Analyzer",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (isScanning) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(10.dp),
                                            strokeWidth = 1.5.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Scanning",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                        val lastTimeStr = remember(lastScanTime) {
                            lastScanTime?.let {
                                SimpleDateFormat("hh:mm:ss a", Locale.US).format(Date(it))
                            } ?: "Waiting for scan..."
                        }
                        Text(
                            text = if (isPaused) "Paused • Last: $lastTimeStr" else "Live Scan: $lastTimeStr (${allScanResults.size} APs)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    IconButton(onClick = { viewModel.togglePause() }) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Resume" else "Pause",
                            tint = if (isPaused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { viewModel.triggerScan() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scan Now",
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            if (!hasPermission) {
                PermissionWarningCard(
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                )
                return@Column
            }

            // 1. Connected Network Hero Banner
            ConnectedNetworkHeroCard(
                state = connectedWifi,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // 2. Frequency Band Chips (2.4 GHz, 5 GHz, 6 GHz)
            BandSelectorRow(
                currentBand = currentBand,
                bandCounts = bandCounts,
                onSelectBand = { viewModel.setBand(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // 3. Tab Bar Navigation (Segmented M3 Control)
            TabSelectorRow(
                currentTab = currentTab,
                onSelectTab = { viewModel.setTab(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // 4. Tab Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentTab) {
                    AnalyzerTab.CHANNEL_GRAPH -> {
                        ChannelGraphView(
                            results = filteredResults,
                            band = currentBand,
                            connectedWifi = connectedWifi
                        )
                    }
                    AnalyzerTab.TIME_GRAPH -> {
                        TimeGraphView(
                            results = filteredResults,
                            history = rssiHistory,
                            connectedWifi = connectedWifi
                        )
                    }
                    AnalyzerTab.BEST_CHANNELS -> {
                        BestChannelsView(
                            ratings = channelRatings,
                            band = currentBand,
                            totalAps = filteredResults.size
                        )
                    }
                    AnalyzerTab.ACCESS_POINTS -> {
                        AccessPointsListView(
                            results = filteredResults,
                            onCopyBssid = { bssid ->
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("BSSID", bssid))
                                Toast.makeText(context, "Copied BSSID: $bssid", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectedNetworkHeroCard(
    state: ConnectedWifiState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (state.isConnected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (state.isConnected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Signal Level Circular Meter
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (state.isConnected) getSignalColor(state.rssi).copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surface
                    )
                    .border(
                        1.5.dp,
                        if (state.isConnected) getSignalColor(state.rssi)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        CircleShape
                    )
            ) {
                if (state.isConnected) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${state.rssi}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = getSignalColor(state.rssi)
                        )
                        Text(
                            text = "dBm",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (state.isConnected) state.ssid else "No Wi-Fi Connection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.isConnected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF4CAF50).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Connected",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                if (state.isConnected) {
                    Text(
                        text = "Ch ${state.channel} (${state.frequencyMhz} MHz) • ${state.bandTitle} • ${state.linkSpeedMbps} Mbps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "IP: ${state.ipAddress} • BSSID: ${state.bssid}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                } else {
                    Text(
                        text = "Connect to a Wi-Fi network to view local link speed & channel metrics",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun BandSelectorRow(
    currentBand: WifiBand,
    bandCounts: Map<WifiBand, Int>,
    onSelectBand: (WifiBand) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .padding(4.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            WifiBand.values().forEach { band ->
                val isSelected = currentBand == band
                val count = bandCounts[band] ?: 0
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectBand(band) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = band.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabSelectorRow(
    currentTab: AnalyzerTab,
    onSelectTab: (AnalyzerTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TabButton(
            icon = Icons.Default.BarChart,
            title = "Graph",
            selected = currentTab == AnalyzerTab.CHANNEL_GRAPH,
            onClick = { onSelectTab(AnalyzerTab.CHANNEL_GRAPH) },
            modifier = Modifier.weight(1f)
        )
        TabButton(
            icon = Icons.Default.ShowChart,
            title = "Time",
            selected = currentTab == AnalyzerTab.TIME_GRAPH,
            onClick = { onSelectTab(AnalyzerTab.TIME_GRAPH) },
            modifier = Modifier.weight(1f)
        )
        TabButton(
            icon = Icons.Default.Star,
            title = "Rating",
            selected = currentTab == AnalyzerTab.BEST_CHANNELS,
            onClick = { onSelectTab(AnalyzerTab.BEST_CHANNELS) },
            modifier = Modifier.weight(1f)
        )
        TabButton(
            icon = Icons.Default.List,
            title = "APs",
            selected = currentTab == AnalyzerTab.ACCESS_POINTS,
            onClick = { onSelectTab(AnalyzerTab.ACCESS_POINTS) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun TabButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ChannelGraphView(
    results: List<AccessPointItem>,
    band: WifiBand,
    connectedWifi: ConnectedWifiState
) {
    val textMeasurer = rememberTextMeasurer()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    if (results.isEmpty()) {
        EmptyStateBox(
            icon = Icons.Default.WifiOff,
            title = "No APs detected on ${band.title}",
            subtitle = "Pull to refresh or wait for the next background scan cycle"
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val bottomPadding = 36.dp.toPx()
            val leftPadding = 32.dp.toPx()
            val graphWidth = width - leftPadding
            val graphHeight = size.height - bottomPadding

            val minDbm = -100f
            val maxDbm = -30f
            val dbmRange = maxDbm - minDbm

            // 1. Draw horizontal dBm grid lines & labels
            val ySteps = 7 // -100, -90, -80, -70, -60, -50, -40, -30
            for (i in 0..ySteps) {
                val ratio = i.toFloat() / ySteps
                val y = graphHeight - (graphHeight * ratio)
                val dbm = (minDbm + (dbmRange * ratio)).toInt()

                drawLine(
                    color = onSurfaceColor.copy(alpha = 0.08f),
                    start = Offset(leftPadding, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )

                drawText(
                    textMeasurer = textMeasurer,
                    text = "$dbm",
                    topLeft = Offset(2.dp.toPx(), y - 7.sp.toPx()),
                    style = TextStyle(
                        color = onSurfaceColor.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            // 2. Frequency Range Mapping
            val minFreq = band.minFreq.toFloat()
            val maxFreq = band.maxFreq.toFloat()
            val freqRange = maxFreq - minFreq

            // 3. Draw Parabolic AP Curves (sorted so connected AP is drawn on top)
            val sortedList = results.sortedBy { it.isConnected }

            sortedList.forEach { ap ->
                val centerFreq = ap.frequencyMhz.toFloat()
                val bw = ap.channelWidthMhz.toFloat().coerceAtLeast(20f)
                val startFreq = centerFreq - (bw / 2f)
                val endFreq = centerFreq + (bw / 2f)

                val startX = leftPadding + (((startFreq - minFreq) / freqRange) * graphWidth)
                val endX = leftPadding + (((endFreq - minFreq) / freqRange) * graphWidth)
                val centerX = leftPadding + (((centerFreq - minFreq) / freqRange) * graphWidth)

                val level = ap.levelDbm.toFloat().coerceIn(minDbm, maxDbm)
                val peakY = graphHeight - (((level - minDbm) / dbmRange) * graphHeight)

                val apColor = if (ap.isConnected) Color(0xFF00E676) else getColorForBssid(ap.bssid)

                val path = Path().apply {
                    moveTo(startX, graphHeight)
                    // Smooth Bezier Curve Peak
                    cubicTo(
                        startX + (centerX - startX) * 0.45f, graphHeight,
                        centerX - (centerX - startX) * 0.25f, peakY,
                        centerX, peakY
                    )
                    cubicTo(
                        centerX + (endX - centerX) * 0.25f, peakY,
                        endX - (endX - centerX) * 0.45f, graphHeight,
                        endX, graphHeight
                    )
                    close()
                }

                // Fill Area
                drawPath(
                    path = path,
                    color = apColor.copy(alpha = if (ap.isConnected) 0.45f else 0.18f),
                    style = Fill
                )

                // Stroke
                drawPath(
                    path = path,
                    color = apColor,
                    style = Stroke(
                        width = if (ap.isConnected) 3.5.dp.toPx() else 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Peak Dot & Label
                drawCircle(
                    color = apColor,
                    radius = if (ap.isConnected) 5.dp.toPx() else 3.dp.toPx(),
                    center = Offset(centerX, peakY)
                )

                val label = if (ap.ssid.length > 14) ap.ssid.take(12) + ".." else ap.ssid
                drawText(
                    textMeasurer = textMeasurer,
                    text = "$label (${ap.levelDbm})",
                    topLeft = Offset(
                        (centerX - 30.dp.toPx()).coerceIn(leftPadding, width - 80.dp.toPx()),
                        (peakY - 16.dp.toPx()).coerceAtLeast(0f)
                    ),
                    style = TextStyle(
                        color = if (ap.isConnected) Color(0xFF00E676) else apColor,
                        fontSize = 10.sp,
                        fontWeight = if (ap.isConnected) FontWeight.ExtraBold else FontWeight.SemiBold
                    )
                )
            }

            // 4. Draw X-Axis Channel Markers
            val channels = when (band) {
                WifiBand.BAND_2_4_GHZ -> listOf(1, 3, 6, 9, 11, 13, 14)
                WifiBand.BAND_5_GHZ -> listOf(36, 44, 52, 60, 100, 116, 132, 149, 157, 165)
                WifiBand.BAND_6_GHZ -> listOf(1, 17, 33, 49, 65, 81, 97)
            }

            channels.forEach { ch ->
                val freq = WiFiAnalyzerViewModel.getFrequencyForChannel(ch, band).toFloat()
                if (freq in minFreq..maxFreq) {
                    val x = leftPadding + (((freq - minFreq) / freqRange) * graphWidth)

                    drawLine(
                        color = onSurfaceColor.copy(alpha = 0.12f),
                        start = Offset(x, graphHeight),
                        end = Offset(x, graphHeight + 6.dp.toPx()),
                        strokeWidth = 1.5f
                    )

                    drawText(
                        textMeasurer = textMeasurer,
                        text = "CH $ch",
                        topLeft = Offset(x - 12.dp.toPx(), graphHeight + 8.dp.toPx()),
                        style = TextStyle(
                            color = onSurfaceColor.copy(alpha = 0.7f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun TimeGraphView(
    results: List<AccessPointItem>,
    history: Map<String, List<Pair<Long, Int>>>,
    connectedWifi: ConnectedWifiState
) {
    val textMeasurer = rememberTextMeasurer()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    val activeBssids = results.map { it.bssid }.toSet()
    val graphData = history.filterKeys { it in activeBssids }

    if (graphData.isEmpty()) {
        EmptyStateBox(
            icon = Icons.Default.Timeline,
            title = "Gathering signal history...",
            subtitle = "Scanning signal strength fluctuations in real-time"
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val bottomPadding = 30.dp.toPx()
            val leftPadding = 32.dp.toPx()
            val graphWidth = width - leftPadding
            val graphHeight = size.height - bottomPadding

            val minDbm = -100f
            val maxDbm = -30f
            val dbmRange = maxDbm - minDbm

            // Grid
            val ySteps = 7
            for (i in 0..ySteps) {
                val ratio = i.toFloat() / ySteps
                val y = graphHeight - (graphHeight * ratio)
                val dbm = (minDbm + (dbmRange * ratio)).toInt()

                drawLine(
                    color = onSurfaceColor.copy(alpha = 0.08f),
                    start = Offset(leftPadding, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )

                drawText(
                    textMeasurer = textMeasurer,
                    text = "$dbm",
                    topLeft = Offset(2.dp.toPx(), y - 7.sp.toPx()),
                    style = TextStyle(
                        color = onSurfaceColor.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            val now = System.currentTimeMillis()
            val timeWindow = 60000L // 60s

            graphData.forEach { (bssid, points) ->
                if (points.size >= 2) {
                    val isConnected = connectedWifi.bssid.equals(bssid, ignoreCase = true)
                    val color = if (isConnected) Color(0xFF00E676) else getColorForBssid(bssid)

                    val path = Path()
                    var started = false
                    var latestPoint: Offset? = null

                    points.forEach { (time, rssi) ->
                        val age = now - time
                        if (age <= timeWindow) {
                            val x = width - ((age.toFloat() / timeWindow) * graphWidth)
                            val level = rssi.toFloat().coerceIn(minDbm, maxDbm)
                            val y = graphHeight - (((level - minDbm) / dbmRange) * graphHeight)

                            val pt = Offset(x, y)
                            if (!started) {
                                path.moveTo(x, y)
                                started = true
                            } else {
                                path.lineTo(x, y)
                            }
                            latestPoint = pt
                        }
                    }

                    if (started) {
                        drawPath(
                            path = path,
                            color = color,
                            style = Stroke(
                                width = if (isConnected) 3.5.dp.toPx() else 1.8.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        latestPoint?.let { pt ->
                            drawCircle(
                                color = color,
                                radius = if (isConnected) 4.5.dp.toPx() else 3.dp.toPx(),
                                center = pt
                            )
                        }
                    }
                }
            }

            // X-Axis time markers
            val timeMarkers = listOf(60 to "60s ago", 45 to "45s", 30 to "30s", 15 to "15s", 0 to "Now")
            timeMarkers.forEach { (sec, label) ->
                val x = width - ((sec.toFloat() / 60f) * graphWidth)
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    topLeft = Offset(x - 14.dp.toPx(), graphHeight + 8.dp.toPx()),
                    style = TextStyle(
                        color = onSurfaceColor.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
fun BestChannelsView(
    ratings: List<ChannelRating>,
    band: WifiBand,
    totalAps: Int
) {
    if (ratings.isEmpty()) {
        EmptyStateBox(
            icon = Icons.Default.StarOutline,
            title = "Analyzing channel interference...",
            subtitle = "Calculating clean channels on ${band.title}"
        )
        return
    }

    val topChannel = ratings.firstOrNull()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Recommendation Hero Card
        if (topChannel != null) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1B5E20).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF4CAF50).copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Recommend,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "TOP RECOMMENDED ${band.title} CHANNEL",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Channel ${topChannel.channel}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "${topChannel.frequencyMhz} MHz • ${topChannel.activeApCount} Active APs • ${topChannel.qualityPercent}% Quality Score",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "CHANNEL CONGESTION & RATINGS (${band.title})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(ratings) { item ->
            val qualityColor = when {
                item.qualityPercent >= 80 -> Color(0xFF2E7D32)
                item.qualityPercent >= 60 -> Color(0xFF00897B)
                item.qualityPercent >= 45 -> Color(0xFFF57C00)
                else -> Color(0xFFD32F2F)
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (item.isCurrentConnected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Channel Circle Badge
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(qualityColor.copy(alpha = 0.15f))
                            .border(1.dp, qualityColor, CircleShape)
                    ) {
                        Text(
                            text = "${item.channel}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = qualityColor
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.statusText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (item.isCurrentConnected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "Current",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "${item.qualityPercent}%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = qualityColor
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        LinearProgressIndicator(
                            progress = { item.qualityPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = qualityColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${item.frequencyMhz} MHz • ${item.activeApCount} co-channel APs",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
fun AccessPointsListView(
    results: List<AccessPointItem>,
    onCopyBssid: (String) -> Unit
) {
    if (results.isEmpty()) {
        EmptyStateBox(
            icon = Icons.Default.WifiOff,
            title = "No Access Points Detected",
            subtitle = "Ensure Wi-Fi and Location are turned on in device settings"
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(results, key = { it.bssid }) { ap ->
            AccessPointCard(ap = ap, onCopyBssid = onCopyBssid)
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
fun AccessPointCard(
    ap: AccessPointItem,
    onCopyBssid: (String) -> Unit
) {
    val signalColor = getSignalColor(ap.levelDbm)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (ap.isConnected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (ap.isConnected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Signal dBm Badge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(signalColor.copy(alpha = 0.15f))
                    .border(1.dp, signalColor, CircleShape)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${ap.levelDbm}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = signalColor
                    )
                    Text(
                        text = "dBm",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ap.ssid,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (ap.isConnected) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF4CAF50).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ap.bssid,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy BSSID",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { onCopyBssid(ap.bssid) }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Metadata tags row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TagChip(text = "Ch ${ap.channel} (${ap.frequencyMhz}M)")
                    TagChip(text = ap.wifiStandard)
                    TagChip(text = "${ap.channelWidthMhz} MHz")
                    TagChip(text = ap.security)
                }
            }
        }
    }
}

@Composable
fun TagChip(text: String) {
    if (text.isBlank()) return
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun EmptyStateBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(16.dp)
                        .size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PermissionWarningCard(onRequestPermission: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Location Permission Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Text(
                text = "Android OS requires fine location permission to perform Wi-Fi channel and access point scanning for Wi-Fi Analyzer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Grant Location Permission", fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun getSignalColor(levelDbm: Int): Color {
    return when {
        levelDbm >= -55 -> Color(0xFF00E676) // Excellent (Emerald Green)
        levelDbm >= -67 -> Color(0xFF00B0FF) // Good (Cyan/Light Blue)
        levelDbm >= -75 -> Color(0xFFFFD600) // Fair (Yellow)
        levelDbm >= -85 -> Color(0xFFFF9100) // Weak (Orange)
        else -> Color(0xFFFF1744)            // Very Weak (Red)
    }
}

fun getColorForBssid(bssid: String): Color {
    val palette = listOf(
        Color(0xFF2979FF), Color(0xFF00E5FF), Color(0xFF76FF03),
        Color(0xFFFFEA00), Color(0xFFFF9100), Color(0xFFFF1744),
        Color(0xFFD500F9), Color(0xFF651FFF), Color(0xFF00B0FF),
        Color(0xFF00E676), Color(0xFFFF3D00)
    )
    val hash = abs(bssid.hashCode())
    return palette[hash % palette.size]
}
