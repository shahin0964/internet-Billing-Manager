package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.BillEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.IspPackageEntity
import java.util.Locale

@Composable
fun EditBillDialog(
    bill: BillEntity,
    customer: CustomerEntity?,
    availablePackages: List<IspPackageEntity>,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (CustomerEntity, BillEntity) -> Unit,
    onDeleteBill: (BillEntity) -> Unit
) {
    // Resolve user/customer from DB or construct fallback from bill
    val targetCustomer = remember(customer, bill) {
        customer ?: CustomerEntity(
            id = bill.customerId,
            customerCode = bill.customerCode,
            name = bill.customerName,
            phone = "",
            address = "",
            pppoeUsername = "",
            packageId = 0L,
            packageName = "",
            monthlyFee = bill.amount,
            joiningDate = bill.generatedDate
        )
    }

    var name by remember { mutableStateOf(targetCustomer.name) }
    var phone by remember { mutableStateOf(targetCustomer.phone) }
    var address by remember { mutableStateOf(targetCustomer.address) }
    var pppoeUsername by remember { mutableStateOf(targetCustomer.pppoeUsername) }
    var ipAddress by remember { mutableStateOf(targetCustomer.ipAddress) }
    var notes by remember { mutableStateOf(targetCustomer.notes) }
    var status by remember { mutableStateOf(if (targetCustomer.status.isBlank()) "ACTIVE" else targetCustomer.status) }

    var selectedPkg by remember {
        mutableStateOf(
            availablePackages.find { it.id == targetCustomer.packageId }
                ?: availablePackages.firstOrNull()
        )
    }

    var monthlyFeeStr by remember {
        mutableStateOf(targetCustomer.monthlyFee.formatAmount())
    }

    var billAmountStr by remember {
        mutableStateOf(bill.amount.formatAmount())
    }

    var billingMonth by remember {
        mutableStateOf(bill.billingMonth)
    }

    var dueDate by remember {
        mutableStateOf(bill.dueDate)
    }

    var pkgDropdownExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Delete Billing Record?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "This will remove this bill from Billing. The customer will remain in Customer and can be billed again later.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteBill(bill)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Column {
                        Text(
                            text = "Edit User & Billing Details",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "User ID: #${targetCustomer.id} • ${targetCustomer.customerCode}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel"
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // User Edit Header Indicator
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = "✏️ Editing User Data (ID: ${targetCustomer.id})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Customer Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("User/Customer Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Phone
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // PPPoE Username
                OutlinedTextField(
                    value = pppoeUsername,
                    onValueChange = { pppoeUsername = it },
                    label = { Text("PPPoE Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // IP Address
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("IP Address (Static)") },
                    singleLine = true,
                    placeholder = { Text("192.168.10.100") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Address
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address / Location") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Package selection
                Column {
                    Text(
                        text = "Internet Package",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = selectedPkg?.let { "${it.name} (${it.speedMbps} Mbps)" } ?: "Select Package",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            Text(
                                "▼",
                                modifier = Modifier
                                    .clickable { pkgDropdownExpanded = true }
                                    .padding(8.dp)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pkgDropdownExpanded = true }
                    )
                    DropdownMenu(
                        expanded = pkgDropdownExpanded,
                        onDismissRequest = { pkgDropdownExpanded = false }
                    ) {
                        availablePackages.forEach { pkg ->
                            DropdownMenuItem(
                                text = { Text("${pkg.name} — $currencySymbol${pkg.monthlyPrice.formatAmount()}/mo") },
                                onClick = {
                                    selectedPkg = pkg
                                    monthlyFeeStr = pkg.monthlyPrice.formatAmount()
                                    pkgDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Monthly Fee & Bill Amount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = monthlyFeeStr,
                        onValueChange = { monthlyFeeStr = it },
                        label = { Text("Monthly Fee ($currencySymbol)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = billAmountStr,
                        onValueChange = { billAmountStr = it },
                        label = { Text("Bill Amount ($currencySymbol)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Billing Month & Due Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = billingMonth,
                        onValueChange = { billingMonth = it },
                        label = { Text("Billing Month") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dueDate,
                        onValueChange = { dueDate = it },
                        label = { Text("Due Date") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Connection Status
                Column {
                    Text(
                        text = "Connection Status",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("ACTIVE", "SUSPENDED", "INACTIVE").forEach { st ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { status = st }
                            ) {
                                RadioButton(
                                    selected = (status == st),
                                    onClick = { status = st }
                                )
                                Text(
                                    text = st.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Comments") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            // Action Bar with ✏️ Edit, 💾 Save, 🗑️ Delete, ❌ Cancel
            val performSave: () -> Unit = {
                if (name.isNotBlank()) {
                    val fee = monthlyFeeStr.replace(",", "").trim().toDoubleOrNull()
                        ?: targetCustomer.monthlyFee
                    val pkgName = selectedPkg?.name ?: targetCustomer.packageName
                    val pkgId = selectedPkg?.id ?: targetCustomer.packageId

                    val updatedCustomer = targetCustomer.copy(
                        name = name.trim(),
                        phone = phone.trim(),
                        address = address.trim(),
                        pppoeUsername = pppoeUsername.trim(),
                        ipAddress = ipAddress.trim(),
                        packageId = pkgId,
                        packageName = pkgName,
                        monthlyFee = fee,
                        status = status,
                        notes = notes.trim()
                    )

                    val billAmt = billAmountStr.replace(",", "").trim().toDoubleOrNull()
                        ?: bill.amount
                    val newDue = (billAmt - bill.paidAmount).coerceAtLeast(0.0)
                    val newStatus = when {
                        newDue <= 0.0 -> "PAID"
                        bill.paidAmount > 0.0 -> "PARTIAL"
                        else -> "UNPAID"
                    }

                    val updatedBill = bill.copy(
                        customerName = name.trim(),
                        amount = billAmt,
                        dueAmount = newDue,
                        status = newStatus,
                        billingMonth = billingMonth.trim(),
                        dueDate = dueDate.trim()
                    )

                    onSave(updatedCustomer, updatedBill)
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ✏️ Edit Button
                    OutlinedButton(
                        onClick = performSave,
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "Edit",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 💾 Save Button
                    Button(
                        onClick = performSave,
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save",
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "Save",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 🗑️ Delete Button
                    OutlinedButton(
                        onClick = { showDeleteConfirmDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error
                        ),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "Delete",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // ❌ Cancel Button
                    OutlinedButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "Cancel",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        dismissButton = null
    )
}
