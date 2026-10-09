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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.data.database.IspDatabase
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
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = "$displayBillNo • $billingMonthLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(status = bill.status)
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
 * Converts stored billing month strings (e.g. "2026-09", "September 2026", "সেপ্টেম্বর ২০২৬")
 * into clear readable Month Year display format (e.g. "September 2026").
 */
fun formatDisplayMonthYear(rawMonth: String): String {
    val trimmed = rawMonth.trim()
    if (trimmed.isEmpty()) return ""

    val englishMonths = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    // Check YYYY-MM or YYYY/MM pattern (e.g. 2026-09 or 2026-9)
    val yyyyMmRegex = """^(\d{4})[-/](\d{1,2})$""".toRegex()
    val match = yyyyMmRegex.find(trimmed)
    if (match != null) {
        val year = match.groupValues[1]
        val mIdx = (match.groupValues[2].toIntOrNull() ?: 1) - 1
        val mName = englishMonths.getOrElse(mIdx) { "Month ${mIdx + 1}" }
        return "$mName $year"
    }

    // Check if it already contains an English month name and 4 digit year
    for (m in englishMonths) {
        if (trimmed.contains(m, ignoreCase = true)) {
            val yearMatch = """\b(20\d{2}|19\d{2})\b""".toRegex().find(trimmed)
            return if (yearMatch != null) "$m ${yearMatch.value}" else trimmed
        }
    }

    // Check Bengali month names
    val bnToEnMonth = mapOf(
        "জানুয়ারি" to "January", "জানুয়ারী" to "January",
        "ফেব্রুয়ারি" to "February", "ফেব্রুয়ারী" to "February",
        "মার্চ" to "March", "এপ্রিল" to "April", "মে" to "May",
        "জুন" to "June", "জুলাই" to "July", "আগস্ট" to "August", "আগষ্ট" to "August",
        "সেপ্টেম্বর" to "September", "অক্টোবর" to "October",
        "নভেম্বর" to "November", "ডিসেম্বর" to "December"
    )
    for ((bn, en) in bnToEnMonth) {
        if (trimmed.contains(bn)) {
            val converted = trimmed.map { ch ->
                when (ch) {
                    '০' -> '0'; '১' -> '1'; '২' -> '2'; '৩' -> '3'; '৪' -> '4'
                    '৫' -> '5'; '৬' -> '6'; '৭' -> '7'; '৮' -> '8'; '৯' -> '9'
                    else -> ch
                }
            }.joinToString("")
            val yearMatch = """\b(20\d{2}|19\d{2})\b""".toRegex().find(converted)
            val year = yearMatch?.value ?: ""
            return if (year.isNotEmpty()) "$en $year" else en
        }
    }

    return trimmed
}

/**
 * Directly formats and opens WhatsApp chat with pre-filled month-by-month bill breakdown and due reminders.
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

    CoroutineScope(Dispatchers.IO).launch {
        try {
            // 1. Retrieve all unpaid or partially paid bills for the specific customer from the database
            val db = IspDatabase.getDatabase(context)
            val customerDbBills = try {
                db.billDao().getBillsListForCustomer(bill.customerId)
            } catch (e: Exception) {
                emptyList<BillEntity>()
            }

            // Filter for actual unpaid or partially paid bills (ignoring synthetic BREAKDOWN bills in DB if any)
            val unpaidDbBills = customerDbBills
                .filter { !it.billNumber.startsWith("BREAKDOWN|") }
                .filter { (it.status == "UNPAID" || it.status == "PARTIAL") && it.dueAmount > 0.0 }
                .sortedBy { it.id }

            data class MonthDueItem(val monthLabel: String, val dueAmount: Double)
            val monthDueItems = mutableListOf<MonthDueItem>()

            if (unpaidDbBills.isNotEmpty()) {
                for (b in unpaidDbBills) {
                    val formattedMonth = formatDisplayMonthYear(b.billingMonth)
                    monthDueItems.add(MonthDueItem(formattedMonth, b.dueAmount))
                }
            } else {
                // If database query had no separate records, check if bill itself is a BREAKDOWN bill
                val isBreakdown = bill.billNumber.startsWith("BREAKDOWN|")
                if (isBreakdown) {
                    val parts = bill.billNumber.split("|")
                    if (parts.size >= 3) {
                        val prevListRaw = parts[1]
                        val prevItems = prevListRaw.split(",").mapNotNull {
                            val pair = it.split(":")
                            if (pair.size == 2) {
                                val m = formatDisplayMonthYear(pair[0])
                                val d = pair[1].toDoubleOrNull() ?: 0.0
                                if (d > 0.0) MonthDueItem(m, d) else null
                            } else null
                        }
                        monthDueItems.addAll(prevItems)

                        val currentPart = parts[2].split(":")
                        val curMonth = formatDisplayMonthYear(currentPart.getOrNull(0) ?: bill.billingMonth)
                        val curDue = currentPart.getOrNull(1)?.toDoubleOrNull() ?: bill.dueAmount
                        if (curDue > 0.0) {
                            monthDueItems.add(MonthDueItem(curMonth, curDue))
                        }
                    }
                }
                // Fallback to the current bill if list is still empty
                if (monthDueItems.isEmpty() && bill.dueAmount > 0.0) {
                    monthDueItems.add(MonthDueItem(formatDisplayMonthYear(bill.billingMonth), bill.dueAmount))
                }
            }

            // 2. Format each bill entry clearly by month and year along with its specific due amount
            // e.g., "- September 2026: 500 Taka", "- October 2026: 500 Taka"
            val breakdownLines = monthDueItems.joinToString("\n") { item ->
                "- ${item.monthLabel}: ${item.dueAmount.formatAmount()} Taka"
            }

            // 3. Calculate the total cumulative due amount
            val totalCumulativeDue = monthDueItems.sumOf { it.dueAmount }.takeIf { it > 0.0 } ?: bill.dueAmount
            val totalDueFormatted = "${totalCumulativeDue.formatAmount()} Taka"

            val customerName = if (!customer?.name.isNullOrBlank()) customer.name else bill.customerName
            val dueDate = bill.dueDate.ifBlank { "" }
            val companyName = ispName.ifBlank { "ISP Net" }
            val isBn = Locale.getDefault().language == "bn"

            // 4. Pass this detailed, multi-month breakdown message into the WhatsApp intent text parameter
            val message = if (monthDueItems.isEmpty()) {
                if (isBn) {
                    "প্রিয় $customerName,\nআপনার ${formatDisplayMonthYear(bill.billingMonth)} মাসের বিল পরিশোধ সম্পন্ন হয়েছে। মোট বিল: $currencySymbol${bill.amount.formatAmount()}।\nধন্যবাদ,\n$companyName"
                } else {
                    "Dear $customerName,\nYour Internet bill for ${formatDisplayMonthYear(bill.billingMonth)} is fully paid. Total: $currencySymbol${bill.amount.formatAmount()}.\nThank you,\n$companyName"
                }
            } else {
                if (isBn) {
                    buildString {
                        append("প্রিয় $customerName,\n")
                        append("আপনার ইন্টারনেট বিলের মাসভিত্তিক বকেয়ার বিবরণ:\n\n")
                        append(breakdownLines)
                        append("\n\n")
                        append("মোট বকেয়া: $totalDueFormatted")
                        if (dueDate.isNotBlank()) {
                            append("\nপরিশোধের শেষ সময়: $dueDate")
                        }
                        append("\n\nসংযোগ সচল রাখতে অনুগ্রহ করে দ্রুত বিল পরিশোধ করুন।\nধন্যবাদ,\n$companyName")
                    }
                } else {
                    buildString {
                        append("Dear $customerName,\n")
                        append("Your pending Internet bill breakdown by month:\n\n")
                        append(breakdownLines)
                        append("\n\n")
                        append("Total Due: $totalDueFormatted")
                        if (dueDate.isNotBlank()) {
                            append("\nDue Date: $dueDate")
                        }
                        append("\n\nPlease pay your due bill to keep your internet connection active.\nThank you,\n$companyName")
                    }
                }
            }

            withContext(Dispatchers.Main) {
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
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error generating WhatsApp reminder: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

