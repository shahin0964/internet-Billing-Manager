package com.example.ui.screens

import com.example.ui.components.formatAmount
import com.example.ui.components.formatAmountPrivacy
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BillEntity
import com.example.data.model.getDisplayBillNumber
import com.example.ui.components.CustomSearchBar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.StatusBadge
import com.example.ui.theme.EmeraldSuccess

import com.example.data.model.CustomerEntity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import java.net.URLEncoder
import java.util.Locale
import com.example.util.AutomaticSmsManager
import com.example.util.SmsTemplateManager

@Composable
fun BillingScreen(
    bills: List<BillEntity>,
    customers: List<CustomerEntity> = emptyList(),
    currencySymbol: String,
    ispName: String = "",
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onGenerateBillsClick: () -> Unit,
    onRecordPaymentForBill: (BillEntity) -> Unit,
    onEditBill: (BillEntity) -> Unit = {}
) {
    val context = LocalContext.current
    var selectedStatusFilter by remember { mutableStateOf("ALL") }
    val isPrivacyModeActive by com.example.util.PrivacyModeManager.privacyModeFlow.collectAsState()

    val customerMap = remember(customers) {
        customers.associateBy { it.id }
    }

    val filteredBills = remember(bills, searchQuery, selectedStatusFilter, customerMap) {
        bills.filter { bill ->
            // Only active, unpaid, or partial bills with outstanding dueAmount > 0 are displayed as running cards
            val isDueOrUnpaid = bill.dueAmount > 0.0 && bill.status != "PAID"

            val cust = customerMap[bill.customerId]
            val custPppoe = cust?.pppoeUsername ?: ""
            val matchesQuery = searchQuery.isBlank() ||
                    bill.customerName.contains(searchQuery, ignoreCase = true) ||
                    custPppoe.contains(searchQuery, ignoreCase = true) ||
                    bill.getDisplayBillNumber().contains(searchQuery, ignoreCase = true) ||
                    bill.billingMonth.contains(searchQuery, ignoreCase = true)

            val custStatus = cust?.status?.trim()?.uppercase(java.util.Locale.ROOT) ?: "ACTIVE"
            val matchesStatus = when (selectedStatusFilter) {
                "ACTIVE" -> custStatus == "ACTIVE"
                "SUSPENDED" -> custStatus == "SUSPENDED" || custStatus == "INACTIVE"
                else -> true
            }

            isDueOrUnpaid && matchesQuery && matchesStatus
        }.sortedWith { b1, b2 ->
            com.example.util.CustomerSortUtils.compareCustomerNames(b1.customerName, b2.customerName)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Top Billing Control Banner
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 3.dp,
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(14.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.monthly_billing_control),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.generate_bills_all),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onGenerateBillsClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(androidx.compose.ui.res.stringResource(com.example.R.string.generate), fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        CustomSearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            placeholder = androidx.compose.ui.res.stringResource(com.example.R.string.search_bills_hint)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Line Status Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "ALL" to "All Due Bills",
                "ACTIVE" to "Active Lines",
                "SUSPENDED" to "Suspended"
            ).forEach { (key, label) ->
                FilterChip(
                    selected = (selectedStatusFilter == key),
                    onClick = { selectedStatusFilter = key },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredBills.isEmpty()) {
            EmptyStateView(
                title = if (searchQuery.isNotEmpty()) androidx.compose.ui.res.stringResource(com.example.R.string.no_matching_bills) else androidx.compose.ui.res.stringResource(com.example.R.string.no_bills_generated),
                description = androidx.compose.ui.res.stringResource(com.example.R.string.generate_bills_desc),
                icon = Icons.Default.ReceiptLong,
                actionButton = {
                    Button(onClick = onGenerateBillsClick) {
                        Text(androidx.compose.ui.res.stringResource(com.example.R.string.generate_monthly_bills))
                    }
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredBills, key = { it.id }) { bill ->
                    val cust = customerMap[bill.customerId]
                    BillItemCard(
                        bill = bill,
                        customer = cust,
                        customerStatus = cust?.status,
                        pppoeUsername = cust?.pppoeUsername,
                        currencySymbol = currencySymbol,
                        ispName = ispName,
                        isPrivacyModeActive = isPrivacyModeActive,
                        onCollectPayment = { onRecordPaymentForBill(bill) },
                        onEditBill = { onEditBill(bill) },
                        onWhatsAppClick = {
                            launchWhatsAppForBill(
                                context = context,
                                bill = bill,
                                customer = cust,
                                currencySymbol = currencySymbol,
                                ispName = ispName
                            )
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(88.dp)) }
            }
        }
    }
}

@Composable
fun BillItemCard(
    bill: BillEntity,
    customer: CustomerEntity? = null,
    customerStatus: String? = null,
    pppoeUsername: String? = null,
    currencySymbol: String,
    ispName: String = "",
    isPrivacyModeActive: Boolean = false,
    onCollectPayment: () -> Unit,
    onEditBill: () -> Unit = {},
    onWhatsAppClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isBreakdown = bill.billNumber.startsWith("BREAKDOWN|")
    val parts = if (isBreakdown) bill.billNumber.split("|") else null
    val displayBillNo = parts?.getOrNull(3) ?: bill.billNumber
    val billingMonthLabel = parts?.getOrNull(2)?.split(":")?.getOrNull(0) ?: bill.billingMonth

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 3.dp,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = bill.customerName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (customerStatus != null && customerStatus.trim().uppercase(java.util.Locale.ROOT) != "ACTIVE") {
                            Spacer(modifier = Modifier.width(6.dp))
                            StatusBadge(status = customerStatus)
                        }
                    }
                    if (!pppoeUsername.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "PPPoE: ",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = pppoeUsername,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "$displayBillNo • $billingMonthLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(status = bill.status)
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        onClick = {
                            if (onWhatsAppClick != null) {
                                onWhatsAppClick()
                            } else {
                                launchWhatsAppForBill(context, bill, customer, currencySymbol, ispName)
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF25D366).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color(0xFF25D366).copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = com.example.R.drawable.ic_whatsapp),
                                contentDescription = "Send WhatsApp Reminder",
                                tint = Color(0xFF25D366),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    androidx.compose.material3.IconButton(
                        onClick = onEditBill,
                        modifier = Modifier.size(24.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Bill",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (isBreakdown && parts != null && parts.size >= 3) {
                Spacer(modifier = Modifier.height(10.dp))
                androidx.compose.material3.HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                val prevListRaw = parts[1]
                val prevItems = prevListRaw.split(",").mapNotNull {
                    val pair = it.split(":")
                    if (pair.size == 2) {
                        val m = pair[0]
                        val d = pair[1].toDoubleOrNull() ?: 0.0
                        m to d
                    } else null
                }
                
                Text(
                    text = if (prevItems.size > 1) "Previous Due Breakdown" else "Previous Due",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                prevItems.forEach { (m, d) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = m,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$currencySymbol${d.formatAmount()}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Current Bill",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                
                val currentPart = parts[2].split(":")
                val curMonth = currentPart.getOrNull(0) ?: bill.billingMonth
                val curDue = currentPart.getOrNull(1)?.toDoubleOrNull() ?: bill.dueAmount
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = curMonth,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$currencySymbol${curDue.formatAmount()}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (isBreakdown) "Total Payable" else androidx.compose.ui.res.stringResource(com.example.R.string.total_bill),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = bill.amount.formatAmountPrivacy(currencySymbol, isPrivacyModeActive),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                Column {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.paid_amount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = bill.paidAmount.formatAmountPrivacy(currencySymbol, isPrivacyModeActive),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = EmeraldSuccess
                        )
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isBreakdown) "Total Due" else androidx.compose.ui.res.stringResource(com.example.R.string.due_amount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = bill.dueAmount.formatAmountPrivacy(currencySymbol, isPrivacyModeActive),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (bill.dueAmount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            if (bill.dueAmount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onCollectPayment,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldSuccess
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreditCard,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Collect Payment ($currencySymbol${bill.dueAmount.formatAmount()})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Surface(
                        onClick = {
                            if (onWhatsAppClick != null) {
                                onWhatsAppClick()
                            } else {
                                launchWhatsAppForBill(context, bill, customer, currencySymbol, ispName)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF25D366).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color(0xFF25D366).copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(id = com.example.R.drawable.ic_whatsapp),
                                contentDescription = "WhatsApp Bill Reminder",
                                tint = Color(0xFF25D366),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "WhatsApp",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF128C7E)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Directly formats and opens WhatsApp chat with pre-filled bill details and due reminders.
 */
fun launchWhatsAppForBill(
    context: Context,
    bill: BillEntity,
    customer: CustomerEntity?,
    currencySymbol: String,
    ispName: String = ""
) {
    val rawPhone = customer?.phone?.trim() ?: ""
    if (rawPhone.isBlank()) {
        val noPhoneMsg = if (Locale.getDefault().language == "bn") {
            "গ্রাহকের কোনো ফোন নম্বর পাওয়া যায়নি"
        } else {
            "No phone number found for this customer"
        }
        Toast.makeText(context, noPhoneMsg, Toast.LENGTH_SHORT).show()
        return
    }

    val isBreakdown = bill.billNumber.startsWith("BREAKDOWN|")
    val parts = if (isBreakdown) bill.billNumber.split("|") else null
    val billingMonthLabel = parts?.getOrNull(2)?.split(":")?.getOrNull(0) ?: bill.billingMonth
    val customerName = if (!customer?.name.isNullOrBlank()) customer.name else bill.customerName
    val totalBillFormatted = "$currencySymbol${bill.amount.formatAmount()}"
    val dueAmountFormatted = "$currencySymbol${bill.dueAmount.formatAmount()}"
    val cleanTotal = bill.amount.formatAmount()
    val cleanDue = bill.dueAmount.formatAmount()
    val dueDate = bill.dueDate
    val companyName = ispName.ifBlank { "ISP Net" }

    // Retrieve active template from settings
    val configuredTemplate = if (bill.dueAmount > 0) {
        val tmpl = AutomaticSmsManager.getTemplateDueReminder(context)
        if (tmpl.isNotBlank()) tmpl else SmsTemplateManager.getSmsTemplate(context)
    } else {
        AutomaticSmsManager.getTemplatePaymentConfirmation(context)
    }

    var message = AutomaticSmsManager.processTemplate(
        template = configuredTemplate,
        customerName = customerName,
        monthlyFee = cleanTotal,
        dueAmount = cleanDue,
        dueDate = dueDate,
        packageSpeed = customer?.packageName ?: "",
        ispName = companyName,
        billMonth = billingMonthLabel,
        customerId = customer?.customerCode?.ifBlank { bill.customerCode } ?: bill.customerCode,
        receiptNo = bill.getDisplayBillNumber(),
        phoneNumber = rawPhone
    )

    // Also support brackets style template placeholders [Customer Name], [Due Amount], etc.
    message = SmsTemplateManager.replaceVariables(
        template = message,
        customerName = customerName,
        monthlyFee = totalBillFormatted,
        dueAmount = dueAmountFormatted,
        packageName = customer?.packageName ?: "",
        phone = rawPhone,
        ispName = companyName,
        dueDate = dueDate,
        customerId = customer?.customerCode?.ifBlank { bill.customerCode } ?: bill.customerCode
    )

    // If template didn't contain due or total bill info or is blank, construct a structured message
    if (message.isBlank() || (!message.contains(cleanDue) && bill.dueAmount > 0)) {
        val isBn = Locale.getDefault().language == "bn"
        message = if (isBn) {
            "প্রিয় $customerName,\nআপনার $billingMonthLabel মাসের ইন্টারনেট বিল $totalBillFormatted, বর্তমান বকেয়া: $dueAmountFormatted।\nপরিশোধের শেষ সময়: $dueDate।\nসংযোগ সচল রাখতে অনুগ্রহ করে দ্রুত বিল পরিশোধ করুন।\nধন্যবাদ,\n$companyName"
        } else {
            "Dear $customerName,\nYour Internet bill for $billingMonthLabel is $totalBillFormatted, Current Due: $dueAmountFormatted.\nDue Date: $dueDate.\nPlease pay your due bill to keep your connection active.\nThank you,\n$companyName"
        }
    } else if (!message.contains(cleanTotal) && !message.contains(totalBillFormatted)) {
        val isBn = Locale.getDefault().language == "bn"
        val extraInfo = if (isBn) {
            "\n(মোট বিল: $totalBillFormatted, বকেয়া: $dueAmountFormatted)"
        } else {
            "\n(Total Bill: $totalBillFormatted, Due: $dueAmountFormatted)"
        }
        message += extraInfo
    }

    try {
        val cleanDigits = rawPhone.replace(Regex("[^0-9]"), "")
        val formattedPhone = if (cleanDigits.startsWith("0")) {
            "880" + cleanDigits.substring(1)
        } else if (cleanDigits.length == 10 && !cleanDigits.startsWith("880")) {
            "880$cleanDigits"
        } else {
            cleanDigits
        }

        val encodedText = URLEncoder.encode(message.trim(), "UTF-8").replace("+", "%20")
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$formattedPhone&text=$encodedText")

        val whatsappIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.whatsapp")
        }
        try {
            context.startActivity(whatsappIntent)
        } catch (e: Exception) {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(fallbackIntent)
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Error opening WhatsApp: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

