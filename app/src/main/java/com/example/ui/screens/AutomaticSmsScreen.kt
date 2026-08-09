package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.database.IspDatabase
import com.example.data.database.SmsDatabase
import com.example.data.model.CustomerEntity
import com.example.data.model.SmsQueueEntity
import com.example.util.AutomaticSmsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomaticSmsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Determine current language
    val isBn = Locale.getDefault().language == "bn"

    // Database access
    val smsDb = remember { SmsDatabase.getDatabase(context) }
    val ispDb = remember { IspDatabase.getDatabase(context) }

    // Retrieve lists reactively
    val smsList by smsDb.smsQueueDao().getAllSmsFlow().collectAsState(initial = emptyList())
    val customerList by ispDb.customerDao().getAllCustomers().collectAsState(initial = emptyList())

    // Tabs
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = if (isBn) {
        listOf("ড্যাশবোর্ড", "নিয়ম ও সেটিংস", "টেমপ্লেট", "ইতিহাস")
    } else {
        listOf("Dashboard", "Settings & Rules", "Templates", "History")
    }

    // Permission States
    var hasSmsPermission by remember { mutableStateOf(AutomaticSmsManager.isSmsPermissionGranted(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasSmsPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, if (isBn) "এসএমএস অনুমতি দেওয়া হয়েছে!" else "SMS permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, if (isBn) "এসএমএস অনুমতি দেওয়া হয়নি।" else "SMS permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    // Dialog state for Template editing
    var editingTemplateKey by remember { mutableStateOf<String?>(null) }
    var editingTemplateTitle by remember { mutableStateOf("") }
    var editingTemplateContent by remember { mutableStateOf("") }

    // Dialog state for Manual SMS
    var showManualSmsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isBn) "স্বয়ংক্রিয় এসএমএস" else "Automatic SMS",
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Permission Banner if missing
            if (!hasSmsPermission) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isBn) "এসএমএস পারমিশন প্রয়োজন" else "SMS Permission Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = if (isBn) "এই ফোন থেকে স্বয়ংক্রিয় এসএমএস অ্যালার্ট পাঠানোর জন্য SMS অনুমতি আবশ্যক।" else "SMS permission is required to send automatic SMS alerts from this device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Button(
                            onClick = {
                                permissionLauncher.launch(Manifest.permission.SEND_SMS)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = if (isBn) "অনুমতি দিন" else "Grant")
                        }
                    }
                }
            }

            // Tab Rows
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    0 -> SmsDashboardTab(
                        context = context,
                        isBn = isBn,
                        smsList = smsList,
                        hasSmsPermission = hasSmsPermission,
                        onRequestPermission = { permissionLauncher.launch(Manifest.permission.SEND_SMS) },
                        onTriggerQueue = {
                            AutomaticSmsManager.triggerSmsWorker(context)
                            Toast.makeText(context, if (isBn) "ব্যাকগ্রাউন্ড প্রসেসর সক্রিয় করা হয়েছে!" else "Background processor triggered!", Toast.LENGTH_SHORT).show()
                        }
                    )
                    1 -> SmsSettingsTab(
                        context = context,
                        isBn = isBn
                    )
                    2 -> SmsTemplatesTab(
                        context = context,
                        isBn = isBn,
                        onEditTemplate = { key, title, currentVal ->
                            editingTemplateKey = key
                            editingTemplateTitle = title
                            editingTemplateContent = currentVal
                        }
                    )
                    3 -> SmsHistoryTab(
                        context = context,
                        isBn = isBn,
                        smsList = smsList,
                        onOpenManualSms = { showManualSmsDialog = true }
                    )
                }
            }
        }
    }

    // Edit Template Dialog
    editingTemplateKey?.let { key ->
        AlertDialog(
            onDismissRequest = { editingTemplateKey = null },
            title = {
                Text(
                    text = editingTemplateTitle,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column {
                    Text(
                        text = if (isBn) "এসএমএস বডি এডিট করুন:" else "Edit SMS Message Body:",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = editingTemplateContent,
                        onValueChange = { editingTemplateContent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isBn) "ব্যবহারযোগ্য ডায়নামিক ভেরিয়েবলসমূহ (ট্যাপ করে যুক্ত করুন):" else "Usable Dynamic Placeholders (tap to insert):",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val placeholders = listOf(
                        "[Customer Name]" to (if (isBn) "গ্রাহকের নাম" else "Name"),
                        "[Monthly Fee]" to (if (isBn) "মাসিক ফি" else "Fee"),
                        "[Due Amount]" to (if (isBn) "বকেয়া ফি" else "Due"),
                        "[Due Date]" to (if (isBn) "বিল পরিশোধের শেষ সময়" else "Due Date"),
                        "[Payment Amount]" to (if (isBn) "পেমেন্ট পরিমাণ" else "Paid Amt"),
                        "[Payment Date]" to (if (isBn) "পেমেন্ট ডেট" else "Paid Date"),
                        "[Package/Speed]" to (if (isBn) "প্যাকেজ স্পিড" else "Package"),
                        "[ISP Name]" to (if (isBn) "আইএসপি নাম" else "ISP Name")
                    )

                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(placeholders) { (tag, label) ->
                            SuggestionChip(
                                onClick = {
                                    editingTemplateContent += tag
                                },
                                label = { Text(text = "$label ($tag)", style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (key) {
                            "bill_generated" -> AutomaticSmsManager.setTemplateBillGenerated(context, editingTemplateContent)
                            "due_reminder" -> AutomaticSmsManager.setTemplateDueReminder(context, editingTemplateContent)
                            "overdue" -> AutomaticSmsManager.setTemplateOverdue(context, editingTemplateContent)
                            "payment_confirmation" -> AutomaticSmsManager.setTemplatePaymentConfirmation(context, editingTemplateContent)
                            "general_notice" -> AutomaticSmsManager.setTemplateGeneralNotice(context, editingTemplateContent)
                        }
                        editingTemplateKey = null
                        Toast.makeText(context, if (isBn) "টেমপ্লেট সংরক্ষণ করা হয়েছে!" else "Template saved successfully!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(text = if (isBn) "সংরক্ষণ করুন" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTemplateKey = null }) {
                    Text(text = if (isBn) "বাতিল" else "Cancel")
                }
            }
        )
    }

    // Manual SMS Dialog
    if (showManualSmsDialog) {
        var selectedCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
        var dropdownExpanded by remember { mutableStateOf(false) }
        var customNumber by remember { mutableStateOf("") }
        var customMessage by remember { mutableStateOf("") }
        var searchQuery by remember { mutableStateOf("") }

        val filteredCustomers = customerList.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.phone.contains(searchQuery) ||
                    it.customerCode.contains(searchQuery, ignoreCase = true)
        }

        AlertDialog(
            onDismissRequest = { showManualSmsDialog = false },
            title = {
                Text(
                    text = if (isBn) "ম্যানুয়াল এসএমএস পাঠান" else "Send Manual SMS",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    Text(
                        text = if (isBn) "গ্রাহক নির্বাচন করুন:" else "Select Customer:",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedCustomer?.name ?: searchQuery,
                            onValueChange = {
                                searchQuery = it
                                if (selectedCustomer != null && it != selectedCustomer?.name) {
                                    selectedCustomer = null
                                    customNumber = ""
                                }
                                dropdownExpanded = true
                            },
                            placeholder = { Text(if (isBn) "খুঁজুন (নাম বা মোবাইল)" else "Search (Name or phone)") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { dropdownExpanded = !dropdownExpanded }) {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                        )

                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .heightIn(max = 250.dp)
                        ) {
                            if (filteredCustomers.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(if (isBn) "কোনো গ্রাহক পাওয়া যায়নি" else "No customers found") },
                                    onClick = {}
                                )
                            } else {
                                filteredCustomers.take(20).forEach { customer ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(text = customer.name, fontWeight = FontWeight.Bold)
                                                Text(text = "📞 ${customer.phone} | ID: ${customer.customerCode}", style = MaterialTheme.typography.bodySmall)
                                            }
                                        },
                                        onClick = {
                                            selectedCustomer = customer
                                            customNumber = customer.phone
                                            dropdownExpanded = false
                                            searchQuery = customer.name
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isBn) "মোবাইল নম্বর:" else "Mobile Number:",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = customNumber,
                        onValueChange = { customNumber = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isBn) "এসএমএস বার্তা:" else "Message Body:",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = customMessage,
                        onValueChange = { customMessage = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        placeholder = { Text(if (isBn) "আপনার বার্তাটি লিখুন..." else "Type your message here...") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customNumber.isBlank() || customMessage.isBlank()) {
                            Toast.makeText(context, if (isBn) "নম্বর এবং বার্তা দুটোই আবশ্যক!" else "Number and message are both required!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        coroutineScope.launch {
                            val custId = selectedCustomer?.id ?: 0L
                            val custName = selectedCustomer?.name ?: "Manual Dispatch"
                            AutomaticSmsManager.queueManualSms(
                                context = context,
                                customerId = custId,
                                customerName = custName,
                                mobileNumber = customNumber,
                                message = customMessage
                            )
                            showManualSmsDialog = false
                            Toast.makeText(context, if (isBn) "এসএমএস সফলভাবে লাইনে যুক্ত করা হয়েছে!" else "SMS added to sending queue successfully!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(text = if (isBn) "পাঠান" else "Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualSmsDialog = false }) {
                    Text(text = if (isBn) "বাতিল" else "Cancel")
                }
            }
        )
    }
}

@Composable
fun SmsDashboardTab(
    context: Context,
    isBn: Boolean,
    smsList: List<SmsQueueEntity>,
    hasSmsPermission: Boolean,
    onRequestPermission: () -> Unit,
    onTriggerQueue: () -> Unit
) {
    var globalEnabled by remember { mutableStateOf(AutomaticSmsManager.isSmsEnabled(context)) }

    val pendingCount = smsList.count { it.status == "PENDING" || it.status == "SENDING" }
    val sentCount = smsList.count { it.status == "SENT" }
    val failedCount = smsList.count { it.status == "FAILED" }

    val selectedSim = AutomaticSmsManager.getSelectedSim(context)
    val simLabel = when (selectedSim) {
        1 -> "SIM 1"
        2 -> "SIM 2"
        else -> if (isBn) "সিস্টেম ডিফল্ট সিম" else "OS Default SIM"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Global Feature Toggle Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isBn) "স্বয়ংক্রিয় এসএমএস সক্রিয় করুন" else "Enable Automatic SMS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isBn) "বিলিং ও পেমেন্টের উপর ভিত্তি করে গ্রাহকদের স্বয়ংক্রিয় মেসেজ পাঠানো হবে" else "Deliver automatic text notifications to clients on billing events",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = globalEnabled,
                        onCheckedChange = {
                            globalEnabled = it
                            AutomaticSmsManager.setSmsEnabled(context, it)
                            if (it) {
                                AutomaticSmsManager.triggerSmsWorker(context)
                            }
                        }
                    )
                }
            }
        }

        // SMS Status Overview Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isBn) "এসএমএস সার্ভিস স্ট্যাটাস" else "SMS Service Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Items rows
                    StatusRow(
                        label = if (isBn) "স্বয়ংক্রিয় এসএমএস সার্ভিস" else "Automatic SMS Service",
                        value = if (globalEnabled) (if (isBn) "চালু আছে (ON)" else "Active (ON)") else (if (isBn) "বন্ধ আছে (OFF)" else "Disabled (OFF)"),
                        valueColor = if (globalEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    StatusRow(
                        label = if (isBn) "ডিভাইস এসএমএস পারমিশন" else "SMS Android Permission",
                        value = if (hasSmsPermission) (if (isBn) "অনুমতি দেওয়া আছে" else "Granted") else (if (isBn) "অনুমতি দেওয়া নেই" else "Not Granted"),
                        valueColor = if (hasSmsPermission) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        onClick = if (!hasSmsPermission) onRequestPermission else null
                    )
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    StatusRow(
                        label = if (isBn) "ব্যবহৃত সিম কার্ড" else "Selected Delivery SIM",
                        value = simLabel,
                        valueColor = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // Queue Statistics Counter Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Pending Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isBn) "অপেক্ষমান" else "Pending",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = pendingCount.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Sent Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isBn) "সফল" else "Sent",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = sentCount.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }

                // Failed Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isBn) "ব্যর্থ" else "Failed",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = failedCount.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Processing Trigger Button
        item {
            Button(
                onClick = onTriggerQueue,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (isBn) "এসএমএস কিউ এখনই প্রসেস করুন" else "Process Queue Immediately")
            }
        }
    }
}

@Composable
fun StatusRow(
    label: String,
    value: String,
    valueColor: Color,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            if (onClick != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = valueColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun SmsSettingsTab(
    context: Context,
    isBn: Boolean
) {
    // Settings States
    var selectedSim by remember { mutableStateOf(AutomaticSmsManager.getSelectedSim(context)) }
    var defaultSendingTime by remember { mutableStateOf(AutomaticSmsManager.getDefaultSendingTime(context)) }
    var retryFailed by remember { mutableStateOf(AutomaticSmsManager.isRetryFailedEnabled(context)) }
    var maxRetryCount by remember { mutableStateOf(AutomaticSmsManager.getMaxRetryCount(context)) }

    // Rules States
    var ruleBillGen by remember { mutableStateOf(AutomaticSmsManager.isRuleBillGeneratedEnabled(context)) }
    var ruleDueReminder by remember { mutableStateOf(AutomaticSmsManager.isRuleDueReminderEnabled(context)) }
    var ruleOverdue by remember { mutableStateOf(AutomaticSmsManager.isRuleOverdueEnabled(context)) }
    var rulePayConfirm by remember { mutableStateOf(AutomaticSmsManager.isRulePaymentConfirmationEnabled(context)) }
    var ruleGeneralNotice by remember { mutableStateOf(AutomaticSmsManager.isRuleGeneralNoticeEnabled(context)) }

    // Timing offset state
    var dueReminderOffset by remember { mutableStateOf(AutomaticSmsManager.getDueReminderOffset(context)) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hardware Config
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isBn) "হার্ডওয়্যার সেটিংস" else "Hardware Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // SIM dropdown selector row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = if (isBn) "সিম কার্ড সিলেক্ট করুন" else "Select Send SIM", fontWeight = FontWeight.Bold)
                            Text(
                                text = if (isBn) "মেসেজ পাঠানোর জন্য ব্যবহৃত সিম" else "The SIM card to deliver text alerts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        var simExpanded by remember { mutableStateOf(false) }
                        Box {
                            Button(onClick = { simExpanded = true }) {
                                Text(
                                    text = when (selectedSim) {
                                        1 -> "SIM 1"
                                        2 -> "SIM 2"
                                        else -> if (isBn) "ডিফল্ট" else "Default"
                                    }
                                )
                            }
                            DropdownMenu(expanded = simExpanded, onDismissRequest = { simExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (isBn) "ডিফল্ট (সিস্টেম নির্বাচিত)" else "Default (OS Selected)") },
                                    onClick = {
                                        selectedSim = 0
                                        AutomaticSmsManager.setSelectedSim(context, 0)
                                        simExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("SIM 1") },
                                    onClick = {
                                        selectedSim = 1
                                        AutomaticSmsManager.setSelectedSim(context, 1)
                                        simExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("SIM 2") },
                                    onClick = {
                                        selectedSim = 2
                                        AutomaticSmsManager.setSelectedSim(context, 2)
                                        simExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    // Time config
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = if (isBn) "ডিফল্ট পাঠানোর সময়" else "Default sending time", fontWeight = FontWeight.Bold)
                            Text(
                                text = if (isBn) "মেসেজ পাঠানোর আদর্শ সময়কাল" else "Target hour of day to process alerts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = defaultSendingTime,
                            onValueChange = {
                                defaultSendingTime = it
                                AutomaticSmsManager.setDefaultSendingTime(context, it)
                            },
                            modifier = Modifier.width(90.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center)
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    // Retry failed toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = if (isBn) "ব্যর্থ মেসেজ পুনরায় চেষ্টা করুন" else "Retry failed SMS", fontWeight = FontWeight.Bold)
                            Text(
                                text = if (isBn) "মেসেজ ব্যর্থ হলে পুনরায় পাঠানোর চেষ্টা করা হবে" else "Attempt redelivery of failed messages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = retryFailed,
                            onCheckedChange = {
                                retryFailed = it
                                AutomaticSmsManager.setRetryFailedEnabled(context, it)
                            }
                        )
                    }

                    if (retryFailed) {
                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = if (isBn) "সর্বোচ্চ ট্রাই কাউন্ট" else "Maximum retry count", fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (isBn) "একটি মেসেজ কতবার ট্রাই করা হবে" else "Max attempts allowed per SMS",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = {
                                        if (maxRetryCount > 1) {
                                            maxRetryCount--
                                            AutomaticSmsManager.setMaxRetryCount(context, maxRetryCount)
                                        }
                                    }
                                ) {
                                    Text("-", style = MaterialTheme.typography.titleLarge)
                                }
                                Text(
                                    text = maxRetryCount.toString(),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                                TextButton(
                                    onClick = {
                                        if (maxRetryCount < 10) {
                                            maxRetryCount++
                                            AutomaticSmsManager.setMaxRetryCount(context, maxRetryCount)
                                        }
                                    }
                                ) {
                                    Text("+", style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Automatic SMS rules
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isBn) "এসএমএস ডেলিভারি নিয়মসমূহ" else "Automatic SMS Delivery Rules",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Rule 1: Bill Generated
                    RuleSwitchRow(
                        title = if (isBn) "📄 বিল তৈরি করা হলে (Bill Generated)" else "📄 Bill Generated Notification",
                        subtitle = if (isBn) "নতুন মাসের বিল জেনারেট হলে স্বয়ংক্রিয় মেসেজ যাবে" else "Send alert as soon as the monthly bill is created",
                        checked = ruleBillGen,
                        onCheckedChange = {
                            ruleBillGen = it
                            AutomaticSmsManager.setRuleBillGeneratedEnabled(context, it)
                        }
                    )
                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    // Rule 2: Due Reminder
                    RuleSwitchRow(
                        title = if (isBn) "⏰ বিল পরিশোধের রিমাইন্ডার (Due Reminder)" else "⏰ Bill Due Reminder Alert",
                        subtitle = if (isBn) "বিল পরিশোধের শেষ সময় অনুযায়ী রিমাইন্ডার পাঠাবে" else "Notify client relative to the billing due date",
                        checked = ruleDueReminder,
                        onCheckedChange = {
                            ruleDueReminder = it
                            AutomaticSmsManager.setRuleDueReminderEnabled(context, it)
                        }
                    )

                    if (ruleDueReminder) {
                        Spacer(modifier = Modifier.height(12.dp))
                        // Show offset timing selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isBn) "রিমাইন্ডার পাঠানোর সময়:" else "Reminder Send Timing Offset:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )

                            var offsetExpanded by remember { mutableStateOf(false) }
                            Box {
                                TextButton(onClick = { offsetExpanded = true }) {
                                    Text(
                                        text = when (dueReminderOffset) {
                                            -3 -> if (isBn) "৩ দিন আগে" else "3 Days Before"
                                            -1 -> if (isBn) "১ দিন আগে" else "1 Day Before"
                                            0 -> if (isBn) "নির্ধারিত দিনে" else "On Due Date"
                                            1 -> if (isBn) "১ দিন পরে" else "1 Day After"
                                            3 -> if (isBn) "৩ দিন পরে" else "3 Days After"
                                            else -> "On Due Date"
                                        }
                                    )
                                }
                                DropdownMenu(expanded = offsetExpanded, onDismissRequest = { offsetExpanded = false }) {
                                    listOf(-3, -1, 0, 1, 3).forEach { offset ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = when (offset) {
                                                        -3 -> if (isBn) "৩ দিন আগে" else "3 Days Before"
                                                        -1 -> if (isBn) "১ দিন আগে" else "1 Day Before"
                                                        0 -> if (isBn) "নির্ধারিত দিনে" else "On Due Date"
                                                        1 -> if (isBn) "১ দিন পরে" else "1 Day After"
                                                        3 -> if (isBn) "৩ দিন পরে" else "3 Days After"
                                                        else -> ""
                                                    }
                                                )
                                            },
                                            onClick = {
                                                dueReminderOffset = offset
                                                AutomaticSmsManager.setDueReminderOffset(context, offset)
                                                offsetExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    // Rule 3: Overdue
                    RuleSwitchRow(
                        title = if (isBn) "⚠️ বকেয়া বিলের সতর্কবার্তা (Overdue)" else "⚠️ Overdue Payment Warning",
                        subtitle = if (isBn) "বিল বকেয়া থাকলে গ্রাহককে সংযোগ বিচ্ছিন্নকরণ সতর্কতা পাঠাবে" else "Deliver suspension warnings on pending overdues",
                        checked = ruleOverdue,
                        onCheckedChange = {
                            ruleOverdue = it
                            AutomaticSmsManager.setRuleOverdueEnabled(context, it)
                        }
                    )
                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    // Rule 4: Payment Confirmation
                    RuleSwitchRow(
                        title = if (isBn) "✅ বিল পরিশোধের নিশ্চয়তা (Payment Confirmation)" else "✅ Payment Confirmation Receipt",
                        subtitle = if (isBn) "বিল সফলভাবে জমা হলে ধন্যবাদ বার্তা পাঠানো হবে" else "Text digital receipts immediately upon payment",
                        checked = rulePayConfirm,
                        onCheckedChange = {
                            rulePayConfirm = it
                            AutomaticSmsManager.setRulePaymentConfirmationEnabled(context, it)
                        }
                    )
                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    // Rule 5: General Notice
                    RuleSwitchRow(
                        title = if (isBn) "🔧 সাধারণ নোটিশ (General Notice)" else "🔧 General Notice Broadcaster",
                        subtitle = if (isBn) "সেবা সাময়িক বিঘ্নিত হলে বা অন্যান্য নোটিশে এসএমএস ব্যবহার করা হবে" else "Enable service maintenance and notice alerts",
                        checked = ruleGeneralNotice,
                        onCheckedChange = {
                            ruleGeneralNotice = it
                            AutomaticSmsManager.setRuleGeneralNoticeEnabled(context, it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RuleSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SmsTemplatesTab(
    context: Context,
    isBn: Boolean,
    onEditTemplate: (key: String, title: String, currentVal: String) -> Unit
) {
    val billGenVal = AutomaticSmsManager.getTemplateBillGenerated(context)
    val dueRemVal = AutomaticSmsManager.getTemplateDueReminder(context)
    val overdueVal = AutomaticSmsManager.getTemplateOverdue(context)
    val payConfirmVal = AutomaticSmsManager.getTemplatePaymentConfirmation(context)
    val generalNoticeVal = AutomaticSmsManager.getTemplateGeneralNotice(context)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Explanatory Intro Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isBn) "এখানে বিভিন্ন ইভেন্টের জন্য পাঠানো এসএমএস এর টেমপ্লেট পরিবর্তন করতে পারবেন। ডায়নামিক মানগুলো যেমন [Customer Name] বা [Due Amount] স্বয়ংক্রিয়ভাবে পরিবর্তিত হয়ে কাস্টমার ডাটা বসাবে।"
                        else "Customize the text content of your automatic SMS alerts here. Placeholders like [Customer Name] or [Due Amount] will be populated automatically with corresponding client data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Templates Cards
        item {
            TemplateCardItem(
                title = if (isBn) "📄 বিল তৈরি হওয়ার বার্তা (Bill Generated)" else "📄 Bill Generated Notification",
                content = billGenVal,
                onEdit = {
                    onEditTemplate("bill_generated", if (isBn) "বিল জেনারেট টেমপ্লেট" else "Bill Generated Template", billGenVal)
                }
            )
        }

        item {
            TemplateCardItem(
                title = if (isBn) "⏰ বিল পরিশোধের শেষ রিমাইন্ডার (Due Reminder)" else "⏰ Due Payment Reminder Alert",
                content = dueRemVal,
                onEdit = {
                    onEditTemplate("due_reminder", if (isBn) "ডিউ রিমাইন্ডার টেমপ্লেট" else "Due Reminder Template", dueRemVal)
                }
            )
        }

        item {
            TemplateCardItem(
                title = if (isBn) "⚠️ সংযোগ বিচ্ছিন্ন সতর্কবার্তা (Overdue Reminder)" else "⚠️ Overdue Payment Warning Alert",
                content = overdueVal,
                onEdit = {
                    onEditTemplate("overdue", if (isBn) "ওভারডিউ সতর্কবার্তা টেমপ্লেট" else "Overdue Template", overdueVal)
                }
            )
        }

        item {
            TemplateCardItem(
                title = if (isBn) "✅ বিল পরিশোধের নিশ্চয়তা (Payment Confirmation)" else "✅ Payment Confirmation Receipt Alert",
                content = payConfirmVal,
                onEdit = {
                    onEditTemplate("payment_confirmation", if (isBn) "পেমেন্ট কনফার্মেশন টেমপ্লেট" else "Payment Confirmation Template", payConfirmVal)
                }
            )
        }

        item {
            TemplateCardItem(
                title = if (isBn) "🔧 সাধারণ নোটিশ ও মেসেজ (General Notice)" else "🔧 General Maintenance / Notice Alert",
                content = generalNoticeVal,
                onEdit = {
                    onEditTemplate("general_notice", if (isBn) "সাধারণ নোটিশ টেমপ্লেট" else "General Notice Template", generalNoticeVal)
                }
            )
        }
    }
}

@Composable
fun TemplateCardItem(
    title: String,
    content: String,
    onEdit: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = onEdit) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SmsHistoryTab(
    context: Context,
    isBn: Boolean,
    smsList: List<SmsQueueEntity>,
    onOpenManualSms: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, PENDING, SENT, FAILED
    var searchKeyword by remember { mutableStateOf("") }

    val filteredList = smsList.filter {
        val matchesFilter = when (selectedFilter) {
            "PENDING" -> it.status == "PENDING" || it.status == "SENDING"
            "SENT" -> it.status == "SENT"
            "FAILED" -> it.status == "FAILED"
            else -> true
        }
        val matchesSearch = it.customerName.contains(searchKeyword, ignoreCase = true) ||
                it.mobileNumber.contains(searchKeyword) ||
                it.message.contains(searchKeyword, ignoreCase = true)

        matchesFilter && matchesSearch
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Floating Action Row for sending manual SMS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isBn) "এসএমএস প্রেরণের ইতিহাস" else "SMS Transmission History",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )

            Button(
                onClick = onOpenManualSms,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = if (isBn) "এসএমএস পাঠান" else "Send SMS")
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchKeyword,
            onValueChange = { searchKeyword = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            placeholder = { Text(if (isBn) "গ্রাহকের নাম বা মেসেজ দিয়ে খুঁজুন..." else "Search client name or message content...") },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        // Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf(
                "ALL" to (if (isBn) "সব" else "All"),
                "PENDING" to (if (isBn) "অপেক্ষমান" else "Pending"),
                "SENT" to (if (isBn) "সফল" else "Sent"),
                "FAILED" to (if (isBn) "ব্যর্থ" else "Failed")
            )

            filters.forEach { (key, label) ->
                FilterChip(
                    selected = selectedFilter == key,
                    onClick = { selectedFilter = key },
                    label = { Text(text = label) }
                )
            }
        }

        // List
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.MarkChatRead,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isBn) "কোনো এসএমএস হিস্ট্রি পাওয়া যায়নি।" else "No SMS history records found.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredList) { sms ->
                    SmsHistoryItemCard(sms = sms, isBn = isBn)
                }
            }
        }
    }
}

@Composable
fun SmsHistoryItemCard(
    sms: SmsQueueEntity,
    isBn: Boolean
) {
    val dateStr = try {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        sdf.format(Date(sms.createdTime))
    } catch (e: Exception) {
        ""
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Customer and Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = sms.customerName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    Text(text = "📞 ${sms.mobileNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Status Badge
                val (badgeColor, textColor, textLabel) = when (sms.status) {
                    "SENT" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), if (isBn) "সফল" else "Sent")
                    "PENDING", "SENDING" -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, if (isBn) "অপেক্ষমান" else "Pending")
                    else -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error, if (isBn) "ব্যর্থ" else "Failed")
                }

                Box(
                    modifier = Modifier
                        .background(badgeColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = textLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body Message
            Text(
                text = sms.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Footer info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type badge
                val typeLabel = when (sms.smsType) {
                    "bill_generated" -> if (isBn) "বিল তৈরি" else "Bill Generated"
                    "due_reminder" -> if (isBn) "রিমাইন্ডার" else "Reminder"
                    "overdue" -> if (isBn) "বকেয়া অ্যালার্ট" else "Overdue Warning"
                    "payment_confirmation" -> if (isBn) "পেমেন্ট রশিদ" else "Payment Receipt"
                    else -> if (isBn) "নোটিশ" else "General Notice"
                }

                Text(
                    text = "🏷️ $typeLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // If Failed, display error reason logs
            if (sms.status == "FAILED" && !sms.lastError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "❌ Error: ${sms.lastError}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
