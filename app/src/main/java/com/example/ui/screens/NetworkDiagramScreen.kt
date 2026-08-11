package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.model.CustomerEntity
import com.example.ui.viewmodel.IspViewModel
import com.example.util.NetworkConnection
import com.example.util.NetworkDeviceStatus
import com.example.util.NetworkDeviceType
import com.example.util.NetworkDiagramData
import com.example.util.NetworkDiagramManager
import com.example.util.NetworkNode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkDiagramScreen(
    onBackClick: () -> Unit,
    viewModel: IspViewModel = viewModel()
) {
    val context = LocalContext.current
    val customers by viewModel.customers.collectAsStateWithLifecycle()

    var diagramData by remember { mutableStateOf(NetworkDiagramData()) }
    var loaded by remember { mutableStateOf(false) }

    // Load saved data
    LaunchedEffect(Unit) {
        diagramData = NetworkDiagramManager.loadDiagramData(context)
        loaded = true
    }

    // Save helper
    fun updateAndSave(newData: NetworkDiagramData) {
        diagramData = newData
        NetworkDiagramManager.saveDiagramData(context, newData)
    }

    // Pan & Zoom gestures
    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Dialog States
    var showAddEditNodeDialog by remember { mutableStateOf(false) }
    var nodeToEdit by remember { mutableStateOf<NetworkNode?>(null) }

    var showAddConnectionDialog by remember { mutableStateOf(false) }
    var sourceNodeForConn by remember { mutableStateOf<NetworkNode?>(null) }

    var selectedNodeDetails by remember { mutableStateOf<NetworkNode?>(null) }
    var customerToViewDetails by remember { mutableStateOf<CustomerEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.network_diagram),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.network_diagram_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_to_list)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scale = 1f
                            panOffset = Offset.Zero
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.reset_view)
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
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (diagramData.nodes.isEmpty()) {
                // Empty state card
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.no_devices_added),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                nodeToEdit = null
                                showAddEditNodeDialog = true
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.add_device))
                        }
                    }
                }
            }

            // Interactive Diagram Canvas
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.4f, 3.0f)
                            panOffset += pan
                        }
                    }
            ) {
                val lineColor = MaterialTheme.colorScheme.primary
                val lineLabelBg = MaterialTheme.colorScheme.surface
                val lineLabelTextColor = MaterialTheme.colorScheme.onSurface

                // Draw connections canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Draw grid background
                    val gridSpacing = 40f * scale
                    val startX = (panOffset.x % gridSpacing)
                    val startY = (panOffset.y % gridSpacing)
                    val dotColor = lineColor.copy(alpha = 0.12f)

                    var x = startX
                    while (x < size.width) {
                        var y = startY
                        while (y < size.height) {
                            drawCircle(
                                color = dotColor,
                                radius = 1.5f * scale,
                                center = Offset(x, y)
                            )
                            y += gridSpacing
                        }
                        x += gridSpacing
                    }

                    // Map node id -> node position
                    val nodeMap = diagramData.nodes.associateBy { it.id }

                    // Draw connection lines
                    for (connection in diagramData.connections) {
                        val fromNode = nodeMap[connection.fromNodeId]
                        val toNode = nodeMap[connection.toNodeId]
                        if (fromNode != null && toNode != null) {
                            // Calculate node center positions in screen space
                            val nodeWidthPx = 140.dp.toPx() * scale
                            val nodeHeightPx = 80.dp.toPx() * scale

                            val startX = (fromNode.x * scale) + panOffset.x + (nodeWidthPx / 2f)
                            val startY = (fromNode.y * scale) + panOffset.y + (nodeHeightPx / 2f)

                            val endX = (toNode.x * scale) + panOffset.x + (nodeWidthPx / 2f)
                            val endY = (toNode.y * scale) + panOffset.y + (nodeHeightPx / 2f)

                            val strokeWidth = 3.dp.toPx() * scale.coerceIn(0.6f, 1.5f)

                            // Draw connection line
                            drawLine(
                                color = lineColor,
                                start = Offset(startX, startY),
                                end = Offset(endX, endY),
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Round
                            )

                            // Connection label pill
                            if (connection.label.isNotBlank()) {
                                val midX = (startX + endX) / 2f
                                val midY = (startY + endY) / 2f

                                val paint = android.graphics.Paint().apply {
                                    color = lineLabelTextColor.hashCode()
                                    textSize = (11.sp.toPx() * scale).coerceIn(18f, 36f)
                                    isAntiAlias = true
                                    textAlign = android.graphics.Paint.Align.CENTER
                                }

                                val textWidth = paint.measureText(connection.label)
                                val textHeight = paint.textSize

                                val padX = 12f * scale
                                val padY = 6f * scale

                                // Draw label background pill
                                drawRoundRect(
                                    color = lineLabelBg,
                                    topLeft = Offset(midX - (textWidth / 2f) - padX, midY - (textHeight / 2f) - padY),
                                    size = androidx.compose.ui.geometry.Size(textWidth + (padX * 2), textHeight + (padY * 2)),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
                                )

                                drawRoundRect(
                                    color = lineColor,
                                    topLeft = Offset(midX - (textWidth / 2f) - padX, midY - (textHeight / 2f) - padY),
                                    size = androidx.compose.ui.geometry.Size(textWidth + (padX * 2), textHeight + (padY * 2)),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
                                    style = Stroke(width = 1.dp.toPx())
                                )

                                drawContext.canvas.nativeCanvas.drawText(
                                    connection.label,
                                    midX,
                                    midY + (textHeight / 3f),
                                    paint
                                )
                            }
                        }
                    }
                }

                // Render Draggable Nodes
                for (node in diagramData.nodes) {
                    val nodeScreenX = (node.x * scale + panOffset.x).roundToInt()
                    val nodeScreenY = (node.y * scale + panOffset.y).roundToInt()

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(nodeScreenX, nodeScreenY) }
                            .pointerInput(node.id, scale) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    // Update node position in canvas coordinates
                                    val newX = node.x + (dragAmount.x / scale)
                                    val newY = node.y + (dragAmount.y / scale)
                                    val updatedNodes = diagramData.nodes.map {
                                        if (it.id == node.id) it.copy(x = newX, y = newY) else it
                                    }
                                    updateAndSave(diagramData.copy(nodes = updatedNodes))
                                }
                            }
                    ) {
                        NodeItemCard(
                            node = node,
                            scale = scale,
                            onClick = {
                                selectedNodeDetails = node
                            }
                        )
                    }
                }
            }

            // Canvas Floating Action Controls (Zoom In, Zoom Out, Reset, Add Connection, Add Device)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Zoom controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp,
                        tonalElevation = 2.dp
                    ) {
                        IconButton(onClick = { scale = (scale + 0.15f).coerceAtMost(3.0f) }) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp,
                        tonalElevation = 2.dp
                    ) {
                        IconButton(onClick = { scale = (scale - 0.15f).coerceAtLeast(0.4f) }) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
                        }
                    }
                }

                if (diagramData.nodes.size >= 2) {
                    FloatingActionButton(
                        onClick = {
                            sourceNodeForConn = null
                            showAddConnectionDialog = true
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Cable, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.add_connection), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                FloatingActionButton(
                    onClick = {
                        nodeToEdit = null
                        showAddEditNodeDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.add_device), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    // --- DIALOGS ---

    // Add / Edit Node Dialog
    if (showAddEditNodeDialog) {
        AddEditNodeDialog(
            existingNode = nodeToEdit,
            customers = customers,
            onDismiss = { showAddEditNodeDialog = false },
            onSave = { newNode ->
                val updatedNodes = diagramData.nodes.toMutableList()
                val existingIndex = updatedNodes.indexOfFirst { it.id == newNode.id }
                if (existingIndex != -1) {
                    updatedNodes[existingIndex] = newNode
                } else {
                    // Auto offset slightly for new node if position not set
                    val autoX = 120f + (updatedNodes.size * 20f % 200f)
                    val autoY = 120f + (updatedNodes.size * 30f % 300f)
                    updatedNodes.add(newNode.copy(x = if (nodeToEdit == null) autoX else newNode.x, y = if (nodeToEdit == null) autoY else newNode.y))
                }
                updateAndSave(diagramData.copy(nodes = updatedNodes))
                showAddEditNodeDialog = false
            }
        )
    }

    // Add Connection Dialog
    if (showAddConnectionDialog) {
        AddConnectionDialog(
            nodes = diagramData.nodes,
            initialSourceNode = sourceNodeForConn,
            onDismiss = { showAddConnectionDialog = false },
            onSave = { fromId, toId, label ->
                val newConn = NetworkConnection(
                    id = "conn_" + System.currentTimeMillis(),
                    fromNodeId = fromId,
                    toNodeId = toId,
                    label = label
                )
                updateAndSave(diagramData.copy(connections = diagramData.connections + newConn))
                showAddConnectionDialog = false
            }
        )
    }

    // Node Details Dialog
    selectedNodeDetails?.let { node ->
        NodeDetailsDialog(
            node = node,
            connections = diagramData.connections.filter { it.fromNodeId == node.id || it.toNodeId == node.id },
            allNodes = diagramData.nodes,
            customers = customers,
            onDismiss = { selectedNodeDetails = null },
            onEdit = {
                nodeToEdit = node
                selectedNodeDetails = null
                showAddEditNodeDialog = true
            },
            onAddConnection = {
                sourceNodeForConn = node
                selectedNodeDetails = null
                showAddConnectionDialog = true
            },
            onDeleteNode = {
                val updatedNodes = diagramData.nodes.filter { it.id != node.id }
                val updatedConns = diagramData.connections.filter { it.fromNodeId != node.id && it.toNodeId != node.id }
                updateAndSave(NetworkDiagramData(nodes = updatedNodes, connections = updatedConns))
                selectedNodeDetails = null
            },
            onDeleteConnection = { connId ->
                val updatedConns = diagramData.connections.filter { it.id != connId }
                updateAndSave(diagramData.copy(connections = updatedConns))
                selectedNodeDetails = diagramData.nodes.find { it.id == node.id }
            },
            onViewCustomerDetails = { customer ->
                customerToViewDetails = customer
            }
        )
    }

    // Linked Customer Details Sheet/Dialog
    customerToViewDetails?.let { customer ->
        CustomerInfoDialog(
            customer = customer,
            onDismiss = { customerToViewDetails = null }
        )
    }
}

@Composable
fun NodeItemCard(
    node: NetworkNode,
    scale: Float,
    onClick: () -> Unit
) {
    val statusColor = when (node.status) {
        NetworkDeviceStatus.ONLINE -> Color(0xFF4CAF50)
        NetworkDeviceStatus.WARNING -> Color(0xFFFFC107)
        NetworkDeviceStatus.OFFLINE -> Color(0xFFF44336)
    }

    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.5.dp,
            color = statusColor.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Device Icon
                Icon(
                    imageVector = getDeviceTypeIcon(node.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                // Status Indicator Dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(statusColor, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                )
            }

            // Name
            Text(
                text = node.name,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Type Badge
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ) {
                Text(
                    text = node.type.displayNameEn,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            // IP Address or Customer Name
            if (node.ipAddress.isNotBlank()) {
                Text(
                    text = node.ipAddress,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            } else if (!node.customerName.isNullOrBlank()) {
                Text(
                    text = "👤 ${node.customerName}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun AddEditNodeDialog(
    existingNode: NetworkNode?,
    customers: List<CustomerEntity>,
    onDismiss: () -> Unit,
    onSave: (NetworkNode) -> Unit
) {
    var name by remember { mutableStateOf(existingNode?.name ?: "") }
    var type by remember { mutableStateOf(existingNode?.type ?: NetworkDeviceType.ROUTER) }
    var ipAddress by remember { mutableStateOf(existingNode?.ipAddress ?: "") }
    var macAddress by remember { mutableStateOf(existingNode?.macAddress ?: "") }
    var location by remember { mutableStateOf(existingNode?.location ?: "") }
    var notes by remember { mutableStateOf(existingNode?.notes ?: "") }
    var status by remember { mutableStateOf(existingNode?.status ?: NetworkDeviceStatus.ONLINE) }

    var selectedCustomer by remember {
        mutableStateOf(existingNode?.customerId?.let { cid -> customers.find { it.id == cid } })
    }

    var showTypeDropdown by remember { mutableStateOf(false) }
    var showCustomerDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingNode == null) stringResource(R.string.add_device) else stringResource(R.string.edit_device),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Device Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.device_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Device Type Selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = type.displayNameEn,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.device_type)) },
                        trailingIcon = {
                            IconButton(onClick = { showTypeDropdown = true }) {
                                Icon(Icons.Default.Dns, contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTypeDropdown = true }
                    )

                    DropdownMenu(
                        expanded = showTypeDropdown,
                        onDismissRequest = { showTypeDropdown = false }
                    ) {
                        NetworkDeviceType.values().forEach { devType ->
                            DropdownMenuItem(
                                text = { Text("${devType.displayNameEn} (${devType.displayNameBn})") },
                                leadingIcon = {
                                    Icon(getDeviceTypeIcon(devType), contentDescription = null)
                                },
                                onClick = {
                                    type = devType
                                    showTypeDropdown = false
                                }
                            )
                        }
                    }
                }

                // Status Selector
                Text(
                    text = stringResource(R.string.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NetworkDeviceStatus.values().forEach { st ->
                        val isSelected = status == st
                        val color = when (st) {
                            NetworkDeviceStatus.ONLINE -> Color(0xFF4CAF50)
                            NetworkDeviceStatus.WARNING -> Color(0xFFFFC107)
                            NetworkDeviceStatus.OFFLINE -> Color(0xFFF44336)
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                if (isSelected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { status = st }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(color, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = st.displayNameEn,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // IP Address
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text(stringResource(R.string.ip_address)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // MAC Address
                OutlinedTextField(
                    value = macAddress,
                    onValueChange = { macAddress = it },
                    label = { Text(stringResource(R.string.mac_address)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Location
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.location)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Link Existing Customer
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedCustomer?.let { "${it.name} (${it.pppoeUsername})" } ?: stringResource(R.string.no_customer_linked),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.link_customer)) },
                        trailingIcon = {
                            IconButton(onClick = { showCustomerDropdown = true }) {
                                Icon(Icons.Default.Person, contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCustomerDropdown = true }
                    )

                    DropdownMenu(
                        expanded = showCustomerDropdown,
                        onDismissRequest = { showCustomerDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.no_customer_linked)) },
                            onClick = {
                                selectedCustomer = null
                                showCustomerDropdown = false
                            }
                        )
                        customers.forEach { customer ->
                            DropdownMenuItem(
                                text = { Text("${customer.name} - ${customer.pppoeUsername}") },
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null)
                                },
                                onClick = {
                                    selectedCustomer = customer
                                    if (name.isBlank()) {
                                        name = customer.name
                                    }
                                    if (ipAddress.isBlank() && customer.ipAddress.isNotBlank()) {
                                        ipAddress = customer.ipAddress
                                    }
                                    showCustomerDropdown = false
                                }
                            )
                        }
                    }
                }

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = name.ifBlank { type.displayNameEn }
                    val node = NetworkNode(
                        id = existingNode?.id ?: ("node_" + System.currentTimeMillis()),
                        name = finalName,
                        type = type,
                        ipAddress = ipAddress,
                        macAddress = macAddress,
                        location = location,
                        notes = notes,
                        status = status,
                        customerId = selectedCustomer?.id,
                        customerName = selectedCustomer?.name,
                        x = existingNode?.x ?: 100f,
                        y = existingNode?.y ?: 100f
                    )
                    onSave(node)
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun AddConnectionDialog(
    nodes: List<NetworkNode>,
    initialSourceNode: NetworkNode?,
    onDismiss: () -> Unit,
    onSave: (fromId: String, toId: String, label: String) -> Unit
) {
    var fromNode by remember { mutableStateOf(initialSourceNode ?: nodes.firstOrNull()) }
    var toNode by remember { mutableStateOf(nodes.find { it.id != fromNode?.id } ?: nodes.firstOrNull()) }
    var label by remember { mutableStateOf("") }

    var showFromDropdown by remember { mutableStateOf(false) }
    var showToDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.add_connection), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // From Node Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = fromNode?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.from_device)) },
                        trailingIcon = {
                            IconButton(onClick = { showFromDropdown = true }) {
                                Icon(Icons.Default.Dns, contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFromDropdown = true }
                    )
                    DropdownMenu(
                        expanded = showFromDropdown,
                        onDismissRequest = { showFromDropdown = false }
                    ) {
                        nodes.forEach { n ->
                            DropdownMenuItem(
                                text = { Text("${n.name} (${n.type.displayNameEn})") },
                                onClick = {
                                    fromNode = n
                                    showFromDropdown = false
                                }
                            )
                        }
                    }
                }

                // To Node Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = toNode?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.to_device)) },
                        trailingIcon = {
                            IconButton(onClick = { showToDropdown = true }) {
                                Icon(Icons.Default.Dns, contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showToDropdown = true }
                    )
                    DropdownMenu(
                        expanded = showToDropdown,
                        onDismissRequest = { showToDropdown = false }
                    ) {
                        nodes.filter { it.id != fromNode?.id }.forEach { n ->
                            DropdownMenuItem(
                                text = { Text("${n.name} (${n.type.displayNameEn})") },
                                onClick = {
                                    toNode = n
                                    showToDropdown = false
                                }
                            )
                        }
                    }
                }

                // Label Input
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.connection_label)) },
                    singleLine = true,
                    placeholder = { Text("e.g. Fiber 1G / Port 1") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = fromNode != null && toNode != null && fromNode?.id != toNode?.id,
                onClick = {
                    if (fromNode != null && toNode != null) {
                        onSave(fromNode!!.id, toNode!!.id, label.trim())
                    }
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun NodeDetailsDialog(
    node: NetworkNode,
    connections: List<NetworkConnection>,
    allNodes: List<NetworkNode>,
    customers: List<CustomerEntity>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onAddConnection: () -> Unit,
    onDeleteNode: () -> Unit,
    onDeleteConnection: (String) -> Unit,
    onViewCustomerDetails: (CustomerEntity) -> Unit
) {
    var showConfirmDeleteNode by remember { mutableStateOf(false) }
    val linkedCustomer = node.customerId?.let { cid -> customers.find { it.id == cid } }

    val statusColor = when (node.status) {
        NetworkDeviceStatus.ONLINE -> Color(0xFF4CAF50)
        NetworkDeviceStatus.WARNING -> Color(0xFFFFC107)
        NetworkDeviceStatus.OFFLINE -> Color(0xFFF44336)
    }

    if (showConfirmDeleteNode) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteNode = false },
            title = { Text(stringResource(R.string.delete_device)) },
            text = { Text(stringResource(R.string.delete_device_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDeleteNode = false
                        onDeleteNode()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDeleteNode = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = getDeviceTypeIcon(node.type),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = node.name, fontWeight = FontWeight.Bold)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(statusColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = node.status.displayNameEn,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Type & Info Rows
                DetailRow(label = stringResource(R.string.device_type), value = node.type.displayNameEn)

                if (node.ipAddress.isNotBlank()) {
                    DetailRow(label = stringResource(R.string.ip_address), value = node.ipAddress)
                }
                if (node.macAddress.isNotBlank()) {
                    DetailRow(label = stringResource(R.string.mac_address), value = node.macAddress)
                }
                if (node.location.isNotBlank()) {
                    DetailRow(label = stringResource(R.string.location), value = node.location)
                }

                // Linked Customer Section
                if (linkedCustomer != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "👤 " + stringResource(R.string.linked_customer),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${linkedCustomer.name} (${linkedCustomer.pppoeUsername})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Phone: ${linkedCustomer.phone} | Package: ${linkedCustomer.packageName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = { onViewCustomerDetails(linkedCustomer) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.view_customer), fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (node.notes.isNotBlank()) {
                    DetailRow(label = stringResource(R.string.notes), value = node.notes)
                }

                // Connections List
                Text(
                    text = "🔌 " + stringResource(R.string.connections),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                if (connections.isEmpty()) {
                    Text(
                        text = "No connections linked to this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val nodeMap = allNodes.associateBy { it.id }
                    connections.forEach { conn ->
                        val otherNodeId = if (conn.fromNodeId == node.id) conn.toNodeId else conn.fromNodeId
                        val otherNode = nodeMap[otherNodeId]

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "↔️ ${otherNode?.name ?: "Unknown Device"}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                                if (conn.label.isNotBlank()) {
                                    Text(
                                        text = "Label: ${conn.label}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onDeleteConnection(conn.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Delete Connection",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.edit))
                }

                Button(onClick = onAddConnection) {
                    Icon(Icons.Default.Cable, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.add_connection))
                }
            }
        },
        dismissButton = {
            IconButton(
                onClick = { showConfirmDeleteNode = true },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_device),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

@Composable
fun CustomerInfoDialog(
    customer: CustomerEntity,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(customer.name, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow(label = "Customer Code", value = customer.customerCode)
                DetailRow(label = "PPPoE Username", value = customer.pppoeUsername)
                if (customer.ipAddress.isNotBlank()) {
                    DetailRow(label = "IP Address", value = customer.ipAddress)
                }
                DetailRow(label = "Phone", value = customer.phone)
                DetailRow(label = "Address", value = customer.address)
                DetailRow(label = "Package", value = "${customer.packageName} (৳${customer.monthlyFee.toInt()}/mo)")
                DetailRow(label = "Status", value = customer.status)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

fun getDeviceTypeIcon(type: NetworkDeviceType): ImageVector {
    return when (type) {
        NetworkDeviceType.INTERNET -> Icons.Default.Language
        NetworkDeviceType.ROUTER -> Icons.Default.Router
        NetworkDeviceType.SWITCH -> Icons.Default.Dns
        NetworkDeviceType.OLT -> Icons.Default.Cable
        NetworkDeviceType.ONU -> Icons.Default.Devices
        NetworkDeviceType.SERVER -> Icons.Default.Storage
        NetworkDeviceType.ACCESS_POINT -> Icons.Default.Wifi
        NetworkDeviceType.CUSTOMER -> Icons.Default.Person
        NetworkDeviceType.OTHER -> Icons.Default.Devices
    }
}
