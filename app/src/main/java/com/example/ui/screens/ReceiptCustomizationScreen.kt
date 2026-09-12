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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.BillEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.PaymentEntity
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.receipt_test_print), fontSize = 13.sp)
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
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.save), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
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

            // Section 4: Visible Fields Toggles
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
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = if (config.paperSize == "THERMAL_80MM") "থার্মাল 80mm" else "A4 সাইজ",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Simulated Mini Paper
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = settings.ispName.ifBlank { "আইএসপি ডিজিটাল নেটওয়ার্ক" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF1E3A8A),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "হটলাইন: ${settings.hotline.ifBlank { "০১৭০০-০০০০০০" }}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF2563EB)
                                ) {
                                    Text(
                                        text = config.receiptTitle.ifBlank { "পেমেন্ট রশিদ" },
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    PreviewItemRow("গ্রাহকের নাম:", "মোঃ আরিফুল ইসলাম (CUST-101)")
                                    if (config.showCustomerPhone) PreviewItemRow("মোবাইল নম্বর:", "01712-345678")
                                    if (config.showCustomerPppoe) PreviewItemRow("ইউজারনেম:", "ariful_net")
                                    if (config.showPackageName) PreviewItemRow("প্যাকেজ:", "Standard 20 Mbps")
                                    if (config.showCustomerAddress) PreviewItemRow("ঠিকানা:", "মিরপুর-১০, ঢাকা")
                                    PreviewItemRow("রশিদ নং:", "RCP-889201")
                                    PreviewItemRow("তারিখ:", "আজকের তারিখ")
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFF8FAFC),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        PreviewItemRow("মোট বিল:", "${settings.currencySymbol} 800.00")
                                        PreviewItemRow("পরিশোধিত:", "${settings.currencySymbol} 800.00", isSuccess = true)
                                        if (config.showRemainingDue) PreviewItemRow("অবশিষ্ট বকেয়া:", "${settings.currencySymbol} 0.00")
                                        if (config.showPaymentMethod) PreviewItemRow("পেমেন্ট মাধ্যম:", "bKash")
                                    }
                                }

                                if (config.customNotes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = config.customNotes,
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B),
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = config.footerMessage.ifBlank { "আমাদের ইন্টারনেট সেবা ব্যবহারের জন্য ধন্যবাদ!" },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF334155),
                                    textAlign = TextAlign.Center
                                )
                            }
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
