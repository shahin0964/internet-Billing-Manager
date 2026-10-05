package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.ui.viewmodel.IspViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedFeaturesScreen(
    onBackClick: () -> Unit,
    onOpenExpenseManagement: () -> Unit = {},
    viewModel: IspViewModel = viewModel()
) {
    var showAdvancedNetwork by remember { mutableStateOf(false) }
    var showNetworkTools by remember { mutableStateOf(false) }
    var showSpeedTest by remember { mutableStateOf(false) }
    var showWifiAnalyzer by remember { mutableStateOf(false) }
    var showReceiptCustomization by remember { mutableStateOf(false) }
    var showImportCustomers by remember { mutableStateOf(false) }
    var showAutomaticSms by remember { mutableStateOf(false) }
    var showActivityAndAuditLog by remember { mutableStateOf(false) }

    if (showAdvancedNetwork) {
        BackHandler { showAdvancedNetwork = false }
        AdvancedNetworkScreen(
            onBackClick = { showAdvancedNetwork = false }
        )
        return
    }

    if (showNetworkTools) {
        BackHandler { showNetworkTools = false }
        NetworkToolsScreen(
            onBackClick = { showNetworkTools = false }
        )
        return
    }

    if (showSpeedTest) {
        BackHandler { showSpeedTest = false }
        SpeedTestScreen(
            onBackClick = { showSpeedTest = false }
        )
        return
    }

    if (showWifiAnalyzer) {
        BackHandler { showWifiAnalyzer = false }
        WiFiAnalyzerScreen(
            onBackClick = { showWifiAnalyzer = false }
        )
        return
    }

    if (showReceiptCustomization) {
        BackHandler { showReceiptCustomization = false }
        ReceiptCustomizationScreen(
            viewModel = viewModel,
            onBackClick = { showReceiptCustomization = false }
        )
        return
    }

    if (showImportCustomers) {
        BackHandler { showImportCustomers = false }
        ImportCustomersScreen(
            onBackClick = { showImportCustomers = false },
            viewModel = viewModel
        )
        return
    }

    if (showAutomaticSms) {
        BackHandler { showAutomaticSms = false }
        AutomaticSmsScreen(
            onBackClick = { showAutomaticSms = false }
        )
        return
    }

    if (showActivityAndAuditLog) {
        BackHandler { showActivityAndAuditLog = false }
        ActivityAndAuditLogScreen(
            onBackClick = { showActivityAndAuditLog = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.advanced_features),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_to_list)
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==========================================
            // SECTION 1: NETWORK & DIAGNOSTICS
            // ==========================================
            item {
                CategorySectionHeader(
                    title = "Network & Diagnostics",
                    subtitle = "Diagnostic suite, speed test, and wireless analysis",
                    badgeText = "4 Tools",
                    badgeColor = MaterialTheme.colorScheme.primaryContainer,
                    badgeTextColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // 1. Network 🛜 (Advanced Suite)
            item {
                AdvancedFeatureCard(
                    title = "Network 🛜",
                    subtitle = "Port Scanner, Subnet/CIDR, Traceroute, MAC Lookup, Whois",
                    badge = "Advanced",
                    icon = Icons.Default.Dns,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTintColor = MaterialTheme.colorScheme.primary,
                    onClick = { showAdvancedNetwork = true }
                )
            }

            // 2. Network Tools (Ping, DNS, IP, Reachability)
            item {
                AdvancedFeatureCard(
                    title = "Network Tools 🛠️",
                    subtitle = "Ping latency, DNS lookup, IP check, and router reachability",
                    badge = "Essential",
                    icon = Icons.Default.Language,
                    iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconTintColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { showNetworkTools = true }
                )
            }

            // 3. Speed Test (Internet download & upload speed)
            item {
                AdvancedFeatureCard(
                    title = "⚡ Speed Test",
                    subtitle = "Benchmark download, upload speed, latency, and jitter",
                    badge = "Live ISP",
                    icon = Icons.Default.Speed,
                    iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconTintColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { showSpeedTest = true }
                )
            }

            // 4. Wi-Fi Analyzer (Analyze Wi-Fi signal and nearby networks)
            item {
                AdvancedFeatureCard(
                    title = "📡 Wi-Fi Analyzer",
                    subtitle = "Real-time 2.4/5/6 GHz channels, signal graph, and ratings",
                    badge = "Real Scan",
                    icon = Icons.Default.Wifi,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    iconTintColor = MaterialTheme.colorScheme.primary,
                    onClick = { showWifiAnalyzer = true }
                )
            }

            // ==========================================
            // SECTION 2: BUSINESS & OPERATIONS
            // ==========================================
            item {
                Spacer(modifier = Modifier.height(8.dp))
                CategorySectionHeader(
                    title = "Business & Operations",
                    subtitle = "Customer billing, operations, automation, and logs",
                    badgeText = "Management",
                    badgeColor = MaterialTheme.colorScheme.secondaryContainer,
                    badgeTextColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            // 1. Receipt Customization
            item {
                AdvancedFeatureCard(
                    title = stringResource(R.string.receipt_customization),
                    subtitle = stringResource(R.string.receipt_customization_subtitle),
                    badge = null,
                    icon = Icons.Default.ReceiptLong,
                    iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTintColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { showReceiptCustomization = true }
                )
            }

            // 2. Customer Import & Export
            item {
                AdvancedFeatureCard(
                    title = stringResource(R.string.customer_import_export_title),
                    subtitle = stringResource(R.string.customer_import_export_subtitle),
                    badge = "CSV / JSON",
                    icon = Icons.Default.GroupAdd,
                    iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTintColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { showImportCustomers = true }
                )
            }

            // 3. Expense Management
            item {
                AdvancedFeatureCard(
                    title = stringResource(R.string.expense_management),
                    subtitle = stringResource(R.string.expense_management_subtitle),
                    badge = "Finance",
                    icon = Icons.Default.Payments,
                    iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTintColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onOpenExpenseManagement() }
                )
            }

            // 4. Automatic SMS & Notifications
            item {
                AdvancedFeatureCard(
                    title = stringResource(R.string.automatic_sms),
                    subtitle = stringResource(R.string.automatic_sms_subtitle),
                    badge = "Automated",
                    icon = Icons.AutoMirrored.Filled.Send,
                    iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTintColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { showAutomaticSms = true }
                )
            }

            // 5. Activity & Audit Log
            item {
                AdvancedFeatureCard(
                    title = "🔐 Activity & Audit Log",
                    subtitle = "Track user actions, billing operations, and system events",
                    badge = "Security",
                    icon = Icons.Default.Security,
                    iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    iconTintColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { showActivityAndAuditLog = true }
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun CategorySectionHeader(
    title: String,
    subtitle: String,
    badgeText: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primaryContainer,
    badgeTextColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            if (!badgeText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AdvancedFeatureCard(
    title: String,
    subtitle: String,
    badge: String? = null,
    icon: ImageVector,
    iconContainerColor: Color,
    iconTintColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
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
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = iconContainerColor,
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTintColor,
                        modifier = Modifier
                            .padding(11.dp)
                            .fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!badge.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
