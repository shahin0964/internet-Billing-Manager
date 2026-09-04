package com.example.ui.screens

import com.example.ui.components.formatAmount
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BillEntity
import com.example.data.model.CustomerEntity
import com.example.ui.components.CustomSearchBar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.KpiCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.EmeraldSuccess

@Composable
fun DueManagementScreen(
    bills: List<BillEntity>,
    customers: List<CustomerEntity>,
    currencySymbol: String,
    ispName: String,
    onRecordPaymentForBill: (BillEntity) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val sortDueDesc = androidx.compose.ui.res.stringResource(com.example.R.string.sort_due_desc)
    val sortDueAsc = androidx.compose.ui.res.stringResource(com.example.R.string.sort_due_asc)
    val sortName = androidx.compose.ui.res.stringResource(com.example.R.string.sort_name)
    var sortOption by remember { mutableStateOf(sortDueDesc) }

    val unpaidBills = remember(bills) {
        bills.filter { it.dueAmount > 0 }
    }

    val totalDueAmount = unpaidBills.sumOf { it.dueAmount }

    val sortedBills = remember(unpaidBills, searchQuery, sortOption) {
        unpaidBills
            .filter {
                searchQuery.isBlank() ||
                        it.customerName.contains(searchQuery, ignoreCase = true) ||
                        it.billNumber.contains(searchQuery, ignoreCase = true) ||
                        it.billingMonth.contains(searchQuery, ignoreCase = true)
            }
            .sortedWith { b1, b2 ->
                when (sortOption) {
                    sortDueAsc -> b1.dueAmount.compareTo(b2.dueAmount)
                    sortName -> com.example.util.CustomerSortUtils.compareCustomerNames(b1.customerName, b2.customerName)
                    else -> b2.dueAmount.compareTo(b1.dueAmount) // DUE_DESC
                }
            }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // KPI Banner
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiCard(
                    title = androidx.compose.ui.res.stringResource(com.example.R.string.total_outstanding_dues),
                    value = "$currencySymbol${totalDueAmount.formatAmount()}",
                    icon = Icons.Default.MoneyOff,
                    iconColor = CrimsonDanger,
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = androidx.compose.ui.res.stringResource(com.example.R.string.unpaid_subscribers),
                    value = "${unpaidBills.size}",
                    icon = Icons.Default.Message,
                    iconColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Search Bar
        item {
            CustomSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = androidx.compose.ui.res.stringResource(com.example.R.string.search_dues_hint)
            )
        }

        // Sort Options
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.sort),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilterChip(
                    selected = (sortOption == sortDueDesc),
                    onClick = { sortOption = sortDueDesc },
                    label = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.highest_due_first)) }
                )
                FilterChip(
                    selected = (sortOption == sortName),
                    onClick = { sortOption = sortName },
                    label = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.subscriber_name)) }
                )
            }
        }

        item {
            SectionHeader(title = androidx.compose.ui.res.stringResource(com.example.R.string.unpaid_accounts_follow_up))
        }

        if (sortedBills.isEmpty()) {
            item {
                EmptyStateView(
                    title = if (searchQuery.isNotEmpty()) androidx.compose.ui.res.stringResource(com.example.R.string.no_matching_dues) else androidx.compose.ui.res.stringResource(com.example.R.string.zero_dues),
                    description = androidx.compose.ui.res.stringResource(com.example.R.string.all_paid_desc),
                    icon = Icons.Default.MoneyOff
                )
            }
        } else {
            items(sortedBills, key = { it.id }) { bill ->
                val customer = customers.find { it.id == bill.customerId }
                DueBillCard(
                    bill = bill,
                    phone = customer?.phone ?: "",
                    currencySymbol = currencySymbol,
                    ispName = ispName,
                    onRecordPayment = { onRecordPaymentForBill(bill) },
                    onSendReminder = { phone, msg ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$phone?body=${Uri.encode(msg)}"))
                        context.startActivity(intent)
                    }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun DueBillCard(
    bill: BillEntity,
    phone: String,
    currencySymbol: String,
    ispName: String,
    onRecordPayment: () -> Unit,
    onSendReminder: (phone: String, message: String) -> Unit
) {
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
                Column {
                    Text(
                        text = bill.customerName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Month: ${bill.billingMonth} • Due Date: ${bill.dueDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(status = bill.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Bill: $currencySymbol${bill.amount.formatAmount()}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Due Amount: $currencySymbol${bill.dueAmount.formatAmount()}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = CrimsonDanger
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (phone.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            val ispPrefix = if (ispName.isNotBlank()) "$ispName " else ""
                            val msg = "Dear ${bill.customerName}, your ${ispPrefix}internet bill for ${bill.billingMonth} of $currencySymbol${bill.dueAmount.formatAmount()} is due. Please clear payment to avoid disconnection. Thank you."
                            onSendReminder(phone, msg)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Message,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(androidx.compose.ui.res.stringResource(com.example.R.string.sms_notice), fontSize = 11.sp)
                    }
                }

                Button(
                    onClick = onRecordPayment,
                    modifier = Modifier.weight(1.2f),
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
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(androidx.compose.ui.res.stringResource(com.example.R.string.collect_payment), fontSize = 11.sp)
                }
            }
        }
    }
}
