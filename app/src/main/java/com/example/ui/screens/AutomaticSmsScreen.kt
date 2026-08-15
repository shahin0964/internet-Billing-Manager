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
import androidx.compose.foundation.horizontalScroll
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
    val billsList by ispDb.billDao().getAllBills().collectAsState(initial = emptyList())
    val businessSettings by ispDb.settingsDao().getSettings().collectAsState(initial = null)
    val ispName = businessSettings?.ispName ?: "ISP Net"
    val currencySymbol = businessSettings?.currencySymbol ?: "৳"

    // Run migration
    LaunchedEffect(Unit) {
        AutomaticSmsManager.migratePendingSms(context)
    }

    // Tabs
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = if (isBn) {
        listOf("ড্যাশবোর্ড", "নিয়ম ও সেটিংস", "ইতিহাস")
    } else {
        listOf("Dashboard", "Settings & Rules", "History")
    }

    // Permission States
    var hasSmsPermission by remember { mutableStateOf(AutomaticSmsManager.isSmsPermissionGranted(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsGranted = permissions[Manifest.permission.SEND_SMS] ?: false
        hasSmsPermission = smsGranted
        if (smsGranted) {
            Toast.makeText(context, if (isBn) "এসএমএস অনুমতি দেওয়া হয়েছে!" else "SMS permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, if (isBn) "এসএমএস অনুমতি দেওয়া হয়নি।" else "SMS permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    var selectedSim by remember { mutableStateOf(AutomaticSmsManager.getSelectedSim(context)) }

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
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.SEND_SMS,
                                        Manifest.permission.READ_PHONE_STATE
                                    )
                                )
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
                        selectedSim = selectedSim,
                        onRequestPermission = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.SEND_SMS,
                                    Manifest.permission.READ_PHONE_STATE
                                )
                            )
                        },
                        onTriggerQueue = {
                            AutomaticSmsManager.triggerSmsWorker(context)
                            Toast.makeText(context, if (isBn) "ব্যাকগ্রাউন্ড প্রসেসর সক্রিয় করা হয়েছে!" else "Background processor triggered!", Toast.LENGTH_SHORT).show()
                        }
                    )
                    1 -> SmsSettingsTab(
                        context = context,
                        isBn = isBn,
                        selectedSim = selectedSim,
                        onSelectedSimChange = { newSim ->
                            selectedSim = newSim
                            AutomaticSmsManager.setSelectedSim(context, newSim)
                        },
                        hasSmsPermission = hasSmsPermission,
                        onEditTemplate = { key, title, currentVal ->
                            editingTemplateKey = key
                            editingTemplateTitle = title
                            editingTemplateContent = currentVal
                        }
                    )
                    2 -> SmsHistoryTab(
                        context = context,
                        isBn = isBn,
                        smsList = smsList,
                        onOpenManualSms = { showManualSmsDialog = true },
                        onDeleteSms = { sms ->
                            coroutineScope.launch {
                                smsDb.smsQueueDao().deleteSms(sms)
                                Toast.makeText(context, if (isBn) "এসএমএসটি ডিলিট করা হয়েছে!" else "SMS deleted successfully!", Toast.LENGTH_SHORT).show()
                            }
                        }
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
                        "{customer_name}" to (if (isBn) "গ্রাহকের নাম" else "Name"),
                        "{bill_month}" to (if (isBn) "মাসের নাম" else "Month"),
                        "{bill_amount}" to (if (isBn) "মাসিক ফি" else "Fee"),
                        "{due_amount}" to (if (isBn) "বকেয়া ফি" else "Due"),
                        "{due_date}" to (if (isBn) "বিল পরিশোধের শেষ সময়" else "Due Date"),
                        "{payment_date}" to (if (isBn) "পেমেন্ট ডেট" else "Paid Date"),
                        "{customer_id}" to (if (isBn) "গ্রাহক আইডি" else "Customer ID")
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
                            "warning_1" -> AutomaticSmsManager.setTemplateWarning1(context, editingTemplateContent)
                            "warning_2" -> AutomaticSmsManager.setTemplateWarning2(context, editingTemplateContent)
                            "warning_3" -> AutomaticSmsManager.setTemplateWarning3(context, editingTemplateContent)
                            "expiry_warning" -> AutomaticSmsManager.setTemplateExpiryWarning(context, editingTemplateContent)
                            "connection_suspend" -> AutomaticSmsManager.setTemplateConnectionSuspend(context, editingTemplateContent)
                        }
                        editingTemplateKey = null
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

    // Manual / Test SMS Dialog
    if (showManualSmsDialog) {
        var selectedCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
        var dropdownExpanded by remember { mutableStateOf(false) }
        var customNumber by remember { mutableStateOf("") }
        var customMessage by remember { mutableStateOf("") }
        var searchQuery by remember { mutableStateOf("") }
        var testType by remember { mutableStateOf("DUE_BILL") }
        var isSendingDirect by remember { mutableStateOf(false) }

        // Compute selected customer calculated due
        val selectedCustDue = remember(selectedCustomer, billsList) {
            if (selectedCustomer != null) {
                val customerBills = billsList.filter { it.customerId == selectedCustomer!!.id }
                customerBills.sumOf { it.dueAmount }
            } else {
                500.0
            }
        }

        // Auto-update message template when type or customer changes
        LaunchedEffect(selectedCustomer, testType) {
            val rawTemplate = when (testType) {
                "DUE_BILL" -> AutomaticSmsManager.getTemplateDueReminder(context)
                "BILL_PAID" -> AutomaticSmsManager.getTemplatePaymentConfirmation(context)
                "BILL_GEN" -> AutomaticSmsManager.getTemplateBillGenerated(context)
                "WARN_1" -> AutomaticSmsManager.getTemplateWarning1(context)
                "WARN_2" -> AutomaticSmsManager.getTemplateWarning2(context)
                "WARN_3" -> AutomaticSmsManager.getTemplateWarning3(context)
                "NOTICE" -> AutomaticSmsManager.getTemplateGeneralNotice(context)
                "CUSTOM" -> if (customMessage.isNotBlank()) customMessage else (if (isBn) "প্রিয় {customer_name}, আপনার ইন্টারনেট সংযোগ সংক্রান্ত নোটিশ। ধন্যবাদ, {company_name}।" else "Dear {customer_name}, internet service notice. Thank you, {company_name}.")
                else -> AutomaticSmsManager.getTemplateDueReminder(context)
            }
            customMessage = rawTemplate

            if (selectedCustomer != null && customNumber.isBlank()) {
                customNumber = selectedCustomer?.phone ?: ""
            }
        }

        // Live preview calculation
        val previewName = selectedCustomer?.name ?: (if (isBn) "রহিম আহমেদ" else "Test Customer")
        val previewPhone = selectedCustomer?.phone ?: customNumber.ifBlank { "01700000000" }
        val previewCustId = selectedCustomer?.customerCode?.ifBlank { selectedCustomer?.id?.toString() } ?: "101"
        val previewMonthlyFee = selectedCustomer?.monthlyFee?.toString() ?: "500"
        val previewDueAmount = if (selectedCustomer != null) selectedCustDue.toString() else "500"
        val previewPkgName = selectedCustomer?.packageName ?: "10 Mbps"
        val monthStr = SimpleDateFormat("MMMM yyyy", Locale("bn", "BD")).format(Date())
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

        val livePreviewText = remember(customMessage, selectedCustomer, selectedCustDue, ispName) {
            AutomaticSmsManager.processTemplate(
                template = customMessage,
                customerName = previewName,
                monthlyFee = previewMonthlyFee,
                dueAmount = previewDueAmount,
                dueDate = dateStr,
                paymentAmount = previewMonthlyFee,
                paymentDate = dateStr,
                packageSpeed = previewPkgName,
                ispName = ispName,
                billMonth = monthStr,
                customerId = previewCustId,
                receiptNo = "REC-${System.currentTimeMillis().toString().takeLast(5)}",
                phoneNumber = previewPhone,
                remainingDue = previewDueAmount
            )
        }

        AlertDialog(
            onDismissRequest = { if (!isSendingDirect) showManualSmsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBn) "টেস্ট ও ম্যানুয়াল এসএমএস পাঠান" else "Send Test & Manual SMS",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    Text(
                        text = if (isBn) "টেমপ্লেট নির্বাচন করুন:" else "Select Template Preset:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    // Template preset chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = testType == "DUE_BILL",
                            onClick = { testType = "DUE_BILL" },
                            label = { Text(text = if (isBn) "⏰ বকেয়া বিল" else "Due Bill") }
                        )
                        FilterChip(
                            selected = testType == "BILL_PAID",
                            onClick = { testType = "BILL_PAID" },
                            label = { Text(text = if (isBn) "✅ বিল পরিশোধ" else "Bill Paid") }
                        )
                        FilterChip(
                            selected = testType == "BILL_GEN",
                            onClick = { testType = "BILL_GEN" },
                            label = { Text(text = if (isBn) "📄 বিল তৈরি" else "Bill Generated") }
                        )
                        FilterChip(
                            selected = testType == "WARN_1",
                            onClick = { testType = "WARN_1" },
                            label = { Text(text = if (isBn) "⚠️ ১ম নোটিশ" else "1st Warning") }
                        )
                        FilterChip(
                            selected = testType == "WARN_2",
                            onClick = { testType = "WARN_2" },
                            label = { Text(text = if (isBn) "⚠️ ২য় নোটিশ" else "2nd Warning") }
                        )
                        FilterChip(
                            selected = testType == "WARN_3",
                            onClick = { testType = "WARN_3" },
                            label = { Text(text = if (isBn) "⚠️ ৩য় নোটিশ" else "3rd Warning") }
                        )
                        FilterChip(
                            selected = testType == "NOTICE",
                            onClick = { testType = "NOTICE" },
                            label = { Text(text = if (isBn) "✉️ নোটিশ" else "Notice") }
                        )
                        FilterChip(
                            selected = testType == "CUSTOM",
                            onClick = { testType = "CUSTOM" },
                            label = { Text(text = if (isBn) "✏️ কাস্টম" else "Custom") }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isBn) "গ্রাহক নির্বাচন করুন (ঐচ্ছিক):" else "Select Customer (Optional):",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = if (selectedCustomer != null) "${selectedCustomer?.name} (${selectedCustomer?.phone})" else searchQuery,
                            onValueChange = {
                                searchQuery = it
                                if (selectedCustomer != null && it != selectedCustomer?.name) {
                                    selectedCustomer = null
                                }
                                dropdownExpanded = true
                            },
                            placeholder = { Text(if (isBn) "খুঁজুন (নাম, মোবাইল বা আইডি)" else "Search (Name, Phone or ID)") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (selectedCustomer != null) {
                                    IconButton(onClick = {
                                        selectedCustomer = null
                                        searchQuery = ""
                                        customNumber = ""
                                    }) {
                                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                } else {
                                    IconButton(onClick = { dropdownExpanded = !dropdownExpanded }) {
                                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                }
                            }
                        )

                        androidx.compose.animation.AnimatedVisibility(visible = dropdownExpanded) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .padding(top = 4.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                val filteredCustomers = customerList.filter {
                                    it.name.contains(searchQuery, ignoreCase = true) ||
                                            it.phone.contains(searchQuery) ||
                                            it.customerCode.contains(searchQuery, ignoreCase = true)
                                }
                                if (filteredCustomers.isEmpty()) {
                                    Text(
                                        text = if (isBn) "কোনো গ্রাহক পাওয়া যায়নি" else "No customers found",
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                } else {
                                    androidx.compose.foundation.lazy.LazyColumn(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(filteredCustomers.size) { index ->
                                            val customer = filteredCustomers[index]
                                            val custDue = billsList.filter { it.customerId == customer.id }.sumOf { it.dueAmount }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedCustomer = customer
                                                        customNumber = customer.phone
                                                        dropdownExpanded = false
                                                        searchQuery = customer.name
                                                    }
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(text = customer.name, fontWeight = FontWeight.Bold)
                                                    Text(text = "📞 ${customer.phone} | ID: ${customer.customerCode}", style = MaterialTheme.typography.bodySmall)
                                                }
                                                Surface(
                                                    color = if (custDue > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = "$currencySymbol${custDue.toInt()}",
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            if (index < filteredCustomers.size - 1) {
                                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Display selected customer summary badge if selected
                    if (selectedCustomer != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "👤 ${selectedCustomer?.name} (${selectedCustomer?.packageName})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isBn) "বকেয়া: $currencySymbol${selectedCustDue.toInt()}" else "Due: $currencySymbol${selectedCustDue.toInt()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (selectedCustDue > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isBn) "প্রাপকের মোবাইল নম্বর:" else "Recipient Mobile Number:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = customNumber,
                        onValueChange = { customNumber = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("01700000000") }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick Variable tags
                    Text(
                        text = if (isBn) "ডায়নামিক ভ্যারিয়েবল ট্যাগ যোগ করুন:" else "Insert Dynamic Variable Tags:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = { customMessage += " {customer_name}" },
                            label = { Text("+ নাম") }
                        )
                        AssistChip(
                            onClick = { customMessage += " {monthly_bill}" },
                            label = { Text("+ মাসিক বিল") }
                        )
                        AssistChip(
                            onClick = { customMessage += " {due_amount}" },
                            label = { Text("+ বকেয়া বিল") }
                        )
                        AssistChip(
                            onClick = { customMessage += " {package_name}" },
                            label = { Text("+ প্যাকেজ") }
                        )
                        AssistChip(
                            onClick = { customMessage += " {bill_month}" },
                            label = { Text("+ বিলের মাস") }
                        )
                        AssistChip(
                            onClick = { customMessage += " {company_name}" },
                            label = { Text("+ কোম্পানি") }
                        )
                        AssistChip(
                            onClick = { customMessage += " {phone_number}" },
                            label = { Text("+ মোবাইল") }
                        )
                        AssistChip(
                            onClick = { customMessage += " {customer_id}" },
                            label = { Text("+ আইডি") }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isBn) "এসএমএস মেসেজ বডি (এডিটেবল):" else "SMS Message Body (Editable):",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = customMessage,
                        onValueChange = { customMessage = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        placeholder = { Text(if (isBn) "বার্তা লিখুন..." else "Type message...") }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live Preview Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isBn) "লাইভ প্রিভিউ (গ্রাহকের তথ্যানুযায়ী):" else "Live Preview (Customer Data):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = livePreviewText.ifBlank { if (isBn) "(মেসেজ লিখলে এখানে প্রিভিউ দেখতে পাবেন)" else "(Type a message to see preview)" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    Button(
                        enabled = !isSendingDirect,
                        onClick = {
                            if (customNumber.isBlank() || customMessage.isBlank()) {
                                Toast.makeText(context, if (isBn) "নম্বর এবং বার্তা আবশ্যক!" else "Number and message are required!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            coroutineScope.launch {
                                isSendingDirect = true
                                val custId = selectedCustomer?.id ?: 0L
                                val custName = selectedCustomer?.name ?: "Test Recipient"
                                val result = AutomaticSmsManager.sendDirectTestSms(
                                    context = context,
                                    testType = testType,
                                    customerId = custId,
                                    customerName = custName,
                                    mobileNumber = customNumber,
                                    message = customMessage
                                )
                                isSendingDirect = false
                                if (result.isSuccess) {
                                    Toast.makeText(context, if (isBn) "রিয়েল টেস্ট এসএমএস সফলভাবে পাঠানো হয়েছে!" else "Real Test SMS sent successfully via SMS Gateway!", Toast.LENGTH_LONG).show()
                                    showManualSmsDialog = false
                                } else {
                                    val err = result.exceptionOrNull()?.message ?: "Unknown Error"
                                    Toast.makeText(context, if (isBn) "এসএমএস পাঠাতে ব্যর্থ: $err" else "Failed to send SMS: $err", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isSendingDirect) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = if (isBn) "পাঠানো হচ্ছে..." else "Sending...")
                        } else {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isBn) "রিয়েল টেস্ট এসএমএস পাঠান" else "Send Real Test SMS")
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedButton(
                        enabled = !isSendingDirect,
                        onClick = {
                            if (customNumber.isBlank() || customMessage.isBlank()) {
                                Toast.makeText(context, if (isBn) "নম্বর এবং বার্তা আবশ্যক!" else "Number and message are required!", Toast.LENGTH_SHORT).show()
                                return@OutlinedButton
                            }
                            coroutineScope.launch {
                                val custId = selectedCustomer?.id ?: 0L
                                val custName = selectedCustomer?.name ?: "Manual Queue"
                                AutomaticSmsManager.queueManualSms(
                                    context = context,
                                    customerId = custId,
                                    customerName = custName,
                                    mobileNumber = customNumber,
                                    message = customMessage
                                )
                                showManualSmsDialog = false
                                Toast.makeText(context, if (isBn) "এসএমএস সফলভাবে লাইনে যুক্ত করা হয়েছে!" else "SMS queued successfully!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text(text = if (isBn) "কিউতে যুক্ত করুন (Queue)" else "Add to Queue")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSendingDirect,
                    onClick = { showManualSmsDialog = false }
                ) {
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
    selectedSim: Int,
    onRequestPermission: () -> Unit,
    onTriggerQueue: () -> Unit
) {
    var globalEnabled by remember { mutableStateOf(AutomaticSmsManager.isSmsEnabled(context)) }

    val pendingCount = smsList.count { it.status == "PENDING" || it.status == "SENDING" }
    val sentCount = smsList.count { it.status == "SENT" }
    val failedCount = smsList.count { it.status == "FAILED" }

    val availableSims = remember(hasSmsPermission) { AutomaticSmsManager.getAvailableSims(context) }
    val simLabel = if (selectedSim == -1) {
        if (isBn) "সিস্টেম ডিফল্ট সিম" else "OS Default SIM"
    } else {
        val simInfo = availableSims.find { it.subscriptionId == selectedSim }
        if (simInfo != null) {
            "SIM ${simInfo.slotIndex + 1} - ${simInfo.carrierName} - ${simInfo.number}"
        } else {
            if (isBn) "নির্বাচিত সিম পাওয়া যায়নি" else "Selected SIM unavailable"
        }
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
    isBn: Boolean,
    selectedSim: Int,
    onSelectedSimChange: (Int) -> Unit,
    hasSmsPermission: Boolean,
    onEditTemplate: (key: String, title: String, currentVal: String) -> Unit
) {
var rulePayConfirm by remember { mutableStateOf(AutomaticSmsManager.isRulePaymentConfirmationEnabled(context)) }
    var ruleDueReminder by remember { mutableStateOf(AutomaticSmsManager.isRuleDueReminderEnabled(context)) }
    var ruleBillGenerated by remember { mutableStateOf(AutomaticSmsManager.isRuleBillGeneratedEnabled(context)) }
    var ruleOverdue by remember { mutableStateOf(AutomaticSmsManager.isRuleOverdueEnabled(context)) }

    var ruleGeneralNotice by remember { mutableStateOf(AutomaticSmsManager.isRuleGeneralNoticeEnabled(context)) }

    var dueOffset by remember { mutableStateOf(AutomaticSmsManager.getDueReminderOffset(context)) }


    
    var ruleWarning1 by remember { mutableStateOf(AutomaticSmsManager.isRuleWarning1Enabled(context)) }
    var dateWarning1 by remember { mutableStateOf(AutomaticSmsManager.getDateWarning1(context)) }
    
    var ruleWarning2 by remember { mutableStateOf(AutomaticSmsManager.isRuleWarning2Enabled(context)) }
    var dateWarning2 by remember { mutableStateOf(AutomaticSmsManager.getDateWarning2(context)) }
    
    var ruleWarning3 by remember { mutableStateOf(AutomaticSmsManager.isRuleWarning3Enabled(context)) }
    var dateWarning3 by remember { mutableStateOf(AutomaticSmsManager.getDateWarning3(context)) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        item {
            val availableSims = remember(hasSmsPermission) { AutomaticSmsManager.getAvailableSims(context) }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isBn) "এসএমএস পাঠানোর সিম (SMS Sending SIM)" else "SMS Sending SIM",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    var expandedSim by remember { mutableStateOf(false) }
                    val currentSimLabel = if (selectedSim == -1) {
                        if (isBn) "সিস্টেম ডিফল্ট সিম" else "System Default SIM"
                    } else {
                        val simInfo = availableSims.find { it.subscriptionId == selectedSim }
                        if (simInfo != null) "SIM ${simInfo.slotIndex + 1} - ${simInfo.carrierName} - ${simInfo.number}" else (if (isBn) "নির্বাচিত সিম পাওয়া যায়নি" else "Selected SIM unavailable")
                    }

                    Box {
                        OutlinedButton(onClick = { expandedSim = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(text = currentSimLabel)
                        }
                        DropdownMenu(expanded = expandedSim, onDismissRequest = { expandedSim = false }) {
                            DropdownMenuItem(
                                text = { Text(if (isBn) "সিস্টেম ডিফল্ট সিম" else "System Default SIM") },
                                onClick = {
                                    onSelectedSimChange(-1)
                                    expandedSim = false
                                }
                            )
                            availableSims.forEach { sim ->
                                DropdownMenuItem(
                                    text = { Text("SIM ${sim.slotIndex + 1} - ${sim.carrierName} - ${sim.number}") },
                                    onClick = {
                                        onSelectedSimChange(sim.subscriptionId)
                                        expandedSim = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

// Bill Generated
        item {
            RuleCard(
                title = if (isBn) "📝 বিল তৈরির বার্তা (Bill Generated Message)" else "📝 Bill Generated Message",
                checked = ruleBillGenerated,
                onCheckedChange = {
                    ruleBillGenerated = it
                    AutomaticSmsManager.setRuleBillGeneratedEnabled(context, it)
                },
                onEditClick = {
                    onEditTemplate("bill_generated", if (isBn) "বিল তৈরির বার্তা" else "Bill Generated Message", AutomaticSmsManager.getTemplateBillGenerated(context))
                },
                isBn = isBn
            )
        }

        // Overdue Bill
        item {
            RuleCard(
                title = if (isBn) "🚫 বকেয়া নোটিশ (Overdue Notice)" else "🚫 Overdue Notice",
                checked = ruleOverdue,
                onCheckedChange = {
                    ruleOverdue = it
                    AutomaticSmsManager.setRuleOverdueEnabled(context, it)
                },
                onEditClick = {
                    onEditTemplate("overdue", if (isBn) "বকেয়া নোটিশ" else "Overdue Notice", AutomaticSmsManager.getTemplateOverdue(context))
                },
                isBn = isBn
            )
        }

        // Connection Suspend Notice
        item {
            RuleCard(
                title = if (isBn) "🔌 সংযোগ বিচ্ছিন্ন নোটিশ (Connection Suspend)" else "🔌 Connection Suspend Notice",
                checked = ruleOverdue,
                onCheckedChange = {
                    ruleOverdue = it
                    AutomaticSmsManager.setRuleOverdueEnabled(context, it)
                },
                onEditClick = {
                    onEditTemplate("connection_suspend", if (isBn) "সংযোগ বিচ্ছিন্ন নোটিশ" else "Connection Suspend Notice", AutomaticSmsManager.getTemplateConnectionSuspend(context))
                },
                isBn = isBn
            )
        }

        // Connection Resume Notice
        item {
            RuleCard(
                title = if (isBn) "⚡ সংযোগ পুনরায় চালু নোটিশ (Connection Resume)" else "⚡ Connection Resume Notice",
                checked = rulePayConfirm,
                onCheckedChange = {
                    rulePayConfirm = it
                    AutomaticSmsManager.setRulePaymentConfirmationEnabled(context, it)
                },
                onEditClick = {
                    onEditTemplate("connection_resume", if (isBn) "সংযোগ পুনরায় চালু নোটিশ" else "Connection Resume Notice", AutomaticSmsManager.getTemplateConnectionResume(context))
                },
                isBn = isBn
            )
        }

        // Expiry Warning Notice
        item {
            RuleCard(
                title = if (isBn) "⌛ মেয়াদ শেষের সতর্কবার্তা (Expiry Warning)" else "⌛ Expiry Warning Notice",
                checked = ruleDueReminder,
                onCheckedChange = {
                    ruleDueReminder = it
                    AutomaticSmsManager.setRuleDueReminderEnabled(context, it)
                },
                onEditClick = {
                    onEditTemplate("expiry_warning", if (isBn) "মেয়াদ শেষের সতর্কবার্তা" else "Expiry Warning Notice", AutomaticSmsManager.getTemplateExpiryWarning(context))
                },
                isBn = isBn
            )
        }

        // General Notice
        item {
            RuleCard(
                title = if (isBn) "📢 সাধারণ নোটিশ (General Notice)" else "📢 General Notice",
                checked = ruleGeneralNotice,
                onCheckedChange = {
                    ruleGeneralNotice = it
                    AutomaticSmsManager.setRuleGeneralNoticeEnabled(context, it)
                },
                onEditClick = {
                    onEditTemplate("general_notice", if (isBn) "সাধারণ নোটিশ" else "General Notice", AutomaticSmsManager.getTemplateGeneralNotice(context))
                },
                isBn = isBn
            )
        }

        // Bill Paid
        item {
            RuleCard(
                title = if (isBn) "✅ বিল পরিশোধের বার্তা (Bill Paid Message)" else "✅ Bill Paid Message",
                checked = rulePayConfirm,
                onCheckedChange = {
                    rulePayConfirm = it
                    AutomaticSmsManager.setRulePaymentConfirmationEnabled(context, it)
                },
                onEditClick = {
                    onEditTemplate("payment_confirmation", if (isBn) "বিল পরিশোধের বার্তা" else "Bill Paid Message", AutomaticSmsManager.getTemplatePaymentConfirmation(context))
                },
                isBn = isBn
            )
        }
        
// Due Offset Selection
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isBn) "বকেয়া বিলের মেসেজ পাঠানোর সময়" else "Due Reminder Timing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = if (isBn) "বকেয়া বিলের মেসেজ কবে পাঠানো হবে তা নির্বাচন করুন:" else "Select when the due reminder should be sent:",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    var expandedOffset by remember { mutableStateOf(false) }
                    val offsetLabel = when (dueOffset) {
                        -3 -> if (isBn) "বিলের শেষ তারিখের ৩ দিন আগে" else "3 days before due date"
                        -1 -> if (isBn) "বিলের শেষ তারিখের ১ দিন আগে" else "1 day before due date"
                        0 -> if (isBn) "বিলের শেষ তারিখে" else "On due date"
                        1 -> if (isBn) "বিলের শেষ তারিখের ১ দিন পর" else "1 day after due date"
                        3 -> if (isBn) "বিলের শেষ তারিখের ৩ দিন পর" else "3 days after due date"
                        else -> if (isBn) "বিলের শেষ তারিখে" else "On due date"
                    }
                    
                    Box {
                        OutlinedButton(onClick = { expandedOffset = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(text = offsetLabel)
                        }
                        DropdownMenu(expanded = expandedOffset, onDismissRequest = { expandedOffset = false }) {
                            val options = listOf(-3, -1, 0, 1, 3)
                            options.forEach { opt ->
                                val lbl = when (opt) {
                                    -3 -> if (isBn) "বিলের শেষ তারিখের ৩ দিন আগে" else "3 days before due date"
                                    -1 -> if (isBn) "বিলের শেষ তারিখের ১ দিন আগে" else "1 day before due date"
                                    0 -> if (isBn) "বিলের শেষ তারিখে" else "On due date"
                                    1 -> if (isBn) "বিলের শেষ তারিখের ১ দিন পর" else "1 day after due date"
                                    3 -> if (isBn) "বিলের শেষ তারিখের ৩ দিন পর" else "3 days after due date"
                                    else -> ""
                                }
                                DropdownMenuItem(
                                    text = { Text(text = lbl) },
                                    onClick = {
                                        dueOffset = opt
                                        AutomaticSmsManager.setDueReminderOffset(context, opt)
                                        expandedOffset = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Due Bill
        item {
            RuleCard(
                title = if (isBn) "⏰ বকেয়া বিলের বার্তা (Due Bill Message)" else "⏰ Due Bill Message",
                checked = ruleDueReminder,
                onCheckedChange = {
                    ruleDueReminder = it
                    AutomaticSmsManager.setRuleDueReminderEnabled(context, it)
                },
                onEditClick = {
                    onEditTemplate("due_reminder", if (isBn) "বকেয়া বিলের বার্তা" else "Due Bill Message", AutomaticSmsManager.getTemplateDueReminder(context))
                },
                isBn = isBn
            )
        }

        // Warning 1
        item {
            WarningCard(
                title = if (isBn) "⚠️ পেমেন্ট ওয়ার্নিং ১ (Payment Warning 1)" else "⚠️ Payment Warning 1",
                checked = ruleWarning1,
                date = dateWarning1,
                onCheckedChange = {
                    ruleWarning1 = it
                    AutomaticSmsManager.setRuleWarning1Enabled(context, it)
                },
                onDateChange = {
                    dateWarning1 = it
                    AutomaticSmsManager.setDateWarning1(context, it)
                },
                onEditClick = {
                    onEditTemplate("warning_1", if (isBn) "পেমেন্ট ওয়ার্নিং ১" else "Payment Warning 1", AutomaticSmsManager.getTemplateWarning1(context))
                },
                isBn = isBn
            )
        }

        // Warning 2
        item {
            WarningCard(
                title = if (isBn) "⚠️ পেমেন্ট ওয়ার্নিং ২ (Payment Warning 2)" else "⚠️ Payment Warning 2",
                checked = ruleWarning2,
                date = dateWarning2,
                onCheckedChange = {
                    ruleWarning2 = it
                    AutomaticSmsManager.setRuleWarning2Enabled(context, it)
                },
                onDateChange = {
                    dateWarning2 = it
                    AutomaticSmsManager.setDateWarning2(context, it)
                },
                onEditClick = {
                    onEditTemplate("warning_2", if (isBn) "পেমেন্ট ওয়ার্নিং ২" else "Payment Warning 2", AutomaticSmsManager.getTemplateWarning2(context))
                },
                isBn = isBn
            )
        }

        // Warning 3
        item {
            WarningCard(
                title = if (isBn) "⚠️ পেমেন্ট ওয়ার্নিং ৩ (Payment Warning 3)" else "⚠️ Payment Warning 3",
                checked = ruleWarning3,
                date = dateWarning3,
                onCheckedChange = {
                    ruleWarning3 = it
                    AutomaticSmsManager.setRuleWarning3Enabled(context, it)
                },
                onDateChange = {
                    dateWarning3 = it
                    AutomaticSmsManager.setDateWarning3(context, it)
                },
                onEditClick = {
                    onEditTemplate("warning_3", if (isBn) "পেমেন্ট ওয়ার্নিং ৩" else "Payment Warning 3", AutomaticSmsManager.getTemplateWarning3(context))
                },
                isBn = isBn
            )
        }
    }
}

@Composable
fun RuleCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    isBn: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onEditClick, modifier = Modifier.fillMaxWidth()) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (isBn) "টেমপ্লেট এডিট করুন" else "Edit Template")
            }
        }
    }
}

@Composable
fun WarningCard(
    title: String,
    checked: Boolean,
    date: Int,
    onCheckedChange: (Boolean) -> Unit,
    onDateChange: (Int) -> Unit,
    onEditClick: () -> Unit,
    isBn: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
            
            if (checked) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = if (isBn) "পাঠানোর তারিখ:" else "Send Date:", style = MaterialTheme.typography.bodyMedium)
                    
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text(text = if (isBn) "মাসের $date তারিখ" else "$date of month")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            for (i in 1..31) {
                                DropdownMenuItem(
                                    text = { Text(text = "$i") },
                                    onClick = {
                                        onDateChange(i)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onEditClick, modifier = Modifier.fillMaxWidth()) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (isBn) "টেমপ্লেট এডিট করুন" else "Edit Template")
            }
        }
    }
}

@Composable
fun SmsHistoryTab(
    context: Context,
    isBn: Boolean,
    smsList: List<SmsQueueEntity>,
    onOpenManualSms: () -> Unit,
    onDeleteSms: (SmsQueueEntity) -> Unit
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
                    SmsHistoryItemCard(sms = sms, isBn = isBn, onDelete = { onDeleteSms(sms) })
                }
            }
        }
    }
}

@Composable
fun SmsHistoryItemCard(
    sms: SmsQueueEntity,
    isBn: Boolean,
    onDelete: () -> Unit
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

            // If PENDING, SENDING, or FAILED display Delete/Cancel option
            if (sms.status == "PENDING" || sms.status == "SENDING" || sms.status == "FAILED") {
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDelete) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (isBn) "বাতিল/মুছুন" else "Cancel/Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // If SENT, display SIM info if available
            if (sms.status == "SENT" && !sms.lastError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "📱 ${sms.lastError}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
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
