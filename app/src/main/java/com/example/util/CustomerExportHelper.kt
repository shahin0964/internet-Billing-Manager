package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.BillEntity
import com.example.data.model.CustomerEntity
import java.io.File
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
    SERIAL("ক্রমিক নং", "SL", true),
    NAME("গ্রাহকের নাম", "Customer Name", true),
    CUSTOMER_CODE("গ্রাহক আইডি", "Customer ID", true),
    PHONE("মোবাইল নম্বর", "Phone", true),
    PACKAGE("প্যাকেজের নাম", "Package", true),
    MONTHLY_BILL("মাসিক বিল", "Monthly Bill", true),
    DUE_AMOUNT("বর্তমান বকেয়া", "Current Due", true),
    PPPOE_USERNAME("PPPoE ইউজারনেম", "PPPoE Username", true),
    PASSWORD("পাসওয়ার্ড", "PPPoE Password", true),
    ADDRESS("ঠিকানা", "Address", false),
    STATUS("সংযোগ স্ট্যাটাস", "Status", true),
    IP_ADDRESS("আইপি অ্যাড্রেস", "IP Address", false),
    JOINING_DATE("যোগদানের তারিখ", "Joining Date", false),
    NOTES("নোট", "Notes", false)
}

enum class ExportFormat(val titleBn: String, val titleEn: String) {
    EXCEL("এক্সেল ফাইল (.CSV)", "Excel (.CSV)"),
    PDF("পিডিএফ ডকুমেন্ট (.PDF)", "PDF Document (.PDF)")
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
                putExtra(Intent.EXTRA_SUBJECT, "Customer List Export (A-Z)")
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
     * Saves a copy of CSV to Public Downloads folder.
     */
    fun saveToDownloads(context: Context, sourceFile: File): File? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val destFile = File(downloadsDir, sourceFile.name)
            sourceFile.copyTo(destFile, overwrite = true)
            destFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
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
