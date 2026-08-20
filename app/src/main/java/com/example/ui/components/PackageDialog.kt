package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.IspPackageEntity

@Composable
fun PackageDialog(
    initialPackage: IspPackageEntity? = null,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (IspPackageEntity) -> Unit,
    onDelete: ((IspPackageEntity) -> Unit)? = null
) {
    var name by remember { mutableStateOf(initialPackage?.name ?: "") }
    var speedStr by remember { mutableStateOf(initialPackage?.speedMbps?.toString() ?: "20") }
    var priceStr by remember { mutableStateOf(initialPackage?.monthlyPrice?.formatAmount() ?: "40") }
    var description by remember { mutableStateOf(initialPackage?.description ?: "") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm && initialPackage != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.delete_package_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.delete_package_confirm, initialPackage.name),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete?.invoke(initialPackage)
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.example.R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.example.R.string.cancel))
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialPackage == null) androidx.compose.ui.res.stringResource(com.example.R.string.add_internet_package) else androidx.compose.ui.res.stringResource(com.example.R.string.edit_package),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.package_name_req)) },
                    placeholder = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.eg_package_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = speedStr,
                    onValueChange = { speedStr = it },
                    label = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.speed_mbps_req)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.monthly_price_currency, currencySymbol)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.description_features)) },
                    placeholder = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.eg_features)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val speed = speedStr.toIntOrNull() ?: 10
                    // Fix the bug where localized commas or unexpected symbols cause toDoubleOrNull to return null (which becomes 0.0)
                    val priceCleaned = priceStr.replace(",", "").trim()
                    val price = priceCleaned.toDoubleOrNull() ?: 0.0

                    val pkgToSave = IspPackageEntity(
                        id = initialPackage?.id ?: 0L,
                        name = name.trim(),
                        speedMbps = speed,
                        monthlyPrice = price,
                        description = description.trim()
                    )
                    onSave(pkgToSave)
                },
                enabled = name.isNotBlank() && (speedStr.toIntOrNull() ?: 0) > 0
            ) {
                Text(androidx.compose.ui.res.stringResource(com.example.R.string.save_package))
            }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (initialPackage != null && onDelete != null) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(androidx.compose.ui.res.stringResource(com.example.R.string.delete))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(androidx.compose.ui.res.stringResource(com.example.R.string.cancel))
                }
            }
        }
    )
}
