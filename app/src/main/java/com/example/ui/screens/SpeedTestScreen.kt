package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.viewmodel.SpeedTestViewModel
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
fun SpeedTestScreen(
    onBackClick: () -> Unit,
    viewModel: SpeedTestViewModel = viewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    val networkInfo by viewModel.networkInfo.collectAsStateWithLifecycle()
    val serverList by viewModel.serverList.collectAsStateWithLifecycle()
    val selectedServerId by viewModel.selectedServerId.collectAsStateWithLifecycle()
    val isUpdatingServers by viewModel.isUpdatingServers.collectAsStateWithLifecycle()
    val serverUpdateStatus by viewModel.serverUpdateStatus.collectAsStateWithLifecycle()
    val lastServerUpdateTime by viewModel.lastServerUpdateTime.collectAsStateWithLifecycle()
    val isProbingServers by viewModel.isProbingServers.collectAsStateWithLifecycle()

    val testPhase by viewModel.testPhase.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTesting.collectAsStateWithLifecycle()
    val testProgress by viewModel.testProgress.collectAsStateWithLifecycle()

    val pingMs by viewModel.pingMs.collectAsStateWithLifecycle()
    val jitterMs by viewModel.jitterMs.collectAsStateWithLifecycle()
    val downloadMbps by viewModel.downloadMbps.collectAsStateWithLifecycle()
    val uploadMbps by viewModel.uploadMbps.collectAsStateWithLifecycle()

    val activeTestServer by viewModel.activeTestServer.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val historyList by viewModel.historyList.collectAsStateWithLifecycle()

    val selectedServer = remember(selectedServerId, serverList) {
        serverList.find { it.id == selectedServerId } ?: serverList.firstOrNull() ?: DEFAULT_BD_SERVERS.first()
    }

    var showServerSelector by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopTest()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⚡ Speed Test",
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Pro",
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
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.fetchCurrentNetworkInfo(context) { _, _ -> }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = "Refresh Network",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Feature 2: Real Network IP & ISP Auto-Detection Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (networkInfo.connectionType.contains("Wi-Fi"))
                                        Color(0xFFE8F5E9) else Color(0xFFE3F2FD),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (networkInfo.connectionType.contains("Wi-Fi"))
                                            Icons.Default.Wifi else Icons.Default.Public,
                                        contentDescription = null,
                                        tint = if (networkInfo.connectionType.contains("Wi-Fi"))
                                            Color(0xFF2E7D32) else Color(0xFF1565C0),
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Active Network Connection",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = networkInfo.connectionType,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    viewModel.fetchCurrentNetworkInfo(context) { _, _ -> }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                if (networkInfo.isDetecting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Detect IP & ISP",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Public IP Box
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PUBLIC IP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = networkInfo.ip,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Detected ISP Name Box
                            Column(
                                modifier = Modifier.weight(1.3f),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = "DETECTED ISP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = networkInfo.ispName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (networkInfo.cityCountry.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = networkInfo.cityCountry,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Feature 1: Server Update Card UI ("Live ISP Server Update")
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(9.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Live ISP Server Update",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Online Speed Test Servers (${serverList.size - 1} Ready)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Update Now Button
                            Button(
                                onClick = {
                                    viewModel.refreshOnlineServers { _, _ -> }
                                },
                                enabled = !isUpdatingServers,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                if (isUpdatingServers) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Updating...", style = MaterialTheme.typography.labelMedium)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Update Now", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Last updated or sync message
                        val updateTimeStr = remember(lastServerUpdateTime) {
                            if (lastServerUpdateTime > 0L) {
                                SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.US).format(Date(lastServerUpdateTime))
                            } else {
                                "Built-in verified endpoints"
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF4CAF50),
                                    modifier = Modifier.size(8.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isUpdatingServers) "Fetching online ISP endpoints..." else "Last Synced: $updateTimeStr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = "Auto-Failover Active",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Selected Server Card
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
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Target Speed Test Server",
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
                                    text = if (selectedServer.isAuto) "Automatic Server Discovery (Lowest Ping)" else "${selectedServer.city} • ${selectedServer.host}",
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
                            TestPhase.FINDING_SERVER -> "Finding lowest-latency ISP server..."
                            TestPhase.TESTING_PING -> "Testing Ping & Jitter..."
                            TestPhase.TESTING_DOWNLOAD -> "Testing Download Speed..."
                            TestPhase.TESTING_UPLOAD -> "Testing Upload Speed..."
                            TestPhase.COMPLETED -> "Speed Test Complete!"
                            TestPhase.FAILED -> "Speed Test Failed"
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
                                onClick = { viewModel.stopTest() },
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
                                onClick = { viewModel.startTest(context) },
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
                        TextButton(onClick = { viewModel.clearHistory(context) }) {
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

            item { Spacer(modifier = Modifier.height(48.dp)) }
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
                        onClick = { viewModel.probeAllServers() },
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
                                    viewModel.selectServer(srv.id, context)
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
                                            viewModel.selectServer(srv.id, context)
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
