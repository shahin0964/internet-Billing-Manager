package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.CustomerEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BillGenerateDialog(
    activeCustomers: List<CustomerEntity>,
    onDismiss: () -> Unit,
    onGenerate: (billingMonth: String, dueDate: String, selectedCustomerIds: Set<Long>?) -> Unit
) {
    val sdfMonth = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val sdfDate = remember { SimpleDateFormat("yyyy-MM-10", Locale.getDefault()) }

    // Filter out free package customers
    val eligibleCustomers = remember(activeCustomers) {
        activeCustomers.filter { customer ->
            val isFree = customer.packageName.contains("free", ignoreCase = true) ||
                    customer.packageName.contains("ফ্রি", ignoreCase = true)
            !isFree
        }
    }

    var billingMonth by remember { mutableStateOf(sdfMonth.format(Date())) }
    var dueDate by remember { mutableStateOf(sdfDate.format(Date())) }

    // 0: Generate All, 1: Select Customers
    var selectedTab by remember { mutableStateOf(0) }

    // Set of selected customer IDs when in Select Customers mode
    var selectedCustomerIds by remember(eligibleCustomers) {
        mutableStateOf(eligibleCustomers.map { it.id }.toSet())
    }

    var customerSearchQuery by remember { mutableStateOf("") }

    val filteredCustomers = remember(eligibleCustomers, customerSearchQuery) {
        if (customerSearchQuery.isBlank()) {
            eligibleCustomers
        } else {
            eligibleCustomers.filter { customer ->
                customer.name.contains(customerSearchQuery, ignoreCase = true) ||
                        customer.customerCode.contains(customerSearchQuery, ignoreCase = true) ||
                        customer.phone.contains(customerSearchQuery, ignoreCase = true) ||
                        customer.pppoeUsername.contains(customerSearchQuery, ignoreCase = true) ||
                        customer.packageName.contains(customerSearchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.generate_monthly_bills),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Month & Due Date Inputs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = billingMonth,
                        onValueChange = { billingMonth = it },
                        label = { Text(stringResource(R.string.billing_month), fontSize = 11.sp) },
                        placeholder = { Text(stringResource(R.string.eg_august_2026), fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = dueDate,
                        onValueChange = { dueDate = it },
                        label = { Text(stringResource(R.string.payment_due_date), fontSize = 11.sp) },
                        placeholder = { Text("2026-08-10", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Choice Tabs: Generate All vs Select Customers
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.generate_all),
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonSearch,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.select_customers),
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    )
                }

                if (selectedTab == 0) {
                    // Mode 1: Generate All description
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = stringResource(R.string.msg_generate_desc, eligibleCustomers.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Mode 2: Select Customers Interface
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Selection Actions Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        selectedCustomerIds = eligibleCustomers.map { it.id }.toSet()
                                    },
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(stringResource(R.string.select_all), fontSize = 11.sp)
                                }

                                TextButton(
                                    onClick = {
                                        selectedCustomerIds = emptySet()
                                    },
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(stringResource(R.string.clear_selection), fontSize = 11.sp)
                                }
                            }

                            Text(
                                text = stringResource(R.string.selected_count, selectedCustomerIds.size),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Search box inside customer selector
                        if (eligibleCustomers.size > 3) {
                            OutlinedTextField(
                                value = customerSearchQuery,
                                onValueChange = { customerSearchQuery = it },
                                placeholder = { Text(stringResource(R.string.search_customers_compact), fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            )
                        }

                        // Customer Selection List
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        ) {
                            if (filteredCustomers.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.no_matching_customers),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(filteredCustomers, key = { it.id }) { customer ->
                                        val isSelected = selectedCustomerIds.contains(customer.id)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedCustomerIds = if (isSelected) {
                                                        selectedCustomerIds - customer.id
                                                    } else {
                                                        selectedCustomerIds + customer.id
                                                    }
                                                }
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(
                                                        alpha = 0.15f
                                                    ) else MaterialTheme.colorScheme.surface
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    selectedCustomerIds = if (checked) {
                                                        selectedCustomerIds + customer.id
                                                    } else {
                                                        selectedCustomerIds - customer.id
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            )

                                            Spacer(modifier = Modifier.width(6.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = customer.name,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = FontWeight.SemiBold
                                                        ),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )

                                                    Text(
                                                        text = "৳${customer.monthlyFee.formatAmount()}",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }

                                                Text(
                                                    text = "${customer.customerCode} • ${customer.pppoeUsername.ifBlank { customer.phone }} • ${customer.packageName}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
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
        },
        confirmButton = {
            if (selectedTab == 0) {
                Button(
                    onClick = {
                        onGenerate(billingMonth.trim(), dueDate.trim(), null)
                    },
                    enabled = billingMonth.isNotBlank() && eligibleCustomers.isNotEmpty()
                ) {
                    Text(stringResource(R.string.msg_generate_customers, eligibleCustomers.size))
                }
            } else {
                Button(
                    onClick = {
                        onGenerate(billingMonth.trim(), dueDate.trim(), selectedCustomerIds)
                    },
                    enabled = billingMonth.isNotBlank() && selectedCustomerIds.isNotEmpty()
                ) {
                    Text(stringResource(R.string.generate_selected, selectedCustomerIds.size))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
