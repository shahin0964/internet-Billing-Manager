package com.example.util

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
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
     * Generates a native PDF document for the payment receipt.
     */
    fun generateReceiptPdfFile(
        context: Context,
        payment: PaymentEntity,
        bill: BillEntity?,
        customer: CustomerEntity?,
        settings: BusinessSettingsEntity,
        isBn: Boolean = true
    ): File {
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
        val badgeWidth = 260f
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
            if (isBn) "পেমেন্ট রশিদ (PAYMENT RECEIPT)" else "OFFICIAL PAYMENT RECEIPT",
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

        val custBoxTop = currentY
        val custBoxHeight = 115f
        val custBoxRect = RectF(leftMargin, custBoxTop, rightMargin, custBoxTop + custBoxHeight)
        paint.color = AndroidColor.parseColor("#F8FAFC")
        canvas.drawRoundRect(custBoxRect, 8f, 8f, paint)
        paint.color = AndroidColor.parseColor("#E2E8F0")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(custBoxRect, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        var rowY = custBoxTop + 20f
        drawInfoRow(canvas, paint, if (isBn) "গ্রাহকের নাম:" else "Customer Name:", "$custName ($custCode)", leftMargin + 14f, rowY, contentWidth - 28f, true)
        rowY += 18f
        drawInfoRow(canvas, paint, if (isBn) "মোবাইল নম্বর:" else "Phone Number:", custPhone, leftMargin + 14f, rowY, contentWidth - 28f)
        rowY += 18f
        drawInfoRow(canvas, paint, if (isBn) "ইউজারনেম:" else "PPPoE Username:", pppoeUser, leftMargin + 14f, rowY, contentWidth - 28f)
        rowY += 18f
        drawInfoRow(canvas, paint, if (isBn) "প্যাকেজ:" else "Package:", packageName, leftMargin + 14f, rowY, contentWidth - 28f)
        rowY += 18f
        drawInfoRow(canvas, paint, if (isBn) "ঠিকানা:" else "Address:", custAddress, leftMargin + 14f, rowY, contentWidth - 28f)

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
        val sumBoxTop = currentY
        val sumBoxHeight = 135f
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
        rowY += 22f
        drawAmountRow(canvas, paint, if (isBn) "অবশিষ্ট বকেয়া (Remaining Due):" else "Remaining Due:", "$currency $dueAmt", leftMargin + 18f, rowY, contentWidth - 36f, false, if ((bill?.dueAmount ?: 0.0) > 0) AndroidColor.parseColor("#DC2626") else AndroidColor.parseColor("#4B5563"), 13f)

        rowY += 14f
        // Divider line in summary box
        paint.color = AndroidColor.parseColor("#CBD5E1")
        paint.strokeWidth = 1f
        canvas.drawLine(leftMargin + 18f, rowY, rightMargin - 18f, rowY, paint)
        rowY += 18f

        drawAmountRow(canvas, paint, if (isBn) "পেমেন্ট মাধ্যম (Payment Method):" else "Payment Method:", payment.paymentMethod, leftMargin + 18f, rowY, contentWidth - 36f, true, AndroidColor.parseColor("#1E293B"), 12f)

        currentY = sumBoxTop + sumBoxHeight + 35f

        // 7. Status Stamp / Pill
        val statusText = if (isBn) "✓ PAID (পরিশোধিত)" else "✓ PAID (RECEIVED)"
        paint.color = AndroidColor.parseColor("#16A34A")
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(statusText, pageWidth / 2f, currentY, paint)
        currentY += 40f

        // 8. Footer & Thank you Note
        paint.color = AndroidColor.parseColor("#CBD5E1")
        paint.strokeWidth = 1f
        canvas.drawLine(leftMargin, currentY, rightMargin, currentY, paint)
        currentY += 20f

        paint.color = AndroidColor.parseColor("#374151")
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(if (isBn) "আমাদের ইন্টারনেট সেবা ব্যবহার করার জন্য আপনাকে ধন্যবাদ!" else "Thank you for using our internet service!", pageWidth / 2f, currentY, paint)
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
     * Shares the generated PDF file.
     */
    fun sharePdfFile(context: Context, pdfFile: File, isBn: Boolean = true) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, pdfFile.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, if (isBn) "রশিদ শেয়ার করুন" else "Share Receipt PDF")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "শেয়ার করতে ব্যর্থ হয়েছে: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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
        isBn: Boolean = true
    ): String {
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
                    <div class="title-badge">${if (isBn) "পেমেন্ট রশিদ (OFFICIAL PAYMENT RECEIPT)" else "OFFICIAL PAYMENT RECEIPT"}</div>
                </div>

                <div class="section-title">${if (isBn) "গ্রাহকের তথ্য (CUSTOMER DETAILS)" else "CUSTOMER DETAILS"}</div>
                <table class="grid-table">
                    <tr>
                        <td class="label">${if (isBn) "গ্রাহকের নাম (Customer Name):" else "Customer Name:"}</td>
                        <td class="val"><strong>$custName</strong> ($custCode)</td>
                    </tr>
                    <tr>
                        <td class="label">${if (isBn) "মোবাইল (Phone):" else "Phone Number:"}</td>
                        <td class="val">$custPhone</td>
                    </tr>
                    <tr>
                        <td class="label">${if (isBn) "ইউজারনেম (Username):" else "PPPoE Username:"}</td>
                        <td class="val">$pppoeUser</td>
                    </tr>
                    <tr>
                        <td class="label">${if (isBn) "প্যাকেজ (Package):" else "Package Name:"}</td>
                        <td class="val">$packageName</td>
                    </tr>
                    <tr>
                        <td class="label">${if (isBn) "ঠিকানা (Address):" else "Address:"}</td>
                        <td class="val">$custAddress</td>
                    </tr>
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
                    <div class="amount-row">
                        <span>${if (isBn) "অবশিষ্ট বকেয়া (Remaining Due):" else "Remaining Due:"}</span>
                        <span>$currency $dueAmt</span>
                    </div>
                    <div class="amount-row" style="margin-top: 8px; border-top: 1px dashed #cbd5e1; padding-top: 8px;">
                        <span>${if (isBn) "পেমেন্ট মাধ্যম (Method):" else "Payment Method:"}</span>
                        <span><strong>${payment.paymentMethod}</strong></span>
                    </div>
                    <div class="amount-row">
                        <span>${if (isBn) "পেমেন্ট স্ট্যাটাস (Status):" else "Payment Status:"}</span>
                        <span class="status-paid">${if (isBn) "PAID (পরিশোধিত)" else "PAID"}</span>
                    </div>
                </div>

                <div class="footer">
                    <p><strong>${if (isBn) "আমাদের ইন্টারনেট সেবা ব্যবহার করার জন্য আপনাকে ধন্যবাদ!" else "Thank you for using our internet service!"}</strong></p>
                    <p style="font-size:10px; font-style:italic;">This is a computer-generated digital receipt issued by $ispName.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
