package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.ExpenseCategoryEntity
import com.example.data.model.ExpenseEntity
import com.example.ui.components.KpiCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.formatAmount
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
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Dashboard, 1: Expense List, 2: Analytics

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

    var selectedMonth by remember(allMonths) { mutableStateOf(allMonths.firstOrNull() ?: SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())) }

    // Pre-calculated default categories
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
                            text = stringResource(R.string.expense_management_subtitle),
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
                    IconButton(onClick = {
                        expenseToEdit = null
                        showAddEditDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.add_expense),
                            tint = MaterialTheme.colorScheme.primary
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
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.dashboard)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.expense_details)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(stringResource(R.string.monthly_trend)) }
                )
            }

            when (selectedTab) {
                0 -> ExpenseDashboardTab(
                    expenses = expenses,
                    allMonths = allMonths,
                    selectedMonth = selectedMonth,
                    onMonthSelected = { selectedMonth = it },
                    currencySymbol = currencySymbol,
                    allCategories = allCategoriesList,
                    onAddExpenseClick = {
                        expenseToEdit = null
                        showAddEditDialog = true
                    },
                    onAddCategoryClick = { showAddCategoryDialog = true },
                    onSelectExpense = { expenseToViewDetail = it }
                )
                1 -> ExpenseListTab(
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
                2 -> ExpenseAnalyticsTab(
                    expenses = expenses,
                    allMonths = allMonths,
                    currencySymbol = currencySymbol
                )
            }
        }
    }

    // Dialogs
    if (showAddEditDialog) {
        AddEditExpenseDialog(
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
        ExpenseDetailDialog(
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
            title = {
                Text(
                    text = stringResource(R.string.delete_expense_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = stringResource(R.string.delete_expense_confirm))
            },
            confirmButton = {
                Button(
                    onClick = {
                        expenseToDeleteConfirm?.let { onDeleteExpense(it) }
                        expenseToDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { expenseToDeleteConfirm = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ExpenseDashboardTab(
    expenses: List<ExpenseEntity>,
    allMonths: List<String>,
    selectedMonth: String,
    onMonthSelected: (String) -> Unit,
    currencySymbol: String,
    allCategories: List<String>,
    onAddExpenseClick: () -> Unit,
    onAddCategoryClick: () -> Unit,
    onSelectExpense: (ExpenseEntity) -> Unit
) {
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
        // Month Selector Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.select_month) + ":",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                MonthDropdownPicker(
                    allMonths = allMonths,
                    selectedMonth = selectedMonth,
                    onMonthSelected = onMonthSelected
                )
            }
        }

        // Current Month Real-time KPIs
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiCard(
                        title = stringResource(R.string.total_expenses),
                        value = "$currencySymbol${totalAmount.formatAmount()}",
                        icon = Icons.Default.Receipt,
                        iconColor = CrimsonDanger,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = stringResource(R.string.number_of_expenses),
                        value = "$expenseCount",
                        icon = Icons.Default.Category,
                        iconColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiCard(
                        title = stringResource(R.string.highest_expense),
                        value = "$currencySymbol${highestExpense.formatAmount()}",
                        icon = Icons.Default.TrendingUp,
                        iconColor = AmberWarning,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = stringResource(R.string.average_expense),
                        value = "$currencySymbol${averageExpense.formatAmount()}",
                        icon = Icons.Default.Payments,
                        iconColor = EmeraldSuccess,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Category Breakdown Card
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(title = stringResource(R.string.expense_breakdown))
                        TextButton(onClick = onAddCategoryClick) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.add_custom_category), fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (categoryBreakdown.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_expenses_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val maxCatVal = categoryBreakdown.maxOf { it.second }.coerceAtLeast(1.0)
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            categoryBreakdown.forEach { (cat, amount) ->
                                val pct = if (totalAmount > 0) (amount / totalAmount * 100) else 0.0
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = cat,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "$currencySymbol${amount.formatAmount()} (${String.format(Locale.US, "%.1f", pct)}%)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = CrimsonDanger
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
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
                                                .fillMaxWidth((amount / maxCatVal).toFloat().coerceIn(0f, 1f))
                                                .background(CrimsonDanger)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recent Month Expenses
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(title = stringResource(R.string.recent_bills))
                        Button(
                            onClick = onAddExpenseClick,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.add_expense), fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (monthExpenses.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_expenses_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            monthExpenses.take(5).forEach { exp ->
                                ExpenseListItemRow(
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
    }
}

@Composable
private fun ExpenseListTab(
    expenses: List<ExpenseEntity>,
    currencySymbol: String,
    allCategories: List<String>,
    allMonths: List<String>,
    onSelectExpense: (ExpenseEntity) -> Unit,
    onEditExpense: (ExpenseEntity) -> Unit,
    onDeleteExpense: (ExpenseEntity) -> Unit
) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        // Filters Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Category Filter Dropdown
            var catMenuExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { catMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (selectedCategoryFilter.isEmpty()) stringResource(R.string.all_categories) else selectedCategoryFilter,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp
                    )
                }
                DropdownMenu(
                    expanded = catMenuExpanded,
                    onDismissRequest = { catMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.all_categories)) },
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

            // Month Filter Dropdown
            var monthMenuExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { monthMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (selectedMonthFilter.isEmpty()) stringResource(R.string.all_months) else selectedMonthFilter,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp
                    )
                }
                DropdownMenu(
                    expanded = monthMenuExpanded,
                    onDismissRequest = { monthMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.all_months)) },
                        onClick = {
                            selectedMonthFilter = ""
                            monthMenuExpanded = false
                        }
                    )
                    allMonths.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m) },
                            onClick = {
                                selectedMonthFilter = m
                                monthMenuExpanded = false
                            }
                        )
                    }
                }
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
                Text(
                    text = stringResource(R.string.no_expenses_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredExpenses, key = { it.id }) { exp ->
                    ExpenseCardItem(
                        expense = exp,
                        currencySymbol = currencySymbol,
                        onClick = { onSelectExpense(exp) },
                        onEdit = { onEditExpense(exp) },
                        onDelete = { onDeleteExpense(exp) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpenseCardItem(
    expense: ExpenseEntity,
    currencySymbol: String,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = expense.category,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${expense.date} • ${expense.paymentMethod}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "-$currencySymbol${expense.amount.formatAmount()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CrimsonDanger
                )
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = CrimsonDanger)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseListItemRow(
    expense: ExpenseEntity,
    currencySymbol: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = expense.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${expense.category} • ${expense.date}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "-$currencySymbol${expense.amount.formatAmount()}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = CrimsonDanger
        )
    }
}

@Composable
private fun ExpenseAnalyticsTab(
    expenses: List<ExpenseEntity>,
    allMonths: List<String>,
    currencySymbol: String
) {
    val currentMonthStr = remember { SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()) }
    val currentMonthExpenses = remember(expenses, currentMonthStr) {
        expenses.filter { it.date.startsWith(currentMonthStr) }.sumOf { it.amount }
    }

    // Determine previous month string e.g., if current is 2026-08, prev is 2026-07
    val prevMonthStr = remember {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.MONTH, -1)
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
        // Expense Comparison Card
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
                    SectionHeader(title = stringResource(R.string.expense_comparison))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(stringResource(R.string.current_month) + " ($currentMonthStr)", style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = "$currencySymbol${currentMonthExpenses.formatAmount()}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.previous_month) + " ($prevMonthStr)", style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = "$currencySymbol${prevMonthExpenses.formatAmount()}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (prevMonthExpenses == 0.0 && currentMonthExpenses == 0.0) {
                        Text(
                            text = stringResource(R.string.no_prev_month_data),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (diff >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = if (diff > 0) CrimsonDanger else EmeraldSuccess
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val isIncrease = diff >= 0
                            val pctText = String.format(Locale.US, "%.1f", Math.abs(pctChange))
                            Text(
                                text = if (isIncrease) "+$pctText% increase" else "-$pctText% decrease",
                                fontWeight = FontWeight.Bold,
                                color = if (diff > 0) CrimsonDanger else EmeraldSuccess
                            )
                        }
                    }
                }
            }
        }

        // Monthly Expense Trend List
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
                    SectionHeader(title = stringResource(R.string.monthly_trend))
                    Spacer(modifier = Modifier.height(12.dp))

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

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            monthTotals.forEach { (m, total) ->
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(m, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "$currencySymbol${total.formatAmount()}",
                                            fontWeight = FontWeight.Bold,
                                            color = CrimsonDanger
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
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
                                                .fillMaxWidth((total / maxMonthVal).toFloat().coerceIn(0f, 1f))
                                                .background(CrimsonDanger)
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

@Composable
private fun MonthDropdownPicker(
    allMonths: List<String>,
    selectedMonth: String,
    onMonthSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(selectedMonth, fontWeight = FontWeight.Bold)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            allMonths.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m) },
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
private fun AddEditExpenseDialog(
    initialExpense: ExpenseEntity?,
    currencySymbol: String,
    allCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: (ExpenseEntity) -> Unit,
    onAddCategoryClick: () -> Unit
) {
    val context = LocalContext.current
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    // Normalize initial date if editing legacy format (e.g., "04 September 2026")
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
                    try {
                        val parser = SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH).apply { isLenient = false }
                        val parsed = parser.parse(raw)
                        if (parsed != null) SimpleDateFormat("yyyy-MM-dd", Locale.US).format(parsed) else raw
                    } catch (_: Exception) {
                        raw
                    }
                }
            }
        } else {
            todayStr
        }
    }

    var title by remember { mutableStateOf(initialExpense?.title ?: "") }
    var amountStr by remember { mutableStateOf(initialExpense?.amount?.let { if (it > 0) it.toString() else "" } ?: "") }
    var category by remember { mutableStateOf(initialExpense?.category ?: if (allCategories.isNotEmpty()) allCategories.first() else "Other") }
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
                if (parsed != null) {
                    cal.time = parsed
                }
            } catch (_: Exception) {}
        }
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selectedCal.time)
                dateError = false
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val paymentMethods = listOf("Cash", "Bank", "Mobile Banking", "Card", "Other")

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { receiptPath = it.toString() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialExpense == null) stringResource(R.string.add_expense) else stringResource(R.string.edit_expense),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            titleError = false
                        },
                        label = { Text(stringResource(R.string.expense_title)) },
                        modifier = Modifier.fillMaxWidth(),
                        isError = titleError,
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = {
                            amountStr = it
                            amountError = false
                        },
                        label = { Text(stringResource(R.string.amount_req) + " ($currencySymbol)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        isError = amountError,
                        singleLine = true
                    )
                }

                // Category Selector
                item {
                    var catExpanded by remember { mutableStateOf(false) }
                    Column {
                        Text(
                            text = stringResource(R.string.category_req),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { catExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(category, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                DropdownMenu(
                                    expanded = catExpanded,
                                    onDismissRequest = { catExpanded = false }
                                ) {
                                    allCategories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = { Text(cat) },
                                            onClick = {
                                                category = cat
                                                catExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(onClick = onAddCategoryClick) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_custom_category))
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = date,
                        onValueChange = {
                            date = it
                            dateError = false
                        },
                        label = { Text(stringResource(R.string.expense_date) + " (yyyy-MM-dd)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("yyyy-MM-dd") },
                        isError = dateError,
                        supportingText = {
                            if (dateError) {
                                Text("Invalid format. Required: yyyy-MM-dd (e.g. 2026-09-04)", color = MaterialTheme.colorScheme.error)
                            } else {
                                Text("Format: yyyy-MM-dd (Tap calendar to select)", style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker() }) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = stringResource(R.string.expense_date),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }

                // Payment Method Selector
                item {
                    var pmExpanded by remember { mutableStateOf(false) }
                    Column {
                        Text(
                            text = stringResource(R.string.payment_method),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { pmExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(paymentMethod)
                            }
                            DropdownMenu(
                                expanded = pmExpanded,
                                onDismissRequest = { pmExpanded = false }
                            ) {
                                paymentMethods.forEach { pm ->
                                    DropdownMenuItem(
                                        text = { Text(pm) },
                                        onClick = {
                                            paymentMethod = pm
                                            pmExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(R.string.reference_note)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }

                // Receipt Attachment Section
                item {
                    Column {
                        Text(
                            text = stringResource(R.string.receipt),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (receiptPath != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AttachFile, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Receipt Attached", fontSize = 12.sp)
                                }
                                TextButton(onClick = { receiptPath = null }) {
                                    Text(stringResource(R.string.remove_receipt), color = CrimsonDanger)
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { filePickerLauncher.launch("image/*") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.attach_receipt))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
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
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
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
        title = { Text(stringResource(R.string.add_custom_category), fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = false
                },
                label = { Text(stringResource(R.string.category_name)) },
                modifier = Modifier.fillMaxWidth(),
                isError = error,
                singleLine = true
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
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ExpenseDetailDialog(
    expense: ExpenseEntity,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = expense.title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.amount_req) + ":", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "$currencySymbol${expense.amount.formatAmount()}",
                        fontWeight = FontWeight.Bold,
                        color = CrimsonDanger,
                        fontSize = 16.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.category_req) + ":", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = expense.category, fontWeight = FontWeight.SemiBold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.expense_date) + ":", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = expense.date)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.payment_method) + ":", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = expense.paymentMethod)
                }

                if (expense.note.isNotEmpty()) {
                    Column {
                        Text(stringResource(R.string.reference_note) + ":", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = expense.note, style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (expense.receiptPath != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.receipt) + ":", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AsyncImage(
                        model = expense.receiptPath,
                        contentDescription = "Receipt Attachment",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.edit_customer).replace("Customer", "Expense"))
                }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.delete))
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
