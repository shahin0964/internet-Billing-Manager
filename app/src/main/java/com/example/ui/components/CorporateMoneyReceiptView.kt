package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BillEntity
import com.example.data.model.BusinessSettingsEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.PaymentEntity
import java.util.Locale

/**
 * Modern Corporate Money Receipt layout replicating the professional ISP money voucher design.
 * Dynamically bound to BusinessSettingsEntity, PaymentEntity, BillEntity, and CustomerEntity.
 */
@Composable
fun CorporateMoneyReceiptView(
    payment: PaymentEntity,
    bill: BillEntity?,
    customer: CustomerEntity?,
    settings: BusinessSettingsEntity,
    modifier: Modifier = Modifier,
    motto: String = "Stay Connected, Stay Ahead"
) {
    // Dynamic Bindings with robust fallbacks
    val ispName = settings.ispName.ifBlank { "ISP & IT Solutions" }
    val hotline = settings.hotline.ifBlank { "01XXXXXXXXX" }
    val email = settings.email.ifBlank { "support@isp.com" }
    val address = settings.address.ifBlank { "Dhaka, Bangladesh" }
    val currency = settings.currencySymbol.ifBlank { "৳" }

    val customerName = (customer?.name ?: payment.customerName).ifBlank { "Valued Customer" }
    val customerCode = customer?.customerCode ?: "CUST-${payment.customerId}"
    val customerArea = customer?.area?.ifBlank { null } ?: customer?.zone?.ifBlank { null } ?: "Main Branch"
    val pppoeUsername = customer?.pppoeUsername?.ifBlank { null } ?: customerCode

    val formattedAmount = String.format(Locale.US, "%.2f", payment.amount)
    val amountInWords = remember(payment.amount) {
        convertNumberToWords(payment.amount.toLong()) + " Only"
    }

    val billingMonthFor = bill?.billingMonth?.ifBlank { null }
        ?: payment.notes.takeIf { it.isNotBlank() && it.contains("Bill", ignoreCase = true) }
        ?: "Monthly Internet Bill"

    val paidAmount = String.format(Locale.US, "%.2f", payment.amount)
    val dueAmount = String.format(Locale.US, "%.2f", bill?.dueAmount ?: 0.0)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
        ) {
            // Decorative Background Corner Waves
            Canvas(modifier = Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height

                // Top-Left Teal Wave
                val pathTopLeft = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w * 0.22f, 0f)
                    cubicTo(w * 0.16f, h * 0.12f, w * 0.06f, h * 0.15f, 0f, h * 0.22f)
                    close()
                }
                drawPath(
                    path = pathTopLeft,
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF0077B6), Color(0xFF00B4D8), Color(0xFF90E0EF))
                    )
                )

                // Top-Left Secondary Accent Wave
                val pathTopLeft2 = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w * 0.15f, 0f)
                    cubicTo(w * 0.10f, h * 0.07f, w * 0.04f, h * 0.09f, 0f, h * 0.15f)
                    close()
                }
                drawPath(
                    path = pathTopLeft2,
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF03045E), Color(0xFF0077B6))
                    )
                )

                // Watermark Origami / Geometric Bird on Right
                val birdPath = Path().apply {
                    val startX = w * 0.82f
                    val startY = h * 0.38f
                    moveTo(startX, startY)
                    lineTo(startX + 80f, startY - 40f)
                    lineTo(startX + 140f, startY - 10f)
                    lineTo(startX + 110f, startY + 60f)
                    lineTo(startX + 30f, startY + 90f)
                    close()
                }
                drawPath(
                    path = birdPath,
                    color = Color(0xFFFCE7F3).copy(alpha = 0.55f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 0.dp)
            ) {
                // ==================== 1. TOP HEADER SECTION ====================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1.1 Left: Company Branding
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1.3f)
                    ) {
                        // Abstract ISP Swirl Logo
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF023E8A), Color(0xFF0096C7), Color(0xFF48CAE4))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = ispName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "ISP & IT SOLUTIONS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = Color(0xFF475569)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "FAST  •  STABLE  •  CONNECTED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                ),
                                color = Color(0xFF0284C7)
                            )
                        }
                    }

                    // 1.2 Center: MONEY RECEIPT Title
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1.1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF1F5F9),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Receipt,
                                        contentDescription = null,
                                        tint = Color(0xFF0F172A),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "MONEY",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp,
                                        letterSpacing = 1.sp
                                    ),
                                    color = Color(0xFF0F172A),
                                    lineHeight = 16.sp
                                )
                                Text(
                                    text = "RECEIPT",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp,
                                        letterSpacing = 1.sp
                                    ),
                                    color = Color(0xFF00A896),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Thank you for being with us!",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontStyle = FontStyle.Italic,
                                fontSize = 9.sp
                            ),
                            color = Color(0xFF64748B)
                        )
                    }

                    // 1.3 Right: Mobile Banking / bKash Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFFE11D48)),
                        shadowElevation = 1.dp,
                        modifier = Modifier
                            .weight(1.2f)
                            .padding(start = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "bKash",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp
                                    ),
                                    color = Color(0xFFD81B60)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                // Origami Bird Logo Icon
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = "bKash",
                                    tint = Color(0xFFE11D48),
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Smartphone,
                                    contentDescription = null,
                                    tint = Color(0xFFD81B60),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "bKash Number",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 8.5.sp
                                    ),
                                    color = Color(0xFF334155)
                                )
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            // Hotline/Payment Number Box
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFFF1F2),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECDD3)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = hotline,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = Color(0xFF9F1239),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 3.dp, horizontal = 4.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ==================== 2. CONTACT INFO RIBBON ====================
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFFF0F9FF),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBAE6FD)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Phone
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0369A1)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = hotline,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF0F172A),
                                fontSize = 10.sp
                            )
                        }

                        Text(text = "|", color = Color(0xFF94A3B8), fontSize = 12.sp)

                        // Email
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF312E81)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = email,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF0F172A),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(text = "|", color = Color(0xFF94A3B8), fontSize = 12.sp)

                        // Address
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF065F46)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = address,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFF0F172A),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ==================== 3. DOTTED FORM BODY ====================
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Line 1: Received with thanks from
                    DottedFormRow(
                        icon = Icons.Default.Person,
                        label = "Received with thanks from",
                        value = "$customerName ($customerCode)"
                    )

                    // Line 2: Amount
                    DottedFormRow(
                        icon = Icons.Default.Paid,
                        label = "Amount",
                        value = "$currency $formattedAmount"
                    )

                    // Line 3: In word
                    DottedFormRow(
                        icon = Icons.Default.Description,
                        label = "In word",
                        value = amountInWords
                    )

                    // Line 4: For & Branch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DottedFormRow(
                            icon = Icons.Default.Place,
                            label = "For",
                            value = billingMonthFor,
                            modifier = Modifier.weight(1.3f)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        DottedInlineField(
                            label = "Branch",
                            value = customerArea,
                            modifier = Modifier.weight(0.9f)
                        )
                    }

                    // Line 5: ACCT. | PAID | DUE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DottedFormRow(
                            icon = Icons.Default.CalendarMonth,
                            label = "ACCT.",
                            value = pppoeUsername,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        DottedInlineField(
                            label = "PAID",
                            value = "$currency $paidAmount",
                            valueColor = Color(0xFF16A34A),
                            isBold = true,
                            modifier = Modifier.weight(0.9f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        DottedInlineField(
                            label = "DUE",
                            value = "$currency $dueAmount",
                            valueColor = if ((bill?.dueAmount ?: 0.0) > 0.0) Color(0xFFDC2626) else Color(0xFF475569),
                            modifier = Modifier.weight(0.9f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ==================== 4. BOTTOM HIGHLIGHT & SIGNATURES ====================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // 4.1 Bottom Left: Amount = [ ৳ 1500.00 ] Highlight Box
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFE0F2FE),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBAE6FD)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0C4A6E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Paid,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "Amount =",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                ),
                                color = Color(0xFF0F172A)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Inset Amount Display Box
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF94A3B8)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "$currency $formattedAmount",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp
                                    ),
                                    color = Color(0xFF0F172A),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    // 4.2 Bottom Right: Received by & Authorized Signature
                    Row(
                        modifier = Modifier.weight(1.2f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Received by
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            DottedUnderline(width = 90.dp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Received by",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = Color(0xFF334155)
                            )
                        }

                        // Authorized Signature
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            DottedUnderline(width = 110.dp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Authorized Signature",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = Color(0xFF334155)
                            )
                        }
                    }
                }

                // ==================== 5. STYLISH FOOTER BAND ====================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(GenericShape { size, _ ->
                            moveTo(0f, 0f)
                            lineTo(size.width * 0.75f, 0f)
                            cubicTo(size.width * 0.88f, 0f, size.width * 0.95f, size.height * 0.5f, size.width, size.height)
                            lineTo(0f, size.height)
                            close()
                        })
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF312E81), Color(0xFF4338CA), Color(0xFF0D9488), Color(0xFF06B6D4))
                            )
                        )
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = motto,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                                fontSize = 11.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable full-width row with leading circular icon, bold label, and dotted underline with dynamic value.
 */
@Composable
private fun DottedFormRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon Circle
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFFE0F2FE)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF0369A1),
                modifier = Modifier.size(14.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            ),
            color = Color(0xFF0F172A)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(IntrinsicSize.Min),
            contentAlignment = Alignment.BottomStart
        ) {
            DottedUnderline(modifier = Modifier.fillMaxWidth())

            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                ),
                color = Color(0xFF1E293B),
                modifier = Modifier.padding(bottom = 2.dp, start = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Inline label + dotted field value for multi-column rows (e.g. Branch, PAID, DUE).
 */
@Composable
private fun DottedInlineField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color(0xFF1E293B),
    isBold: Boolean = false
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            ),
            color = Color(0xFF0F172A)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(IntrinsicSize.Min),
            contentAlignment = Alignment.BottomStart
        ) {
            DottedUnderline(modifier = Modifier.fillMaxWidth())

            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 11.sp
                ),
                color = valueColor,
                modifier = Modifier.padding(bottom = 2.dp, start = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Draws a clean dotted baseline using Canvas and DashPathEffect.
 */
@Composable
fun DottedUnderline(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    color: Color = Color(0xFF0284C7)
) {
    val mod = if (width != null) modifier.width(width) else modifier
    Canvas(modifier = mod.height(10.dp)) {
        val y = size.height - 2f
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.8f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
        )
    }
}

/**
 * Converts numbers into English words representation.
 */
fun convertNumberToWords(number: Long): String {
    if (number == 0L) return "Zero Taka"

    val units = arrayOf(
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
        "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
        "Seventeen", "Eighteen", "Nineteen"
    )

    val tens = arrayOf(
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    )

    fun convertLessThanThousand(n: Long): String {
        var rem = n
        var result = ""

        if (rem >= 100) {
            result += units[(rem / 100).toInt()] + " Hundred "
            rem %= 100
        }

        if (rem >= 20) {
            result += tens[(rem / 10).toInt()] + " "
            rem %= 10
        }

        if (rem > 0) {
            result += units[rem.toInt()] + " "
        }

        return result.trim()
    }

    var num = number
    var words = ""

    if (num >= 10000000) { // Crore
        words += convertLessThanThousand(num / 10000000) + " Crore "
        num %= 10000000
    }

    if (num >= 100000) { // Lakh
        words += convertLessThanThousand(num / 100000) + " Lakh "
        num %= 100000
    }

    if (num >= 1000) { // Thousand
        words += convertLessThanThousand(num / 1000) + " Thousand "
        num %= 1000
    }

    if (num > 0) {
        words += convertLessThanThousand(num) + " "
    }

    return (words.trim() + " Taka").trim()
}
