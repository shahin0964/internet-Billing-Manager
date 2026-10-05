package com.example.ui.screens

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.ExpenseCategoryEntity
import com.example.data.model.ExpenseEntity
import com.example.ui.components.formatAmount
import com.example.ui.components.formatAmountPrivacy
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.EmeraldSuccess
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseManagementScreen(
    expenses: List<ExpenseEntity>,
    customCategories: List<ExpenseCategoryEntity>,
    currencySymbol: String,
    onBackClick: () -> Unit,
    onSaveExpense: (ExpenseEntity) -> Unit,
    onUpdateExpense: (ExpenseEntity) -> Unit,
    onDeleteExpense: (ExpenseEntity) -> Unit,
    onAddCustomCategory: (String) -> Unit
) {
    val context = LocalContext.current
    val isPrivacyModeActive by com.example.util.PrivacyModeManager.privacyModeFlow.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Dashboard, 1: Expenses List, 2: Analytics

    // State for Dialogs
    var showAddEditDialog by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<ExpenseEntity?>(null) }
    var expenseToViewDetail by remember { mutableStateOf<ExpenseEntity?>(null) }
    var expenseToDeleteConfirm by remember { mutableStateOf<ExpenseEntity?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }

    // Date/Month Filtering
    val allMonths = remember(expenses) {
        val set = mutableSetOf<String>()
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        set.add(currentMonth)
        expenses.forEach { e ->
            if (e.date.length >= 7) {
                set.add(e.date.substring(0, 7))
            }
        }
        set.toList().sortedDescending()
    }

    var selectedMonth by remember(allMonths) {
        mutableStateOf(allMonths.firstOrNull() ?: SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()))
    }

    // Default categories
    val defaultCategories = listOf(
        stringResource(R.string.cat_bandwidth),
        stringResource(R.string.cat_staff_salary),
        stringResource(R.string.cat_electricity),
        stringResource(R.string.cat_equipment),
        stringResource(R.string.cat_server_hosting),
        stringResource(R.string.cat_office_rent),
        stringResource(R.string.cat_transport),
        stringResource(R.string.cat_maintenance),
        stringResource(R.string.cat_marketing),
        stringResource(R.string.cat_software_sub),
        stringResource(R.string.cat_other)
    )

    val allCategoriesList = remember(customCategories) {
        val list = defaultCategories.toMutableList()
        customCategories.forEach { c ->
            if (!list.contains(c.name)) list.add(c.name)
        }
        list
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.expense_management),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Track office, maintenance & operational costs",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_to_list)
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = {
                            expenseToEdit = null
                            showAddEditDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Add Expense",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            // Modern M3 Primary Tab Row
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.dashboard), fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.expense_details), fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.monthly_trend), fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                )
            }

            when (selectedTab) {
                0 -> ModernExpenseDashboardTab(
                    expenses = expenses,
                    allMonths = allMonths,
                    selectedMonth = selectedMonth,
                    onMonthSelected = { selectedMonth = it },
                    currencySymbol = currencySymbol,
                    onAddExpenseClick = {
                        expenseToEdit = null
                        showAddEditDialog = true
                    },
                    onAddCategoryClick = { showAddCategoryDialog = true },
                    onSelectExpense = { expenseToViewDetail = it }
                )
                1 -> ModernExpenseListTab(
                    expenses = expenses,
                    currencySymbol = currencySymbol,
                    allCategories = allCategoriesList,
                    allMonths = allMonths,
                    onSelectExpense = { expenseToViewDetail = it },
                    onEditExpense = {
                        expenseToEdit = it
                        showAddEditDialog = true
                    },
                    onDeleteExpense = { expenseToDeleteConfirm = it }
                )
                2 -> ModernExpenseAnalyticsTab(
                    expenses = expenses,
                    allMonths = allMonths,
                    currencySymbol = currencySymbol
                )
            }
        }
    }

    // Modern Dialogs
    if (showAddEditDialog) {
        ModernAddEditExpenseDialog(
            initialExpense = expenseToEdit,
            currencySymbol = currencySymbol,
            allCategories = allCategoriesList,
            onDismiss = { showAddEditDialog = false },
            onSave = { expense ->
                if (expense.id == 0L) {
                    onSaveExpense(expense)
                } else {
                    onUpdateExpense(expense)
                }
                showAddEditDialog = false
            },
            onAddCategoryClick = { showAddCategoryDialog = true }
        )
    }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            onDismiss = { showAddCategoryDialog = false },
            onSave = { name ->
                onAddCustomCategory(name)
                showAddCategoryDialog = false
            }
        )
    }

    if (expenseToViewDetail != null) {
        ModernExpenseDetailDialog(
            expense = expenseToViewDetail!!,
            currencySymbol = currencySymbol,
            onDismiss = { expenseToViewDetail = null },
            onEdit = {
                expenseToEdit = expenseToViewDetail
                expenseToViewDetail = null
                showAddEditDialog = true
            },
            onDelete = {
                expenseToDeleteConfirm = expenseToViewDetail
                expenseToViewDetail = null
            }
        )
    }

    if (expenseToDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { expenseToDeleteConfirm = null },
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = stringResource(R.string.delete_expense_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this expense (${expenseToDeleteConfirm?.title})? This will be removed from your monthly ledger."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        expenseToDeleteConfirm?.let { onDeleteExpense(it) }
                        expenseToDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.delete), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { expenseToDeleteConfirm = null },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// =========================================================================================
// 1. MODERN DASHBOARD TAB
// =========================================================================================
@Composable
private fun ModernExpenseDashboardTab(
    expenses: List<ExpenseEntity>,
    allMonths: List<String>,
    selectedMonth: String,
    onMonthSelected: (String) -> Unit,
    currencySymbol: String,
    onAddExpenseClick: () -> Unit,
    onAddCategoryClick: () -> Unit,
    onSelectExpense: (ExpenseEntity) -> Unit
) {
    val isPrivacyModeActive by com.example.util.PrivacyModeManager.privacyModeFlow.collectAsState()
    val monthExpenses = remember(expenses, selectedMonth) {
        expenses.filter { it.date.startsWith(selectedMonth) }
    }

    val totalAmount = remember(monthExpenses) { monthExpenses.sumOf { it.amount } }
    val expenseCount = monthExpenses.size
    val highestExpense = remember(monthExpenses) { monthExpenses.maxOfOrNull { it.amount } ?: 0.0 }
    val averageExpense = remember(monthExpenses) { if (expenseCount > 0) totalAmount / expenseCount else 0.0 }

    val categoryBreakdown = remember(monthExpenses) {
        monthExpenses.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Month Selector Pill Bar
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Active Billing Period",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatMonthPretty(selectedMonth),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    ModernMonthDropdownPicker(
                        allMonths = allMonths,
                        selectedMonth = selectedMonth,
                        onMonthSelected = onMonthSelected
                    )
                }
            }
        }

        // Summary Metric Cards (Soft-toned Material 3 elevated layout)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModernMetricCard(
                        title = stringResource(R.string.total_expenses),
                        value = totalAmount.formatAmountPrivacy(currencySymbol, isPrivacyModeActive),
                        subtitle = "This Month Outflow",
                        icon = Icons.Default.Receipt,
                        accentColor = CrimsonDanger,
                        modifier = Modifier.weight(1f)
                    )
                    ModernMetricCard(
                        title = "Expense Count",
                        value = "$expenseCount",
                        subtitle = "Total Vouchers Logged",
                        icon = Icons.Default.Category,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModernMetricCard(
                        title = stringResource(R.string.highest_expense),
                        value = highestExpense.formatAmountPrivacy(currencySymbol, isPrivacyModeActive),
                        subtitle = "Single Largest Cost",
                        icon = Icons.Default.TrendingUp,
                        accentColor = AmberWarning,
                        modifier = Modifier.weight(1f)
                    )
                    ModernMetricCard(
                        title = stringResource(R.string.average_expense),
                        value = averageExpense.formatAmountPrivacy(currencySymbol, isPrivacyModeActive),
                        subtitle = "Per Transaction Avg",
                        icon = Icons.Default.Payments,
                        accentColor = EmeraldSuccess,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Category Breakdown Card with modern progress bars
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 2.dp,
                tonalElevation = 1.dp,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.List,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(7.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.expense_breakdown),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        TextButton(
                            onClick = onAddCategoryClick,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.add_custom_category), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (categoryBreakdown.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No expenses recorded for ${formatMonthPretty(selectedMonth)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val maxCatVal = categoryBreakdown.maxOf { it.second }.coerceAtLeast(1.0)
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            categoryBreakdown.forEach { (cat, amount) ->
                                val pct = if (totalAmount > 0) (amount / totalAmount * 100) else 0.0
                                val catColor = getCategoryColor(cat)

                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(catColor)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = cat,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = amount.formatAmountPrivacy(currencySymbol, isPrivacyModeActive),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = catColor.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "${String.format(Locale.US, "%.1f", pct)}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = catColor,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth((amount / maxCatVal).toFloat().coerceIn(0.04f, 1f))
                                                .clip(CircleShape)
                                                .background(catColor)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recent Expenses Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 2.dp,
                tonalElevation = 1.dp,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(7.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Recent Expenses",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = onAddExpenseClick,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (monthExpenses.isEmpty()) {
                        Text(
                            text = "No recent transactions found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            monthExpenses.take(5).forEach { exp ->
                                ModernExpenseListItemRow(
                                    expense = exp,
                                    currencySymbol = currencySymbol,
                                    onClick = { onSelectExpense(exp) }
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

// =========================================================================================
// 2. MODERN EXPENSE DETAILS TAB
// =========================================================================================
@Composable
private fun ModernExpenseListTab(
    expenses: List<ExpenseEntity>,
    currencySymbol: String,
    allCategories: List<String>,
    allMonths: List<String>,
    onSelectExpense: (ExpenseEntity) -> Unit,
    onEditExpense: (ExpenseEntity) -> Unit,
    onDeleteExpense: (ExpenseEntity) -> Unit
) {
    val isPrivacyModeActive by com.example.util.PrivacyModeManager.privacyModeFlow.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("") } // empty = All
    var selectedMonthFilter by remember { mutableStateOf("") } // empty = All

    val filteredExpenses = remember(expenses, searchQuery, selectedCategoryFilter, selectedMonthFilter) {
        expenses.filter { exp ->
            val matchesQuery = searchQuery.isEmpty() ||
                    exp.title.contains(searchQuery, ignoreCase = true) ||
                    exp.note.contains(searchQuery, ignoreCase = true) ||
                    exp.category.contains(searchQuery, ignoreCase = true) ||
                    exp.paymentMethod.contains(searchQuery, ignoreCase = true)

            val matchesCategory = selectedCategoryFilter.isEmpty() || exp.category.equals(selectedCategoryFilter, ignoreCase = true)
            val matchesMonth = selectedMonthFilter.isEmpty() || exp.date.startsWith(selectedMonthFilter)

            matchesQuery && matchesCategory && matchesMonth
        }.sortedByDescending { it.date }
    }

    val totalFilteredAmount = remember(filteredExpenses) { filteredExpenses.sumOf { it.amount } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Modern Pill Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search expense title, category, payment mode...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        )

        // Filter Pills Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Category Dropdown Filter Chip
            var catMenuExpanded by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = selectedCategoryFilter.isNotEmpty(),
                    onClick = { catMenuExpanded = true },
                    label = {
                        Text(
                            text = if (selectedCategoryFilter.isEmpty()) "All Categories" else selectedCategoryFilter,
                            fontWeight = if (selectedCategoryFilter.isNotEmpty()) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                DropdownMenu(
                    expanded = catMenuExpanded,
                    onDismissRequest = { catMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All Categories") },
                        onClick = {
                            selectedCategoryFilter = ""
                            catMenuExpanded = false
                        }
                    )
                    allCategories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                selectedCategoryFilter = cat
                                catMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // Month Dropdown Filter Chip
            var monthMenuExpanded by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = selectedMonthFilter.isNotEmpty(),
                    onClick = { monthMenuExpanded = true },
                    label = {
                        Text(
                            text = if (selectedMonthFilter.isEmpty()) "All Months" else formatMonthPretty(selectedMonthFilter),
                            fontWeight = if (selectedMonthFilter.isNotEmpty()) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                DropdownMenu(
                    expanded = monthMenuExpanded,
                    onDismissRequest = { monthMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All Months") },
                        onClick = {
                            selectedMonthFilter = ""
                            monthMenuExpanded = false
                        }
                    )
                    allMonths.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(formatMonthPretty(m)) },
                            onClick = {
                                selectedMonthFilter = m
                                monthMenuExpanded = false
                            }
                        )
                    }
                }
            }

            if (selectedCategoryFilter.isNotEmpty() || selectedMonthFilter.isNotEmpty() || searchQuery.isNotEmpty()) {
                AssistChip(
                    onClick = {
                        searchQuery = ""
                        selectedCategoryFilter = ""
                        selectedMonthFilter = ""
                    },
                    label = { Text("Reset Filters") },
                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp)) }
                )
            }
        }

        // Summary banner for active filters
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredExpenses.size} Expenses Found",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Total: ${totalFilteredAmount.formatAmountPrivacy(currencySymbol, isPrivacyModeActive)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = CrimsonDanger
                )
            }
        }

        // Expense List
        if (filteredExpenses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.no_expenses_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredExpenses, key = { it.id }) { exp ->
                    ModernExpenseCardItem(
                        expense = exp,
                        currencySymbol = currencySymbol,
                        onClick = { onSelectExpense(exp) },
                        onEdit = { onEditExpense(exp) },
                        onDelete = { onDeleteExpense(exp) }
                    )
                }
                item { Spacer(modifier = Modifier.height(48.dp)) }
            }
        }
    }
}

// =========================================================================================
// 3. MODERN EXPENSE ANALYTICS & TREND TAB
// =========================================================================================
@Composable
private fun ModernExpenseAnalyticsTab(
    expenses: List<ExpenseEntity>,
    allMonths: List<String>,
    currencySymbol: String
) {
    val isPrivacyModeActive by com.example.util.PrivacyModeManager.privacyModeFlow.collectAsState()
    val currentMonthStr = remember { SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()) }
    val currentMonthExpenses = remember(expenses, currentMonthStr) {
        expenses.filter { it.date.startsWith(currentMonthStr) }.sumOf { it.amount }
    }

    val prevMonthStr = remember {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -1)
        sdf.format(cal.time)
    }

    val prevMonthExpenses = remember(expenses, prevMonthStr) {
        expenses.filter { it.date.startsWith(prevMonthStr) }.sumOf { it.amount }
    }

    val diff = currentMonthExpenses - prevMonthExpenses
    val pctChange = if (prevMonthExpenses > 0) (diff / prevMonthExpenses * 100) else 0.0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Month-over-Month Comparison Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 3.dp,
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MONTHLY RUN-RATE COMPARISON",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val isIncrease = diff > 0
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isIncrease) CrimsonDanger.copy(alpha = 0.15f) else EmeraldSuccess.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isIncrease) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                    contentDescription = null,
                                    tint = if (isIncrease) CrimsonDanger else EmeraldSuccess,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${if (isIncrease) "+" else ""}${String.format(Locale.US, "%.1f", pctChange)}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isIncrease) CrimsonDanger else EmeraldSuccess
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Current Month Box
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Current (${formatMonthPretty(currentMonthStr)})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentMonthExpenses.formatAmountPrivacy(currencySymbol, isPrivacyModeActive),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Previous Month Box
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Previous (${formatMonthPretty(prevMonthStr)})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = prevMonthExpenses.formatAmountPrivacy(currencySymbol, isPrivacyModeActive),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isPrivacyModeActive) {
                            "Financial trend details are hidden while Privacy Mode is active."
                        } else if (diff > 0) {
                            "Expenses increased by $currencySymbol${Math.abs(diff).formatAmount()} compared to last month."
                        } else if (diff < 0) {
                            "Expenses decreased by $currencySymbol${Math.abs(diff).formatAmount()} compared to last month. Great savings!"
                        } else {
                            "Expenses remained identical to last month."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Monthly Expense Trend List
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 3.dp,
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.monthly_trend),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Historical Run-rate",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (allMonths.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_expenses_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val monthTotals = allMonths.map { m ->
                            m to expenses.filter { it.date.startsWith(m) }.sumOf { it.amount }
                        }
                        val maxMonthVal = monthTotals.maxOf { it.second }.coerceAtLeast(1.0)

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            monthTotals.forEachIndexed { index, (m, total) ->
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (m == currentMonthStr) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.size(22.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "${index + 1}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (m == currentMonthStr) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = formatMonthPretty(m),
                                                fontWeight = if (m == currentMonthStr) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }

                                        Text(
                                            text = "$currencySymbol${total.formatAmount()}",
                                            fontWeight = FontWeight.Bold,
                                            color = CrimsonDanger
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth((total / maxMonthVal).toFloat().coerceIn(0.04f, 1f))
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.horizontalGradient(
                                                        listOf(MaterialTheme.colorScheme.primary, CrimsonDanger)
                                                    )
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

// =========================================================================================
// 4. MODERN ADD / EDIT EXPENSE DIALOG
// =========================================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernAddEditExpenseDialog(
    initialExpense: ExpenseEntity?,
    currencySymbol: String,
    allCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: (ExpenseEntity) -> Unit,
    onAddCategoryClick: () -> Unit
) {
    val context = LocalContext.current
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    val initialDateFormatted = remember(initialExpense) {
        val raw = initialExpense?.date?.trim() ?: ""
        if (raw.isNotBlank()) {
            if (Regex("""^\d{4}-\d{2}-\d{2}$""").matches(raw)) {
                raw
            } else {
                try {
                    val parser = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).apply { isLenient = false }
                    val parsed = parser.parse(raw)
                    if (parsed != null) SimpleDateFormat("yyyy-MM-dd", Locale.US).format(parsed) else raw
                } catch (_: Exception) {
                    raw
                }
            }
        } else {
            todayStr
        }
    }

    var title by remember { mutableStateOf(initialExpense?.title ?: "") }
    var amountStr by remember { mutableStateOf(initialExpense?.amount?.let { if (it > 0) it.toString() else "" } ?: "") }
    var category by remember { mutableStateOf(initialExpense?.category ?: if (allCategories.isNotEmpty()) allCategories.first() else "General") }
    var date by remember { mutableStateOf(initialDateFormatted) }
    var paymentMethod by remember { mutableStateOf(initialExpense?.paymentMethod ?: "Cash") }
    var note by remember { mutableStateOf(initialExpense?.note ?: "") }
    var receiptPath by remember { mutableStateOf(initialExpense?.receiptPath) }

    var titleError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf(false) }

    val showDatePicker = {
        val cal = Calendar.getInstance()
        val trimmed = date.trim()
        if (Regex("""^\d{4}-\d{2}-\d{2}$""").matches(trimmed)) {
            try {
                val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(trimmed)
                if (parsed != null) cal.time = parsed
            } catch (_: Exception) {}
        }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selectedCal.time)
                dateError = false
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val paymentMethods = listOf("Cash", "bKash", "Nagad", "Rocket", "Bank Transfer", "Card", "Cheque")

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { receiptPath = it.toString() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (initialExpense == null) Icons.Default.Add else Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (initialExpense == null) "Add Expense Voucher" else "Edit Expense",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // Form content
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Title
                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = {
                                title = it
                                titleError = false
                            },
                            label = { Text("Expense Title / Purpose *") },
                            placeholder = { Text("e.g. Fiber Optical Cable splicing, Office Rent") },
                            modifier = Modifier.fillMaxWidth(),
                            isError = titleError,
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Amount
                    item {
                        OutlinedTextField(
                            value = amountStr,
                            onValueChange = {
                                amountStr = it
                                amountError = false
                            },
                            label = { Text("Amount ($currencySymbol) *") },
                            placeholder = { Text("e.g. 2500") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            isError = amountError,
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Text(
                                    text = currencySymbol,
                                    fontWeight = FontWeight.Bold,
                                    color = CrimsonDanger,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        )
                    }

                    // Category Selector (Modern Chip Row)
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Category *", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                TextButton(
                                    onClick = onAddCategoryClick,
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("New Category", fontSize = 11.sp)
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                allCategories.forEach { cat ->
                                    val isSelected = category == cat
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { category = cat },
                                        label = { Text(cat, fontSize = 12.sp) },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Date with quick Today / Yesterday selectors
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = date,
                                onValueChange = {
                                    date = it
                                    dateError = false
                                },
                                label = { Text("Expense Date (yyyy-MM-dd) *") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                isError = dateError,
                                shape = RoundedCornerShape(12.dp),
                                trailingIcon = {
                                    IconButton(onClick = { showDatePicker() }) {
                                        Icon(Icons.Default.DateRange, contentDescription = "Date Picker", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(
                                    onClick = { date = todayStr; dateError = false },
                                    label = { Text("Today") }
                                )
                                AssistChip(
                                    onClick = {
                                        val cal = Calendar.getInstance()
                                        cal.add(Calendar.DAY_OF_YEAR, -1)
                                        date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                                        dateError = false
                                    },
                                    label = { Text("Yesterday") }
                                )
                            }
                        }
                    }

                    // Payment Method Selector (Chips)
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Payment Method", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                paymentMethods.forEach { pm ->
                                    val isSelected = paymentMethod == pm
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { paymentMethod = pm },
                                        label = { Text(pm, fontSize = 12.sp) },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Reference Note
                    item {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("Reference / Memo / Notes") },
                            placeholder = { Text("e.g. Paid to technician Rahim for Router replacement") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Receipt Attachment Modern Box
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Receipt / Bill Attachment", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                            if (receiptPath != null) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AsyncImage(
                                                model = receiptPath,
                                                contentDescription = "Receipt Attachment",
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text("Receipt Image Attached", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                                Text("Ready to save", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }

                                        TextButton(onClick = { receiptPath = null }) {
                                            Text("Remove", color = CrimsonDanger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { filePickerLauncher.launch("image/*") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Attach Receipt / Voucher Photo", fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }

                    Button(
                        onClick = {
                            var valid = true
                            if (title.isBlank()) {
                                titleError = true
                                valid = false
                            }
                            val parsedAmt = amountStr.toDoubleOrNull()
                            if (parsedAmt == null || parsedAmt <= 0) {
                                amountError = true
                                valid = false
                            }

                            val trimmedDate = if (date.isBlank()) todayStr else date.trim()
                            val isDateValid = if (Regex("""^\d{4}-\d{2}-\d{2}$""").matches(trimmedDate)) {
                                try {
                                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
                                    sdf.parse(trimmedDate) != null
                                } catch (_: Exception) {
                                    false
                                }
                            } else {
                                false
                            }

                            if (!isDateValid) {
                                dateError = true
                                valid = false
                            }

                            if (valid) {
                                val exp = ExpenseEntity(
                                    id = initialExpense?.id ?: 0L,
                                    title = title.trim(),
                                    amount = parsedAmt!!,
                                    category = category,
                                    date = trimmedDate,
                                    paymentMethod = paymentMethod,
                                    note = note.trim(),
                                    receiptPath = receiptPath,
                                    createdAt = initialExpense?.createdAt ?: System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis()
                                )
                                onSave(exp)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (initialExpense == null) "Save Expense" else "Update", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// =========================================================================================
// 5. HELPER COMPOSABLES & CARDS
// =========================================================================================
@Composable
private fun ModernMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 2.dp,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (accentColor == CrimsonDanger) CrimsonDanger else MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun ModernExpenseCardItem(
    expense: ExpenseEntity,
    currencySymbol: String,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val catColor = getCategoryColor(expense.category)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = catColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = getCategoryIcon(expense.category),
                        contentDescription = null,
                        tint = catColor,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = expense.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = catColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = expense.category,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = catColor
                            )
                        }
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${expense.date} (${expense.paymentMethod})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (expense.note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = expense.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = expense.amount.formatAmountPrivacy("-$currencySymbol", com.example.util.PrivacyModeManager.privacyModeFlow.value),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = CrimsonDanger
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = CrimsonDanger)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernExpenseListItemRow(
    expense: ExpenseEntity,
    currencySymbol: String,
    onClick: () -> Unit
) {
    val catColor = getCategoryColor(expense.category)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(catColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = expense.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${expense.category} • ${expense.date}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = expense.amount.formatAmountPrivacy("-$currencySymbol", com.example.util.PrivacyModeManager.privacyModeFlow.value),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = CrimsonDanger
        )
    }
}

@Composable
private fun ModernMonthDropdownPicker(
    allMonths: List<String>,
    selectedMonth: String,
    onMonthSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        FilledTonalButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(formatMonthPretty(selectedMonth), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            allMonths.forEach { m ->
                DropdownMenuItem(
                    text = { Text(formatMonthPretty(m)) },
                    onClick = {
                        onMonthSelected(m)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ModernExpenseDetailDialog(
    expense: ExpenseEntity,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val catColor = getCategoryColor(expense.category)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = catColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = getCategoryIcon(expense.category),
                        contentDescription = null,
                        tint = catColor,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = expense.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = expense.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = catColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Amount:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "-$currencySymbol${expense.amount.formatAmount()}",
                        fontWeight = FontWeight.ExtraBold,
                        color = CrimsonDanger,
                        fontSize = 17.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Date:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = expense.date, fontWeight = FontWeight.Medium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Payment Method:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = expense.paymentMethod, fontWeight = FontWeight.SemiBold)
                }

                if (expense.note.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Note / Reference:", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = expense.note,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }

                if (expense.receiptPath != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Receipt Photo:", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    AsyncImage(
                        model = expense.receiptPath,
                        contentDescription = "Receipt Attachment",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onEdit,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.delete))
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = { Text(stringResource(R.string.add_custom_category), fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = false
                },
                label = { Text(stringResource(R.string.category_name)) },
                placeholder = { Text("e.g. Generator Fuel, Cloud Backup") },
                modifier = Modifier.fillMaxWidth(),
                isError = error,
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        error = true
                    } else {
                        onSave(name.trim())
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun formatMonthPretty(monthStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US).apply { isLenient = false }
        val date = sdf.parse(monthStr)
        if (date != null) {
            SimpleDateFormat("MMMM yyyy", Locale.US).format(date)
        } else {
            monthStr
        }
    } catch (_: Exception) {
        monthStr
    }
}

private fun getCategoryColor(category: String): Color {
    val lower = category.lowercase(Locale.US)
    return when {
        lower.contains("bandwidth") || lower.contains("upstream") || lower.contains("internet") -> Color(0xFF1E88E5)
        lower.contains("salary") || lower.contains("staff") || lower.contains("payroll") -> Color(0xFF2E7D32)
        lower.contains("electricity") || lower.contains("power") || lower.contains("current") -> Color(0xFFF57C00)
        lower.contains("server") || lower.contains("hosting") || lower.contains("cloud") -> Color(0xFF00ACC1)
        lower.contains("rent") || lower.contains("office") -> Color(0xFF8E24AA)
        lower.contains("equipment") || lower.contains("onu") || lower.contains("router") || lower.contains("cable") -> Color(0xFFD84315)
        lower.contains("maintenance") || lower.contains("repair") || lower.contains("splicing") -> Color(0xFF6D4C41)
        lower.contains("transport") || lower.contains("fuel") || lower.contains("bike") -> Color(0xFF3949AB)
        lower.contains("marketing") || lower.contains("promo") || lower.contains("ad") -> Color(0xFFE91E63)
        lower.contains("software") || lower.contains("subscription") || lower.contains("license") -> Color(0xFF5E35B1)
        else -> Color(0xFF546E7A)
    }
}

private fun getCategoryIcon(category: String): ImageVector {
    val lower = category.lowercase(Locale.US)
    return when {
        lower.contains("bandwidth") || lower.contains("upstream") || lower.contains("internet") -> Icons.Default.Language
        lower.contains("salary") || lower.contains("staff") || lower.contains("payroll") -> Icons.Default.Payments
        lower.contains("electricity") || lower.contains("power") || lower.contains("current") -> Icons.Default.Warning
        lower.contains("server") || lower.contains("hosting") || lower.contains("cloud") -> Icons.Default.Dns
        lower.contains("rent") || lower.contains("office") -> Icons.Default.Place
        lower.contains("equipment") || lower.contains("onu") || lower.contains("router") || lower.contains("cable") -> Icons.Default.Build
        lower.contains("maintenance") || lower.contains("repair") || lower.contains("splicing") -> Icons.Default.Build
        lower.contains("transport") || lower.contains("fuel") || lower.contains("bike") -> Icons.Default.Place
        lower.contains("marketing") || lower.contains("promo") || lower.contains("ad") -> Icons.Default.Send
        lower.contains("software") || lower.contains("subscription") || lower.contains("license") -> Icons.Default.Settings
        else -> Icons.Default.Receipt
    }
}
