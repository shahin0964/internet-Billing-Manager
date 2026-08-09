package com.example.ui.components

import androidx.compose.runtime.Composable
import com.example.data.model.BillEntity

@Composable
fun EditBillDialog(
    bill: BillEntity,
    onDismiss: () -> Unit,
    onSave: (BillEntity) -> Unit
) {
    // Dummy implementation
    onDismiss()
}