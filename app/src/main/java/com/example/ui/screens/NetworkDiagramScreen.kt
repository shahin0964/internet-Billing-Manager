package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.model.CustomerEntity
import com.example.data.model.NetworkConnectionEntity
import com.example.data.model.NetworkDiagramEntity
import com.example.data.model.NetworkNodeEntity
import com.example.data.model.NodeType
import com.example.ui.viewmodel.IspViewModel
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkDiagramScreen(
    onBackClick: () -> Unit,
    viewModel: IspViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Database state
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val diagrams by viewModel.repository.diagrams.collectAsStateWithLifecycle(initialValue = emptyList())

    var activeDiagram by remember { mutableStateOf<NetworkDiagramEntity?>(null) }

    LaunchedEffect(diagrams) {
        if (diagrams.isNotEmpty()) {
            if (activeDiagram == null || diagrams.none { it.id == activeDiagram?.id }) {
                activeDiagram = diagrams.first()
            }
        } else {
            scope.launch {
                val def = viewModel.repository.getOrCreateDefaultDiagram()
                activeDiagram = def
            }
        }
    }

    val activeDiagramId = activeDiagram?.id ?: 0L

    val nodesFlow = remember(activeDiagramId) {
        viewModel.repository.getNodesForDiagram(activeDiagramId)
    }
    val connectionsFlow = remember(activeDiagramId) {
        viewModel.repository.getConnectionsForDiagram(activeDiagramId)
    }

    val nodes by nodesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val connections by connectionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // Canvas pan & zoom state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Dialog & Interaction state
    var selectedNode by remember { mutableStateOf<NetworkNodeEntity?>(null) }
    var connectSourceNode by remember { mutableStateOf<NetworkNodeEntity?>(null) }

    var showAddNodeDialog by remember { mutableStateOf(false) }
    var showEditNodeDialog by remember { mutableStateOf<NetworkNodeEntity?>(null) }
    var showConnectDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showSaveToast by remember { mutableStateOf(false) }
    var showNodeDetailsDialog by remember { mutableStateOf<NetworkNodeEntity?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "🌐 Internet Network Diagram",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = activeDiagram?.name ?: "Network Topology",
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
                    IconButton(onClick = { showAddNodeDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Device"
                        )
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Diagram layout saved successfully")
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save Diagram"
                        )
                    }
                    IconButton(onClick = { showClearConfirmDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Diagram",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                FloatingActionButton(
                    onClick = { showAddNodeDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Device", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (nodes.isEmpty()) {
                // Empty State
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(20.dp)
                                .fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Network Devices Added",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Create a visual topology diagram of your ISP network by adding devices like Routers, Switches, OLT, ONUs, and Customers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showAddNodeDialog = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add First Device")
                    }
                }
            } else {
                // Interactive Topology Canvas
                val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                val primaryColor = MaterialTheme.colorScheme.primary
                val outlineColor = MaterialTheme.colorScheme.outline

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.4f, 3.0f)
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        }
                ) {
                    // Background Grid & Connection Lines Canvas
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                    ) {
                        // 1. Subtle Background Grid Dots/Lines
                        val gridSpacing = 40.dp.toPx()
                        val width = size.width * 2
                        val height = size.height * 2

                        var x = -width
                        while (x < width) {
                            var y = -height
                            while (y < height) {
                                drawCircle(
                                    color = gridColor,
                                    radius = 1.5f,
                                    center = Offset(x, y)
                                )
                                y += gridSpacing
                            }
                            x += gridSpacing
                        }

                        // 2. Draw Connection Lines between Nodes
                        connections.forEach { conn ->
                            val fromNode = nodes.find { it.id == conn.fromNodeId }
                            val toNode = nodes.find { it.id == conn.toNodeId }

                            if (fromNode != null && toNode != null) {
                                // Node center offsets (approx 80dp x 40dp card size offset)
                                val startX = fromNode.positionX + 90f
                                val startY = fromNode.positionY + 45f
                                val endX = toNode.positionX + 90f
                                val endY = toNode.positionY + 45f

                                val startOffset = Offset(startX, startY)
                                val endOffset = Offset(endX, endY)

                                // Draw connecting line
                                drawLine(
                                    color = primaryColor,
                                    start = startOffset,
                                    end = endOffset,
                                    strokeWidth = 3f,
                                    cap = StrokeCap.Round
                                )

                                // Draw Directional Arrow
                                val angle = atan2((endY - startY).toDouble(), (endX - startX).toDouble())
                                val midX = (startX + endX) / 2
                                val midY = (startY + endY) / 2

                                val arrowSize = 12f
                                val arrowPath = Path().apply {
                                    moveTo(
                                        midX.toFloat() + (arrowSize * cos(angle)).toFloat(),
                                        midY.toFloat() + (arrowSize * sin(angle)).toFloat()
                                    )
                                    lineTo(
                                        midX.toFloat() - (arrowSize * cos(angle - Math.PI / 6)).toFloat(),
                                        midY.toFloat() - (arrowSize * sin(angle - Math.PI / 6)).toFloat()
                                    )
                                    lineTo(
                                        midX.toFloat() - (arrowSize * cos(angle + Math.PI / 6)).toFloat(),
                                        midY.toFloat() - (arrowSize * sin(angle + Math.PI / 6)).toFloat()
                                    )
                                    close()
                                }
                                drawPath(
                                    path = arrowPath,
                                    color = primaryColor
                                )
                            }
                        }
                    }

                    // Render Draggable Device Nodes
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                    ) {
                        nodes.forEach { node ->
                            key(node.id) {
                                DeviceNodeCard(
                                    node = node,
                                    isSelected = selectedNode?.id == node.id,
                                    isConnectSource = connectSourceNode?.id == node.id,
                                    onClick = {
                                        if (connectSourceNode != null && connectSourceNode?.id != node.id) {
                                            // Create connection from connectSourceNode to this node
                                            val src = connectSourceNode!!
                                            scope.launch {
                                                viewModel.repository.saveConnection(
                                                    NetworkConnectionEntity(
                                                        id = UUID.randomUUID().toString(),
                                                        diagramId = activeDiagramId,
                                                        fromNodeId = src.id,
                                                        toNodeId = node.id
                                                    )
                                                )
                                                snackbarHostState.showSnackbar("Connected ${src.name} ➔ ${node.name}")
                                                connectSourceNode = null
                                            }
                                        } else {
                                            selectedNode = if (selectedNode?.id == node.id) null else node
                                        }
                                    },
                                    onPositionChange = { newX, newY ->
                                        scope.launch {
                                            viewModel.repository.updateNodePosition(node.id, newX, newY)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Node Action Bar / Toolbar when a node is selected
                if (selectedNode != null) {
                    val selNode = selectedNode!!
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 72.dp, start = 16.dp, end = 16.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 8.dp,
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${selNode.typeEnum().defaultBadge} ${selNode.name}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = selNode.typeEnum().displayName + (if (selNode.ipAddress.isNotEmpty()) " • ${selNode.ipAddress}" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { showNodeDetailsDialog = selNode }) {
                                    Icon(Icons.Default.Info, contentDescription = "Details")
                                }
                                IconButton(onClick = {
                                    connectSourceNode = selNode
                                    selectedNode = null
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Select target device to connect with ${selNode.name}")
                                    }
                                }) {
                                    Icon(Icons.Default.LinearScale, contentDescription = "Connect", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { showEditNodeDialog = selNode }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        viewModel.repository.deleteNode(selNode.id)
                                        selectedNode = null
                                        snackbarHostState.showSnackbar("Deleted device ${selNode.name}")
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                // Connect Mode Notification Floating Pill
                if (connectSourceNode != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Connecting from ${connectSourceNode!!.name}... Tap target device",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { connectSourceNode = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel connection mode")
                            }
                        }
                    }
                }

                // Canvas Control Floating Buttons
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shadowElevation = 4.dp
                ) {
                    Column {
                        IconButton(onClick = { scale = (scale + 0.2f).coerceAtMost(3.0f) }) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
                        }
                        IconButton(onClick = { scale = (scale - 0.2f).coerceAtLeast(0.4f) }) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
                        }
                        IconButton(onClick = {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        }) {
                            Icon(Icons.Default.CenterFocusStrong, contentDescription = "Fit / Center Screen")
                        }
                        IconButton(onClick = { showDisconnectDialog = true }) {
                            Icon(Icons.Default.LinkOff, contentDescription = "Manage Connections")
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS ---

    // 1. Add Device Dialog
    if (showAddNodeDialog) {
        NodeEditDialog(
            title = "Add Network Device",
            initialNode = null,
            diagramId = activeDiagramId,
            customers = customers,
            onDismiss = { showAddNodeDialog = false },
            onSave = { newNode ->
                scope.launch {
                    // Place near center of viewport
                    val nodeWithPos = newNode.copy(
                        positionX = -offsetX + 150f,
                        positionY = -offsetY + 200f
                    )
                    viewModel.repository.saveNode(nodeWithPos)
                    showAddNodeDialog = false
                    snackbarHostState.showSnackbar("Device added: ${newNode.name}")
                }
            }
        )
    }

    // 2. Edit Device Dialog
    if (showEditNodeDialog != null) {
        NodeEditDialog(
            title = "Edit Device Info",
            initialNode = showEditNodeDialog,
            diagramId = activeDiagramId,
            customers = customers,
            onDismiss = { showEditNodeDialog = null },
            onSave = { updatedNode ->
                scope.launch {
                    viewModel.repository.saveNode(updatedNode)
                    showEditNodeDialog = null
                    selectedNode = updatedNode
                    snackbarHostState.showSnackbar("Device updated: ${updatedNode.name}")
                }
            }
        )
    }

    // 3. Device Details Dialog
    if (showNodeDetailsDialog != null) {
        val node = showNodeDetailsDialog!!
        AlertDialog(
            onDismissRequest = { showNodeDetailsDialog = null },
            icon = {
                Text(text = node.typeEnum().defaultBadge, fontSize = 28.sp)
            },
            title = {
                Text(text = node.name, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Type: ${node.typeEnum().displayName}", fontWeight = FontWeight.SemiBold)
                    if (node.ipAddress.isNotEmpty()) Text("IP Address: ${node.ipAddress}")
                    if (node.location.isNotEmpty()) Text("Location: ${node.location}")
                    if (node.areaZone.isNotEmpty()) Text("Area / Zone: ${node.areaZone}")
                    if (node.portInfo.isNotEmpty()) Text("Port Info: ${node.portInfo}")
                    if (node.customerRef.isNotEmpty()) Text("Customer Ref: ${node.customerRef}")
                    if (node.notes.isNotEmpty()) Text("Notes: ${node.notes}")
                }
            },
            confirmButton = {
                TextButton(onClick = { showNodeDetailsDialog = null }) {
                    Text("Close")
                }
            }
        )
    }

    // 4. Manage Connections / Disconnect Dialog
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Network Connections (${connections.size})") },
            text = {
                if (connections.isEmpty()) {
                    Text("No active node connections.")
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(connections) { conn ->
                            val from = nodes.find { it.id == conn.fromNodeId }?.name ?: conn.fromNodeId
                            val to = nodes.find { it.id == conn.toNodeId }?.name ?: conn.toNodeId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$from ➔ $to",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            viewModel.repository.deleteConnection(conn.id)
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove Connection",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // 5. Clear Diagram Confirmation Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear Entire Diagram?") },
            text = { Text("This will delete all devices and connections from this diagram. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.repository.clearDiagram(activeDiagramId)
                            selectedNode = null
                            connectSourceNode = null
                            showClearConfirmDialog = false
                            snackbarHostState.showSnackbar("Diagram cleared")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Extension helper for NetworkNodeEntity
private fun NetworkNodeEntity.typeEnum(): NodeType = NodeType.fromName(type)

@Composable
fun DeviceNodeCard(
    node: NetworkNodeEntity,
    isSelected: Boolean,
    isConnectSource: Boolean,
    onClick: () -> Unit,
    onPositionChange: (Float, Float) -> Unit
) {
    var posX by remember(node.positionX) { mutableFloatStateOf(node.positionX) }
    var posY by remember(node.positionY) { mutableFloatStateOf(node.positionY) }

    val nodeType = node.typeEnum()

    Surface(
        modifier = Modifier
            .offset { IntOffset(posX.roundToInt(), posY.roundToInt()) }
            .pointerInput(node.id) {
                detectDragGestures(
                    onDragEnd = {
                        onPositionChange(posX, posY)
                    }
                ) { change, dragAmount ->
                    change.consume()
                    posX += dragAmount.x
                    posY += dragAmount.y
                }
            }
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isSelected) 8.dp else 3.dp,
        shadowElevation = if (isSelected) 8.dp else 4.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected || isConnectSource) 2.5.dp else 1.dp,
            color = when {
                isConnectSource -> MaterialTheme.colorScheme.tertiary
                isSelected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = when (nodeType) {
                    NodeType.INTERNET -> MaterialTheme.colorScheme.primaryContainer
                    NodeType.CORE_ROUTER -> MaterialTheme.colorScheme.errorContainer
                    NodeType.MIKROTIK -> MaterialTheme.colorScheme.secondaryContainer
                    NodeType.SERVER -> MaterialTheme.colorScheme.tertiaryContainer
                    NodeType.SWITCH -> MaterialTheme.colorScheme.primaryContainer
                    NodeType.OLT -> MaterialTheme.colorScheme.secondaryContainer
                    NodeType.ONU_ONT -> MaterialTheme.colorScheme.surfaceVariant
                    NodeType.CUSTOMER -> MaterialTheme.colorScheme.primaryContainer
                    NodeType.AREA_ZONE -> MaterialTheme.colorScheme.secondaryContainer
                },
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = nodeType.defaultBadge, fontSize = 18.sp)
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = node.name.ifEmpty { nodeType.displayName },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (node.ipAddress.isNotEmpty()) {
                    Text(
                        text = node.ipAddress,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (node.location.isNotEmpty() || node.areaZone.isNotEmpty()) {
                    Text(
                        text = node.location.ifEmpty { node.areaZone },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = nodeType.displayName,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NodeEditDialog(
    title: String,
    initialNode: NetworkNodeEntity?,
    diagramId: Long,
    customers: List<CustomerEntity>,
    onDismiss: () -> Unit,
    onSave: (NetworkNodeEntity) -> Unit
) {
    var selectedType by remember { mutableStateOf(initialNode?.typeEnum() ?: NodeType.MIKROTIK) }
    var name by remember { mutableStateOf(initialNode?.name ?: "") }
    var ipAddress by remember { mutableStateOf(initialNode?.ipAddress ?: "") }
    var location by remember { mutableStateOf(initialNode?.location ?: "") }
    var areaZone by remember { mutableStateOf(initialNode?.areaZone ?: "") }
    var portInfo by remember { mutableStateOf(initialNode?.portInfo ?: "") }
    var customerRef by remember { mutableStateOf(initialNode?.customerRef ?: "") }
    var notes by remember { mutableStateOf(initialNode?.notes ?: "") }

    var selectedCustomer by remember { mutableStateOf<CustomerEntity?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .heightIn(max = 520.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text("Select Device Type", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    item {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            NodeType.values().forEach { type ->
                                FilterChip(
                                    selected = selectedType == type,
                                    onClick = {
                                        selectedType = type
                                        if (name.isEmpty()) {
                                            name = type.displayName
                                        }
                                    },
                                    label = { Text("${type.defaultBadge} ${type.displayName}") }
                                )
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Device Name *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    if (selectedType in listOf(NodeType.CORE_ROUTER, NodeType.MIKROTIK, NodeType.SERVER, NodeType.SWITCH)) {
                        item {
                            OutlinedTextField(
                                value = ipAddress,
                                onValueChange = { ipAddress = it },
                                label = { Text("IP Address (e.g., 192.168.1.1)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }

                    if (selectedType in listOf(NodeType.SWITCH, NodeType.OLT)) {
                        item {
                            OutlinedTextField(
                                value = portInfo,
                                onValueChange = { portInfo = it },
                                label = { Text("Port Information (e.g. 24 Ports, PON 1-8)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }

                    if (selectedType == NodeType.CUSTOMER && customers.isNotEmpty()) {
                        item {
                            Text("Link Existing Customer (Optional)", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 120.dp)
                            ) {
                                items(customers) { cust ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedCustomer = cust
                                                name = cust.name
                                                customerRef = cust.customerCode + " / " + cust.pppoeUsername
                                                areaZone = cust.address
                                            }
                                            .padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (selectedCustomer?.id == cust.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${cust.name} (${cust.pppoeUsername})",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text("Location / Address") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = areaZone,
                            onValueChange = { areaZone = it },
                            label = { Text("Area / Zone") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notes / Description") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val node = (initialNode ?: NetworkNodeEntity(
                                id = UUID.randomUUID().toString(),
                                diagramId = diagramId,
                                name = name.ifBlank { selectedType.displayName },
                                type = selectedType.name
                            )).copy(
                                name = name.ifBlank { selectedType.displayName },
                                type = selectedType.name,
                                ipAddress = ipAddress,
                                location = location,
                                areaZone = areaZone,
                                portInfo = portInfo,
                                customerRef = customerRef,
                                customerId = selectedCustomer?.id?.toString() ?: initialNode?.customerId ?: "",
                                notes = notes
                            )
                            onSave(node)
                        },
                        enabled = name.isNotBlank() || selectedType != null
                    ) {
                        Text("Save Device")
                    }
                }
            }
        }
    }
}
