package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.BillEntity
import com.example.data.model.CustomerEntity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportedCustomerRow(
    val serialNo: Int,
    val customerId: Long,
    val name: String,
    val customerCode: String,
    val phone: String,
    val address: String,
    val packageName: String,
    val monthlyBill: Double,
    val dueAmount: Double,
    val pppoeUsername: String,
    val password: String,
    val ipAddress: String,
    val status: String,
    val joiningDate: String,
    val notes: String
)

enum class CustomerSortOption(val titleBn: String, val titleEn: String) {
    A_TO_Z("নামানুসারে (A - Z)", "Name (A - Z)"),
    Z_TO_A("নামানুসারে (Z - A)", "Name (Z - A)"),
    CUSTOMER_CODE("গ্রাহক আইডি (Code)", "Customer Code"),
    MONTHLY_BILL_DESC("মাসিক বিল (বেশি থেকে কম)", "Bill (High to Low)"),
    MONTHLY_BILL_ASC("মাসিক বিল (কম থেকে বেশি)", "Bill (Low to High)")
}

enum class CustomerFilterStatus(val titleBn: String, val titleEn: String) {
    ALL("সকল গ্রাহক", "All Customers"),
    ACTIVE("শুধু সক্রিয় (Active)", "Active Only"),
    INACTIVE("শুধু নিষ্ক্রিয় (Inactive/Suspended)", "Inactive Only")
}

enum class ExportCustomerField(
    val titleBn: String,
    val titleEn: String,
    val isDefaultSelected: Boolean = true
) {
    SERIAL("SL", "SL", true),
    PPPOE_USERNAME("PPPoE Username", "PPPoE Username", true),
    PASSWORD("PPPoE password", "PPPoE password", true),
    PHONE("Number", "Number", true),
    MONTHLY_BILL("Bill", "Bill", true),
    NAME("গ্রাহকের নাম (Name)", "Name", false),
    CUSTOMER_CODE("গ্রাহক আইডি (ID)", "Customer ID", false),
    PACKAGE("প্যাকেজ (Package)", "Package", false),
    DUE_AMOUNT("বকেয়া (Due)", "Due Amount", false),
    STATUS("স্ট্যাটাস (Status)", "Status", false),
    ADDRESS("ঠিকানা (Address)", "Address", false),
    IP_ADDRESS("আইপি (IP Address)", "IP Address", false),
    JOINING_DATE("যোগদানের তারিখ", "Joining Date", false),
    NOTES("নোট (Notes)", "Notes", false)
}

enum class ExportFormat(val titleBn: String, val titleEn: String) {
    EXCEL("এক্সেল ফাইল (.CSV)", "Excel (.CSV)"),
    PDF("পিডিএফ ডকুমেন্ট (.PDF)", "PDF Document (.PDF)"),
    JPG("জেপিজি ছবি (.JPG)", "JPG Image (.JPG)")
}

object CustomerExportHelper {

    /**
     * Extracts password from notes if recorded by ISP staff (e.g. "pass: 1234", "password: abc", "পাসওয়ার্ড: ...")
     */
    fun extractPassword(customer: CustomerEntity): String {
        val notes = customer.notes.trim()
        if (notes.isBlank()) return ""

        val regexes = listOf(
            Regex("""(?i)(?:pppoe\s*pass(?:word)?|pass(?:word)?|pw|পাসওয়ার্ড)\s*[:=\-]\s*([^\s,;\n]+)"""),
            Regex("""(?i)\bpass(?:word)?\b\s*[:=\-]?\s*([A-Za-z0-9@#\$\*\!\_\-\.]+)""")
        )

        for (regex in regexes) {
            val match = regex.find(notes)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1].trim()
            }
        }

        if (!notes.contains(" ") && !notes.contains("\n") && notes.length in 3..20) {
            return notes
        }
        return ""
    }

    /**
     * Prepares sorted & filtered rows with serial numbers starting from 1 to N.
     */
    fun prepareExportData(
        customers: List<CustomerEntity>,
        bills: List<BillEntity>,
        sortOption: CustomerSortOption = CustomerSortOption.A_TO_Z,
        filterStatus: CustomerFilterStatus = CustomerFilterStatus.ALL,
        searchQuery: String = ""
    ): List<ExportedCustomerRow> {
        // Calculate due for each customer
        val dueByCustomerId = bills
            .filter { it.status != "PAID" }
            .groupBy { it.customerId }
            .mapValues { entry -> entry.value.sumOf { it.dueAmount } }

        var filtered = customers.filter { cust ->
            when (filterStatus) {
                CustomerFilterStatus.ALL -> true
                CustomerFilterStatus.ACTIVE -> cust.status.equals("ACTIVE", ignoreCase = true)
                CustomerFilterStatus.INACTIVE -> !cust.status.equals("ACTIVE", ignoreCase = true)
            }
        }

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase(Locale.getDefault())
            filtered = filtered.filter {
                it.name.lowercase(Locale.getDefault()).contains(q) ||
                it.customerCode.lowercase(Locale.getDefault()).contains(q) ||
                it.pppoeUsername.lowercase(Locale.getDefault()).contains(q) ||
                it.phone.contains(q) ||
                it.packageName.lowercase(Locale.getDefault()).contains(q)
            }
        }

        val sorted = when (sortOption) {
            CustomerSortOption.A_TO_Z -> filtered.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.trim() }
            )
            CustomerSortOption.Z_TO_A -> filtered.sortedWith(
                compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name.trim() }
            )
            CustomerSortOption.CUSTOMER_CODE -> filtered.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.customerCode.trim() }
            )
            CustomerSortOption.MONTHLY_BILL_DESC -> filtered.sortedByDescending { it.monthlyFee }
            CustomerSortOption.MONTHLY_BILL_ASC -> filtered.sortedBy { it.monthlyFee }
        }

        return sorted.mapIndexed { index, cust ->
            ExportedCustomerRow(
                serialNo = index + 1,
                customerId = cust.id,
                name = cust.name.trim(),
                customerCode = cust.customerCode.trim(),
                phone = cust.phone.trim(),
                address = cust.address.trim(),
                packageName = cust.packageName.trim(),
                monthlyBill = cust.monthlyFee,
                dueAmount = dueByCustomerId[cust.id] ?: 0.0,
                pppoeUsername = cust.pppoeUsername.trim(),
                password = extractPassword(cust),
                ipAddress = cust.ipAddress.trim(),
                status = cust.status.trim(),
                joiningDate = cust.joiningDate.trim(),
                notes = cust.notes.trim()
            )
        }
    }

    /**
     * Generates a CSV File with UTF-8 BOM so Microsoft Excel & Google Sheets display Bangla & English flawlessly.
     */
    fun generateCsvFile(
        context: Context,
        rows: List<ExportedCustomerRow>,
        selectedFields: Set<ExportCustomerField> = ExportCustomerField.values().filter { it.isDefaultSelected }.toSet(),
        isBn: Boolean = true
    ): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Customer_Export_${timeStamp}.csv"
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, fileName)

        val effectiveFields = if (selectedFields.isEmpty()) setOf(ExportCustomerField.NAME) else selectedFields
        val activeFieldsOrdered = ExportCustomerField.values().filter { it in effectiveFields }

        FileOutputStream(file).use { fos ->
            // Write UTF-8 BOM so Excel recognises unicode characters correctly
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                // Header row
                val headers = activeFieldsOrdered.map { field ->
                    if (isBn) field.titleBn else field.titleEn
                }
                writer.write(headers.joinToString(",") { escapeCsvCell(it) })
                writer.write("\r\n")

                // Rows
                rows.forEach { row ->
                    val cells = activeFieldsOrdered.map { field ->
                        when (field) {
                            ExportCustomerField.SERIAL -> row.serialNo.toString()
                            ExportCustomerField.NAME -> row.name
                            ExportCustomerField.CUSTOMER_CODE -> row.customerCode
                            ExportCustomerField.PHONE -> row.phone
                            ExportCustomerField.PACKAGE -> row.packageName
                            ExportCustomerField.MONTHLY_BILL -> String.format(Locale.US, "%.2f", row.monthlyBill)
                            ExportCustomerField.DUE_AMOUNT -> String.format(Locale.US, "%.2f", row.dueAmount)
                            ExportCustomerField.PPPOE_USERNAME -> row.pppoeUsername
                            ExportCustomerField.PASSWORD -> row.password
                            ExportCustomerField.ADDRESS -> row.address
                            ExportCustomerField.STATUS -> row.status
                            ExportCustomerField.IP_ADDRESS -> row.ipAddress
                            ExportCustomerField.JOINING_DATE -> row.joiningDate
                            ExportCustomerField.NOTES -> row.notes
                        }
                    }
                    writer.write(cells.joinToString(",") { escapeCsvCell(it) })
                    writer.write("\r\n")
                }
                writer.flush()
            }
        }
        return file
    }

    private fun escapeCsvCell(value: String): String {
        var str = value.replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
        if (str.contains(",") || str.contains("\"") || str.contains(";")) {
            str = str.replace("\"", "\"\"")
            return "\"$str\""
        }
        return str
    }

    /**
     * Share exported CSV file via Android Share sheet.
     */
    fun shareCsvFile(context: Context, file: File) {
        try {
            val authority = "${context.packageName}.provider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Customer List Export")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "গ্রাহক তালিকা এক্সপোর্ট শেয়ার করুন"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "শেয়ার ব্যর্থ হয়েছে: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Share exported PDF file via Android Share sheet.
     */
    fun sharePdfFile(context: Context, file: File) {
        try {
            val authority = "${context.packageName}.provider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_SUBJECT, "Customer List PDF Export")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "গ্রাহক তালিকা PDF শেয়ার করুন"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "শেয়ার ব্যর্থ হয়েছে: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Share exported JPG file via Android Share sheet.
     */
    fun shareJpgFile(context: Context, file: File) {
        try {
            val authority = "${context.packageName}.provider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_SUBJECT, "Customer List Image Export")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "গ্রাহক তালিকা JPG ছবি শেয়ার করুন"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "শেয়ার ব্যর্থ হয়েছে: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Generates a high-resolution JPG image file of exported customer rows.
     */
    fun generateJpgFile(
        context: Context,
        rows: List<ExportedCustomerRow>,
        ispName: String,
        selectedFields: Set<ExportCustomerField> = ExportCustomerField.values().filter { it.isDefaultSelected }.toSet(),
        currencySymbol: String = "৳",
        isBn: Boolean = true
    ): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "customer_list_${timeStamp}.jpg"
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outputFile = File(exportDir, fileName)

        val effectiveFields = if (selectedFields.isEmpty()) {
            ExportCustomerField.values().filter { it.isDefaultSelected }.toSet()
        } else {
            selectedFields
        }
        val activeFieldsOrdered = ExportCustomerField.values().filter { it in effectiveFields }

        // Width: 1200px (crisp, readable on mobile & desktop)
        val imageWidth = 1200
        val leftMargin = 40f
        val rightMargin = 1160f
        val contentWidth = rightMargin - leftMargin
        val headerHeight = 110f
        val tableHeaderHeight = 44f
        val rowHeight = 36f
        val footerHeight = 45f

        val totalHeight = (headerHeight + tableHeaderHeight + (rows.size * rowHeight) + footerHeight).toInt()
        val bitmapHeight = totalHeight.coerceAtLeast(400)

        val bitmap = Bitmap.createBitmap(imageWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun getFieldWeight(field: ExportCustomerField): Float = when (field) {
            ExportCustomerField.SERIAL -> 0.8f
            ExportCustomerField.PPPOE_USERNAME -> 2.4f
            ExportCustomerField.PASSWORD -> 1.8f
            ExportCustomerField.PHONE -> 2.2f
            ExportCustomerField.MONTHLY_BILL -> 1.5f
            ExportCustomerField.NAME -> 2.5f
            ExportCustomerField.CUSTOMER_CODE -> 1.8f
            ExportCustomerField.PACKAGE -> 1.8f
            ExportCustomerField.DUE_AMOUNT -> 1.5f
            ExportCustomerField.STATUS -> 1.3f
            ExportCustomerField.ADDRESS -> 2.8f
            ExportCustomerField.IP_ADDRESS -> 2.0f
            ExportCustomerField.JOINING_DATE -> 1.8f
            ExportCustomerField.NOTES -> 2.2f
        }

        val totalWeight = activeFieldsOrdered.sumOf { getFieldWeight(it).toDouble() }.toFloat()
        val columnWidths = activeFieldsOrdered.map { field ->
            (getFieldWeight(field) / totalWeight) * contentWidth
        }

        var y = 28f

        // Top accent bar
        paint.color = AndroidColor.parseColor("#0891B2")
        canvas.drawRect(leftMargin, y, rightMargin, y + 6f, paint)
        y += 32f

        // ISP Name
        val company = ispName.ifBlank { if (isBn) "আইএসপি ডিজিটাল নেটওয়ার্ক" else "ISP Digital Network" }
        paint.color = AndroidColor.parseColor("#0F172A")
        paint.textSize = 28f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(company, leftMargin, y, paint)

        // Date
        val printDate = SimpleDateFormat("dd-MM-yyyy, hh:mm a", Locale.getDefault()).format(Date())
        paint.color = AndroidColor.parseColor("#64748B")
        paint.textSize = 17f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(printDate, rightMargin, y, paint)
        y += 28f

        // Subtitle
        val title = if (isBn) "গ্রাহক তালিকা রিপোর্ট" else "Customer List Report"
        paint.color = AndroidColor.parseColor("#334155")
        paint.textSize = 19f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("$title • মোট: ${rows.size} জন", leftMargin, y, paint)
        y += 22f

        // Table Header Bar
        paint.color = AndroidColor.parseColor("#0E7490")
        canvas.drawRect(leftMargin, y, rightMargin, y + tableHeaderHeight, paint)

        paint.color = AndroidColor.WHITE
        paint.textSize = 16.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        var colX = leftMargin
        activeFieldsOrdered.forEachIndexed { i, field ->
            val colW = columnWidths[i]
            val headerTitle = if (isBn) field.titleBn else field.titleEn
            val textX = when (field) {
                ExportCustomerField.SERIAL -> {
                    paint.textAlign = Paint.Align.CENTER
                    colX + colW / 2f
                }
                ExportCustomerField.MONTHLY_BILL, ExportCustomerField.DUE_AMOUNT -> {
                    paint.textAlign = Paint.Align.RIGHT
                    colX + colW - 8f
                }
                else -> {
                    paint.textAlign = Paint.Align.LEFT
                    colX + 8f
                }
            }
            canvas.drawText(headerTitle, textX, y + 28f, paint)
            colX += colW
        }

        y += tableHeaderHeight

        // Data Rows
        rows.forEachIndexed { rowIndex, row ->
            // Alternating zebra row
            if (rowIndex % 2 == 1) {
                paint.color = AndroidColor.parseColor("#F8FAFC")
                canvas.drawRect(leftMargin, y, rightMargin, y + rowHeight, paint)
            }

            // Bottom border line
            paint.color = AndroidColor.parseColor("#E2E8F0")
            paint.strokeWidth = 1f
            canvas.drawLine(leftMargin, y + rowHeight, rightMargin, y + rowHeight, paint)

            // Cell contents
            paint.color = AndroidColor.parseColor("#1E293B")
            paint.textSize = 15.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

            colX = leftMargin
            activeFieldsOrdered.forEachIndexed { i, field ->
                val colW = columnWidths[i]
                val textValue = when (field) {
                    ExportCustomerField.SERIAL -> row.serialNo.toString()
                    ExportCustomerField.PPPOE_USERNAME -> row.pppoeUsername
                    ExportCustomerField.PASSWORD -> row.password
                    ExportCustomerField.PHONE -> row.phone
                    ExportCustomerField.MONTHLY_BILL -> String.format(Locale.US, "%.0f", row.monthlyBill)
                    ExportCustomerField.NAME -> row.name
                    ExportCustomerField.CUSTOMER_CODE -> row.customerCode
                    ExportCustomerField.PACKAGE -> row.packageName
                    ExportCustomerField.DUE_AMOUNT -> String.format(Locale.US, "%.0f", row.dueAmount)
                    ExportCustomerField.STATUS -> row.status
                    ExportCustomerField.ADDRESS -> row.address
                    ExportCustomerField.IP_ADDRESS -> row.ipAddress
                    ExportCustomerField.JOINING_DATE -> row.joiningDate
                    ExportCustomerField.NOTES -> row.notes
                }

                // Ellipsize text to fit column width
                val maxTextWidth = colW - 16f
                var fitText = textValue
                if (paint.measureText(fitText) > maxTextWidth && maxTextWidth > 16f) {
                    while (fitText.isNotEmpty() && paint.measureText("$fitText...") > maxTextWidth) {
                        fitText = fitText.dropLast(1)
                    }
                    fitText = "$fitText..."
                }

                when (field) {
                    ExportCustomerField.SERIAL -> {
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText(fitText, colX + colW / 2f, y + 24f, paint)
                    }
                    ExportCustomerField.MONTHLY_BILL, ExportCustomerField.DUE_AMOUNT -> {
                        paint.textAlign = Paint.Align.RIGHT
                        canvas.drawText(fitText, colX + colW - 8f, y + 24f, paint)
                    }
                    else -> {
                        paint.textAlign = Paint.Align.LEFT
                        canvas.drawText(fitText, colX + 8f, y + 24f, paint)
                    }
                }
                colX += colW
            }

            y += rowHeight
        }

        // Footer note
        paint.color = AndroidColor.parseColor("#94A3B8")
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Generated by ${ispName.ifBlank { "ISP Digital" }}", imageWidth / 2f, y + 26f, paint)

        var out: FileOutputStream? = null
        try {
            out = FileOutputStream(outputFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { out?.close() } catch (_: Exception) {}
            bitmap.recycle()
        }

        return outputFile
    }

    /**
     * Generates a multi-page PDF document of exported customer rows.
     */
    fun generatePdfFile(
        context: Context,
        rows: List<ExportedCustomerRow>,
        ispName: String,
        selectedFields: Set<ExportCustomerField> = ExportCustomerField.values().filter { it.isDefaultSelected }.toSet(),
        currencySymbol: String = "৳",
        isBn: Boolean = true
    ): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "customer_list_${timeStamp}.pdf"
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outputFile = File(exportDir, fileName)

        val effectiveFields = if (selectedFields.isEmpty()) {
            ExportCustomerField.values().filter { it.isDefaultSelected }.toSet()
        } else {
            selectedFields
        }
        val activeFieldsOrdered = ExportCustomerField.values().filter { it in effectiveFields }

        // A4 dimensions in points: 595 x 842
        val pageWidth = 595
        val pageHeight = 842
        val leftMargin = 30f
        val rightMargin = 565f
        val contentWidth = rightMargin - leftMargin
        val topMargin = 32f
        val bottomMargin = 36f

        val pdfDocument = PdfDocument()

        fun getFieldWeight(field: ExportCustomerField): Float = when (field) {
            ExportCustomerField.SERIAL -> 0.8f
            ExportCustomerField.PPPOE_USERNAME -> 2.4f
            ExportCustomerField.PASSWORD -> 1.8f
            ExportCustomerField.PHONE -> 2.2f
            ExportCustomerField.MONTHLY_BILL -> 1.5f
            ExportCustomerField.NAME -> 2.5f
            ExportCustomerField.CUSTOMER_CODE -> 1.8f
            ExportCustomerField.PACKAGE -> 1.8f
            ExportCustomerField.DUE_AMOUNT -> 1.5f
            ExportCustomerField.STATUS -> 1.3f
            ExportCustomerField.ADDRESS -> 2.8f
            ExportCustomerField.IP_ADDRESS -> 2.0f
            ExportCustomerField.JOINING_DATE -> 1.8f
            ExportCustomerField.NOTES -> 2.2f
        }

        val totalWeight = activeFieldsOrdered.sumOf { getFieldWeight(it).toDouble() }.toFloat()
        val columnWidths = activeFieldsOrdered.map { field ->
            (getFieldWeight(field) / totalWeight) * contentWidth
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        fun drawHeader(isFirstPage: Boolean): Float {
            canvas.drawColor(AndroidColor.WHITE)
            var y = topMargin

            if (isFirstPage) {
                // Top accent bar
                paint.color = AndroidColor.parseColor("#0891B2")
                canvas.drawRect(leftMargin, y, rightMargin, y + 3f, paint)
                y += 18f

                // ISP Name
                val company = ispName.ifBlank { if (isBn) "আইএসপি ডিজিটাল নেটওয়ার্ক" else "ISP Digital Network" }
                paint.color = AndroidColor.parseColor("#0F172A")
                paint.textSize = 15f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(company, leftMargin, y, paint)

                // Date
                val printDate = SimpleDateFormat("dd-MM-yyyy, hh:mm a", Locale.getDefault()).format(Date())
                paint.color = AndroidColor.parseColor("#64748B")
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(printDate, rightMargin, y, paint)
                y += 14f

                // Subtitle
                val title = if (isBn) "গ্রাহক তালিকা রিপোর্ট" else "Customer List Report"
                paint.color = AndroidColor.parseColor("#334155")
                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText("$title • মোট: ${rows.size} জন", leftMargin, y, paint)
                y += 12f
            } else {
                y += 6f
            }

            // Table Header Bar
            val tableHeaderHeight = 22f
            paint.color = AndroidColor.parseColor("#0E7490")
            canvas.drawRect(leftMargin, y, rightMargin, y + tableHeaderHeight, paint)

            paint.color = AndroidColor.WHITE
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            var colX = leftMargin
            activeFieldsOrdered.forEachIndexed { i, field ->
                val colW = columnWidths[i]
                val headerTitle = if (isBn) field.titleBn else field.titleEn
                val textX = when (field) {
                    ExportCustomerField.SERIAL -> {
                        paint.textAlign = Paint.Align.CENTER
                        colX + colW / 2f
                    }
                    ExportCustomerField.MONTHLY_BILL, ExportCustomerField.DUE_AMOUNT -> {
                        paint.textAlign = Paint.Align.RIGHT
                        colX + colW - 4f
                    }
                    else -> {
                        paint.textAlign = Paint.Align.LEFT
                        colX + 4f
                    }
                }
                canvas.drawText(headerTitle, textX, y + 14.5f, paint)
                colX += colW
            }

            return y + tableHeaderHeight
        }

        var currentY = drawHeader(isFirstPage = true)
        val rowHeight = 18f

        rows.forEachIndexed { rowIndex, row ->
            // Check page overflow
            if (currentY + rowHeight > pageHeight - bottomMargin) {
                paint.color = AndroidColor.parseColor("#94A3B8")
                paint.textSize = 7.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("Page $pageNumber", pageWidth / 2f, pageHeight - 14f, paint)

                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                currentY = drawHeader(isFirstPage = false)
            }

            // Alternating zebra row
            if (rowIndex % 2 == 1) {
                paint.color = AndroidColor.parseColor("#F8FAFC")
                canvas.drawRect(leftMargin, currentY, rightMargin, currentY + rowHeight, paint)
            }

            // Bottom border line
            paint.color = AndroidColor.parseColor("#E2E8F0")
            paint.strokeWidth = 0.5f
            canvas.drawLine(leftMargin, currentY + rowHeight, rightMargin, currentY + rowHeight, paint)

            // Cell contents
            paint.color = AndroidColor.parseColor("#1E293B")
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

            var colX = leftMargin
            activeFieldsOrdered.forEachIndexed { i, field ->
                val colW = columnWidths[i]
                val textValue = when (field) {
                    ExportCustomerField.SERIAL -> row.serialNo.toString()
                    ExportCustomerField.PPPOE_USERNAME -> row.pppoeUsername
                    ExportCustomerField.PASSWORD -> row.password
                    ExportCustomerField.PHONE -> row.phone
                    ExportCustomerField.MONTHLY_BILL -> String.format(Locale.US, "%.0f", row.monthlyBill)
                    ExportCustomerField.NAME -> row.name
                    ExportCustomerField.CUSTOMER_CODE -> row.customerCode
                    ExportCustomerField.PACKAGE -> row.packageName
                    ExportCustomerField.DUE_AMOUNT -> String.format(Locale.US, "%.0f", row.dueAmount)
                    ExportCustomerField.STATUS -> row.status
                    ExportCustomerField.ADDRESS -> row.address
                    ExportCustomerField.IP_ADDRESS -> row.ipAddress
                    ExportCustomerField.JOINING_DATE -> row.joiningDate
                    ExportCustomerField.NOTES -> row.notes
                }

                // Ellipsize text to fit column width
                val maxTextWidth = colW - 8f
                var fitText = textValue
                if (paint.measureText(fitText) > maxTextWidth && maxTextWidth > 8f) {
                    while (fitText.isNotEmpty() && paint.measureText("$fitText...") > maxTextWidth) {
                        fitText = fitText.dropLast(1)
                    }
                    fitText = "$fitText..."
                }

                when (field) {
                    ExportCustomerField.SERIAL -> {
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText(fitText, colX + colW / 2f, currentY + 12.5f, paint)
                    }
                    ExportCustomerField.MONTHLY_BILL, ExportCustomerField.DUE_AMOUNT -> {
                        paint.textAlign = Paint.Align.RIGHT
                        canvas.drawText(fitText, colX + colW - 4f, currentY + 12.5f, paint)
                    }
                    else -> {
                        paint.textAlign = Paint.Align.LEFT
                        canvas.drawText(fitText, colX + 4f, currentY + 12.5f, paint)
                    }
                }
                colX += colW
            }

            currentY += rowHeight
        }

        // Footer on last page
        paint.color = AndroidColor.parseColor("#94A3B8")
        paint.textSize = 7.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Page $pageNumber", pageWidth / 2f, pageHeight - 14f, paint)

        pdfDocument.finishPage(page)

        var out: FileOutputStream? = null
        try {
            out = FileOutputStream(outputFile)
            pdfDocument.writeTo(out)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { out?.close() } catch (_: Exception) {}
            pdfDocument.close()
        }

        return outputFile
    }

    /**
     * Saves a copy of CSV, PDF, or JPG to Public Downloads folder.
     */
    fun saveToDownloads(context: Context, sourceFile: File): File? {
        return try {
            val fileNameLower = sourceFile.name.lowercase(Locale.ROOT)
            val mimeType = when {
                fileNameLower.endsWith(".pdf") -> "application/pdf"
                fileNameLower.endsWith(".jpg") || fileNameLower.endsWith(".jpeg") -> "image/jpeg"
                else -> "text/csv"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, sourceFile.name)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(sourceFile).use { input ->
                            input.copyTo(out)
                        }
                    }
                }
            }

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val destFile = File(downloadsDir, sourceFile.name)
            sourceFile.copyTo(destFile, overwrite = true)
            destFile
        } catch (e: Exception) {
            e.printStackTrace()
            // If direct external copy failed, sourceFile itself is valid
            sourceFile
        }
    }

    /**
     * Generates a printable HTML report ready for Android PrintManager / PDF export.
     */
    fun generatePrintableHtml(
        rows: List<ExportedCustomerRow>,
        ispName: String,
        selectedFields: Set<ExportCustomerField> = ExportCustomerField.values().filter { it.isDefaultSelected }.toSet(),
        currencySymbol: String = "৳",
        isBn: Boolean = true
    ): String {
        val printDate = SimpleDateFormat("dd MMMM, yyyy - hh:mm a", Locale.getDefault()).format(Date())
        val totalCount = rows.size
        val totalMonthlyBill = rows.sumOf { it.monthlyBill }
        val totalDue = rows.sumOf { it.dueAmount }

        val title = if (isBn) "গ্রাহক তালিকা রিপোর্ট (A - Z ক্রমানুসারে)" else "Customer List Report (A to Z)"
        val company = ispName.ifBlank { if (isBn) "আইএসপি ডিজিটাল নেটওয়ার্ক" else "ISP Digital Network" }

        val effectiveFields = if (selectedFields.isEmpty()) setOf(ExportCustomerField.NAME) else selectedFields
        val activeFieldsOrdered = ExportCustomerField.values().filter { it in effectiveFields }

        val sb = StringBuilder()
        sb.append("""
            <!DOCTYPE html>
            <html lang="${if (isBn) "bn" else "en"}">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$title</title>
                <style>
                    @page {
                        size: A4 landscape;
                        margin: 10mm;
                    }
                    body {
                        font-family: 'Segoe UI', Arial, 'SolaimanLipi', sans-serif;
                        color: #1e293b;
                        margin: 0;
                        padding: 12px;
                        font-size: 11px;
                        background: #ffffff;
                    }
                    .header-container {
                        text-align: center;
                        border-bottom: 2px solid #2563eb;
                        padding-bottom: 8px;
                        margin-bottom: 12px;
                    }
                    .company-name {
                        font-size: 20px;
                        font-weight: bold;
                        color: #1e3a8a;
                        margin-bottom: 2px;
                    }
                    .report-title {
                        font-size: 14px;
                        font-weight: 600;
                        color: #2563eb;
                    }
                    .meta-row {
                        display: flex;
                        justify-content: space-between;
                        font-size: 11px;
                        color: #64748b;
                        margin-top: 6px;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-top: 8px;
                    }
                    th {
                        background-color: #2563eb;
                        color: #ffffff;
                        font-weight: 600;
                        text-align: left;
                        padding: 6px 8px;
                        font-size: 10px;
                        border: 1px solid #1d4ed8;
                    }
                    td {
                        padding: 5px 8px;
                        border: 1px solid #e2e8f0;
                        font-size: 10px;
                        vertical-align: middle;
                    }
                    tr:nth-child(even) {
                        background-color: #f8fafc;
                    }
                    .text-right {
                        text-align: right;
                    }
                    .text-center {
                        text-align: center;
                    }
                    .badge-active {
                        background: #dcfce7;
                        color: #166534;
                        font-weight: bold;
                        padding: 2px 6px;
                        border-radius: 4px;
                        display: inline-block;
                        font-size: 9px;
                    }
                    .badge-inactive {
                        background: #fee2e2;
                        color: #991b1b;
                        font-weight: bold;
                        padding: 2px 6px;
                        border-radius: 4px;
                        display: inline-block;
                        font-size: 9px;
                    }
                    .footer {
                        margin-top: 16px;
                        text-align: right;
                        font-size: 9px;
                        color: #94a3b8;
                    }
                </style>
            </head>
            <body>
                <div class="header-container">
                    <div class="company-name">$company</div>
                    <div class="report-title">$title</div>
                    <div class="meta-row">
                        <span>তারিখ: $printDate</span>
                        <span>মোট গ্রাহক: $totalCount জন</span>
                        <span>মোট মাসিক বিল: $currencySymbol ${String.format(Locale.US, "%,.2f", totalMonthlyBill)}</span>
                        <span>মোট বকেয়া: $currencySymbol ${String.format(Locale.US, "%,.2f", totalDue)}</span>
                    </div>
                </div>

                <table>
                    <thead>
                        <tr>
        """.trimIndent())

        activeFieldsOrdered.forEach { field ->
            val alignClass = when (field) {
                ExportCustomerField.SERIAL -> "text-center"
                ExportCustomerField.MONTHLY_BILL, ExportCustomerField.DUE_AMOUNT -> "text-right"
                ExportCustomerField.STATUS -> "text-center"
                else -> ""
            }
            val titleText = if (isBn) field.titleBn else field.titleEn
            sb.append("""<th class="$alignClass">$titleText</th>""")
        }

        sb.append("""
                        </tr>
                    </thead>
                    <tbody>
        """.trimIndent())

        rows.forEach { row ->
            sb.append("<tr>")
            activeFieldsOrdered.forEach { field ->
                when (field) {
                    ExportCustomerField.SERIAL -> sb.append("""<td class="text-center">${row.serialNo}</td>""")
                    ExportCustomerField.NAME -> sb.append("""<td><strong>${row.name}</strong></td>""")
                    ExportCustomerField.CUSTOMER_CODE -> sb.append("""<td>${row.customerCode}</td>""")
                    ExportCustomerField.PHONE -> sb.append("""<td>${row.phone.ifBlank { "-" }}</td>""")
                    ExportCustomerField.PACKAGE -> sb.append("""<td>${row.packageName}</td>""")
                    ExportCustomerField.MONTHLY_BILL -> sb.append("""<td class="text-right">$currencySymbol ${String.format(Locale.US, "%.2f", row.monthlyBill)}</td>""")
                    ExportCustomerField.DUE_AMOUNT -> {
                        val color = if (row.dueAmount > 0) "#dc2626" else "#16a34a"
                        sb.append("""<td class="text-right" style="color: $color; font-weight: bold;">$currencySymbol ${String.format(Locale.US, "%.2f", row.dueAmount)}</td>""")
                    }
                    ExportCustomerField.PPPOE_USERNAME -> sb.append("""<td><code>${row.pppoeUsername}</code></td>""")
                    ExportCustomerField.PASSWORD -> sb.append("""<td><code>${row.password.ifBlank { "-" }}</code></td>""")
                    ExportCustomerField.ADDRESS -> sb.append("""<td>${row.address.ifBlank { "-" }}</td>""")
                    ExportCustomerField.STATUS -> {
                        val statusBadge = if (row.status.equals("ACTIVE", ignoreCase = true)) {
                            """<span class="badge-active">Active</span>"""
                        } else {
                            """<span class="badge-inactive">${row.status}</span>"""
                        }
                        sb.append("""<td class="text-center">$statusBadge</td>""")
                    }
                    ExportCustomerField.IP_ADDRESS -> sb.append("""<td>${row.ipAddress.ifBlank { "-" }}</td>""")
                    ExportCustomerField.JOINING_DATE -> sb.append("""<td>${row.joiningDate.ifBlank { "-" }}</td>""")
                    ExportCustomerField.NOTES -> sb.append("""<td>${row.notes.ifBlank { "-" }}</td>""")
                }
            }
            sb.append("</tr>")
        }

        sb.append("""
                    </tbody>
                </table>
                <div class="footer">
                    স্বয়ংক্রিয়ভাবে তৈরি আইএসপি ডিজিটাল নেটওয়ার্ক সফটওয়্যার থেকে।
                </div>
            </body>
            </html>
        """.trimIndent())

        return sb.toString()
    }

    /**
     * Prints or saves as PDF using Android PrintManager
     */
    fun printCustomerReport(
        context: Context,
        rows: List<ExportedCustomerRow>,
        ispName: String,
        selectedFields: Set<ExportCustomerField> = ExportCustomerField.values().filter { it.isDefaultSelected }.toSet(),
        currencySymbol: String = "৳",
        isBn: Boolean = true
    ) {
        val htmlContent = generatePrintableHtml(rows, ispName, selectedFields, currencySymbol, isBn)
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                if (printManager != null) {
                    val printAdapter = webView.createPrintDocumentAdapter("Customer_Report_A_Z")
                    val attributes = PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()
                    printManager.print("Customer_Report_A_Z", printAdapter, attributes)
                } else {
                    Toast.makeText(context, "প্রিন্ট সেবা পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                }
            }
        }
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }
}
