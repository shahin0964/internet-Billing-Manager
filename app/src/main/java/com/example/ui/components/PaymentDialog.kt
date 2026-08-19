package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.BillEntity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import com.example.data.model.PreviousDueItem

@Composable
fun PaymentDialog(
    unpaidBills: List<BillEntity>,
    preSelectedBill: BillEntity? = null,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onRecordPayment: (billId: Long, customerId: Long, amount: Double, method: String, notes: String, advanceMonths: Int) -> Unit
) {
    var selectedBill by remember { mutableStateOf(preSelectedBill ?: unpaidBills.firstOrNull()) }
    var billDropdownExpanded by remember { mutableStateOf(false) }

    var amountStr by remember { mutableStateOf(selectedBill?.dueAmount?.formatAmount() ?: "0") }
    val defaultPaymentMethod = androidx.compose.ui.res.stringResource(com.example.R.string.cash)
    var paymentMethod by remember { mutableStateOf(defaultPaymentMethod) }
    var notes by remember { mutableStateOf("") }
    
    var isAdvancePayment by remember { mutableStateOf(false) }
    var advancePaymentsList by remember { mutableStateOf(listOf<PreviousDueItem>()) }

    val monthsList = remember {
        listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
    }
    
    val currentCal = remember { java.util.Calendar.getInstance() }
    val currentYearVal = remember { currentCal.get(java.util.Calendar.YEAR) }
    val yearsList = remember(currentYearVal) {
        ((currentYearVal - 4)..(currentYearVal + 2)).map { it.toString() }
    }
    
    var selectedAdvanceMonth by remember { mutableStateOf(monthsList[currentCal.get(java.util.Calendar.MONTH)]) }
    var selectedAdvanceYear by remember { mutableStateOf(currentYearVal.toString()) }
    var advanceAmountInput by remember { mutableStateOf("") }
    
    var monthDropdownExpanded by remember { mutableStateOf(false) }
    var yearDropdownExpanded by remember { mutableStateOf(false) }

    val methods = listOf(
        androidx.compose.ui.res.stringResource(com.example.R.string.cash),
        androidx.compose.ui.res.stringResource(com.example.R.string.bkash),
        androidx.compose.ui.res.stringResource(com.example.R.string.card),
        androidx.compose.ui.res.stringResource(com.example.R.string.bank_transfer),
        androidx.compose.ui.res.stringResource(com.example.R.string.online)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = androidx.compose.ui.res.stringResource(com.example.R.string.collect_payment),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Bill / Customer Selection Dropdown
                Column {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.select_bill_req),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = selectedBill?.let { bill ->
                            val isBd = bill.billNumber.startsWith("BREAKDOWN|")
                            if (isBd) {
                                "${bill.customerName} (${bill.billingMonth} + Prev) — Due: $currencySymbol${bill.dueAmount.formatAmount()}"
                            } else {
                                "${bill.customerName} (${bill.billingMonth}) — Due: $currencySymbol${bill.dueAmount.formatAmount()}"
                            }
                        } ?: androidx.compose.ui.res.stringResource(com.example.R.string.no_unpaid_bill_selected),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { Text("▼") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (unpaidBills.isNotEmpty()) billDropdownExpanded = true }
                    )
                    DropdownMenu(
                        expanded = billDropdownExpanded,
                        onDismissRequest = { billDropdownExpanded = false }
                    ) {
                        unpaidBills.forEach { bill ->
                            val isBd = bill.billNumber.startsWith("BREAKDOWN|")
                            val displayText = if (isBd) {
                                "${bill.customerName} — ${bill.billingMonth} (with previous dues) — Due: $currencySymbol${bill.dueAmount.formatAmount()}"
                            } else {
                                "${bill.customerName} — ${bill.billingMonth} — Due: $currencySymbol${bill.dueAmount.formatAmount()}"
                            }
                            DropdownMenuItem(
                                text = { Text(displayText) },
                                onClick = {
                                    selectedBill = bill
                                    amountStr = bill.dueAmount.formatAmount()
                                    billDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // 2. Breakdown Section (if consolidated bill & not advance payment)
                if (!isAdvancePayment) {
                    selectedBill?.let { bill ->
                        if (bill.billNumber.startsWith("BREAKDOWN|")) {
                            val parts = bill.billNumber.split("|")
                            if (parts.size >= 3) {
                                val prevListRaw = parts[1]
                                val prevItems = prevListRaw.split(",").mapNotNull {
                                    val pair = it.split(":")
                                    if (pair.size == 2) {
                                        val m = pair[0]
                                        val d = pair[1].toDoubleOrNull() ?: 0.0
                                        m to d
                                    } else null
                                }
                                
                                val currentPart = parts[2].split(":")
                                val curMonth = currentPart.getOrNull(0) ?: bill.billingMonth
                                val curDue = currentPart.getOrNull(1)?.toDoubleOrNull() ?: bill.dueAmount

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
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
                                                Text(text = m, style = MaterialTheme.typography.bodySmall)
                                                Text(text = "$currencySymbol${d.formatAmount()}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Current Bill",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = curMonth, style = MaterialTheme.typography.bodySmall)
                                            Text(text = "$currencySymbol${curDue.formatAmount()}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        HorizontalDivider(
                                            thickness = 1.dp,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = "Total Payable", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                            Text(text = "$currencySymbol${bill.dueAmount.formatAmount()}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Advance Payment Section (exact layout matching CustomerDialog)
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )

                Text(
                    text = "Advance Payment",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isAdvancePayment = !isAdvancePayment }
                ) {
                    Checkbox(
                        checked = isAdvancePayment,
                        onCheckedChange = { isAdvancePayment = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Advance Payment",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (isAdvancePayment) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Month Selector
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = selectedAdvanceMonth,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Month") },
                                    trailingIcon = { Text("▼") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable { monthDropdownExpanded = true }
                                )
                                DropdownMenu(
                                    expanded = monthDropdownExpanded,
                                    onDismissRequest = { monthDropdownExpanded = false }
                                ) {
                                    monthsList.forEach { m ->
                                        DropdownMenuItem(
                                            text = { Text(m) },
                                            onClick = {
                                                selectedAdvanceMonth = m
                                                monthDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Year Selector
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = selectedAdvanceYear,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Year") },
                                    trailingIcon = { Text("▼") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable { yearDropdownExpanded = true }
                                )
                                DropdownMenu(
                                    expanded = yearDropdownExpanded,
                                    onDismissRequest = { yearDropdownExpanded = false }
                                ) {
                                    yearsList.forEach { y ->
                                        DropdownMenuItem(
                                            text = { Text(y) },
                                            onClick = {
                                                selectedAdvanceYear = y
                                                yearDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = advanceAmountInput,
                                onValueChange = { advanceAmountInput = it },
                                label = { Text("Advance Amount ($currencySymbol)") },
                                singleLine = true,
                                modifier = Modifier.weight(1.5f)
                            )

                            Button(
                                onClick = {
                                    val amt = advanceAmountInput.replace(",", "").trim().toDoubleOrNull()
                                    if (amt != null && amt > 0) {
                                        val alreadyExists = advancePaymentsList.any { it.month == selectedAdvanceMonth && it.year == selectedAdvanceYear }
                                        if (!alreadyExists) {
                                            advancePaymentsList = advancePaymentsList + PreviousDueItem(
                                                month = selectedAdvanceMonth,
                                                year = selectedAdvanceYear,
                                                amount = amt
                                            )
                                            advanceAmountInput = ""
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add"
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add")
                            }
                        }

                        if (advancePaymentsList.isNotEmpty()) {
                            Text(
                                text = "Added Advance Months:",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            advancePaymentsList.forEach { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${item.month} ${item.year} — $currencySymbol${item.amount.formatAmount()}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        IconButton(
                                            onClick = {
                                                advancePaymentsList = advancePaymentsList.filter { it != item }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isAdvancePayment) {
                    val totalAmt = (selectedBill?.dueAmount ?: 0.0) + advancePaymentsList.sumOf { it.amount }
                    OutlinedTextField(
                        value = "$currencySymbol${totalAmt.formatAmount()}",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Total Payment Amount") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // 5. Payment Amount Input (ALWAYS VISIBLE if not advance payment)
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.payment_amount_req)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 6. Quick Amount Chips (Full / Half) if not advance payment
                    selectedBill?.let { bill ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = amountStr == bill.dueAmount.formatAmount(),
                                onClick = { amountStr = bill.dueAmount.formatAmount() },
                                label = { Text("Full ($currencySymbol${bill.dueAmount.formatAmount()})") }
                            )
                            val half = (bill.dueAmount / 2.0)
                            if (half > 0) {
                                FilterChip(
                                    selected = amountStr == half.formatAmount(),
                                    onClick = { amountStr = half.formatAmount() },
                                    label = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.msg_half, currencySymbol, half.formatAmount())) }
                                )
                            }
                        }
                    }
                }

                // 7. Payment Method Section
                Column {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.payment_method),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        methods.take(3).forEach { m ->
                            FilterChip(
                                selected = (paymentMethod == m),
                                onClick = { paymentMethod = m },
                                label = { Text(m) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        methods.drop(3).forEach { m ->
                            FilterChip(
                                selected = (paymentMethod == m),
                                onClick = { paymentMethod = m },
                                label = { Text(m) }
                            )
                        }
                    }
                }

                // 8. Receipt Notes / Reference Field
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.receipt_notes)) },
                    placeholder = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.eg_bkash_trx)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val parsedAmount = if (isAdvancePayment) {
                (selectedBill?.dueAmount ?: 0.0) + advancePaymentsList.sumOf { it.amount }
            } else {
                amountStr.replace(",", "").trim().toDoubleOrNull() ?: 0.0
            }
            val parsedMonths = if (isAdvancePayment) {
                advancePaymentsList.size
            } else {
                0
            }
            val targetCustId = selectedBill?.customerId ?: preSelectedBill?.customerId ?: 0L

            val isFormValid = if (isAdvancePayment) {
                targetCustId != 0L && parsedMonths > 0 && parsedAmount > 0
            } else {
                selectedBill != null && parsedAmount > 0
            }

            Button(
                onClick = {
                    if (isAdvancePayment) {
                        if (targetCustId != 0L && parsedMonths > 0 && parsedAmount > 0) {
                            val billIdToPass = selectedBill?.id ?: 0L
                            onRecordPayment(billIdToPass, targetCustId, parsedAmount, paymentMethod, notes, parsedMonths)
                        }
                    } else {
                        val bill = selectedBill ?: return@Button
                        if (parsedAmount > 0) {
                            onRecordPayment(bill.id, bill.customerId, parsedAmount, paymentMethod, notes, 0)
                        }
                    }
                },
                enabled = isFormValid
            ) {
                Text(androidx.compose.ui.res.stringResource(com.example.R.string.confirm_payment))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(androidx.compose.ui.res.stringResource(com.example.R.string.cancel))
            }
        }
    )
}
