package com.example.ui.screens

import com.example.ui.components.formatAmount
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BillEntity
import com.example.data.model.BusinessSettingsEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.PaymentEntity
import com.example.ui.components.KpiCard
import com.example.ui.components.QuickActionButton
import com.example.ui.components.SectionHeader
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlueSecondary
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldSuccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    customers: List<CustomerEntity>,
    bills: List<BillEntity>,
    payments: List<PaymentEntity>,
    expenses: List<ExpenseEntity> = emptyList(),
    settings: BusinessSettingsEntity,
    onNavigateToCustomers: () -> Unit,
    onNavigateToBilling: () -> Unit,
    onNavigateToCollection: () -> Unit,
    onNavigateToDue: () -> Unit,
    onAddCustomerClick: () -> Unit,
    onCollectPaymentClick: () -> Unit,
    onGenerateBillsClick: () -> Unit
) {
    val activeCount = customers.count { it.status == "ACTIVE" }
    val totalCount = customers.size
    val currentMonthStr = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
    
    val currentMonthBills = androidx.compose.runtime.remember(bills, currentMonthStr) {
        bills.filter { it.billingMonth.equals(currentMonthStr, ignoreCase = true) }
    }
    val currentMonthBillIds = androidx.compose.runtime.remember(currentMonthBills) {
        currentMonthBills.map { it.id }.toSet()
    }
    val monthlyBillAmount = currentMonthBills.sumOf { it.amount }
    val monthlyCollectedAmount = androidx.compose.runtime.remember(payments, currentMonthBillIds) {
        payments.filter { it.billId in currentMonthBillIds }.sumOf { it.amount }
    }

    val currency = settings.currencySymbol

    val totalBillingAmount = bills.sumOf { it.amount }
    val totalCollectedAmount = bills.sumOf { it.paidAmount }
    val totalDueAmount = bills.sumOf { it.dueAmount }
    
    val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val todayCollectionAmount = payments.filter { it.paymentDate == todayDateStr }.sumOf { it.amount }

    val sdf = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
    val currentDateStr = sdf.format(Date())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Top Control Center Header
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
shadowElevation = 3.dp,
tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "INTERNET BILL MANAGEMENT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Column {
                        if (settings.ispName.isNotBlank() || settings.logoUri != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (settings.logoUri != null) {
                                    coil.compose.AsyncImage(
                                        model = settings.logoUri,
                                        contentDescription = "Company Logo",
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                if (settings.ispName.isNotBlank()) {
                                    Text(
                                        text = settings.ispName,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        Text(
                            text = currentDateStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                }
            }
        }

        // Quick Operations Row
        item {
            Column {
                SectionHeader(title = androidx.compose.ui.res.stringResource(com.example.R.string.quick_operations))
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        QuickActionButton(
                            label = androidx.compose.ui.res.stringResource(com.example.R.string.add_customer),
                            icon = Icons.Default.Add,
                            onClick = onAddCustomerClick,
                            accentColor = CyanPrimary
                        )
                    }
                    item {
                        QuickActionButton(
                            label = androidx.compose.ui.res.stringResource(com.example.R.string.collect_payment),
                            icon = Icons.Default.CreditCard,
                            onClick = onCollectPaymentClick,
                            accentColor = EmeraldSuccess
                        )
                    }
                    item {
                        QuickActionButton(
                            label = androidx.compose.ui.res.stringResource(com.example.R.string.generate_bills),
                            icon = Icons.Default.ReceiptLong,
                            onClick = onGenerateBillsClick,
                            accentColor = BlueSecondary
                        )
                    }
                    item {
                        QuickActionButton(
                            label = androidx.compose.ui.res.stringResource(com.example.R.string.unpaid_dues),
                            icon = Icons.Default.MoneyOff,
                            onClick = onNavigateToDue,
                            accentColor = CrimsonDanger
                        )
                    }
                }
            }
        }

        // KPI Summary Section
        item {
            Column {
                SectionHeader(title = androidx.compose.ui.res.stringResource(com.example.R.string.business_kpis), subtitle = androidx.compose.ui.res.stringResource(com.example.R.string.live_network_status))
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiCard(
                        title = androidx.compose.ui.res.stringResource(com.example.R.string.active_users),
                        value = "$activeCount",
                        icon = Icons.Default.CheckCircle,
                        iconColor = EmeraldSuccess,
                        modifier = Modifier.weight(1f),
                        subtitle = androidx.compose.ui.res.stringResource(com.example.R.string.msg_total, totalCount)
                    )
                    KpiCard(
                        title = androidx.compose.ui.res.stringResource(com.example.R.string.inactive_susp),
                        value = "$currency${monthlyBillAmount.formatAmount()}",
                        icon = Icons.Default.Payments,
                        iconColor = EmeraldSuccess,
                        modifier = Modifier.weight(1f),
                        subtitle = currentMonthStr
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiCard(
                        title = androidx.compose.ui.res.stringResource(com.example.R.string.monthly_collection),
                        value = "$currency${monthlyCollectedAmount.formatAmount()}",
                        icon = Icons.Default.CreditCard,
                        iconColor = EmeraldSuccess,
                        modifier = Modifier.weight(1f),
                        subtitle = currentMonthStr
                    )
                    KpiCard(
                        title = androidx.compose.ui.res.stringResource(com.example.R.string.today_collection),
                        value = "$currency${todayCollectionAmount.formatAmount()}",
                        icon = Icons.Default.Payments,
                        iconColor = EmeraldSuccess,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Billing Analytics Bar Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
shadowElevation = 3.dp,
tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(
                        title = androidx.compose.ui.res.stringResource(com.example.R.string.billing_analytics),
                        actionLabel = androidx.compose.ui.res.stringResource(com.example.R.string.view_bills),
                        onActionClick = onNavigateToBilling
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val maxVal = totalBillingAmount.coerceAtLeast(1.0)
                    val collectedRatio = (totalCollectedAmount / maxVal).coerceIn(0.0, 1.0).toFloat()
                    val dueRatio = (totalDueAmount / maxVal).coerceIn(0.0, 1.0).toFloat()

                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.msg_total_generated, currency, totalBillingAmount.formatAmount()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Custom Segmented Progress Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (collectedRatio > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(collectedRatio)
                                    .background(EmeraldSuccess)
                            )
                        }
                        if (dueRatio > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(dueRatio)
                                    .background(CrimsonDanger)
                            )
                        }
                        if (collectedRatio + dueRatio == 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldSuccess)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.msg_collected, currency, totalCollectedAmount.formatAmount()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(CrimsonDanger)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.msg_outstanding, currency, totalDueAmount.formatAmount()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Recent Payments Timeline Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
shadowElevation = 3.dp,
tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(
                        title = androidx.compose.ui.res.stringResource(com.example.R.string.recent_collections),
                        actionLabel = androidx.compose.ui.res.stringResource(com.example.R.string.view_all),
                        onActionClick = onNavigateToCollection
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (payments.isEmpty()) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.no_collections_yet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            payments.take(3).forEach { payment ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = payment.customerName,
                                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${payment.paymentDate} • ${payment.paymentMethod}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "+$currency${payment.amount.formatAmount()}",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldSuccess
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
