package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.BillEntity
import com.example.data.model.PaymentEntity
import com.example.ui.components.formatAmount
import com.example.ui.theme.EmeraldSuccess
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyBillEntryScreen(
    payments: List<PaymentEntity>,
    bills: List<BillEntity>,
    currencySymbol: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("TODAY") }
    var customDateStr by remember { mutableStateOf("") }

    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val yesterdayStr = remember {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }
    val thisMonthStr = remember { SimpleDateFormat("yyyy-MM", Locale.US).format(Date()) }
    val prevMonthStr = remember {
        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
    }

    val mappedPaidBills = remember(payments, bills) {
        payments.map { payment ->
            val bill = bills.find { it.id == payment.billId }
            BillEntity(
                id = payment.id,
                billNumber = bill?.billNumber ?: "",
                customerId = payment.customerId,
                customerName = payment.customerName,
                customerCode = bill?.customerCode ?: "",
                billingMonth = bill?.billingMonth ?: "",
                amount = payment.amount,
                paidAmount = payment.amount,
                dueAmount = 0.0,
                status = "PAID",
                generatedDate = payment.paymentDate,
                dueDate = bill?.dueDate ?: ""
            )
        }
    }

    val filteredBills = remember(mappedPaidBills, selectedFilter, customDateStr) {
        when (selectedFilter) {
            "TODAY" -> mappedPaidBills.filter { it.generatedDate == todayStr }
            "YESTERDAY" -> mappedPaidBills.filter { it.generatedDate == yesterdayStr }
            "THIS_MONTH" -> mappedPaidBills.filter { it.generatedDate.startsWith(thisMonthStr) }
            "PREV_MONTH" -> mappedPaidBills.filter { it.generatedDate.startsWith(prevMonthStr) }
            "CUSTOM_DATE" -> if (customDateStr.isNotBlank()) mappedPaidBills.filter { it.generatedDate == customDateStr } else mappedPaidBills
            else -> mappedPaidBills
        }
    }

    val groupedBills = remember(filteredBills) {
        filteredBills.groupBy { it.generatedDate.ifBlank { "Unknown Date" } }
            .entries
            .sortedByDescending { it.key }
    }

    val expandedStates = remember(groupedBills) {
        val map = mutableStateMapOf<String, Boolean>()
        groupedBills.take(2).forEach { map[it.key] = true }
        map
    }

    val totalFilteredCount = filteredBills.size
    val totalFilteredAmount = filteredBills.sumOf { it.amount }

    val isBangla = Locale.getDefault().language == "bn"

    val datePickerDialog = remember {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                customDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selectedCal.time)
                selectedFilter = "CUSTOM_DATE"
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "📅 " + stringResource(R.string.daily_bill_entry_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Filter Chips Horizontal Scroll Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val filters = listOf(
                    "ALL" to stringResource(R.string.filter_all),
                    "TODAY" to stringResource(R.string.filter_today),
                    "YESTERDAY" to stringResource(R.string.filter_yesterday),
                    "THIS_MONTH" to stringResource(R.string.filter_this_month),
                    "PREV_MONTH" to stringResource(R.string.filter_prev_month),
                    "CUSTOM_DATE" to if (selectedFilter == "CUSTOM_DATE" && customDateStr.isNotBlank()) customDateStr else stringResource(R.string.filter_custom_date)
                )

                filters.forEach { (key, label) ->
                    FilterChip(
                        selected = (selectedFilter == key),
                        onClick = {
                            if (key == "CUSTOM_DATE") {
                                datePickerDialog.show()
                            } else {
                                selectedFilter = key
                            }
                        },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selectedFilter == key) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = if (key == "CUSTOM_DATE") {
                            {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        } else null
                    )
                }
            }

            // Overview Summary KPI
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.bill_entries_count, totalFilteredCount),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = stringResource(R.string.total_amount_label, currencySymbol, totalFilteredAmount.formatAmount()),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (groupedBills.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_bill_entries_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(groupedBills, key = { it.key }) { (dateStr, billList) ->
                        val isExpanded = expandedStates[dateStr] ?: false
                        val (formattedDate, formattedDay) = remember(dateStr, isBangla) {
                            formatDateAndDay(dateStr, isBangla)
                        }
                        val dayTotalAmount = billList.sumOf { it.amount }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Date Header Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expandedStates[dateStr] = !isExpanded }
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "📅 $formattedDate" + if (formattedDay.isNotBlank()) " — $formattedDay" else "",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = stringResource(R.string.bill_entries_count, billList.size),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "•",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = stringResource(R.string.total_amount_label, currencySymbol, dayTotalAmount.formatAmount()),
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Expanded Customer Bill List
                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically(),
                                    exit = shrinkVertically()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                        ) {
                                            Spacer(modifier = Modifier.height(1.dp))
                                        }

                                        billList.forEach { bill ->
                                            DailyBillCustomerCard(
                                                bill = bill,
                                                currencySymbol = currencySymbol
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DailyBillCustomerCard(
    bill: BillEntity,
    currencySymbol: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bill.customerName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (bill.customerCode.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.customer_code_label, bill.customerCode),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (bill.billingMonth.isNotBlank()) {
                        Text(
                            text = "(${bill.billingMonth})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$currencySymbol${bill.amount.formatAmount()}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                val (statusColor, statusText) = when (bill.status.uppercase()) {
                    "PAID" -> EmeraldSuccess to "PAID"
                    "PARTIAL" -> MaterialTheme.colorScheme.tertiary to "PARTIAL"
                    else -> MaterialTheme.colorScheme.error to "UNPAID"
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

fun formatDateAndDay(dateStr: String, isBangla: Boolean): Pair<String, String> {
    try {
        val sdfIn = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = sdfIn.parse(dateStr) ?: return Pair(dateStr, "")

        val locale = if (isBangla) Locale("bn", "BD") else Locale.ENGLISH

        val sdfDate = SimpleDateFormat("dd MMMM yyyy", locale)
        val sdfDay = SimpleDateFormat("EEEE", locale)

        val formattedDate = sdfDate.format(date)
        val formattedDay = sdfDay.format(date)

        return Pair(formattedDate, formattedDay)
    } catch (e: Exception) {
        return Pair(dateStr, "")
    }
}
