package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.BillEntity
import com.example.data.model.BusinessSettingsEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.PaymentEntity
import com.example.ui.theme.EmeraldSuccess
import com.example.util.ReceiptPrintUtils

/**
 * Post-payment confirmation popup dialog.
 * Asks "Payment Receipt দিতে চান?" with options to Generate, Print, Download PDF, or Skip.
 */
@Composable
fun PostPaymentReceiptPromptDialog(
    payment: PaymentEntity,
    bill: BillEntity?,
    customer: CustomerEntity?,
    settings: BusinessSettingsEntity,
    isBn: Boolean = true,
    onViewReceipt: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val htmlContent = ReceiptPrintUtils.generateReceiptHtml(payment, bill, customer, settings, isBn)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(EmeraldSuccess.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = if (isBn) "পেমেন্ট সফল হয়েছে!" else "Payment Successful!",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = EmeraldSuccess
                    )
                    Text(
                        text = if (isBn) "পেমেন্ট রশিদ দিতে চান?" else "Would you like a Payment Receipt?",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = if (isBn) "গ্রাহক:" else "Customer:", style = MaterialTheme.typography.labelMedium)
                            Text(text = payment.customerName, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = if (isBn) "রশিদ নং:" else "Receipt No:", style = MaterialTheme.typography.labelMedium)
                            Text(text = payment.paymentReceiptNo, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = if (isBn) "পরিমাণ:" else "Amount Paid:", style = MaterialTheme.typography.labelMedium)
                            Text(text = "${settings.currencySymbol}${payment.amount}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = EmeraldSuccess)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onViewReceipt,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (isBn) "🧾 রশিদ দেখুন (Generate Receipt)" else "🧾 View / Generate Receipt")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            ReceiptPrintUtils.printReceipt(context, payment, bill, customer, settings, isBn)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = if (isBn) "প্রিন্ট" else "Print", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            ReceiptPrintUtils.savePdfReceipt(context, payment, bill, customer, settings, isBn)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = if (isBn) "PDF" else "PDF", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val pdf = ReceiptPrintUtils.generateReceiptPdfFile(context, payment, bill, customer, settings, isBn)
                            ReceiptPrintUtils.sharePdfFile(context, pdf, isBn)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = if (isBn) "শেয়ার" else "Share", fontSize = 11.sp)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = if (isBn) "বন্ধ করুন (Skip / Close)" else "Skip / Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

/**
 * On-screen printable receipt view modal dialog.
 */
@Composable
fun PaymentReceiptModal(
    payment: PaymentEntity,
    bill: BillEntity?,
    customer: CustomerEntity?,
    settings: BusinessSettingsEntity,
    isBn: Boolean = true,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val htmlContent = ReceiptPrintUtils.generateReceiptHtml(payment, bill, customer, settings, isBn)

    val ispName = settings.ispName.ifBlank { if (isBn) "আইএসপি ডিজিটাল নেটওয়ার্ক" else "ISP Digital Network" }
    val hotline = settings.hotline.ifBlank { if (isBn) "০১৭০০-০০০০০০" else "01700-000000" }
    val address = settings.address.ifBlank { if (isBn) "হেড অফিস, ঢাকা, বাংলাদেশ" else "Head Office, Dhaka, Bangladesh" }
    val currency = settings.currencySymbol.ifBlank { "৳" }

    val custName = customer?.name ?: payment.customerName
    val custCode = customer?.customerCode ?: "CUST-${payment.customerId}"
    val custPhone = customer?.phone ?: "N/A"
    val pppoeUser = customer?.pppoeUsername ?: "N/A"
    val packageName = customer?.packageName ?: "Standard Package"
    val custAddress = customer?.address ?: "N/A"

    val invNo = bill?.billNumber ?: "INV-${payment.billId}"
    val receiptNo = payment.paymentReceiptNo
    val billMonth = bill?.billingMonth ?: payment.paymentDate.take(7)
    val billAmt = String.format("%.2f", bill?.amount ?: payment.amount)
    val paidAmt = String.format("%.2f", payment.amount)
    val dueAmt = String.format("%.2f", bill?.dueAmount ?: 0.0)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Modal Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isBn) "পেমেন্ট রশিদ (Receipt)" else "Payment Receipt",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Receipt Content (Scrollable Paper view)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // ISP Header
                        Text(
                            text = ispName,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF1E3A8A),
                            textAlign = TextAlign.Center
                        )
                        if (address.isNotBlank()) {
                            Text(
                                text = address,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF475569),
                                textAlign = TextAlign.Center
                            )
                        }
                        Text(
                            text = "${if (isBn) "হটলাইন:" else "Hotline:"} $hotline",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFF475569),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Title Badge
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2563EB)
                        ) {
                            Text(
                                text = if (isBn) "পেমেন্ট রশিদ" else "OFFICIAL PAYMENT RECEIPT",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Customer Details Table
                        ReceiptSectionHeader(title = if (isBn) "গ্রাহকের তথ্য" else "CUSTOMER DETAILS")
                        ReceiptDataRow(label = if (isBn) "গ্রাহকের নাম:" else "Customer Name:", value = "$custName ($custCode)", isBold = true)
                        ReceiptDataRow(label = if (isBn) "মোবাইল:" else "Phone:", value = custPhone)
                        ReceiptDataRow(label = if (isBn) "ইউজারনেম:" else "PPPoE Username:", value = pppoeUser)
                        ReceiptDataRow(label = if (isBn) "প্যাকেজ:" else "Package:", value = packageName)
                        ReceiptDataRow(label = if (isBn) "ঠিকানা:" else "Address:", value = custAddress)

                        Spacer(modifier = Modifier.height(10.dp))

                        // Payment & Bill Info
                        ReceiptSectionHeader(title = if (isBn) "পেমেন্ট তথ্য" else "PAYMENT DETAILS")
                        ReceiptDataRow(label = if (isBn) "রশিদ নং:" else "Receipt No:", value = receiptNo, valueColor = Color(0xFF1E3A8A), isBold = true)
                        ReceiptDataRow(label = if (isBn) "ইনভয়েস নং:" else "Invoice No:", value = invNo)
                        ReceiptDataRow(label = if (isBn) "তারিখ:" else "Payment Date:", value = payment.paymentDate)
                        ReceiptDataRow(label = if (isBn) "বিলিং মাস:" else "Bill Month:", value = billMonth)

                        Spacer(modifier = Modifier.height(12.dp))

                        // Amount Breakdown Box
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                ReceiptDataRow(label = if (isBn) "মোট বিল:" else "Total Bill:", value = "$currency $billAmt")
                                ReceiptDataRow(label = if (isBn) "পরিশোধিত:" else "Paid Amount:", value = "$currency $paidAmt", valueColor = Color(0xFF16A34A), isBold = true)
                                ReceiptDataRow(label = if (isBn) "অবশিষ্ট বকেয়া:" else "Remaining Due:", value = "$currency $dueAmt")
                                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Color(0xFFCBD5E1))
                                ReceiptDataRow(label = if (isBn) "পেমেন্ট মেথড:" else "Method:", value = payment.paymentMethod, isBold = true)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = if (isBn) "পেমেন্ট স্ট্যাটাস:" else "Status:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF475569))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFDCFCE7)
                                    ) {
                                        Text(
                                            text = if (isBn) "PAID (পরিশোধিত)" else "PAID",
                                            color = Color(0xFF15803D),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Footer Note
                        Text(
                            text = if (isBn) "আমাদের ইন্টারনেট সেবা ব্যবহারের জন্য ধন্যবাদ!" else "Thank you for using our internet service!",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF334155),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            ReceiptPrintUtils.printReceipt(context, payment, bill, customer, settings, isBn)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (isBn) "প্রিন্ট" else "Print", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            ReceiptPrintUtils.savePdfReceipt(context, payment, bill, customer, settings, isBn)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (isBn) "PDF" else "PDF", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val pdf = ReceiptPrintUtils.generateReceiptPdfFile(context, payment, bill, customer, settings, isBn)
                            ReceiptPrintUtils.sharePdfFile(context, pdf, isBn)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (isBn) "শেয়ার" else "Share", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptSectionHeader(title: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF1E40AF)
        )
        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
    }
}

@Composable
private fun ReceiptDataRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    valueColor: Color = Color(0xFF1E293B)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64748B),
            modifier = Modifier.weight(0.45f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
            ),
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.55f)
        )
    }
}
