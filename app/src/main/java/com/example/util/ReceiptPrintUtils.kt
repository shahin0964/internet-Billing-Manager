package com.example.util

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.BillEntity
import com.example.data.model.BusinessSettingsEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.PaymentEntity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReceiptPrintUtils {

    private const val TAG = "ReceiptPrintUtils"

    private fun findActivity(context: Context): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    /**
     * Generates a native PDF document for the payment receipt according to configured format.
     */
    fun generateReceiptPdfFile(
        context: Context,
        payment: PaymentEntity,
        bill: BillEntity?,
        customer: CustomerEntity?,
        settings: BusinessSettingsEntity,
        isBn: Boolean = true
    ): File {
        val config = ReceiptCustomizationManager.getConfig(context)
        return when (config.paperSize) {
            "A4" -> generateStandardA4ReceiptPdfFile(context, payment, bill, customer, settings, isBn)
            else -> generateMoneyReceiptPdfFile(context, payment, bill, customer, settings, isBn)
        }
    }

    /**
     * Generates a high-resolution JPG image (.jpg) for the payment receipt according to the exact configured design and layout.
     */
    fun generateReceiptJpgFile(
        context: Context,
        payment: PaymentEntity,
        bill: BillEntity?,
        customer: CustomerEntity?,
        settings: BusinessSettingsEntity,
        isBn: Boolean = true
    ): File {
        val pdfFile = generateReceiptPdfFile(context, payment, bill, customer, settings, isBn)
        val docsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir, "Receipts")
        if (!docsDir.exists()) docsDir.mkdirs()

        val safeReceiptNo = payment.paymentReceiptNo.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val jpgFile = File(docsDir, "Receipt_${safeReceiptNo}.jpg")

        val bitmap = renderPdfPageToBitmap(pdfFile)
        if (bitmap != null) {
            FileOutputStream(jpgFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 96, out)
            }
            bitmap.recycle()
        }
        return jpgFile
    }

    /**
     * Generates a professional Internet / ISP Monthly Bill Money Receipt (মানি রশিদ) PDF format.
     */
    fun generateMoneyReceiptPdfFile(
        context: Context,
        payment: PaymentEntity,
        bill: BillEntity?,
        customer: CustomerEntity?,
        settings: BusinessSettingsEntity,
        isBn: Boolean = true
    ): File {
        val config = ReceiptCustomizationManager.getConfig(context)
        val ispName = settings.ispName.ifBlank { if (isBn) "আইএসপি ডিজিটাল নেটওয়ার্ক" else "ISP Digital Network" }
        val hotline = settings.hotline.ifBlank { if (isBn) "০১৭০০-০০০০০০" else "01700-000000" }
        val email = settings.email.ifBlank { "support@isp.com" }
        val address = settings.address.ifBlank { if (isBn) "হেড অফিস, ঢাকা, বাংলাদেশ" else "Head Office, Dhaka, Bangladesh" }
        val currency = settings.currencySymbol.ifBlank { "৳" }

        val custName = customer?.name ?: payment.customerName
        val custCode = customer?.customerCode ?: "CUST-${payment.customerId}"
        val pppoeUser = (customer?.pppoeUsername?.ifBlank { null } ?: customer?.customerCode?.ifBlank { null } ?: payment.customerName).ifBlank { "N/A" }
        val packageName = customer?.packageName ?: "Standard Package"
        val custAddress = customer?.address ?: "N/A"
        val custBranch = (customer?.area?.ifBlank { null } ?: customer?.zone?.ifBlank { null } ?: "Main Branch")

        val invNo = bill?.billNumber ?: "INV-${payment.billId}"
        val receiptNo = payment.paymentReceiptNo
        val billMonth = bill?.billingMonth ?: payment.paymentDate.take(7)
        val billAmt = String.format(Locale.US, "%.2f", bill?.amount ?: payment.amount)
        val paidAmt = String.format(Locale.US, "%.2f", payment.amount)
        val dueAmt = String.format(Locale.US, "%.2f", bill?.dueAmount ?: 0.0)

        val bkashNumber = config.bkashNumber.trim()
        val nagadNumber = config.nagadNumber.trim()
        val sigName = config.signatureName.trim()
        val motto = config.corporateMotto.ifBlank { "Stay Connected, Stay Ahead" }

        // Professional landscape voucher canvas dimensions: 650 x 410
        val pageWidth = 650
        val pageHeight = 410

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Clean white background
        canvas.drawColor(AndroidColor.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val leftMargin = 22f
        val rightMargin = pageWidth - 22f
        val contentWidth = rightMargin - leftMargin

        // 1. Top Header Section
        // Left: Logo & Company Name
        paint.color = AndroidColor.parseColor("#0284C7")
        canvas.drawCircle(leftMargin + 18f, 36f, 18f, paint)
        paint.color = AndroidColor.WHITE
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("ISP", leftMargin + 18f, 40f, paint)

        paint.textAlign = Paint.Align.LEFT
        paint.color = AndroidColor.parseColor("#0F172A")
        paint.textSize = 15f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(ispName, leftMargin + 42f, 32f, paint)

        paint.color = AndroidColor.parseColor("#0284C7")
        paint.textSize = 7.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("FAST • STABLE • CONNECTED", leftMargin + 42f, 45f, paint)

        // Center: MONEY RECEIPT Title
        val centerX = pageWidth / 2f - 20f
        paint.textAlign = Paint.Align.CENTER
        paint.color = AndroidColor.parseColor("#0F172A")
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("MONEY", centerX, 28f, paint)
        paint.color = AndroidColor.parseColor("#00A896")
        paint.textSize = 16f
        canvas.drawText("RECEIPT", centerX, 44f, paint)
        paint.color = AndroidColor.parseColor("#64748B")
        paint.textSize = 7.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        canvas.drawText("Thank you for being with us!", centerX, 55f, paint)

        // Right: Dedicated Mobile Banking Box (bKash / Nagad)
        val bankBoxW = 160f
        val bankBoxH = 50f
        val bankBoxRect = RectF(rightMargin - bankBoxW, 14f, rightMargin, 14f + bankBoxH)
        paint.color = AndroidColor.parseColor("#FFF1F2")
        canvas.drawRoundRect(bankBoxRect, 8f, 8f, paint)
        paint.color = AndroidColor.parseColor("#E11D48")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(bankBoxRect, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        paint.textAlign = Paint.Align.CENTER
        if (bkashNumber.isNotBlank() && nagadNumber.isNotBlank()) {
            paint.color = AndroidColor.parseColor("#D81B60")
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("bKash: $bkashNumber", bankBoxRect.centerX(), 32f, paint)
            paint.color = AndroidColor.parseColor("#EA580C")
            canvas.drawText("Nagad: $nagadNumber", bankBoxRect.centerX(), 48f, paint)
        } else if (nagadNumber.isNotBlank()) {
            paint.color = AndroidColor.parseColor("#EA580C")
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Nagad Mobile Banking", bankBoxRect.centerX(), 30f, paint)
            paint.textSize = 9.5f
            canvas.drawText(nagadNumber, bankBoxRect.centerX(), 48f, paint)
        } else {
            val num = if (bkashNumber.isNotBlank()) bkashNumber else hotline
            paint.color = AndroidColor.parseColor("#D81B60")
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("bKash Mobile Banking", bankBoxRect.centerX(), 30f, paint)
            paint.textSize = 9.5f
            canvas.drawText(num, bankBoxRect.centerX(), 48f, paint)
        }

        // 2. Contact Information Ribbon
        val ribbonY = 72f
        val ribbonH = 20f
        val ribbonRect = RectF(leftMargin, ribbonY, rightMargin, ribbonY + ribbonH)
        paint.color = AndroidColor.parseColor("#F0F9FF")
        canvas.drawRoundRect(ribbonRect, 10f, 10f, paint)
        paint.color = AndroidColor.parseColor("#BAE6FD")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f
        canvas.drawRoundRect(ribbonRect, 10f, 10f, paint)
        paint.style = Paint.Style.FILL

        paint.textAlign = Paint.Align.LEFT
        paint.color = AndroidColor.parseColor("#0F172A")
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("☎ Hotline: $hotline", leftMargin + 12f, ribbonY + 13f, paint)
        canvas.drawText("✉ Email: $email", leftMargin + 190f, ribbonY + 13f, paint)
        canvas.drawText("📍 Address: $address", leftMargin + 380f, ribbonY + 13f, paint)

        // 3. Dotted Form Rows Section
        var formY = 118f
        val lineSpacing = 30f

        // Helper to draw dotted row with prominent, bold dynamic value
        fun drawFormDottedRow(
            label: String,
            value: String,
            y: Float,
            startX: Float = leftMargin,
            endX: Float = rightMargin,
            valueColor: Int = AndroidColor.parseColor("#0F172A"),
            isBold: Boolean = true,
            valueSize: Float = 11.5f
        ) {
            paint.textAlign = Paint.Align.LEFT
            paint.color = AndroidColor.parseColor("#334155")
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(label, startX, y, paint)

            val labelW = paint.measureText(label) + 6f
            val dotStartX = startX + labelW
            paint.color = AndroidColor.parseColor("#0284C7")
            paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 4f), 0f)
            paint.strokeWidth = 1f
            canvas.drawLine(dotStartX, y, endX, y, paint)
            paint.pathEffect = null

            // Prominent, clear dynamic text value
            paint.color = valueColor
            paint.textSize = valueSize
            paint.typeface = Typeface.create(Typeface.DEFAULT, if (isBold) Typeface.BOLD else Typeface.NORMAL)
            
            // Measure available width and truncate gracefully if needed
            val availableW = endX - (dotStartX + 4f)
            var displayVal = value
            if (paint.measureText(displayVal) > availableW && availableW > 20f) {
                while (displayVal.isNotEmpty() && paint.measureText("$displayVal...") > availableW) {
                    displayVal = displayVal.dropLast(1)
                }
                displayVal = "$displayVal..."
            }
            canvas.drawText(displayVal, dotStartX + 4f, y - 2f, paint)
        }

        // Row 1: Received with thanks from (Customer's PPPoE Username)
        drawFormDottedRow("Received with thanks from", pppoeUser, formY, valueSize = 12.5f, isBold = true)
        formY += lineSpacing

        // Row 2: Amount
        drawFormDottedRow("Amount", "$currency $paidAmt", formY, valueSize = 12.5f, isBold = true)
        formY += lineSpacing

        // Row 3: In word
        val words = com.example.ui.components.convertNumberToWords(payment.amount.toLong())
        drawFormDottedRow("In word", "$words Only", formY, valueSize = 11.5f, isBold = true)
        formY += lineSpacing

        // Row 4: For & Branch
        val midX = leftMargin + (contentWidth * 0.6f)
        drawFormDottedRow("For", billMonth, formY, leftMargin, midX - 10f, valueSize = 12f, isBold = true)
        drawFormDottedRow("Branch", custBranch, formY, midX, rightMargin, valueSize = 12f, isBold = true)
        formY += lineSpacing

        // Row 5: ACCT. | PAID | DUE
        val col1End = leftMargin + (contentWidth * 0.38f)
        val col2End = leftMargin + (contentWidth * 0.68f)
        drawFormDottedRow("ACCT.", custCode, formY, leftMargin, col1End - 10f, valueSize = 12f, isBold = true)
        drawFormDottedRow("PAID", "$currency $paidAmt", formY, col1End, col2End - 10f, valueColor = AndroidColor.parseColor("#16A34A"), valueSize = 12f, isBold = true)
        drawFormDottedRow("DUE", "$currency $dueAmt", formY, col2End, rightMargin, valueColor = if ((bill?.dueAmount ?: 0.0) > 0.0) AndroidColor.parseColor("#DC2626") else AndroidColor.parseColor("#475569"), valueSize = 12f, isBold = true)

        // 4. Highlighted Amount Box & Signatures
        val btmY = 285f

        // Bottom Left: Prominent Amount = [ ৳ 1,500.00 ] Box
        val amtBoxRect = RectF(leftMargin, btmY, leftMargin + 200f, btmY + 40f)
        paint.color = AndroidColor.parseColor("#E0F2FE")
        canvas.drawRoundRect(amtBoxRect, 8f, 8f, paint)
        paint.color = AndroidColor.parseColor("#BAE6FD")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(amtBoxRect, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        paint.textAlign = Paint.Align.LEFT
        paint.color = AndroidColor.parseColor("#0F172A")
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Amount =", leftMargin + 12f, btmY + 25f, paint)

        val amtInsetRect = RectF(leftMargin + 72f, btmY + 6f, leftMargin + 192f, btmY + 34f)
        paint.color = AndroidColor.WHITE
        canvas.drawRoundRect(amtInsetRect, 4f, 4f, paint)
        paint.color = AndroidColor.parseColor("#94A3B8")
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(amtInsetRect, 4f, 4f, paint)
        paint.style = Paint.Style.FILL

        paint.textAlign = Paint.Align.CENTER
        paint.color = AndroidColor.parseColor("#0F172A")
        paint.textSize = 13.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("$currency $paidAmt", amtInsetRect.centerX(), btmY + 25f, paint)

        // Bottom Right: Received by & Authorized Signature
        val sigLineY = btmY + 24f
        val recByX = rightMargin - 240f
        val authSigX = rightMargin - 100f

        // Received by line
        paint.color = AndroidColor.parseColor("#0284C7")
        paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 3f), 0f)
        canvas.drawLine(recByX - 50f, sigLineY, recByX + 50f, sigLineY, paint)
        paint.pathEffect = null
        paint.textAlign = Paint.Align.CENTER
        paint.color = AndroidColor.parseColor("#334155")
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Received by", recByX, sigLineY + 12f, paint)

        // Authorized Signature
        if (sigName.isNotBlank()) {
            val sigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#1E3A8A")
                textSize = 16.5f
                try {
                    typeface = Typeface.create("cursive", Typeface.ITALIC)
                } catch (_: Throwable) {
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
                }
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(sigName, authSigX, sigLineY - 4f, sigPaint)
        }

        paint.color = AndroidColor.parseColor("#0284C7")
        paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 3f), 0f)
        canvas.drawLine(authSigX - 60f, sigLineY, authSigX + 60f, sigLineY, paint)
        paint.pathEffect = null
        paint.textAlign = Paint.Align.CENTER
        paint.color = AndroidColor.parseColor("#334155")
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Authorized Signature", authSigX, sigLineY + 12f, paint)

        // 5. Corporate Footer Band
        val footerY = pageHeight - 34f
        val footerH = 34f
        val footerRect = RectF(0f, footerY, pageWidth.toFloat(), pageHeight.toFloat())
        paint.color = AndroidColor.parseColor("#312E81")
        canvas.drawRect(footerRect, paint)

        paint.textAlign = Paint.Align.LEFT
        paint.color = AndroidColor.WHITE
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        canvas.drawText("⚡ $motto", leftMargin + 8f, footerY + 21f, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Receipt #: $receiptNo • $billMonth", rightMargin - 8f, footerY + 21f, paint)

        pdfDocument.finishPage(page)

        // Save to document file
        val docsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir, "Receipts")
        if (!docsDir.exists()) docsDir.mkdirs()

        val safeReceiptNo = receiptNo.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outputFile = File(docsDir, "MoneyReceipt_${safeReceiptNo}.pdf")

        var out: FileOutputStream? = null
        try {
            out = FileOutputStream(outputFile)
            pdfDocument.writeTo(out)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing Money Receipt PDF: ${e.message}", e)
        } finally {
            try { out?.close() } catch (_: Exception) {}
            pdfDocument.close()
        }

        return outputFile
    }

    /**
     * Generates standard full-page A4 PDF invoice.
     */
    fun generateStandardA4ReceiptPdfFile(
        context: Context,
        payment: PaymentEntity,
        bill: BillEntity?,
        customer: CustomerEntity?,
        settings: BusinessSettingsEntity,
        isBn: Boolean = true
    ): File {
        val config = ReceiptCustomizationManager.getConfig(context)
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
        val billAmt = String.format(Locale.US, "%.2f", bill?.amount ?: payment.amount)
        val paidAmt = String.format(Locale.US, "%.2f", payment.amount)
        val dueAmt = String.format(Locale.US, "%.2f", bill?.dueAmount ?: 0.0)

        // A4 page dimensions in points: 595 x 842
        val pageWidth = 595
        val pageHeight = 842

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Background
        canvas.drawColor(AndroidColor.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var currentY = 50f
        val leftMargin = 45f
        val rightMargin = pageWidth - 45f
        val contentWidth = rightMargin - leftMargin

        // 1. Top Decorative Bar
        paint.color = AndroidColor.parseColor("#2563EB")
        canvas.drawRect(leftMargin, currentY, rightMargin, currentY + 4f, paint)
        currentY += 28f

        // 2. Header: Company Name
        paint.color = AndroidColor.parseColor("#1E3A8A")
        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(ispName, pageWidth / 2f, currentY, paint)
        currentY += 18f

        // Address & Hotline
        paint.color = AndroidColor.parseColor("#4B5563")
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(address, pageWidth / 2f, currentY, paint)
        currentY += 15f
        canvas.drawText("${if (isBn) "হটলাইন:" else "Hotline:"} $hotline", pageWidth / 2f, currentY, paint)
        currentY += 24f

        // 3. Receipt Title Pill
        val titleText = config.receiptTitle.ifBlank {
            if (isBn) "পেমেন্ট রশিদ (PAYMENT RECEIPT)" else "OFFICIAL PAYMENT RECEIPT"
        }
        val badgeWidth = (paint.measureText(titleText) + 60f).coerceIn(240f, 400f)
        val badgeHeight = 26f
        val badgeLeft = (pageWidth - badgeWidth) / 2f
        val badgeRect = RectF(badgeLeft, currentY, badgeLeft + badgeWidth, currentY + badgeHeight)
        paint.color = AndroidColor.parseColor("#2563EB")
        canvas.drawRoundRect(badgeRect, 13f, 13f, paint)

        paint.color = AndroidColor.WHITE
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            titleText,
            pageWidth / 2f,
            currentY + 17f,
            paint
        )
        currentY += 40f

        // Reset alignment to Left
        paint.textAlign = Paint.Align.LEFT

        // 4. Customer Information Section
        drawSectionHeader(canvas, paint, if (isBn) "গ্রাহকের তথ্য (CUSTOMER DETAILS)" else "CUSTOMER DETAILS", leftMargin, currentY, contentWidth)
        currentY += 22f

        val customerRows = mutableListOf<Triple<String, String, Boolean>>()
        customerRows.add(Triple(if (isBn) "গ্রাহকের নাম:" else "Customer Name:", "$custName ($custCode)", true))
        if (config.showCustomerPhone) {
            customerRows.add(Triple(if (isBn) "মোবাইল নম্বর:" else "Phone Number:", custPhone, false))
        }
        if (config.showCustomerPppoe) {
            customerRows.add(Triple(if (isBn) "ইউজারনেম:" else "PPPoE Username:", pppoeUser, false))
        }
        if (config.showPackageName) {
            customerRows.add(Triple(if (isBn) "প্যাকেজ:" else "Package:", packageName, false))
        }
        if (config.showCustomerAddress) {
            customerRows.add(Triple(if (isBn) "ঠিকানা:" else "Address:", custAddress, false))
        }

        val custBoxTop = currentY
        val custBoxHeight = 16f + (customerRows.size * 19f)
        val custBoxRect = RectF(leftMargin, custBoxTop, rightMargin, custBoxTop + custBoxHeight)
        paint.color = AndroidColor.parseColor("#F8FAFC")
        canvas.drawRoundRect(custBoxRect, 8f, 8f, paint)
        paint.color = AndroidColor.parseColor("#E2E8F0")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(custBoxRect, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        var rowY = custBoxTop + 18f
        for ((lbl, v, isBold) in customerRows) {
            drawInfoRow(canvas, paint, lbl, v, leftMargin + 14f, rowY, contentWidth - 28f, isBold)
            rowY += 19f
        }

        currentY = custBoxTop + custBoxHeight + 20f

        // 5. Payment & Invoice Details Section
        drawSectionHeader(canvas, paint, if (isBn) "পেমেন্ট ও ইনভয়েস তথ্য (PAYMENT DETAILS)" else "PAYMENT & INVOICE DETAILS", leftMargin, currentY, contentWidth)
        currentY += 22f

        val payBoxTop = currentY
        val payBoxHeight = 95f
        val payBoxRect = RectF(leftMargin, payBoxTop, rightMargin, payBoxTop + payBoxHeight)
        paint.color = AndroidColor.parseColor("#F8FAFC")
        canvas.drawRoundRect(payBoxRect, 8f, 8f, paint)
        paint.color = AndroidColor.parseColor("#E2E8F0")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(payBoxRect, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        rowY = payBoxTop + 20f
        drawInfoRow(canvas, paint, if (isBn) "রশিদ নম্বর:" else "Receipt No:", receiptNo, leftMargin + 14f, rowY, contentWidth - 28f, true, AndroidColor.parseColor("#1E3A8A"))
        rowY += 18f
        drawInfoRow(canvas, paint, if (isBn) "ইনভয়েস নম্বর:" else "Invoice No:", invNo, leftMargin + 14f, rowY, contentWidth - 28f)
        rowY += 18f
        drawInfoRow(canvas, paint, if (isBn) "পেমেন্টের তারিখ:" else "Payment Date:", payment.paymentDate, leftMargin + 14f, rowY, contentWidth - 28f)
        rowY += 18f
        drawInfoRow(canvas, paint, if (isBn) "বিলিং মাস:" else "Billing Month:", billMonth, leftMargin + 14f, rowY, contentWidth - 28f)

        currentY = payBoxTop + payBoxHeight + 20f

        // 6. Payment Amount Summary Box
        val showDue = config.showRemainingDue
        val showMethod = config.showPaymentMethod
        var sumBoxHeight = 58f
        if (showDue) sumBoxHeight += 24f
        if (showMethod) sumBoxHeight += 32f

        val sumBoxTop = currentY
        val sumBoxRect = RectF(leftMargin, sumBoxTop, rightMargin, sumBoxTop + sumBoxHeight)
        paint.color = AndroidColor.parseColor("#F0FDF4")
        canvas.drawRoundRect(sumBoxRect, 10f, 10f, paint)
        paint.color = AndroidColor.parseColor("#86EFAC")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        canvas.drawRoundRect(sumBoxRect, 10f, 10f, paint)
        paint.style = Paint.Style.FILL

        rowY = sumBoxTop + 24f
        drawAmountRow(canvas, paint, if (isBn) "মোট বিল পরিমাণ (Total Bill):" else "Total Bill Amount:", "$currency $billAmt", leftMargin + 18f, rowY, contentWidth - 36f, false, AndroidColor.parseColor("#374151"), 13f)
        rowY += 24f
        drawAmountRow(canvas, paint, if (isBn) "পরিশোধিত পরিমাণ (Paid Amount):" else "Paid Amount:", "$currency $paidAmt", leftMargin + 18f, rowY, contentWidth - 36f, true, AndroidColor.parseColor("#15803D"), 16f)

        if (showDue) {
            rowY += 22f
            drawAmountRow(canvas, paint, if (isBn) "অবশিষ্ট বকেয়া (Remaining Due):" else "Remaining Due:", "$currency $dueAmt", leftMargin + 18f, rowY, contentWidth - 36f, false, if ((bill?.dueAmount ?: 0.0) > 0) AndroidColor.parseColor("#DC2626") else AndroidColor.parseColor("#4B5563"), 13f)
        }

        if (showMethod) {
            rowY += 14f
            paint.color = AndroidColor.parseColor("#CBD5E1")
            paint.strokeWidth = 1f
            canvas.drawLine(leftMargin + 18f, rowY, rightMargin - 18f, rowY, paint)
            rowY += 18f
            drawAmountRow(canvas, paint, if (isBn) "পেমেন্ট মাধ্যম (Payment Method):" else "Payment Method:", payment.paymentMethod, leftMargin + 18f, rowY, contentWidth - 36f, true, AndroidColor.parseColor("#1E293B"), 12f)
        }

        currentY = sumBoxTop + sumBoxHeight + 25f

        // 7. Status Stamp / Pill
        val statusText = if (isBn) "✓ PAID (পরিশোধিত)" else "✓ PAID (RECEIVED)"
        paint.color = AndroidColor.parseColor("#16A34A")
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(statusText, pageWidth / 2f, currentY, paint)
        currentY += 26f

        // Signature section for A4 as well
        val sigLineY = currentY + 36f
        val sigLineLength = 160f
        val authSigStartX = rightMargin - sigLineLength
        val authSigCenterX = authSigStartX + (sigLineLength / 2f)

        if (config.signatureName.isNotBlank()) {
            val sigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.parseColor("#0F2942")
                textSize = 18f
                try {
                    typeface = Typeface.create("cursive", Typeface.ITALIC)
                } catch (_: Throwable) {
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
                }
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(config.signatureName, authSigCenterX, sigLineY - 6f, sigPaint)
        }

        paint.color = AndroidColor.parseColor("#1E3A8A")
        paint.strokeWidth = 1f
        canvas.drawLine(authSigStartX, sigLineY, rightMargin, sigLineY, paint)

        paint.color = AndroidColor.parseColor("#1E3A8A")
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(if (isBn) "কর্তৃপক্ষের স্বাক্ষর" else "Authorized Signature", authSigCenterX, sigLineY + 14f, paint)

        currentY = sigLineY + 30f

        // Custom terms/notes
        if (config.customNotes.isNotBlank()) {
            paint.color = AndroidColor.parseColor("#64748B")
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(config.customNotes, pageWidth / 2f, currentY, paint)
            currentY += 18f
        }

        // 8. Footer & Thank you Note
        paint.color = AndroidColor.parseColor("#CBD5E1")
        paint.strokeWidth = 1f
        canvas.drawLine(leftMargin, currentY, rightMargin, currentY, paint)
        currentY += 20f

        paint.color = AndroidColor.parseColor("#374151")
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        val footerText = config.footerMessage.ifBlank {
            if (isBn) "আমাদের ইন্টারনেট সেবা ব্যবহার করার জন্য আপনাকে ধন্যবাদ!" else "Thank you for using our internet service!"
        }
        canvas.drawText(footerText, pageWidth / 2f, currentY, paint)
        currentY += 14f

        paint.color = AndroidColor.parseColor("#9CA3AF")
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val generatedAt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        canvas.drawText("This is a computer-generated official receipt • Generated on $generatedAt", pageWidth / 2f, currentY, paint)

        pdfDocument.finishPage(page)

        // Save PDF to documents directory
        val docsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir, "Receipts")
        if (!docsDir.exists()) docsDir.mkdirs()

        val safeReceiptNo = receiptNo.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outputFile = File(docsDir, "Receipt_${safeReceiptNo}.pdf")

        var out: FileOutputStream? = null
        try {
            out = FileOutputStream(outputFile)
            pdfDocument.writeTo(out)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing PDF: ${e.message}", e)
        } finally {
            try { out?.close() } catch (_: Exception) {}
            pdfDocument.close()
        }

        return outputFile
    }

    private fun drawCompactInfoRow(
        canvas: Canvas,
        paint: Paint,
        label: String,
        value: String,
        x: Float,
        y: Float,
        width: Float,
        isBold: Boolean = false
    ) {
        paint.color = AndroidColor.parseColor("#64748B")
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(label, x, y, paint)

        paint.color = AndroidColor.parseColor("#0F172A")
        paint.textSize = 9f
        paint.typeface = if (isBold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(value, x + width, y, paint)
    }

    private fun drawCompactAmountRow(
        canvas: Canvas,
        paint: Paint,
        label: String,
        value: String,
        x: Float,
        y: Float,
        width: Float,
        isBold: Boolean,
        valueColor: Int,
        fontSize: Float
    ) {
        paint.color = AndroidColor.parseColor("#334155")
        paint.textSize = fontSize - 1f
        paint.typeface = if (isBold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(label, x, y, paint)

        paint.color = valueColor
        paint.textSize = fontSize
        paint.typeface = if (isBold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(value, x + width, y, paint)
    }

    private fun drawSectionHeader(canvas: Canvas, paint: Paint, title: String, x: Float, y: Float, width: Float) {
        paint.color = AndroidColor.parseColor("#1E40AF")
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(title, x, y, paint)

        paint.color = AndroidColor.parseColor("#DBEAFE")
        paint.strokeWidth = 1.5f
        canvas.drawLine(x, y + 4f, x + width, y + 4f, paint)
    }

    private fun drawInfoRow(
        canvas: Canvas,
        paint: Paint,
        label: String,
        value: String,
        x: Float,
        y: Float,
        width: Float,
        isBoldValue: Boolean = false,
        valueColor: Int = AndroidColor.parseColor("#1E293B")
    ) {
        paint.color = AndroidColor.parseColor("#64748B")
        paint.textSize = 10.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(label, x, y, paint)

        paint.color = valueColor
        paint.textSize = 10.5f
        paint.typeface = if (isBoldValue) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(value, x + width, y, paint)
    }

    private fun drawAmountRow(
        canvas: Canvas,
        paint: Paint,
        label: String,
        value: String,
        x: Float,
        y: Float,
        width: Float,
        isBold: Boolean,
        valueColor: Int,
        fontSize: Float
    ) {
        paint.color = AndroidColor.parseColor("#374151")
        paint.textSize = fontSize - 1f
        paint.typeface = if (isBold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(label, x, y, paint)

        paint.color = valueColor
        paint.textSize = fontSize
        paint.typeface = if (isBold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(value, x + width, y, paint)
    }

    /**
     * Prints the receipt using Android PrintManager.
     */
    fun printReceipt(
        context: Context,
        payment: PaymentEntity,
        bill: BillEntity?,
        customer: CustomerEntity?,
        settings: BusinessSettingsEntity,
        isBn: Boolean = true
    ) {
        try {
            val pdfFile = generateReceiptPdfFile(context, payment, bill, customer, settings, isBn)
            val activity = findActivity(context) ?: context
            val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager

            if (printManager != null) {
                val printAdapter = object : PrintDocumentAdapter() {
                    override fun onLayout(
                        oldAttributes: PrintAttributes?,
                        newAttributes: PrintAttributes?,
                        cancellationSignal: CancellationSignal?,
                        callback: LayoutResultCallback?,
                        extras: Bundle?
                    ) {
                        if (cancellationSignal?.isCanceled == true) {
                            callback?.onLayoutCancelled()
                            return
                        }
                        val info = PrintDocumentInfo.Builder("Receipt_${payment.paymentReceiptNo}.pdf")
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(1)
                            .build()
                        callback?.onLayoutFinished(info, true)
                    }

                    override fun onWrite(
                        pages: Array<out PageRange>?,
                        destination: ParcelFileDescriptor?,
                        cancellationSignal: CancellationSignal?,
                        callback: WriteResultCallback?
                    ) {
                        var input: FileInputStream? = null
                        var output: FileOutputStream? = null
                        try {
                            input = FileInputStream(pdfFile)
                            output = FileOutputStream(destination?.fileDescriptor)
                            val buffer = ByteArray(4096)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } >= 0) {
                                if (cancellationSignal?.isCanceled == true) {
                                    callback?.onWriteCancelled()
                                    return
                                }
                                output.write(buffer, 0, bytesRead)
                            }
                            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                        } catch (e: Exception) {
                            Log.e(TAG, "Print write failed: ${e.message}", e)
                            callback?.onWriteFailed(e.message)
                        } finally {
                            try { input?.close() } catch (_: Exception) {}
                            try { output?.close() } catch (_: Exception) {}
                        }
                    }
                }

                val jobName = "Receipt_${payment.paymentReceiptNo}"
                printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
            } else {
                // If PrintManager not found, open PDF directly
                openPdfFile(activity, pdfFile, isBn)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error printing receipt: ${t.message}", t)
            Toast.makeText(context, if (isBn) "প্রিন্ট চালু করতে সমস্যা: ${t.localizedMessage}" else "Print failed: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Backward-compatible printReceipt with HTML string.
     */
    fun printReceipt(context: Context, htmlContent: String, jobName: String = "Payment_Receipt") {
        Handler(Looper.getMainLooper()).post {
            try {
                val activity = findActivity(context) ?: context
                val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                if (printManager != null) {
                    val webView = android.webkit.WebView(activity)
                    webView.webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                            try {
                                val printAdapter = webView.createPrintDocumentAdapter(jobName)
                                printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                } else {
                    Toast.makeText(activity, "প্রিন্ট সেবা ডিভাইসে উপলব্ধ নয়", Toast.LENGTH_SHORT).show()
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    /**
     * Saves the receipt as a high-resolution JPG image (.jpg) and opens it in image viewer.
     */
    fun saveJpgReceipt(
        context: Context,
        payment: PaymentEntity,
        bill: BillEntity?,
        customer: CustomerEntity?,
        settings: BusinessSettingsEntity,
        isBn: Boolean = true
    ): File? {
        return try {
            val jpgFile = generateReceiptJpgFile(context, payment, bill, customer, settings, isBn)
            trySaveJpgToPublicGalleryOrDownloads(context, jpgFile)

            Toast.makeText(
                context,
                if (isBn) "✓ রশিদ ছবি (JPG) সংরক্ষিত হয়েছে: ${jpgFile.name}" else "✓ Receipt JPG Saved: ${jpgFile.name}",
                Toast.LENGTH_SHORT
            ).show()

            openJpgFile(context, jpgFile, isBn)
            jpgFile
        } catch (t: Throwable) {
            Log.e(TAG, "Error saving JPG receipt: ${t.message}", t)
            Toast.makeText(context, if (isBn) "রশিদ তৈরিতে সমস্যা: ${t.localizedMessage}" else "Failed to create receipt JPG: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    /**
     * Saves the PDF receipt and opens it in user's PDF viewer.
     */
    fun savePdfReceipt(
        context: Context,
        payment: PaymentEntity,
        bill: BillEntity?,
        customer: CustomerEntity?,
        settings: BusinessSettingsEntity,
        isBn: Boolean = true
    ) {
        try {
            val pdfFile = generateReceiptPdfFile(context, payment, bill, customer, settings, isBn)
            
            // Also attempt to copy to MediaStore/Downloads for Android 10+ or external directory
            trySaveToPublicDownloads(context, pdfFile)

            Toast.makeText(
                context,
                if (isBn) "✓ পিডিএফ সংরক্ষিত হয়েছে: ${pdfFile.name}" else "✓ PDF Saved: ${pdfFile.name}",
                Toast.LENGTH_SHORT
            ).show()

            openPdfFile(context, pdfFile, isBn)
        } catch (t: Throwable) {
            Log.e(TAG, "Error saving PDF receipt: ${t.message}", t)
            Toast.makeText(context, if (isBn) "পিডিএফ তৈরিতে সমস্যা: ${t.localizedMessage}" else "Failed to create PDF: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Backward-compatible savePdfReceipt.
     */
    fun savePdfReceipt(context: Context, htmlContent: String, fileName: String = "Payment_Receipt.pdf") {
        printReceipt(context, htmlContent, fileName.removeSuffix(".pdf"))
    }

    private fun trySaveJpgToPublicGalleryOrDownloads(context: Context, sourceFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, sourceFile.name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ISP_Receipts")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(sourceFile).use { input ->
                            input.copyTo(out)
                        }
                    }
                }
            } else {
                val publicPictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val targetDir = File(publicPictures, "ISP_Receipts")
                if (!targetDir.exists()) targetDir.mkdirs()
                val destFile = File(targetDir, sourceFile.name)
                sourceFile.copyTo(destFile, overwrite = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not copy JPG to public pictures folder: ${e.message}")
        }
    }

    private fun trySaveToPublicDownloads(context: Context, sourceFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, sourceFile.name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ISP_Receipts")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(sourceFile).use { input ->
                            input.copyTo(out)
                        }
                    }
                }
            } else {
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(publicDownloads, "ISP_Receipts")
                if (!targetDir.exists()) targetDir.mkdirs()
                val destFile = File(targetDir, sourceFile.name)
                sourceFile.copyTo(destFile, overwrite = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not copy to public downloads folder: ${e.message}")
        }
    }

    /**
     * Opens the generated JPG image file using system image viewer.
     */
    fun openJpgFile(context: Context, jpgFile: File, isBn: Boolean = true) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                jpgFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/jpeg")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, if (isBn) "রশিদ ছবি ওপেন করুন" else "Open Receipt Image")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.w(TAG, "No default image viewer found, trying share intent: ${e.message}")
            shareJpgFile(context, jpgFile, isBn)
        }
    }

    /**
     * Shares the generated JPG image file (.jpg).
     */
    fun shareJpgFile(context: Context, jpgFile: File, isBn: Boolean = true) {
        try {
            val authority = "${context.packageName}.provider"
            val imageUri: Uri = FileProvider.getUriForFile(
                context,
                authority,
                jpgFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra(Intent.EXTRA_SUBJECT, jpgFile.name)
                putExtra(Intent.EXTRA_TEXT, if (isBn) "🧾 পেমেন্ট রশিদ" else "🧾 Payment Receipt")
                clipData = ClipData.newUri(context.contentResolver, "Receipt Image", imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, if (isBn) "রশিদ ছবি শেয়ার করুন" else "Share Receipt JPG")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Share JPG failed: ${e.message}", e)
            Toast.makeText(context, if (isBn) "শেয়ার করতে ব্যর্থ হয়েছে: ${e.localizedMessage}" else "Failed to share: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Opens the generated PDF file using system PDF viewer.
     */
    fun openPdfFile(context: Context, pdfFile: File, isBn: Boolean = true) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                pdfFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, if (isBn) "পিডিএফ ওপেন করুন" else "Open PDF Receipt")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.w(TAG, "No default PDF viewer found, trying share intent: ${e.message}")
            sharePdfFile(context, pdfFile, isBn)
        }
    }

    /**
     * Renders the first page of a generated PDF file as a high-resolution, ultra-crisp Bitmap (~300 DPI).
     */
    fun renderPdfPageToBitmap(pdfFile: File, pageIndex: Int = 0): Bitmap? {
        if (!pdfFile.exists() || pdfFile.length() == 0L) return null
        return try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (renderer.pageCount <= pageIndex) {
                renderer.close()
                pfd.close()
                return null
            }
            val page = renderer.openPage(pageIndex)
            // 3x scaling for high-definition 300 DPI preview clarity
            val scale = 3
            val targetWidth = (page.width * scale).coerceAtLeast(1200)
            val targetHeight = (page.height * scale).coerceAtLeast(1200)
            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(AndroidColor.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            pfd.close()
            bitmap
        } catch (e: Throwable) {
            Log.w(TAG, "Error rendering high-res PDF thumbnail bitmap: ${e.message}")
            null
        }
    }

    /**
     * Generates a preview image file (.jpg) corresponding to the given PDF file.
     */
    fun generatePdfThumbnailFile(context: Context, pdfFile: File): File? {
        return try {
            val bitmap = renderPdfPageToBitmap(pdfFile) ?: return null
            val thumbDir = File(context.cacheDir, "pdf_previews").apply { mkdirs() }
            val thumbFile = File(thumbDir, "${pdfFile.nameWithoutExtension}_preview.jpg")
            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 96, out)
            }
            bitmap.recycle()
            thumbFile
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to save PDF thumbnail file: ${e.message}")
            null
        }
    }

    /**
     * Shares the generated PDF file along with an inline visual preview/thumbnail.
     */
    fun sharePdfFile(context: Context, pdfFile: File, isBn: Boolean = true) {
        try {
            val authority = "${context.packageName}.provider"
            val pdfUri: Uri = FileProvider.getUriForFile(
                context,
                authority,
                pdfFile
            )
            val thumbFile = generatePdfThumbnailFile(context, pdfFile)
            val thumbUri: Uri? = thumbFile?.let {
                try {
                    FileProvider.getUriForFile(context, authority, it)
                } catch (e: Exception) {
                    null
                }
            }

            val shareIntent = if (thumbUri != null) {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(thumbUri, pdfUri))
                    putExtra(Intent.EXTRA_SUBJECT, pdfFile.name)
                    putExtra(Intent.EXTRA_TEXT, if (isBn) "🧾 পেমেন্ট রশিদ (PDF ও প্রিভিউ)" else "🧾 Payment Receipt (PDF & Preview)")
                    clipData = ClipData.newUri(context.contentResolver, "Receipt Preview", thumbUri).apply {
                        addItem(ClipData.Item(pdfUri))
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, pdfUri)
                    putExtra(Intent.EXTRA_SUBJECT, pdfFile.name)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            val chooser = Intent.createChooser(shareIntent, if (isBn) "রশিদ শেয়ার করুন" else "Share Receipt PDF")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.w(TAG, "Multi-share failed, falling back to direct PDF share: ${e.message}")
            try {
                val authority = "${context.packageName}.provider"
                val pdfUri: Uri = FileProvider.getUriForFile(
                    context,
                    authority,
                    pdfFile
                )
                val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, pdfUri)
                    putExtra(Intent.EXTRA_SUBJECT, pdfFile.name)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(fallbackIntent, if (isBn) "রশিদ শেয়ার করুন" else "Share Receipt PDF")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } catch (ex: Exception) {
                Toast.makeText(context, "শেয়ার করতে ব্যর্থ হয়েছে: ${ex.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shareReceiptText(
        context: Context,
        payment: PaymentEntity,
        customer: CustomerEntity?,
        settings: BusinessSettingsEntity,
        isBn: Boolean = true
    ) {
        try {
            val ispName = settings.ispName.ifBlank { if (isBn) "আইএসপি ডিজিটাল নেটওয়ার্ক" else "ISP Digital Network" }
            val hotline = settings.hotline.ifBlank { if (isBn) "০১৭০০-০০০০০০" else "01700-000000" }
            val custName = customer?.name ?: payment.customerName
            val custCode = customer?.customerCode ?: "CUST-${payment.customerId}"
            val currency = settings.currencySymbol.ifBlank { "৳" }

            val text = """
                🧾 *পেমেন্ট রশিদ (Payment Receipt)*
                --------------------------------
                🏢 *$ispName*
                📞 Hotline: $hotline

                👤 গ্রাহক: $custName ($custCode)
                🆔 রশিদ নং: ${payment.paymentReceiptNo}
                📅 তারিখ: ${payment.paymentDate}
                💳 মাধ্যম: ${payment.paymentMethod}
                💰 পরিশোধিত পরিমাণ: $currency${payment.amount}
                --------------------------------
                ধন্যবাদ আমাদের সাথে থাকার জন্য!
            """.trimIndent()

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Payment Receipt - ${payment.paymentReceiptNo}")
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, if (isBn) "রশিদ শেয়ার করুন" else "Share Receipt")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (t: Throwable) {
            t.printStackTrace()
            Toast.makeText(context, "শেয়ার করতে ব্যর্থ হয়েছে: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun generateReceiptHtml(
        payment: PaymentEntity,
        bill: BillEntity?,
        customer: CustomerEntity?,
        settings: BusinessSettingsEntity,
        isBn: Boolean = true,
        config: ReceiptCustomizationConfig? = null,
        context: Context? = null
    ): String {
        val resolvedConfig = config 
            ?: (context?.let { ReceiptCustomizationManager.getConfig(it) }) 
            ?: ReceiptCustomizationManager.DEFAULT_CONFIG

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
        val billAmt = String.format(Locale.US, "%.2f", bill?.amount ?: payment.amount)
        val paidAmt = String.format(Locale.US, "%.2f", payment.amount)
        val dueAmt = String.format(Locale.US, "%.2f", bill?.dueAmount ?: 0.0)

        val receiptTitle = resolvedConfig.receiptTitle.ifBlank {
            if (isBn) "পেমেন্ট রশিদ (OFFICIAL PAYMENT RECEIPT)" else "OFFICIAL PAYMENT RECEIPT"
        }
        val footerText = resolvedConfig.footerMessage.ifBlank {
            if (isBn) "আমাদের ইন্টারনেট সেবা ব্যবহার করার জন্য আপনাকে ধন্যবাদ!" else "Thank you for using our internet service!"
        }

        // Check if thermal POS 80mm format requested
        if (resolvedConfig.paperSize == "THERMAL_80MM") {
            return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <style>
                        body {
                            font-family: 'Courier New', Courier, monospace, sans-serif;
                            font-size: 12px;
                            line-height: 1.35;
                            color: #000000;
                            max-width: 320px;
                            margin: 0 auto;
                            padding: 8px;
                            background: #ffffff;
                        }
                        .thermal-center { text-align: center; }
                        .thermal-bold { font-weight: bold; }
                        .thermal-title { font-size: 16px; font-weight: bold; margin: 4px 0; }
                        .thermal-subtitle { font-size: 12px; font-weight: bold; border: 1px dashed #000; padding: 3px; display: inline-block; margin: 6px 0; }
                        .dashed-divider { border-top: 1px dashed #000000; margin: 6px 0; }
                        .thermal-row { display: flex; justify-content: space-between; margin: 2px 0; font-size: 12px; }
                        .thermal-total { font-size: 14px; font-weight: bold; }
                    </style>
                </head>
                <body>
                    <div class="thermal-center">
                        <div class="thermal-title">$ispName</div>
                        <div>$address</div>
                        <div>${if (isBn) "হটলাইন:" else "Tel:"} $hotline</div>
                        <div class="thermal-subtitle">$receiptTitle</div>
                    </div>
                    <div class="dashed-divider"></div>
                    <div class="thermal-row"><span>${if (isBn) "রশিদ নং:" else "Receipt #:"}</span><span class="thermal-bold">$receiptNo</span></div>
                    <div class="thermal-row"><span>${if (isBn) "তারিখ:" else "Date:"}</span><span>${payment.paymentDate}</span></div>
                    <div class="thermal-row"><span>${if (isBn) "ইনভয়েস নং:" else "Inv #:"}</span><span>$invNo</span></div>
                    <div class="thermal-row"><span>${if (isBn) "মাস:" else "Month:"}</span><span>$billMonth</span></div>
                    <div class="dashed-divider"></div>
                    <div class="thermal-row"><span>${if (isBn) "গ্রাহক:" else "Customer:"}</span><span class="thermal-bold">$custName</span></div>
                    <div class="thermal-row"><span>${if (isBn) "আইডি:" else "ID:"}</span><span>$custCode</span></div>
                    ${if (resolvedConfig.showCustomerPhone) """<div class="thermal-row"><span>${if (isBn) "মোবাইল:" else "Phone:"}</span><span>$custPhone</span></div>""" else ""}
                    ${if (resolvedConfig.showCustomerPppoe) """<div class="thermal-row"><span>${if (isBn) "ইউজার:" else "PPPoE:"}</span><span>$pppoeUser</span></div>""" else ""}
                    ${if (resolvedConfig.showPackageName) """<div class="thermal-row"><span>${if (isBn) "প্যাকেজ:" else "Package:"}</span><span>$packageName</span></div>""" else ""}
                    ${if (resolvedConfig.showCustomerAddress) """<div class="thermal-row"><span>${if (isBn) "ঠিকানা:" else "Address:"}</span><span>$custAddress</span></div>""" else ""}
                    <div class="dashed-divider"></div>
                    <div class="thermal-row"><span>${if (isBn) "মোট বিল:" else "Total Bill:"}</span><span>$currency $billAmt</span></div>
                    <div class="thermal-row thermal-total"><span>${if (isBn) "পরিশোধিত:" else "PAID:"}</span><span>$currency $paidAmt</span></div>
                    ${if (resolvedConfig.showRemainingDue) """<div class="thermal-row"><span>${if (isBn) "বকেয়া:" else "Due:"}</span><span>$currency $dueAmt</span></div>""" else ""}
                    ${if (resolvedConfig.showPaymentMethod) """<div class="thermal-row"><span>${if (isBn) "মাধ্যম:" else "Method:"}</span><span>${payment.paymentMethod}</span></div>""" else ""}
                    <div class="thermal-center thermal-bold" style="margin-top: 6px;">*** ${if (isBn) "পরিশোধ সম্পন্ন (PAID)" else "PAID FULLY"} ***</div>
                    <div class="dashed-divider"></div>
                    ${if (resolvedConfig.customNotes.isNotBlank()) """<div class="thermal-center" style="font-size: 10px; margin: 4px 0;">${resolvedConfig.customNotes}</div>""" else ""}
                    <div class="thermal-center" style="font-size: 11px; margin-top: 6px;">$footerText</div>
                </body>
                </html>
            """.trimIndent()
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        padding: 24px;
                        color: #1f2937;
                        max-width: 650px;
                        margin: 0 auto;
                        background: #ffffff;
                    }
                    .header {
                        text-align: center;
                        border-bottom: 2px solid #2563eb;
                        padding-bottom: 12px;
                        margin-bottom: 16px;
                    }
                    .company-name {
                        font-size: 26px;
                        font-weight: 800;
                        color: #1e3a8a;
                        margin: 0;
                        text-transform: uppercase;
                    }
                    .company-info {
                        font-size: 13px;
                        color: #4b5563;
                        margin-top: 4px;
                    }
                    .badge-container {
                        text-align: center;
                        margin: 14px 0;
                    }
                    .title-badge {
                        display: inline-block;
                        background: #2563eb;
                        color: #ffffff;
                        padding: 6px 18px;
                        font-size: 13px;
                        font-weight: 700;
                        border-radius: 20px;
                    }
                    .section-title {
                        font-size: 13px;
                        font-weight: 700;
                        color: #1e40af;
                        border-bottom: 1px solid #e5e7eb;
                        padding-bottom: 4px;
                        margin: 16px 0 8px 0;
                        text-transform: uppercase;
                    }
                    .grid-table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-bottom: 12px;
                    }
                    .grid-table td {
                        padding: 5px 2px;
                        font-size: 13px;
                    }
                    .label {
                        font-weight: 600;
                        color: #4b5563;
                        width: 42%;
                    }
                    .val {
                        color: #111827;
                        font-weight: 500;
                    }
                    .payment-box {
                        border: 1px solid #cbd5e1;
                        border-radius: 8px;
                        padding: 14px;
                        background: #f8fafc;
                        margin-top: 14px;
                        margin-bottom: 16px;
                    }
                    .amount-row {
                        display: flex;
                        justify-content: space-between;
                        font-size: 14px;
                        padding: 5px 0;
                    }
                    .amount-paid {
                        font-size: 17px;
                        font-weight: 800;
                        color: #16a34a;
                    }
                    .status-paid {
                        display: inline-block;
                        background: #dcfce7;
                        color: #15803d;
                        padding: 2px 10px;
                        border-radius: 4px;
                        font-size: 12px;
                        font-weight: 700;
                    }
                    .footer {
                        text-align: center;
                        font-size: 12px;
                        color: #6b7280;
                        border-top: 1px dashed #cbd5e1;
                        padding-top: 12px;
                        margin-top: 24px;
                    }
                    @media print {
                        body { padding: 0; }
                    }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1 class="company-name">$ispName</h1>
                    <div class="company-info">$address</div>
                    <div class="company-info">${if (isBn) "হটলাইন/মোবাইল:" else "Hotline:"} <strong>$hotline</strong></div>
                </div>

                <div class="badge-container">
                    <div class="title-badge">$receiptTitle</div>
                </div>

                <div class="section-title">${if (isBn) "গ্রাহকের তথ্য (CUSTOMER DETAILS)" else "CUSTOMER DETAILS"}</div>
                <table class="grid-table">
                    <tr>
                        <td class="label">${if (isBn) "গ্রাহকের নাম (Customer Name):" else "Customer Name:"}</td>
                        <td class="val"><strong>$custName</strong> ($custCode)</td>
                    </tr>
                    ${if (resolvedConfig.showCustomerPhone) """<tr><td class="label">${if (isBn) "মোবাইল (Phone):" else "Phone Number:"}</td><td class="val">$custPhone</td></tr>""" else ""}
                    ${if (resolvedConfig.showCustomerPppoe) """<tr><td class="label">${if (isBn) "ইউজারনেম (Username):" else "PPPoE Username:"}</td><td class="val">$pppoeUser</td></tr>""" else ""}
                    ${if (resolvedConfig.showPackageName) """<tr><td class="label">${if (isBn) "প্যাকেজ (Package):" else "Package Name:"}</td><td class="val">$packageName</td></tr>""" else ""}
                    ${if (resolvedConfig.showCustomerAddress) """<tr><td class="label">${if (isBn) "ঠিকানা (Address):" else "Address:"}</td><td class="val">$custAddress</td></tr>""" else ""}
                </table>

                <div class="section-title">${if (isBn) "পেমেন্ট ও বিল তথ্য (PAYMENT & BILL DETAILS)" else "PAYMENT & BILL DETAILS"}</div>
                <table class="grid-table">
                    <tr>
                        <td class="label">${if (isBn) "রশিদ নং (Receipt No):" else "Receipt No:"}</td>
                        <td class="val"><strong style="color:#1e3a8a;">$receiptNo</strong></td>
                    </tr>
                    <tr>
                        <td class="label">${if (isBn) "ইনভয়েস নং (Invoice No):" else "Invoice No:"}</td>
                        <td class="val">$invNo</td>
                    </tr>
                    <tr>
                        <td class="label">${if (isBn) "পেমেন্টের তারিখ (Date):" else "Payment Date:"}</td>
                        <td class="val">${payment.paymentDate}</td>
                    </tr>
                    <tr>
                        <td class="label">${if (isBn) "বিলিং মাস (Bill Month):" else "Billing Month:"}</td>
                        <td class="val">$billMonth</td>
                    </tr>
                </table>

                <div class="payment-box">
                    <div class="amount-row">
                        <span>${if (isBn) "মোট বিল পরিমাণ (Total Bill):" else "Total Bill Amount:"}</span>
                        <span>$currency $billAmt</span>
                    </div>
                    <div class="amount-row">
                        <span>${if (isBn) "পরিশোধিত পরিমাণ (Paid Amount):" else "Paid Amount:"}</span>
                        <span class="amount-paid">$currency $paidAmt</span>
                    </div>
                    ${if (resolvedConfig.showRemainingDue) """
                    <div class="amount-row">
                        <span>${if (isBn) "অবশিষ্ট বকেয়া (Remaining Due):" else "Remaining Due:"}</span>
                        <span>$currency $dueAmt</span>
                    </div>
                    """ else ""}
                    ${if (resolvedConfig.showPaymentMethod) """
                    <div class="amount-row" style="margin-top: 8px; border-top: 1px dashed #cbd5e1; padding-top: 8px;">
                        <span>${if (isBn) "পেমেন্ট মাধ্যম (Method):" else "Payment Method:"}</span>
                        <span><strong>${payment.paymentMethod}</strong></span>
                    </div>
                    """ else ""}
                    <div class="amount-row" style="${if (!resolvedConfig.showPaymentMethod) "margin-top: 8px; border-top: 1px dashed #cbd5e1; padding-top: 8px;" else ""}">
                        <span>${if (isBn) "পেমেন্ট স্ট্যাটাস (Status):" else "Payment Status:"}</span>
                        <span class="status-paid">${if (isBn) "PAID (পরিশোধিত)" else "PAID"}</span>
                    </div>
                </div>

                ${if (resolvedConfig.customNotes.isNotBlank()) """
                <div style="background: #f1f5f9; border-radius: 6px; padding: 8px 12px; margin-top: 12px; font-size: 11px; color: #475569; text-align: center;">
                    ${resolvedConfig.customNotes}
                </div>
                """ else ""}

                <div class="footer">
                    <p><strong>$footerText</strong></p>
                    <p style="font-size:10px; font-style:italic;">This is a computer-generated digital receipt issued by $ispName.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
