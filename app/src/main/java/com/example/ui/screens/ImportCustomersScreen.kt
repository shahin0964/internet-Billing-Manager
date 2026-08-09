package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.CustomerEntity
import com.example.ui.viewmodel.IspViewModel
import com.example.util.CustomerField
import com.example.util.CustomerImportParser
import com.example.util.ImportValidationSummary
import com.example.util.ParseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportCustomersScreen(
    onBackClick: () -> Unit,
    viewModel: IspViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val existingCustomers by viewModel.customers.collectAsStateWithLifecycle()
    val existingPackages by viewModel.packages.collectAsStateWithLifecycle()

    var parseResult by remember { mutableStateOf<ParseResult?>(null) }
    var isParsing by remember { mutableStateOf(false) }
    var columnMapping by remember { mutableStateOf<Map<CustomerField, Int>>(emptyMap()) }
    var overwriteDuplicates by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }

    var validationSummary by remember { mutableStateOf<ImportValidationSummary?>(null) }

    // Re-run validation whenever mapping, rows or existing customer data changes
    LaunchedEffect(parseResult, columnMapping, existingCustomers, existingPackages) {
        val currentParse = parseResult
        if (currentParse != null && currentParse.rows.isNotEmpty()) {
            validationSummary = withContext(Dispatchers.Default) {
                CustomerImportParser.validateRows(
                    rows = currentParse.rows,
                    fieldMapping = columnMapping,
                    existingCustomers = existingCustomers,
                    existingPackages = existingPackages
                )
            }
        } else {
            validationSummary = null
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isParsing = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    CustomerImportParser.parseFile(context, uri)
                }
                parseResult = result
                isParsing = false

                if (result.errorMessage != null) {
                    Toast.makeText(context, result.errorMessage, Toast.LENGTH_LONG).show()
                } else if (result.headers.isNotEmpty()) {
                    columnMapping = CustomerImportParser.autoDetectMapping(result.headers)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.import_customers),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_to_list)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // STEP 1: FILE SELECTION CARD
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.select_import_file),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.supported_file_formats),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.import_file_instructions),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    filePickerLauncher.launch(
                                        arrayOf(
                                            "text/csv",
                                            "text/comma-separated-values",
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "application/vnd.ms-excel",
                                            "*/*"
                                        )
                                    )
                                },
                                enabled = !isParsing && !isImporting,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = stringResource(R.string.select_import_file))
                            }

                            if (isParsing) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }

                        parseResult?.let { res ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "${res.fileName} (${res.rows.size} rows)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // STEP 2: COLUMN MAPPING SECTION
            parseResult?.let { res ->
                if (res.headers.isNotEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            shadowElevation = 2.dp,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.column_mapping),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CustomerField.values().forEach { field ->
                                        ColumnMappingDropdownRow(
                                            field = field,
                                            headers = res.headers,
                                            selectedIndex = columnMapping[field],
                                            onIndexSelected = { newIdx ->
                                                val updatedMap = columnMapping.toMutableMap()
                                                if (newIdx == null) {
                                                    updatedMap.remove(field)
                                                } else {
                                                    updatedMap[field] = newIdx
                                                }
                                                columnMapping = updatedMap
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // STEP 3: PREVIEW & VALIDATION SUMMARY
            validationSummary?.let { summary ->
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        shadowElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.validation_summary),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            // Grid of stats
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricBadge(
                                    label = stringResource(R.string.total_rows_detected),
                                    value = summary.totalRows.toString(),
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricBadge(
                                    label = stringResource(R.string.valid_rows),
                                    value = summary.validCount.toString(),
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricBadge(
                                    label = stringResource(R.string.invalid_rows),
                                    value = summary.invalidCount.toString(),
                                    containerColor = if (summary.invalidCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (summary.invalidCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricBadge(
                                    label = stringResource(R.string.duplicate_rows),
                                    value = summary.duplicateCount.toString(),
                                    containerColor = if (summary.duplicateCount > 0) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (summary.duplicateCount > 0) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // DUPLICATE ACTION CHOICES
                            if (summary.duplicateCount > 0) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = stringResource(R.string.duplicate_action),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { overwriteDuplicates = false }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        RadioButton(
                                            selected = !overwriteDuplicates,
                                            onClick = { overwriteDuplicates = false }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.skip_duplicates),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { overwriteDuplicates = true }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        RadioButton(
                                            selected = overwriteDuplicates,
                                            onClick = { overwriteDuplicates = true }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.update_duplicates),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }

                            // BUTTON TO IMPORT
                            val candidatesToImport = summary.details
                                .filter { it.isValid && it.candidate != null }
                                .mapNotNull { it.candidate }

                            Button(
                                onClick = {
                                    if (candidatesToImport.isEmpty()) return@Button
                                    isImporting = true
                                    viewModel.importCustomers(
                                        candidates = candidatesToImport,
                                        overwriteDuplicates = overwriteDuplicates
                                    ) { imported, updated, skipped ->
                                        isImporting = false
                                        val msg = context.getString(
                                            R.string.import_success_msg,
                                            imported + updated,
                                            updated,
                                            skipped
                                        )
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        onBackClick()
                                    }
                                },
                                enabled = candidatesToImport.isNotEmpty() && !isImporting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isImporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = stringResource(R.string.importing_progress))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.btn_import_valid),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
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

@Composable
private fun ColumnMappingDropdownRow(
    field: CustomerField,
    headers: List<String>,
    selectedIndex: Int?,
    onIndexSelected: (Int?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val fieldName = if (java.util.Locale.getDefault().language == "bn") field.displayNameBn else field.displayNameEn

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = fieldName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val label = if (selectedIndex != null && selectedIndex in headers.indices) {
                        headers[selectedIndex]
                    } else {
                        stringResource(R.string.ignore_column)
                    }

                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selectedIndex != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.ignore_column)) },
                    onClick = {
                        onIndexSelected(null)
                        expanded = false
                    }
                )
                headers.forEachIndexed { index, header ->
                    DropdownMenuItem(
                        text = { Text(text = "$header (Col #${index + 1})") },
                        onClick = {
                            onIndexSelected(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricBadge(
    label: String,
    value: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.8f),
                fontSize = 10.sp
            )
        }
    }
}
