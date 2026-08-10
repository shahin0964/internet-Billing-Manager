package com.example.ui.screens

import com.example.ui.components.formatAmount
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

@Composable
fun BillingScreen(
    bills: List<BillEntity>,
    currencySymbol: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onGenerateBillsClick: () -> Unit,
    onRecordPaymentForBill: (BillEntity) -> Unit,
    onEditBill: (BillEntity) -> Unit = {}
) {
    val filteredBills = remember(bills, searchQuery) {
        bills.filter { bill ->
            val isUnpaid = bill.status == "UNPAID" || bill.status == "PARTIAL"
            val matchesQuery = searchQuery.isBlank() ||
                    bill.customerName.contains(searchQuery, ignoreCase = true) ||
                    bill.getDisplayBillNumber().contains(searchQuery, ignoreCase = true) ||
                    bill.billingMonth.contains(searchQuery, ignoreCase = true)

            isUnpaid && matchesQuery
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
                    BillItemCard(
                        bill = bill,
                        currencySymbol = currencySymbol,
                        onCollectPayment = { onRecordPaymentForBill(bill) },
                        onEditBill = { onEditBill(bill) }
                    )
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
fun BillItemCard(
    bill: BillEntity,
    currencySymbol: String,
    onCollectPayment: () -> Unit,
    onEditBill: () -> Unit = {}
) {
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
                    Text(
                        text = bill.customerName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$displayBillNo • $billingMonthLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(status = bill.status)
                    Spacer(modifier = Modifier.width(8.dp))
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
                        text = "$currencySymbol${bill.amount.formatAmount()}",
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
                        text = "$currencySymbol${bill.paidAmount.formatAmount()}",
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
                        text = "$currencySymbol${bill.dueAmount.formatAmount()}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (bill.dueAmount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            if (bill.dueAmount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onCollectPayment,
                    modifier = Modifier.fillMaxWidth(),
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
                    Text("Collect Payment ($currencySymbol${bill.dueAmount.formatAmount()})", fontSize = 12.sp)
                }
            }
        }
    }
}
