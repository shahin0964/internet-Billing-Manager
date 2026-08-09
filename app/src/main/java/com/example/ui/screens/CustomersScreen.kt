package com.example.ui.screens

import com.example.ui.components.formatAmount
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BillEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.IspPackageEntity
import com.example.data.model.PaymentEntity
import com.example.ui.components.CustomSearchBar
import com.example.ui.components.EmptyStateView
import com.example.ui.components.StatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    customers: List<CustomerEntity>,
    bills: List<BillEntity>,
    payments: List<PaymentEntity>,
    packages: List<IspPackageEntity>,
    currencySymbol: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedStatusFilter: String,
    onStatusFilterChange: (String) -> Unit,
    onAddCustomerClick: () -> Unit,
    onEditCustomerClick: (CustomerEntity) -> Unit,
    onDeleteCustomerClick: (CustomerEntity) -> Unit,
    onToggleStatusClick: (CustomerEntity) -> Unit,
    onCollectPaymentForCustomer: (CustomerEntity) -> Unit
) {
    var previewCustomerState by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerToDelete by remember { mutableStateOf<CustomerEntity?>(null) }

    // Always get the latest data from the database if preview is active
    val currentPreviewCustomer = previewCustomerState?.let { selected ->
        customers.find { it.id == selected.id }
    }

    val filteredCustomers = remember(customers, searchQuery, selectedStatusFilter) {
        customers.filter { c ->
            val matchesFilter = when (selectedStatusFilter) {
                "ACTIVE" -> c.status == "ACTIVE"
                "INACTIVE" -> c.status == "INACTIVE"
                "SUSPENDED" -> c.status == "SUSPENDED"
                else -> true
            }
            val matchesQuery = searchQuery.isBlank() ||
                    c.name.contains(searchQuery, ignoreCase = true) ||
                    c.phone.contains(searchQuery, ignoreCase = true) ||
                    c.pppoeUsername.contains(searchQuery, ignoreCase = true) ||
                    c.customerCode.contains(searchQuery, ignoreCase = true) ||
                    c.address.contains(searchQuery, ignoreCase = true)

            matchesFilter && matchesQuery
        }
    }

    // Delete Confirmation Dialog
    if (customerToDelete != null) {
        val cust = customerToDelete!!
        AlertDialog(
            onDismissRequest = { customerToDelete = null },
            title = {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.delete_customer),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this customer? This will also delete all associated bills and payments. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCustomerClick(cust)
                        if (previewCustomerState?.id == cust.id) {
                            previewCustomerState = null
                        }
                        customerToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.example.R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { customerToDelete = null }) {
                    Text(androidx.compose.ui.res.stringResource(com.example.R.string.cancel))
                }
            }
        )
    }

    if (currentPreviewCustomer != null) {
        // Dedicated Customer Preview Screen
        CustomerPreviewScreen(
            customer = currentPreviewCustomer,
            bills = bills.filter { it.customerId == currentPreviewCustomer.id },
            payments = payments.filter { it.customerId == currentPreviewCustomer.id },
            packages = packages,
            currencySymbol = currencySymbol,
            onBackClick = { previewCustomerState = null },
            onEditClick = { onEditCustomerClick(currentPreviewCustomer) },
            onDeleteClick = { customerToDelete = currentPreviewCustomer },
            onCollectPaymentClick = { onCollectPaymentForCustomer(currentPreviewCustomer) }
        )
    } else {
        // Customer List Screen
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onAddCustomerClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.add_customer)
                    )
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                CustomSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    placeholder = androidx.compose.ui.res.stringResource(com.example.R.string.search_customers_hint)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("ALL", "ACTIVE", "SUSPENDED", "INACTIVE").forEach { filter ->
                        FilterChip(
                            selected = (selectedStatusFilter == filter),
                            onClick = { onStatusFilterChange(filter) },
                            label = {
                                Text(
                                    text = filter.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (filteredCustomers.isEmpty()) {
                    EmptyStateView(
                        title = if (searchQuery.isNotEmpty()) androidx.compose.ui.res.stringResource(com.example.R.string.no_matching_customers) else androidx.compose.ui.res.stringResource(com.example.R.string.no_customers_added),
                        description = androidx.compose.ui.res.stringResource(com.example.R.string.manage_isp_desc),
                        icon = Icons.Default.People,
                        actionButton = {
                            Surface(
                                onClick = onAddCustomerClick,
                                shape = RoundedCornerShape(12.dp),
shadowElevation = 6.dp,
tonalElevation = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.example.R.string.add_new_customer),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                            }
                        }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredCustomers, key = { it.id }) { customer ->
                            CustomerItemCard(
                                customer = customer,
                                bills = bills.filter { it.customerId == customer.id },
                                currencySymbol = currencySymbol,
                                onClick = { previewCustomerState = customer },
                                onEditClick = { onEditCustomerClick(customer) },
                                onDeleteClick = { customerToDelete = customer }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerItemCard(
    customer: CustomerEntity,
    bills: List<BillEntity>,
    currencySymbol: String,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val totalDue = bills.sumOf { it.dueAmount }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
shadowElevation = 6.dp,
tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = customer.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = customer.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "#${customer.customerCode}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "PPPoE: ${customer.pppoeUsername} • ${customer.packageName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "Fee: $currencySymbol${customer.monthlyFee.formatAmount()}/mo" + (if (totalDue > 0) androidx.compose.ui.res.stringResource(com.example.R.string.msg_due, currencySymbol, totalDue.formatAmount()) else ""),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = if (totalDue > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                StatusBadge(status = customer.status)
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(8.dp))

            // Action Row on List Item: Edit and Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = customer.phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.edit_customer),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.delete_customer),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.view_preview),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerPreviewScreen(
    customer: CustomerEntity,
    bills: List<BillEntity>,
    payments: List<PaymentEntity>,
    packages: List<IspPackageEntity>,
    currencySymbol: String,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCollectPaymentClick: () -> Unit
) {
    val context = LocalContext.current
    val totalDue = bills.sumOf { it.dueAmount }
    val matchedPackage = packages.find { it.id == customer.packageId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.customer_preview),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.back_to_list)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onEditClick) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.edit_customer),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.delete_customer),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Profile Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = customer.name.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = customer.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Account Code: ${customer.customerCode}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusBadge(status = customer.status)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action buttons inside Preview Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer.phone}"))
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(12.dp),
shadowElevation = 6.dp,
tonalElevation = 3.dp,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.call),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(androidx.compose.ui.res.stringResource(com.example.R.string.call), style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        Surface(
                            onClick = onEditClick,
                            shape = RoundedCornerShape(12.dp),
shadowElevation = 6.dp,
tonalElevation = 3.dp,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.edit_customer),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    androidx.compose.ui.res.stringResource(com.example.R.string.edit),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Surface(
                            onClick = onDeleteClick,
                            shape = RoundedCornerShape(12.dp),
shadowElevation = 6.dp,
tonalElevation = 3.dp,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.delete_customer),
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Delete",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // Subscription & Connection Details
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Router,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.connection_package),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(12.dp))

                    PreviewInfoRow(androidx.compose.ui.res.stringResource(com.example.R.string.pppoe_username), customer.pppoeUsername, isBold = true)
                    PreviewInfoRow(
                        androidx.compose.ui.res.stringResource(com.example.R.string.static_ip_address),
                        if (customer.ipAddress.isNotBlank()) customer.ipAddress else androidx.compose.ui.res.stringResource(com.example.R.string.dynamic_none)
                    )
                    PreviewInfoRow(
                        androidx.compose.ui.res.stringResource(com.example.R.string.package_name),
                        matchedPackage?.let { "${it.name} (${it.speedMbps} Mbps)" } ?: customer.packageName,
                        isBold = true
                    )
                    PreviewInfoRow(androidx.compose.ui.res.stringResource(com.example.R.string.monthly_fee), "$currencySymbol${customer.monthlyFee.formatAmount()}/month", isBold = true)
                    PreviewInfoRow(androidx.compose.ui.res.stringResource(com.example.R.string.connection_date), customer.joiningDate)
                    PreviewInfoRow(androidx.compose.ui.res.stringResource(com.example.R.string.account_status), customer.status)
                }
            }

            // Subscriber Information
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.subscriber_details),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(12.dp))

                    PreviewInfoRow(androidx.compose.ui.res.stringResource(com.example.R.string.customer_name), customer.name)
                    PreviewInfoRow(androidx.compose.ui.res.stringResource(com.example.R.string.customer_code), customer.customerCode)
                    PreviewInfoRow(androidx.compose.ui.res.stringResource(com.example.R.string.phone_number), customer.phone)
                    PreviewInfoRow(
                        androidx.compose.ui.res.stringResource(com.example.R.string.address_location),
                        if (customer.address.isNotBlank()) customer.address else androidx.compose.ui.res.stringResource(com.example.R.string.not_provided)
                    )
                    if (customer.notes.isNotBlank()) {
                        PreviewInfoRow(androidx.compose.ui.res.stringResource(com.example.R.string.notes), customer.notes)
                    }
                }
            }

            // Billing & Financial Overview
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.financial_overview),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (totalDue > 0) {
                            Surface(
                                onClick = onCollectPaymentClick,
                                shape = RoundedCornerShape(8.dp),
shadowElevation = 6.dp,
tonalElevation = 3.dp,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(com.example.R.string.collect_payment),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(12.dp))

                    PreviewInfoRow(androidx.compose.ui.res.stringResource(com.example.R.string.monthly_fee), "$currencySymbol${customer.monthlyFee.formatAmount()}")
                    PreviewInfoRow(
                        androidx.compose.ui.res.stringResource(com.example.R.string.msg_outstanding_balance),
                        "$currencySymbol${totalDue.formatAmount()}",
                        isBold = true,
                        valueColor = if (totalDue > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )

                    if (bills.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.recent_bills),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        bills.take(5).forEach { bill ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${bill.billingMonth} (${bill.billNumber})",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Due: ${bill.dueDate}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "$currencySymbol${bill.amount.formatAmount()}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    StatusBadge(status = bill.status)
                                }
                            }
                        }
                    }

                    if (payments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.recent_payments),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        payments.take(5).forEach { payment ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${payment.paymentDate} • ${payment.paymentMethod}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = payment.paymentReceiptNo,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "+$currencySymbol${payment.amount.formatAmount()}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun PreviewInfoRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
            ),
            color = valueColor
        )
    }
}
