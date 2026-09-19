package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.BillEntity
import com.example.data.model.BusinessSettingsEntity
import com.example.data.model.CustomerEntity
import com.example.util.CustomerExportHelper
import com.example.util.CustomerFilterStatus
import com.example.util.CustomerSortOption
import com.example.util.ExportCustomerField
import com.example.util.ExportFormat
import com.example.util.ExportedCustomerRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerExportContent(
    customers: List<CustomerEntity>,
    bills: List<BillEntity>,
    settings: BusinessSettingsEntity,
    isBn: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sortOption by remember { mutableStateOf(CustomerSortOption.A_TO_Z) }
    var filterStatus by remember { mutableStateOf(CustomerFilterStatus.ALL) }
    var selectedFields by remember {
        mutableStateOf(ExportCustomerField.values().filter { it.isDefaultSelected }.toSet())
    }
    var selectedFormat by remember { mutableStateOf(ExportFormat.EXCEL) }
    var isProcessing by remember { mutableStateOf(false) }

    val exportedRows = remember(customers, bills, sortOption, filterStatus) {
        CustomerExportHelper.prepareExportData(
            customers = customers,
            bills = bills,
            sortOption = sortOption,
            filterStatus = filterStatus,
            searchQuery = ""
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        // Description Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(9.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.export_customers_header),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stringResource(R.string.export_customers_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Sort & Status Filter Controls (without search box)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Sort By Row
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "সাজানোর ক্রম (Sort By):",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CustomerSortOption.values().forEach { opt ->
                                FilterChip(
                                    selected = sortOption == opt,
                                    onClick = { sortOption = opt },
                                    label = { Text(if (isBn) opt.titleBn else opt.titleEn, fontSize = 12.sp) },
                                    leadingIcon = if (sortOption == opt) {
                                        { Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    // Filter By Status Row
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "স্ট্যাটাস ফিল্টার:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CustomerFilterStatus.values().forEach { st ->
                                FilterChip(
                                    selected = filterStatus == st,
                                    onClick = { filterStatus = st },
                                    label = { Text(if (isBn) st.titleBn else st.titleEn, fontSize = 12.sp) },
                                    leadingIcon = if (filterStatus == st) {
                                        { Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }
        }

        // Field Selection Card (সিলেক্ট করুন কি কি চান)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "এক্সপোর্টে যা অন্তর্ভুক্ত করতে চান:",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${selectedFields.size} টি ফিল্ড নির্বাচিত",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        TextButton(
                            onClick = {
                                selectedFields = if (selectedFields.size == ExportCustomerField.values().size) {
                                    setOf(ExportCustomerField.NAME)
                                } else {
                                    ExportCustomerField.values().toSet()
                                }
                            }
                        ) {
                            Text(
                                text = if (selectedFields.size == ExportCustomerField.values().size) "ক্লিয়ার করুন" else "সব নির্বাচন",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Field list in 2-column grid
                    val allFields = ExportCustomerField.values().toList()
                    allFields.chunked(2).forEach { rowFields ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowFields.forEach { field ->
                                val isSelected = field in selectedFields
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            selectedFields = if (isSelected) {
                                                if (selectedFields.size > 1) selectedFields - field else selectedFields
                                            } else {
                                                selectedFields + field
                                            }
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedFields = if (checked) {
                                                    selectedFields + field
                                                } else {
                                                    if (selectedFields.size > 1) selectedFields - field else selectedFields
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isBn) field.titleBn else field.titleEn,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 11.5.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (rowFields.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Export Format Selection (এক্সেল / পিডিএফ) & Download Buttons
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "এক্সপোর্ট ফরম্যাট নির্বাচন করুন:",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Format Radio Cards: Excel vs PDF
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Excel Option
                        val isExcel = selectedFormat == ExportFormat.EXCEL
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedFormat = ExportFormat.EXCEL },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isExcel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                if (isExcel) 2.dp else 1.dp,
                                if (isExcel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                RadioButton(
                                    selected = isExcel,
                                    onClick = { selectedFormat = ExportFormat.EXCEL },
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "এক্সেল (Excel)",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = ".CSV ফাইল",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // PDF Option
                        val isPdf = selectedFormat == ExportFormat.PDF
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedFormat = ExportFormat.PDF },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isPdf) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                if (isPdf) 2.dp else 1.dp,
                                if (isPdf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                RadioButton(
                                    selected = isPdf,
                                    onClick = { selectedFormat = ExportFormat.PDF },
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "পিডিএফ (PDF)",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "প্রিন্ট / PDF",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Main Action Button for the selected format
                    Button(
                        onClick = {
                            if (exportedRows.isEmpty()) {
                                Toast.makeText(context, "এক্সপোর্ট করার জন্য কোনো গ্রাহক নেই", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (selectedFormat == ExportFormat.EXCEL) {
                                isProcessing = true
                                scope.launch(Dispatchers.IO) {
                                    val file = CustomerExportHelper.generateCsvFile(context, exportedRows, selectedFields, isBn)
                                    val savedFile = CustomerExportHelper.saveToDownloads(context, file)
                                    withContext(Dispatchers.Main) {
                                        isProcessing = false
                                        if (savedFile != null) {
                                            Toast.makeText(
                                                context,
                                                "সফলভাবে Downloads এ সেভ হয়েছে: ${savedFile.name}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            CustomerExportHelper.shareCsvFile(context, file)
                                        }
                                    }
                                }
                            } else {
                                CustomerExportHelper.printCustomerReport(
                                    context = context,
                                    rows = exportedRows,
                                    ispName = settings.ispName,
                                    selectedFields = selectedFields,
                                    currencySymbol = settings.currencySymbol,
                                    isBn = isBn
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isProcessing && exportedRows.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = if (selectedFormat == ExportFormat.EXCEL) Icons.Default.Download else Icons.Default.Print,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedFormat == ExportFormat.EXCEL) {
                                "এক্সেল ফাইলে এক্সপোর্ট করুন (${exportedRows.size} জন)"
                            } else {
                                "পিডিএফ ফাইলে এক্সপোর্ট করুন (${exportedRows.size} জন)"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // Direct Share Button if Excel is chosen
                    if (selectedFormat == ExportFormat.EXCEL) {
                        OutlinedButton(
                            onClick = {
                                if (exportedRows.isEmpty()) {
                                    Toast.makeText(context, "এক্সপোর্ট করার জন্য কোনো গ্রাহক নেই", Toast.LENGTH_SHORT).show()
                                    return@OutlinedButton
                                }
                                isProcessing = true
                                scope.launch(Dispatchers.IO) {
                                    val file = CustomerExportHelper.generateCsvFile(context, exportedRows, selectedFields, isBn)
                                    withContext(Dispatchers.Main) {
                                        isProcessing = false
                                        CustomerExportHelper.shareCsvFile(context, file)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isProcessing && exportedRows.isNotEmpty()
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "এক্সেল ফাইল সরাসরি শেয়ার করুন", fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Live Preview Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "গ্রাহক প্রিভিউ তালিকা (${if (sortOption == CustomerSortOption.A_TO_Z) "A থেকে Z" else "সাজানো"})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${exportedRows.size} জন",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (exportedRows.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.export_no_customers),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(exportedRows, key = { it.customerId }) { row ->
                CustomerExportCard(row = row, currencySymbol = settings.currencySymbol)
            }
        }
    }
}

@Composable
private fun CustomerExportCard(
    row: ExportedCustomerRow,
    currencySymbol: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top Row: Serial, Name, Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${row.serialNo}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = row.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (row.status.equals("ACTIVE", ignoreCase = true)) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                ) {
                    Text(
                        text = row.status,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (row.status.equals("ACTIVE", ignoreCase = true)) Color(0xFF15803D) else Color(0xFFB91C1C),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Details Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    DetailText(label = "আইডি:", value = row.customerCode)
                    DetailText(label = "মোবাইল:", value = row.phone.ifBlank { "N/A" })
                    DetailText(label = "প্যাকেজ:", value = row.packageName)
                }

                Column(modifier = Modifier.weight(1f)) {
                    DetailText(label = "বিল:", value = "$currencySymbol ${String.format(Locale.US, "%.0f", row.monthlyBill)}")
                    DetailText(
                        label = "বকেয়া:",
                        value = "$currencySymbol ${String.format(Locale.US, "%.0f", row.dueAmount)}",
                        valueColor = if (row.dueAmount > 0) Color(0xFFDC2626) else Color(0xFF16A34A)
                    )
                    DetailText(label = "PPPoE:", value = row.pppoeUsername)
                }
            }

            // PPPoE Password Row (if present)
            if (row.password.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "পাসওয়ার্ড: ${row.password}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Address if available
            if (row.address.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ঠিকানা: ${row.address}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DetailText(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = valueColor,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
