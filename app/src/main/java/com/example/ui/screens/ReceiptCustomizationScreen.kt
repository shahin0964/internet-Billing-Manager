package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.BillEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.PaymentEntity
import com.example.ui.components.CorporateMoneyReceiptView
import com.example.ui.viewmodel.IspViewModel
import com.example.util.ReceiptCustomizationConfig
import com.example.util.ReceiptCustomizationManager
import com.example.util.ReceiptPrintUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptCustomizationScreen(
    viewModel: IspViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var config by remember { mutableStateOf(ReceiptCustomizationManager.getConfig(context)) }
    var hasChanges by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    fun updateConfig(newConfig: ReceiptCustomizationConfig) {
        config = newConfig
        hasChanges = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.receipt_customization),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
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
                actions = {
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = stringResource(R.string.receipt_reset_defaults),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = {
                        ReceiptCustomizationManager.saveConfig(context, config)
                        hasChanges = false
                        Toast.makeText(
                            context,
                            context.getString(R.string.receipt_save_success),
                            Toast.LENGTH_SHORT
                        ).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = stringResource(R.string.save),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
        ) {
            // Section 1: Auto Popup on Bill Payment
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.receipt_auto_popup_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.receipt_auto_popup_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = config.autoPopupEnabled,
                            onCheckedChange = { checked ->
                                updateConfig(config.copy(autoPopupEnabled = checked))
                            }
                        )
                    }
                }
            }

            // Section 2: Format / Paper Size
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.receipt_paper_size),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FormatOptionCard(
                                title = stringResource(R.string.receipt_paper_money_receipt),
                                icon = Icons.Default.Receipt,
                                isSelected = config.paperSize == "MONEY_RECEIPT",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    updateConfig(config.copy(paperSize = "MONEY_RECEIPT"))
                                }
                            )

                            FormatOptionCard(
                                title = stringResource(R.string.receipt_paper_standard),
                                icon = Icons.Default.Article,
                                isSelected = config.paperSize == "A4",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    updateConfig(config.copy(paperSize = "A4"))
                                }
                            )

                            FormatOptionCard(
                                title = stringResource(R.string.receipt_paper_thermal),
                                icon = Icons.Default.ReceiptLong,
                                isSelected = config.paperSize == "THERMAL_80MM",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    updateConfig(config.copy(paperSize = "THERMAL_80MM"))
                                }
                            )
                        }
                    }
                }
            }

            // Section 3: Header, Footer & Custom Notes
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "শিরোনাম ও নোট কাস্টমাইজেশন",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        OutlinedTextField(
                            value = config.receiptTitle,
                            onValueChange = { updateConfig(config.copy(receiptTitle = it)) },
                            label = { Text(stringResource(R.string.receipt_title_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = config.footerMessage,
                            onValueChange = { updateConfig(config.copy(footerMessage = it)) },
                            label = { Text(stringResource(R.string.receipt_footer_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = config.customNotes,
                            onValueChange = { updateConfig(config.copy(customNotes = it)) },
                            label = { Text(stringResource(R.string.receipt_custom_notes_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            shape = RoundedCornerShape(8.dp),
                            placeholder = { Text("যেমন: হেল্পলাইন ২৪ ঘণ্টা খোলা থাকে।") }
                        )
                    }
                }
            }

            // Section 4: Mobile Banking & Signature Customization
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "মোবাইল ব্যাংকিং ও স্বাক্ষর (bKash / Nagad & Signature)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Text(
                            text = "মানি রসিদের টপ-রাইট বক্সে প্রদর্শনের জন্য বিকাশ ও নগদ নম্বর এবং অনুমোদিত স্বাক্ষরকারীর নাম সেট করুন।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = config.bkashNumber,
                            onValueChange = { updateConfig(config.copy(bkashNumber = it)) },
                            label = { Text("bKash Number / বিকাশ নম্বর") },
                            placeholder = { Text("যেমন: 01XXXXXXXXX") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Smartphone,
                                    contentDescription = null,
                                    tint = Color(0xFFD81B60),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = config.nagadNumber,
                            onValueChange = { updateConfig(config.copy(nagadNumber = it)) },
                            label = { Text("Nagad Number / নগদ নম্বর") },
                            placeholder = { Text("যেমন: 01XXXXXXXXX") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Smartphone,
                                    contentDescription = null,
                                    tint = Color(0xFFEA580C),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = config.signatureName,
                            onValueChange = { updateConfig(config.copy(signatureName = it)) },
                            label = { Text("Signature Name / স্বাক্ষরকারীর নাম") },
                            placeholder = { Text("যেমন: Md Shahin") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = config.corporateMotto,
                            onValueChange = { updateConfig(config.copy(corporateMotto = it)) },
                            label = { Text("Corporate Motto / কোম্পানির স্লোগান") },
                            placeholder = { Text("যেমন: Stay Connected, Stay Ahead") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // Section 5: Visible Fields Toggles
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.receipt_visible_fields),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        FieldToggleRow(
                            title = stringResource(R.string.receipt_show_phone),
                            checked = config.showCustomerPhone,
                            onCheckedChange = { updateConfig(config.copy(showCustomerPhone = it)) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        FieldToggleRow(
                            title = stringResource(R.string.receipt_show_pppoe),
                            checked = config.showCustomerPppoe,
                            onCheckedChange = { updateConfig(config.copy(showCustomerPppoe = it)) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        FieldToggleRow(
                            title = stringResource(R.string.receipt_show_package),
                            checked = config.showPackageName,
                            onCheckedChange = { updateConfig(config.copy(showPackageName = it)) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        FieldToggleRow(
                            title = stringResource(R.string.receipt_show_address),
                            checked = config.showCustomerAddress,
                            onCheckedChange = { updateConfig(config.copy(showCustomerAddress = it)) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        FieldToggleRow(
                            title = stringResource(R.string.receipt_show_due),
                            checked = config.showRemainingDue,
                            onCheckedChange = { updateConfig(config.copy(showRemainingDue = it)) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                        FieldToggleRow(
                            title = stringResource(R.string.receipt_show_method),
                            checked = config.showPaymentMethod,
                            onCheckedChange = { updateConfig(config.copy(showPaymentMethod = it)) }
                        )
                    }
                }
            }

            // Section 5: Live Real-time Visual Preview
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Preview,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.receipt_live_preview),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = when (config.paperSize) {
                                    "THERMAL_80MM" -> Color(0xFFFEF3C7)
                                    "A4" -> Color(0xFFE0E7FF)
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                }
                            ) {
                                Text(
                                    text = when (config.paperSize) {
                                        "THERMAL_80MM" -> "থার্মাল ৮০মিমি (POS Slip)"
                                        "A4" -> "এ৪ স্ট্যান্ডার্ড ইনভয়েস (A4 Invoice)"
                                        else -> "মানি রশিদ ভাউচার (Money Receipt)"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = when (config.paperSize) {
                                        "THERMAL_80MM" -> Color(0xFF92400E)
                                        "A4" -> Color(0xFF3730A3)
                                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Dynamic preview corresponding to the selected receipt format
                        when (config.paperSize) {
                            "THERMAL_80MM" -> ThermalReceiptLivePreview(config = config, settings = settings)
                            "A4" -> A4InvoiceLivePreview(config = config, settings = settings)
                            else -> MoneyReceiptLivePreview(config = config, settings = settings)
                        }
                    }
                }
            }

            // Section 8: Action Controls (Test Print Receipt & Save)
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 3.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                val samplePayment = PaymentEntity(
                                    id = 1,
                                    paymentReceiptNo = "RCP-${System.currentTimeMillis().toString().takeLast(6)}",
                                    billId = 1,
                                    customerId = 1,
                                    customerName = "মোঃ আরিফুল ইসলাম",
                                    amount = 800.0,
                                    paymentDate = today,
                                    paymentMethod = "bKash (01700-112233)",
                                    notes = "Sample test payment"
                                )
                                val sampleBill = BillEntity(
                                    id = 1,
                                    billNumber = "INV-SAMPLE",
                                    customerId = 1,
                                    customerName = "মোঃ আরিফুল ইসলাম",
                                    customerCode = "CUST-101",
                                    billingMonth = today.take(7),
                                    amount = 800.0,
                                    paidAmount = 800.0,
                                    dueAmount = 0.0,
                                    status = "PAID",
                                    generatedDate = today,
                                    dueDate = today
                                )
                                val sampleCust = CustomerEntity(
                                    id = 1,
                                    customerCode = "CUST-101",
                                    name = "মোঃ আরিফুল ইসলাম",
                                    phone = "01712-345678",
                                    address = "বাড়ি # ১২, রোড # ৪, মিরপুর-১০, ঢাকা",
                                    pppoeUsername = "ariful_net",
                                    packageId = 1,
                                    packageName = "Standard 20 Mbps",
                                    monthlyFee = 800.0,
                                    joiningDate = today
                                )
                                ReceiptPrintUtils.printReceipt(
                                    context = context,
                                    payment = samplePayment,
                                    bill = sampleBill,
                                    customer = sampleCust,
                                    settings = settings,
                                    isBn = true
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.receipt_test_print),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = {
                                ReceiptCustomizationManager.saveConfig(context, config)
                                hasChanges = false
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.receipt_save_success),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.save),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.receipt_reset_defaults)) },
            text = { Text(stringResource(R.string.receipt_reset_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        ReceiptCustomizationManager.resetToDefaults(context)
                        config = ReceiptCustomizationManager.getConfig(context)
                        hasChanges = false
                        showResetDialog = false
                        Toast.makeText(context, "রশিদ কাস্টমাইজেশন পূর্বাবস্থায় ফিরে গেছে", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(text = "রিসেট করুন", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * 1. Money Receipt Live Preview - Corporate Money Voucher format inspired by the reference image
 */
@Composable
private fun MoneyReceiptLivePreview(
    config: ReceiptCustomizationConfig,
    settings: com.example.data.model.BusinessSettingsEntity
) {
    val samplePayment = remember {
        PaymentEntity(
            id = 1,
            paymentReceiptNo = "RCP-889201",
            billId = 1,
            customerId = 1,
            customerName = "মোঃ আরিফুল ইসলাম",
            amount = 1500.0,
            paymentDate = "2026-09-30",
            paymentMethod = "bKash",
            notes = "September 2026 Monthly Internet Bill"
        )
    }
    val sampleBill = remember {
        BillEntity(
            id = 1,
            billNumber = "INV-2026-0901",
            customerId = 1,
            customerName = "মোঃ আরিফুল ইসলাম",
            customerCode = "CUST-101",
            billingMonth = "September 2026",
            amount = 1500.0,
            paidAmount = 1500.0,
            dueAmount = 0.0,
            status = "PAID",
            generatedDate = "2026-09-01",
            dueDate = "2026-09-10"
        )
    }
    val sampleCustomer = remember {
        CustomerEntity(
            id = 1,
            customerCode = "CUST-101",
            name = "মোঃ আরিফুল ইসলাম",
            phone = "01712-345678",
            address = "মিরপুর-১০, ঢাকা",
            pppoeUsername = "ariful_net",
            packageId = 1,
            packageName = "Standard 20 Mbps",
            monthlyFee = 1500.0,
            joiningDate = "2026-01-01",
            area = "মিরপুর শাখা"
        )
    }

    CorporateMoneyReceiptView(
        payment = samplePayment,
        bill = sampleBill,
        customer = sampleCustomer,
        settings = settings,
        config = config,
        bkashNumber = config.bkashNumber,
        nagadNumber = config.nagadNumber,
        signatureName = config.signatureName,
        motto = config.corporateMotto
    )
}

/**
 * 2. A4 Standard Corporate Invoice Live Preview - Full structured invoice with header box, billed-to section, item table, and terms
 */
@Composable
private fun A4InvoiceLivePreview(
    config: ReceiptCustomizationConfig,
    settings: com.example.data.model.BusinessSettingsEntity
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF94A3B8)),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Corporate Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1.3f)) {
                    Text(
                        text = settings.ispName.ifBlank { "আইএসপি ডিজিটাল নেটওয়ার্ক" },
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = settings.address.ifBlank { "হেড অফিস, ঢাকা, বাংলাদেশ" },
                        fontSize = 9.sp,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        text = "হটলাইন: ${settings.hotline.ifBlank { "০১৭০০-০০০০০০" }}",
                        fontSize = 9.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFF1F5F9),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(6.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = config.receiptTitle.ifBlank { "ইনভয়েস চালান" },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E3A8A)
                        )
                        Text(text = "ইনভয়েস #: INV-2026-0901", fontSize = 8.sp, color = Color(0xFF334155))
                        Text(text = "রশিদ #: RCP-889201", fontSize = 8.sp, color = Color(0xFF334155))
                        Text(text = "তারিখ: আজকের তারিখ", fontSize = 8.sp, color = Color(0xFF64748B))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFF1E3A8A), thickness = 2.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Bill To Box
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFFF8FAFC),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "বিল প্রাপক (BILLED TO):",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "মোঃ আরিফুল ইসলাম [ আইডি: CUST-101 ]",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (config.showCustomerPhone) {
                            Text(text = "ফোন: 01712-345678", fontSize = 8.5.sp, color = Color(0xFF475569))
                        }
                        if (config.showCustomerPppoe) {
                            Text(text = "PPPoE: ariful_net", fontSize = 8.5.sp, color = Color(0xFF475569))
                        }
                        if (config.showPackageName) {
                            Text(text = "প্যাকেজ: 20 Mbps", fontSize = 8.5.sp, color = Color(0xFF475569))
                        }
                    }
                    if (config.showCustomerAddress) {
                        Text(text = "ঠিকানা: বাড়ি # ১২, রোড # ৪, মিরপুর-১০, ঢাকা", fontSize = 8.5.sp, color = Color(0xFF64748B))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Itemized Table Header
            Surface(
                color = Color(0xFF1E3A8A),
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "নং", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(0.3f))
                    Text(text = "সেবার বিবরণ", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1.5f))
                    Text(text = "মাস", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(0.7f))
                    Text(text = "মূল্য (${settings.currencySymbol})", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End, modifier = Modifier.weight(0.8f))
                }
            }

            // Table Row 1
            Surface(
                color = Color(0xFFFFFFFF),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFCBD5E1)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "০১", fontSize = 8.5.sp, color = Color(0xFF0F172A), modifier = Modifier.weight(0.3f))
                    Column(modifier = Modifier.weight(1.5f)) {
                        Text(text = "ব্রডব্যান্ড ইন্টারনেট মাসিক ফি", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                        if (config.showPackageName) Text(text = "প্যাকেজ: Standard 20 Mbps Unlimited", fontSize = 7.5.sp, color = Color(0xFF64748B))
                    }
                    Text(text = "সেপ্টেম্বর ২০২৬", fontSize = 8.5.sp, color = Color(0xFF475569), modifier = Modifier.weight(0.7f))
                    Text(text = "800.00", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), textAlign = TextAlign.End, modifier = Modifier.weight(0.8f))
                }
            }

            // Subtotal & Summary Rows
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalAlignment = Alignment.End
            ) {
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.width(180.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("মোট বিল:", fontSize = 8.5.sp, color = Color(0xFF475569))
                            Text("${settings.currencySymbol} 800.00", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("পরিশোধিত:", fontSize = 8.5.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
                            Text("${settings.currencySymbol} 800.00", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                        }
                        if (config.showRemainingDue) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("অবশিষ্ট বকেয়া:", fontSize = 8.5.sp, color = Color(0xFFDC2626))
                                Text("${settings.currencySymbol} 0.00", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                            }
                        }
                        if (config.showPaymentMethod) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("পেমেন্ট মাধ্যম:", fontSize = 8.sp, color = Color(0xFF64748B))
                                Text("bKash Online", fontSize = 8.sp, color = Color(0xFF0F172A))
                            }
                        }
                    }
                }
            }

            if (config.customNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "শর্তাবলী ও নোট: ${config.customNotes}",
                    fontSize = 8.5.sp,
                    color = Color(0xFF64748B)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Signature Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("_____________________", fontSize = 8.sp, color = Color(0xFF94A3B8))
                    Text("গ্রাহকের স্বাক্ষর ও তারিখ", fontSize = 8.sp, color = Color(0xFF64748B))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (config.signatureName.isNotBlank()) {
                        Text(
                            text = config.signatureName,
                            fontFamily = FontFamily.Cursive,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF1E3A8A)
                        )
                    }
                    Text("_____________________", fontSize = 8.sp, color = Color(0xFF1E3A8A))
                    Text("অনুমোদিত কর্মকর্তার স্বাক্ষর ও সিল", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = config.footerMessage.ifBlank { "আমাদের ইন্টারনেট সেবা ব্যবহার করার জন্য ধন্যবাদ!" },
                fontSize = 8.5.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 3. Thermal 80mm POS Slip Live Preview - Monospaced roll slip receipt format with dashed line dividers and compact style
 */
@Composable
private fun ThermalReceiptLivePreview(
    config: ReceiptCustomizationConfig,
    settings: com.example.data.model.BusinessSettingsEntity
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFFFFFDF7),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD1D5DB)),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Receipt Header
            Text(
                text = settings.ispName.ifBlank { "আইএসপি ডিজিটাল নেটওয়ার্ক" }.uppercase(),
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
            Text(
                text = settings.address.ifBlank { "হেড অফিস, ঢাকা, বাংলাদেশ" },
                fontSize = 9.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = Color.DarkGray,
                textAlign = TextAlign.Center
            )
            Text(
                text = "TEL: ${settings.hotline.ifBlank { "০১৭০০-০০০০০০" }}",
                fontSize = 9.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = Color.DarkGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "------------------------------------------",
                fontSize = 9.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = Color.Gray,
                maxLines = 1
            )

            // Receipt Title badge in thermal style
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = Color.Black,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Text(
                    text = " [ ${config.receiptTitle.ifBlank { "পেমেন্ট রশিদ" }} ] ",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Text(
                text = "------------------------------------------",
                fontSize = 9.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = Color.Gray,
                maxLines = 1
            )

            // Monospace Metadata Lines
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ThermalRow("রশিদ নং / REC #:", "RCP-889201")
                ThermalRow("তারিখ / DATE:", "2026-09-26")
                ThermalRow("বিলিং মাস / MONTH:", "2026-09")
                ThermalRow("গ্রাহক / CUSTOMER:", "মোঃ আরিফুল ইসলাম")
                ThermalRow("আইডি / CODE:", "CUST-101")
                if (config.showCustomerPhone) ThermalRow("ফোন / TEL:", "01712-345678")
                if (config.showCustomerPppoe) ThermalRow("PPPoE USER:", "ariful_net")
                if (config.showPackageName) ThermalRow("প্যাকেজ / PLAN:", "20 Mbps")
                if (config.showCustomerAddress) ThermalRow("ঠিকানা / ADDR:", "মিরপুর-১০, ঢাকা")
            }

            Text(
                text = "------------------------------------------",
                fontSize = 9.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = Color.Gray,
                maxLines = 1
            )

            // Item Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "1x Monthly Internet Bill",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = Color.Black
                )
                Text(
                    text = "800.00",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = Color.Black
                )
            }

            Text(
                text = "==========================================",
                fontSize = 9.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = Color.Black,
                maxLines = 1
            )

            // Total Calculations
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ThermalRow("মোট বিল / TOTAL:", "${settings.currencySymbol} 800.00", isBold = true)
                ThermalRow("পরিশোধ / PAID:", "${settings.currencySymbol} 800.00", isBold = true)
                if (config.showRemainingDue) {
                    ThermalRow("বকেয়া / DUE:", "${settings.currencySymbol} 0.00", isBold = true)
                }
                if (config.showPaymentMethod) {
                    ThermalRow("পেমেন্ট মাধ্যম / METHOD:", "bKash (01700-112233)")
                }
            }

            Text(
                text = "------------------------------------------",
                fontSize = 9.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = Color.Gray,
                maxLines = 1
            )

            if (config.customNotes.isNotBlank()) {
                Text(
                    text = "* ${config.customNotes}",
                    fontSize = 8.5.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Simulated Barcode / Cut line
            Text(
                text = "|||| ||||| |||| ||||| ||||||| ||||",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                letterSpacing = 2.sp,
                color = Color.Black
            )
            Text(
                text = "* RCP889201 *",
                fontSize = 8.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = config.footerMessage.ifBlank { "ধন্যবাদ! আবার আসবেন।" },
                fontSize = 9.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
            Text(
                text = "*** PAID - THANK YOU ***",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ThermalRow(
    label: String,
    value: String,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = if (isBold) Color.Black else Color.DarkGray
        )
        Text(
            text = value,
            fontSize = 9.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = Color.Black
        )
    }
}

@Composable
private fun FormatOptionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FieldToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun PreviewItemRow(
    label: String,
    value: String,
    isSuccess: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 10.sp, color = Color(0xFF64748B))
        Text(
            text = value,
            fontSize = 10.sp,
            fontWeight = if (isSuccess) FontWeight.Bold else FontWeight.Normal,
            color = if (isSuccess) Color(0xFF16A34A) else Color(0xFF1E293B)
        )
    }
}
