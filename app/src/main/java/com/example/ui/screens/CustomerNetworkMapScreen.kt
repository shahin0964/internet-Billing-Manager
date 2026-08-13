package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CustomerEntity
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.viewmodel.IspViewModel
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomerNetworkMapScreen(
    onBackClick: () -> Unit,
    viewModel: IspViewModel
) {
    val context = LocalContext.current
    val customers by viewModel.customers.collectAsState()

    // Filters
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("ALL") } // ALL, ACTIVE, INACTIVE, SUSPENDED
    var selectedPackageFilter by remember { mutableStateOf("ALL") }
    var selectedAreaFilter by remember { mutableStateOf("ALL") }
    var selectedOltFilter by remember { mutableStateOf("ALL") }

    // Dialog & Panel States
    var showFilterSheet by remember { mutableStateOf(false) }
    var showAreaSummarySheet by remember { mutableStateOf(false) }
    var showSummaryDashboard by remember { mutableStateOf(true) }
    var selectedCustomerForDetails by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerToEditLocation by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerToEditNetwork by remember { mutableStateOf<CustomerEntity?>(null) }

    // Pin Location Mode on Map
    var isPinModeActive by remember { mutableStateOf(false) }
    var targetCustomerForPinning by remember { mutableStateOf<CustomerEntity?>(null) }

    // Map Zoom & Pan Canvas State
    var zoomLevel by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Calculate real data metrics from SQLite DB
    val totalCustomers = customers.size
    val activeCustomers = customers.count { it.status.equals("ACTIVE", ignoreCase = true) }
    val suspendedCustomers = customers.count { it.status.equals("SUSPENDED", ignoreCase = true) }
    val inactiveCustomers = customers.count { it.status.equals("INACTIVE", ignoreCase = true) || it.status.equals("EXPIRED", ignoreCase = true) }
    val mappedCustomersList = customers.filter { it.latitude != 0.0 || it.longitude != 0.0 }
    val unmappedCustomersList = customers.filter { it.latitude == 0.0 && it.longitude == 0.0 }
    
    val areaList = customers.map { it.area.ifBlank { "Unassigned Area" } }.distinct().sorted()
    val packageList = customers.map { it.packageName.ifBlank { "Standard Package" } }.distinct().sorted()
    val oltList = customers.map { it.oltName.ifBlank { "Default OLT" } }.distinct().sorted()

    // Filter customers according to real selected criteria
    val filteredCustomers = customers.filter { customer ->
        val matchesSearch = searchQuery.isBlank() ||
                customer.name.contains(searchQuery, ignoreCase = true) ||
                customer.customerCode.contains(searchQuery, ignoreCase = true) ||
                customer.phone.contains(searchQuery, ignoreCase = true) ||
                customer.pppoeUsername.contains(searchQuery, ignoreCase = true) ||
                customer.area.contains(searchQuery, ignoreCase = true) ||
                customer.zone.contains(searchQuery, ignoreCase = true) ||
                customer.oltName.contains(searchQuery, ignoreCase = true) ||
                customer.onuSerial.contains(searchQuery, ignoreCase = true)

        val matchesStatus = selectedStatusFilter == "ALL" ||
                customer.status.equals(selectedStatusFilter, ignoreCase = true)

        val matchesPackage = selectedPackageFilter == "ALL" ||
                customer.packageName.equals(selectedPackageFilter, ignoreCase = true)

        val matchesArea = selectedAreaFilter == "ALL" ||
                (if (selectedAreaFilter == "Unassigned Area") customer.area.isBlank() else customer.area.equals(selectedAreaFilter, ignoreCase = true))

        val matchesOlt = selectedOltFilter == "ALL" ||
                (if (selectedOltFilter == "Default OLT") customer.oltName.isBlank() else customer.oltName.equals(selectedOltFilter, ignoreCase = true))

        matchesSearch && matchesStatus && matchesPackage && matchesArea && matchesOlt
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "🗺️ Customer Network Map",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Mapped: ${mappedCustomersList.size} / $totalCustomers Customers",
                            style = MaterialTheme.typography.labelSmall,
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
                    IconButton(onClick = { showAreaSummarySheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "Area Summary",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter",
                            tint = if (selectedStatusFilter != "ALL" || selectedAreaFilter != "ALL" || selectedPackageFilter != "ALL" || selectedOltFilter != "ALL") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showSummaryDashboard = !showSummaryDashboard }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Toggle Summary"
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0F172A)) // Dark slate map background
        ) {
            // Interactive Map Canvas
            InteractiveNetworkMapCanvas(
                customers = filteredCustomers,
                zoomLevel = zoomLevel,
                panOffset = panOffset,
                onPanZoomChange = { newOffset, newZoom ->
                    panOffset = newOffset
                    zoomLevel = newZoom
                },
                onCustomerMarkerClick = { customer ->
                    selectedCustomerForDetails = customer
                },
                isPinModeActive = isPinModeActive,
                onMapTapForPin = { lat, lng ->
                    targetCustomerForPinning?.let { cust ->
                        val updated = cust.copy(latitude = lat, longitude = lng)
                        viewModel.updateCustomer(updated)
                        Toast.makeText(context, "📍 Location saved for ${cust.name}", Toast.LENGTH_SHORT).show()
                        isPinModeActive = false
                        targetCustomerForPinning = null
                    }
                }
            )

            // Top Search & Quick Summary Floating Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .align(Alignment.TopCenter)
            ) {
                // Search Input
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search name, ID, phone, area, OLT...", fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Summary Chips Bar
                AnimatedVisibility(visible = showSummaryDashboard) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        FlowRow(
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            MapSummaryBadge("Total", "$totalCustomers", MaterialTheme.colorScheme.primary)
                            MapSummaryBadge("Active", "$activeCustomers", EmeraldSuccess)
                            MapSummaryBadge("Suspended", "$suspendedCustomers", Color(0xFFF59E0B))
                            MapSummaryBadge("Expired", "$inactiveCustomers", MaterialTheme.colorScheme.error)
                            MapSummaryBadge("Mapped", "${mappedCustomersList.size}", Color(0xFF0284C7))
                            MapSummaryBadge("Unmapped", "${unmappedCustomersList.size}", Color(0xFF64748B))
                        }
                    }
                }

                // Search Results Dropdown List if typing
                if (searchQuery.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .heightIn(max = 220.dp)
                    ) {
                        LazyColumn {
                            items(filteredCustomers.take(10)) { customer ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCustomerForDetails = customer
                                            searchQuery = ""
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${customer.name} (${customer.customerCode})",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "Area: ${customer.area.ifBlank { "Unassigned" }} • OLT: ${customer.oltName.ifBlank { "N/A" }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    StatusBadgeChip(status = customer.status)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }

            // Pinning Mode Notification Banner
            if (isPinModeActive) {
                Surface(
                    color = Color(0xFF0284C7),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 110.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.PinDrop, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tap on the map to pin location for ${targetCustomerForPinning?.name ?: "Customer"}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                isPinModeActive = false
                                targetCustomerForPinning = null
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                        }
                    }
                }
            }

            // Floating Map Zoom & Action Controls (Bottom-Right)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pin Unmapped Customer Quick Button
                if (unmappedCustomersList.isNotEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(46.dp)
                            .clickable {
                                targetCustomerForPinning = unmappedCustomersList.first()
                                isPinModeActive = true
                                Toast
                                    .makeText(
                                        context,
                                        "Tap map to locate ${unmappedCustomersList.first().name}",
                                        Toast.LENGTH_SHORT
                                    )
                                    .show()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PinDrop,
                                contentDescription = "Pin Unmapped Customer",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // Reset Map View Button
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .size(46.dp)
                        .clickable {
                            zoomLevel = 1.0f
                            panOffset = Offset.Zero
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Reset Zoom & Pan",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Zoom In
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .size(46.dp)
                        .clickable { zoomLevel = (zoomLevel * 1.3f).coerceAtMost(5.0f) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Zoom In",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Zoom Out
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .size(46.dp)
                        .clickable { zoomLevel = (zoomLevel / 1.3f).coerceAtLeast(0.4f) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Zoom Out",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Customer Details Bottom Sheet
            selectedCustomerForDetails?.let { customer ->
                CustomerDetailsBottomSheet(
                    customer = customer,
                    onDismiss = { selectedCustomerForDetails = null },
                    onEditLocation = {
                        customerToEditLocation = customer
                        selectedCustomerForDetails = null
                    },
                    onEditNetwork = {
                        customerToEditNetwork = customer
                        selectedCustomerForDetails = null
                    },
                    onPinOnMap = {
                        targetCustomerForPinning = customer
                        isPinModeActive = true
                        selectedCustomerForDetails = null
                        Toast.makeText(context, "Tap on the map to set pin location", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Filter Bottom Sheet
            if (showFilterSheet) {
                MapFilterDialog(
                    selectedStatus = selectedStatusFilter,
                    selectedArea = selectedAreaFilter,
                    selectedPackage = selectedPackageFilter,
                    selectedOlt = selectedOltFilter,
                    areaList = areaList,
                    packageList = packageList,
                    oltList = oltList,
                    onApplyFilters = { status, area, pkg, olt ->
                        selectedStatusFilter = status
                        selectedAreaFilter = area
                        selectedPackageFilter = pkg
                        selectedOltFilter = olt
                        showFilterSheet = false
                    },
                    onDismiss = { showFilterSheet = false }
                )
            }

            // Area Summary Bottom Sheet
            if (showAreaSummarySheet) {
                AreaSummaryDialog(
                    customers = customers,
                    areaList = areaList,
                    onSelectArea = { area ->
                        selectedAreaFilter = area
                        showAreaSummarySheet = false
                    },
                    onDismiss = { showAreaSummarySheet = false }
                )
            }

            // Edit Location Dialog
            customerToEditLocation?.let { customer ->
                EditCustomerLocationDialog(
                    customer = customer,
                    onSave = { updatedCust ->
                        viewModel.updateCustomer(updatedCust)
                        customerToEditLocation = null
                        Toast.makeText(context, "Location updated successfully", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { customerToEditLocation = null }
                )
            }

            // Edit Network Dialog
            customerToEditNetwork?.let { customer ->
                EditCustomerNetworkDialog(
                    customer = customer,
                    onSave = { updatedCust ->
                        viewModel.updateCustomer(updatedCust)
                        customerToEditNetwork = null
                        Toast.makeText(context, "Network mapping updated", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { customerToEditNetwork = null }
                )
            }
        }
    }
}

/**
 * Interactive Network Map Canvas that renders customer markers, OLT/Router nodes, and connection lines.
 */
@Composable
private fun InteractiveNetworkMapCanvas(
    customers: List<CustomerEntity>,
    zoomLevel: Float,
    panOffset: Offset,
    onPanZoomChange: (Offset, Float) -> Unit,
    onCustomerMarkerClick: (CustomerEntity) -> Unit,
    isPinModeActive: Boolean,
    onMapTapForPin: (Double, Double) -> Unit
) {
    // Generate static base reference point (e.g. Center of Bangladesh / Dhaka 23.8103, 90.4125)
    val baseLat = 23.8103
    val baseLng = 90.4125

    // Store mapped positions for click detection
    var clickTargets by remember { mutableStateOf<List<Pair<CustomerEntity, Offset>>>(emptyList()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newZoom = (zoomLevel * zoom).coerceIn(0.4f, 5.0f)
                    val newPan = panOffset + pan
                    onPanZoomChange(newPan, newZoom)
                }
            }
            .pointerInput(clickTargets, isPinModeActive) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val position = event.changes.firstOrNull()?.position ?: continue
                        if (event.changes.firstOrNull()?.pressed == false) {
                            // Check if tapped a customer marker
                            val tappedMarker = clickTargets.find { (_, offset) ->
                                (offset.x - position.x).pow(2) + (offset.y - position.y).pow(2) <= 35.dp.toPx().pow(2)
                            }
                            if (tappedMarker != null) {
                                onCustomerMarkerClick(tappedMarker.first)
                            } else if (isPinModeActive) {
                                // Convert tap position back to estimated Lat/Lng
                                val centerPxX = size.width / 2f + panOffset.x
                                val centerPxY = size.height / 2f + panOffset.y
                                val scale = 2200f * zoomLevel

                                val calcLng = baseLng + (position.x - centerPxX) / scale
                                val calcLat = baseLat - (position.y - centerPxY) / scale
                                onMapTapForPin(calcLat, calcLng)
                            }
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val centerX = canvasWidth / 2f + panOffset.x
            val centerY = canvasHeight / 2f + panOffset.y
            val scale = 2200f * zoomLevel

            // 1. Draw Map Grid & Background Aesthetics
            val gridSpacing = 80f * zoomLevel
            var gridX = (panOffset.x % gridSpacing)
            while (gridX < canvasWidth) {
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(gridX, 0f),
                    end = Offset(gridX, canvasHeight),
                    strokeWidth = 1f
                )
                gridX += gridSpacing
            }
            var gridY = (panOffset.y % gridSpacing)
            while (gridY < canvasHeight) {
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(0f, gridY),
                    end = Offset(canvasWidth, gridY),
                    strokeWidth = 1f
                )
                gridY += gridSpacing
            }

            // 2. Map Customer Coordinates to Screen Offset
            val targets = mutableListOf<Pair<CustomerEntity, Offset>>()

            // Group customers by OLT Name to render real OLT Hub Nodes
            val oltGroups = customers.groupBy { it.oltName.ifBlank { "Core OLT" } }
            val oltNodePositions = mutableMapOf<String, Offset>()

            // Place OLT Nodes
            var oltIndex = 0
            oltGroups.keys.forEach { oltName ->
                val oltX = centerX + (oltIndex * 240f - 120f) * zoomLevel
                val oltY = centerY - 180f * zoomLevel
                oltNodePositions[oltName] = Offset(oltX, oltY)
                oltIndex++
            }

            // Center Core Router
            val routerPos = Offset(centerX, centerY - 320f * zoomLevel)

            // Draw Router Node
            drawCircle(color = Color(0xFF2563EB), radius = 22f * zoomLevel, center = routerPos)
            drawCircle(color = Color.White, radius = 10f * zoomLevel, center = routerPos)

            // Draw Lines from Router to OLTs
            oltNodePositions.forEach { (oltName, oltPos) ->
                drawLine(
                    color = Color(0xFF3B82F6),
                    start = routerPos,
                    end = oltPos,
                    strokeWidth = 3f * zoomLevel,
                    cap = StrokeCap.Round
                )
                // Draw OLT Hub
                drawCircle(color = Color(0xFF0284C7), radius = 18f * zoomLevel, center = oltPos)
                drawCircle(color = Color.White, radius = 8f * zoomLevel, center = oltPos)
            }

            // Render Customers
            customers.forEachIndexed { idx, cust ->
                val screenPos = if (cust.latitude != 0.0 || cust.longitude != 0.0) {
                    val x = centerX + ((cust.longitude - baseLng) * scale).toFloat()
                    val y = centerY - ((cust.latitude - baseLat) * scale).toFloat()
                    Offset(x, y)
                } else {
                    // Unmapped fallback circular cluster layout for visual cleanliness
                    val angle = (idx * 0.45f)
                    val radius = (120f + (idx % 6) * 35f) * zoomLevel
                    val x = centerX + (radius * cos(angle.toDouble())).toFloat()
                    val y = centerY + 180f * zoomLevel + (radius * kotlin.math.sin(angle.toDouble())).toFloat()
                    Offset(x, y)
                }

                targets.add(Pair(cust, screenPos))

                // Connection line from OLT to Customer
                val parentOltPos = oltNodePositions[cust.oltName.ifBlank { "Core OLT" }] ?: Offset(centerX, centerY)
                drawLine(
                    color = if (cust.status.equals("ACTIVE", true)) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFEF4444).copy(alpha = 0.3f),
                    start = parentOltPos,
                    end = screenPos,
                    strokeWidth = 1.5f * zoomLevel
                )

                // Marker Color based on Status
                val markerColor = when {
                    cust.status.equals("ACTIVE", true) -> Color(0xFF10B981)
                    cust.status.equals("SUSPENDED", true) -> Color(0xFFF59E0B)
                    else -> Color(0xFFEF4444)
                }

                // Customer Outer Glow & Pin Circle
                drawCircle(color = markerColor.copy(alpha = 0.25f), radius = 16f * zoomLevel, center = screenPos)
                drawCircle(color = markerColor, radius = 10f * zoomLevel, center = screenPos)
                drawCircle(color = Color.White, radius = 4f * zoomLevel, center = screenPos)
            }

            clickTargets = targets
        }
    }
}

@Composable
private fun MapSummaryBadge(label: String, value: String, valueColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(text = "$label:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = valueColor)
    }
}

@Composable
private fun StatusBadgeChip(status: String) {
    val (bg, fg, label) = when (status.uppercase()) {
        "ACTIVE" -> Triple(EmeraldSuccess.copy(alpha = 0.15f), EmeraldSuccess, "ACTIVE")
        "SUSPENDED" -> Triple(Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFD97706), "SUSPENDED")
        else -> Triple(MaterialTheme.colorScheme.error.copy(alpha = 0.15f), MaterialTheme.colorScheme.error, "INACTIVE")
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * Customer Details Bottom Sheet / Floating Panel.
 */
@Composable
private fun CustomerDetailsBottomSheet(
    customer: CustomerEntity,
    onDismiss: () -> Unit,
    onEditLocation: () -> Unit,
    onEditNetwork: () -> Unit,
    onPinOnMap: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = customer.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "ID: ${customer.customerCode} • Username: ${customer.pppoeUsername}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            // Infrastructure Path View
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(text = customer.routerName.ifBlank { "Router" }, style = MaterialTheme.typography.labelSmall)
                    Text(text = "➔", color = MaterialTheme.colorScheme.primary)
                    Text(text = customer.oltName.ifBlank { "OLT-01" }, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    Text(text = "➔", color = MaterialTheme.colorScheme.primary)
                    Text(text = customer.ponPort.ifBlank { "PON 1" }, style = MaterialTheme.typography.labelSmall)
                    Text(text = "➔", color = MaterialTheme.colorScheme.primary)
                    Text(text = customer.onuSerial.ifBlank { "ONU" }, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Details Table
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DetailRow(label = "Phone:", value = customer.phone)
                DetailRow(label = "Area / Zone:", value = "${customer.area.ifBlank { "Unassigned" }} / ${customer.zone.ifBlank { "N/A" }}")
                DetailRow(label = "Address:", value = customer.address)
                DetailRow(label = "Package:", value = "${customer.packageName} (৳${customer.monthlyFee}/mo)")
                DetailRow(
                    label = "Coordinates:",
                    value = if (customer.latitude != 0.0 || customer.longitude != 0.0) "${customer.latitude}, ${customer.longitude}" else "Unmapped"
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPinOnMap,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.PinDrop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Pin Map", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = onEditLocation,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Lat/Lng", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = onEditNetwork,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Router, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Network", fontSize = 11.sp)
                }

                if (customer.phone.isNotBlank()) {
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.phone}"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not launch phone dialer", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Phone, contentDescription = "Call", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
    }
}

/**
 * Filter Bottom Sheet Dialog.
 */
@Composable
private fun MapFilterDialog(
    selectedStatus: String,
    selectedArea: String,
    selectedPackage: String,
    selectedOlt: String,
    areaList: List<String>,
    packageList: List<String>,
    oltList: List<String>,
    onApplyFilters: (String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var status by remember { mutableStateOf(selectedStatus) }
    var area by remember { mutableStateOf(selectedArea) }
    var pkg by remember { mutableStateOf(selectedPackage) }
    var olt by remember { mutableStateOf(selectedOlt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🎯 Filter Map Data", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Status Filter
                Text("Connection Status", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL", "ACTIVE", "SUSPENDED", "INACTIVE").forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st, fontSize = 11.sp) }
                        )
                    }
                }

                HorizontalDivider()

                // Area Filter Dropdown
                Text("Area / Zone", style = MaterialTheme.typography.labelMedium)
                FilterDropdown(
                    selected = area,
                    options = listOf("ALL") + areaList,
                    onSelected = { area = it }
                )

                // Package Filter
                Text("Package", style = MaterialTheme.typography.labelMedium)
                FilterDropdown(
                    selected = pkg,
                    options = listOf("ALL") + packageList,
                    onSelected = { pkg = it }
                )

                // OLT Filter
                Text("OLT Network", style = MaterialTheme.typography.labelMedium)
                FilterDropdown(
                    selected = olt,
                    options = listOf("ALL") + oltList,
                    onSelected = { olt = it }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onApplyFilters(status, area, pkg, olt) }) {
                Text("Apply Filters")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onApplyFilters("ALL", "ALL", "ALL", "ALL")
            }) {
                Text("Reset All")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        onSelected(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Area Summary Connections Breakdown Dialog.
 */
@Composable
private fun AreaSummaryDialog(
    customers: List<CustomerEntity>,
    areaList: List<String>,
    onSelectArea: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📊 Area-Wise Connection Summary", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(areaList) { areaName ->
                    val areaCustomers = customers.filter {
                        if (areaName == "Unassigned Area") it.area.isBlank() else it.area.equals(areaName, true)
                    }
                    val total = areaCustomers.size
                    val active = areaCustomers.count { it.status.equals("ACTIVE", true) }
                    val suspended = areaCustomers.count { it.status.equals("SUSPENDED", true) }
                    val inactive = areaCustomers.count { it.status.equals("INACTIVE", true) || it.status.equals("EXPIRED", true) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectArea(areaName) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = areaName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(text = "Total: $total", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Active: $active", style = MaterialTheme.typography.labelSmall, color = EmeraldSuccess)
                                Text(text = "Suspended: $suspended", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF59E0B))
                                Text(text = "Expired: $inactive", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Edit Customer Location (Lat/Long/Area/Zone) Dialog.
 */
@Composable
private fun EditCustomerLocationDialog(
    customer: CustomerEntity,
    onSave: (CustomerEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var latStr by remember { mutableStateOf(if (customer.latitude != 0.0) customer.latitude.toString() else "") }
    var lngStr by remember { mutableStateOf(if (customer.longitude != 0.0) customer.longitude.toString() else "") }
    var area by remember { mutableStateOf(customer.area) }
    var zone by remember { mutableStateOf(customer.zone) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📍 Edit Customer Location", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Customer: ${customer.name}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))

                OutlinedTextField(
                    value = area,
                    onValueChange = { area = it },
                    label = { Text("Area / এলাকা") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = zone,
                    onValueChange = { zone = it },
                    label = { Text("Zone / Block / Road") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latStr,
                        onValueChange = { latStr = it },
                        label = { Text("Latitude") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lngStr,
                        onValueChange = { lngStr = it },
                        label = { Text("Longitude") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newLat = latStr.toDoubleOrNull() ?: 0.0
                    val newLng = lngStr.toDoubleOrNull() ?: 0.0
                    onSave(
                        customer.copy(
                            latitude = newLat,
                            longitude = newLng,
                            area = area.trim(),
                            zone = zone.trim()
                        )
                    )
                }
            ) {
                Text("Save Location")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Edit Customer Network Mapping (OLT/PON/ONU/Router) Dialog.
 */
@Composable
private fun EditCustomerNetworkDialog(
    customer: CustomerEntity,
    onSave: (CustomerEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var oltName by remember { mutableStateOf(customer.oltName) }
    var ponPort by remember { mutableStateOf(customer.ponPort) }
    var onuSerial by remember { mutableStateOf(customer.onuSerial) }
    var routerName by remember { mutableStateOf(customer.routerName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔌 Edit Network Infrastructure", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "Customer: ${customer.name}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))

                OutlinedTextField(
                    value = oltName,
                    onValueChange = { oltName = it },
                    label = { Text("OLT Name / ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = ponPort,
                    onValueChange = { ponPort = it },
                    label = { Text("PON Port (e.g. PON 1/4)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = onuSerial,
                    onValueChange = { onuSerial = it },
                    label = { Text("ONU/ONT Serial Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = routerName,
                    onValueChange = { routerName = it },
                    label = { Text("Core Router Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        customer.copy(
                            oltName = oltName.trim(),
                            ponPort = ponPort.trim(),
                            onuSerial = onuSerial.trim(),
                            routerName = routerName.trim()
                        )
                    )
                }
            ) {
                Text("Save Infrastructure")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
