package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.model.CustomerEntity
import com.example.data.model.IspPackageEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

enum class CustomerField(val key: String, val displayNameEn: String, val displayNameBn: String, val isRequired: Boolean) {
    NAME("name", "Customer Name *", "গ্রাহকের নাম *", true),
    CODE("code", "Customer Code / ID *", "গ্রাহক কোড / আইডি *", true),
    PHONE("phone", "Phone Number", "ফোন নম্বর", false),
    ADDRESS("address", "Address", "ঠিকানা", false),
    PPPOE("pppoe", "PPPoE Username", "পিপিপিওই ইউজারনেম", false),
    IP_ADDRESS("ip", "IP Address", "আইপি অ্যাড্রেস", false),
    PACKAGE_NAME("package", "Package Name", "প্যাকেজের নাম", false),
    MONTHLY_FEE("fee", "Monthly Fee / Bill", "মাসিক ফি / বিল", false),
    DUE_AMOUNT("due", "Existing Due", "বিদ্যমান বকেয়া", false),
    BILLING_MONTH("month", "Billing Month", "বিলিং মাস", false),
    JOINING_DATE("date", "Joining Date", "যোগদানের তারিখ", false),
    STATUS("status", "Status (Active/Suspended/Inactive)", "স্ট্যাটাস (সক্রিয়/সাসপেন্ড/নিষ্ক্রিয়)", false),
    NOTES("notes", "Notes", "নোট", false)
}

data class ImportedCustomerCandidate(
    val customer: CustomerEntity,
    val initialDueAmount: Double = 0.0,
    val billingMonth: String = ""
)

data class ParseResult(
    val headers: List<String>,
    val rows: List<List<String>>,
    val fileName: String,
    val errorMessage: String? = null
)

data class RowValidationDetail(
    val rowIndex: Int,
    val candidate: CustomerEntity?,
    val initialDueAmount: Double = 0.0,
    val billingMonth: String = "",
    val isValid: Boolean,
    val isDuplicate: Boolean,
    val duplicateCustomerName: String? = null,
    val errorReasonEn: String? = null,
    val errorReasonBn: String? = null,
    val rawSummary: String
)

data class ImportValidationSummary(
    val totalRows: Int,
    val validCount: Int,
    val invalidCount: Int,
    val duplicateCount: Int,
    val details: List<RowValidationDetail>
)

object CustomerImportParser {

    private const val TAG = "CustomerImportParser"

    fun parseFile(context: Context, uri: Uri): ParseResult {
        var fileName = "imported_file"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex) ?: "imported_file"
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve file name: ${e.message}")
        }

        return try {
            if (fileName.endsWith(".xlsx", ignoreCase = true)) {
                parseXlsx(context, uri, fileName)
            } else {
                parseCsv(context, uri, fileName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing file $fileName: ${e.message}", e)
            ParseResult(
                headers = emptyList(),
                rows = emptyList(),
                fileName = fileName,
                errorMessage = e.localizedMessage ?: "Failed to read file format"
            )
        }
    }

    private fun parseCsv(context: Context, uri: Uri, fileName: String): ParseResult {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: return ParseResult(emptyList(), emptyList(), fileName, "Unable to open file stream")

        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val lines = mutableListOf<String>()
        reader.useLines { sequence ->
            sequence.forEach { lines.add(it) }
        }

        if (lines.isEmpty()) {
            return ParseResult(emptyList(), emptyList(), fileName, "File is empty")
        }

        // Clean UTF-8 BOM if present
        if (lines[0].startsWith("\uFEFF")) {
            lines[0] = lines[0].substring(1)
        }

        val parsedRows = mutableListOf<List<String>>()
        for (line in lines) {
            if (line.isBlank()) continue
            val row = parseCsvLine(line)
            if (row.any { it.isNotBlank() }) {
                parsedRows.add(row)
            }
        }

        if (parsedRows.isEmpty()) {
            return ParseResult(emptyList(), emptyList(), fileName, "No valid data rows found in CSV")
        }

        val headers = parsedRows[0].map { it.trim() }
        val rows = parsedRows.drop(1)

        return ParseResult(headers, rows, fileName)
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        // Detect delimiter: check if semicolon or tab is more prevalent than comma
        var delimiter = ','
        if (!line.contains(",") && line.contains(";")) {
            delimiter = ';'
        } else if (!line.contains(",") && line.contains("\t")) {
            delimiter = '\t'
        }

        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == delimiter && !inQuotes) {
                result.add(sb.toString().trim())
                sb.clear()
            } else {
                sb.append(c)
            }
            i++
        }
        result.add(sb.toString().trim())
        return result
    }

    private fun parseXlsx(context: Context, uri: Uri, fileName: String): ParseResult {
        val sharedStrings = mutableListOf<String>()
        var sheetEntriesFound = false
        val parsedRows = mutableListOf<List<String>>()

        // Pass 1: Parse shared strings
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "xl/sharedStrings.xml") {
                        parseSharedStrings(zis, sharedStrings)
                        break
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        // Pass 2: Parse sheet rows
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.startsWith("xl/worksheets/sheet", ignoreCase = true) && entry.name.endsWith(".xml", ignoreCase = true)) {
                        sheetEntriesFound = true
                        parseSheetXml(zis, sharedStrings, parsedRows)
                        break
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        if (!sheetEntriesFound || parsedRows.isEmpty()) {
            // Fallback to CSV if zip/xlsx parsing didn't yield sheets
            return parseCsv(context, uri, fileName)
        }

        val headers = parsedRows[0].map { it.trim() }
        val rows = parsedRows.drop(1)

        return ParseResult(headers, rows, fileName)
    }

    private fun parseSharedStrings(stream: InputStream, sharedStrings: MutableList<String>) {
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(stream, "UTF-8")

            var eventType = parser.eventType
            var inT = false
            val currentText = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName.equals("t", ignoreCase = true)) {
                            inT = true
                            currentText.clear()
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inT) {
                            currentText.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName.equals("t", ignoreCase = true)) {
                            inT = false
                            sharedStrings.add(currentText.toString())
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing sharedStrings.xml: ${e.message}")
        }
    }

    private fun parseSheetXml(stream: InputStream, sharedStrings: List<String>, rows: MutableList<List<String>>) {
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(stream, "UTF-8")

            var eventType = parser.eventType
            var currentCellRef = ""
            var currentCellType = ""
            var inV = false
            var inT = false
            val cellValue = StringBuilder()
            var currentRowCells = mutableMapOf<Int, String>()
            var autoColIndex = 0

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName.equals("row", ignoreCase = true)) {
                            currentRowCells = mutableMapOf()
                            autoColIndex = 0
                        } else if (tagName.equals("c", ignoreCase = true)) {
                            currentCellRef = parser.getAttributeValue(null, "r") ?: ""
                            currentCellType = parser.getAttributeValue(null, "t") ?: ""
                            cellValue.clear()
                        } else if (tagName.equals("v", ignoreCase = true)) {
                            inV = true
                        } else if (tagName.equals("t", ignoreCase = true)) {
                            inT = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inV || inT) {
                            cellValue.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName.equals("v", ignoreCase = true)) {
                            inV = false
                        } else if (tagName.equals("t", ignoreCase = true)) {
                            inT = false
                        } else if (tagName.equals("c", ignoreCase = true)) {
                            val parsedColIndex = getColumnIndexFromRef(currentCellRef)
                            val colIndex = if (parsedColIndex >= 0) parsedColIndex else autoColIndex
                            autoColIndex = colIndex + 1

                            var rawVal = cellValue.toString().trim()
                            if (currentCellType == "s") {
                                val idx = rawVal.toIntOrNull()
                                if (idx != null && idx in sharedStrings.indices) {
                                    rawVal = sharedStrings[idx]
                                }
                            }
                            if (colIndex >= 0) {
                                currentRowCells[colIndex] = rawVal
                            }
                        } else if (tagName.equals("row", ignoreCase = true)) {
                            if (currentRowCells.isNotEmpty()) {
                                val maxCol = currentRowCells.keys.maxOrNull() ?: 0
                                val rowList = ArrayList<String>(maxCol + 1)
                                for (c in 0..maxCol) {
                                    rowList.add(currentRowCells[c] ?: "")
                                }
                                if (rowList.any { it.isNotBlank() }) {
                                    rows.add(rowList)
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing sheet.xml: ${e.message}")
        }
    }

    private fun getColumnIndexFromRef(ref: String): Int {
        val colStr = ref.takeWhile { it.isLetter() }.uppercase(Locale.ROOT)
        if (colStr.isEmpty()) return -1
        var num = 0
        for (ch in colStr) {
            num = num * 26 + (ch - 'A' + 1)
        }
        return num - 1
    }

    private data class MappingCandidate(val score: Int, val field: CustomerField, val colIndex: Int)

    fun autoDetectMapping(headers: List<String>): Map<CustomerField, Int> {
        val mapping = mutableMapOf<CustomerField, Int>()
        val candidates = mutableListOf<MappingCandidate>()

        for ((colIndex, header) in headers.withIndex()) {
            val raw = header.trim().lowercase(Locale.ROOT)
            val norm = raw.replace("_", " ").replace("-", " ").replace(".", " ").replace(Regex("\\s+"), " ")

            for (field in CustomerField.values()) {
                val score = scoreHeaderForField(norm, raw, field)
                if (score > 0) {
                    candidates.add(MappingCandidate(score, field, colIndex))
                }
            }
        }

        // Sort by score descending to pick best matches first
        candidates.sortByDescending { it.score }

        val assignedFields = mutableSetOf<CustomerField>()
        val assignedCols = mutableSetOf<Int>()

        for (cand in candidates) {
            if (!assignedFields.contains(cand.field) && !assignedCols.contains(cand.colIndex)) {
                mapping[cand.field] = cand.colIndex
                assignedFields.add(cand.field)
                assignedCols.add(cand.colIndex)
            }
        }

        return mapping
    }

    private fun scoreHeaderForField(norm: String, raw: String, field: CustomerField): Int {
        if (norm.isBlank()) return 0

        return when (field) {
            CustomerField.PPPOE -> {
                when {
                    norm == "pppoe username" || norm == "pppoe_username" || norm == "pppoe user" || norm == "pppoe name" -> 100
                    norm == "pppoe" || norm == "username" || norm == "user name" || norm == "pppoeid" -> 90
                    norm.contains("pppoe") || norm.contains("পিপিপিওই") -> 85
                    norm.contains("username") || norm.contains("ইউজারনেম") || norm.contains("ইউজার নেম") -> 80
                    norm.contains("user id") || norm.contains("user_id") -> 75
                    else -> 0
                }
            }
            CustomerField.PHONE -> {
                when {
                    norm == "phone number" || norm == "phone_number" || norm == "phone no" || norm == "mobile number" || norm == "mobile_number" || norm == "contact no" || norm == "contact_number" -> 100
                    norm == "phone" || norm == "mobile" || norm == "contact" || norm == "cell" -> 90
                    norm.contains("phone") || norm.contains("mobile") || norm.contains("contact") || norm.contains("cell") || norm.contains("ফোন") || norm.contains("মোবাইল") -> 80
                    norm.contains("নম্বর") || norm.contains("নাম্বার") -> if (!norm.contains("রসিদ") && !norm.contains("কোড") && !norm.contains("বিল")) 70 else 0
                    else -> 0
                }
            }
            CustomerField.NAME -> {
                // Strictly exclude PPPoE, package names, files, etc.
                if (norm.contains("pppoe") || norm.contains("username") || norm.contains("package") || norm.contains("plan") || norm.contains("phone") || norm.contains("file")) return 0
                when {
                    norm == "customer name" || norm == "customer_name" || norm == "client name" || norm == "subscriber name" || norm == "full name" || norm == "গ্রাহকের নাম" || norm == "গ্রাহক নাম" -> 100
                    norm == "name" || norm == "customer" || norm == "subscriber" || norm == "client" || norm == "নাম" -> 90
                    norm.contains("customer") || norm.contains("subscriber") || norm.contains("client") || norm.contains("গ্রাহক") -> 80
                    norm.contains("name") || norm.contains("নাম") -> 70
                    else -> 0
                }
            }
            CustomerField.CODE -> {
                when {
                    norm == "customer code" || norm == "customer_code" || norm == "client code" || norm == "cust code" || norm == "customer id" || norm == "customer_id" || norm == "client id" || norm == "subscriber id" -> 100
                    norm == "code" || norm == "id" || norm == "cust_id" || norm == "কোড" || norm == "আইডি" -> 90
                    norm.contains("code") || norm.contains("কোড") -> 80
                    (norm.contains("id") || norm.contains("আইডি")) && !norm.contains("pppoe") && !norm.contains("paid") && !norm.contains("valid") -> 70
                    else -> 0
                }
            }
            CustomerField.PACKAGE_NAME -> {
                when {
                    norm == "package name" || norm == "package_name" || norm == "plan name" || norm == "package" || norm == "plan" || norm == "প্যাকেজের নাম" || norm == "প্যাকেজ" -> 100
                    norm.contains("package") || norm.contains("plan") || norm.contains("profile") || norm.contains("speed") || norm.contains("প্যাকেজ") || norm.contains("প্ল্যান") -> 85
                    else -> 0
                }
            }
            CustomerField.MONTHLY_FEE -> {
                if (norm.contains("due") || norm.contains("বকেয়া") || norm.contains("বকেয়া") || norm == "billing month" || norm == "billing_month" || norm == "বিলিং মাস" || (norm.contains("billing") && norm.contains("month") && !norm.contains("bill"))) return 0
                when {
                    norm == "monthly fee" || norm == "monthly_fee" || norm == "monthly bill" || norm == "monthly_bill" || norm == "monthly price" || norm == "package price" || norm == "package_price" || norm == "fee" || norm == "price" || norm == "মাসিক বিল" || norm == "মাসিক ফি" || norm == "বিল" || norm == "ফি" || norm == "মাসিক ফি / বিল" -> 100
                    norm.contains("monthly") || norm.contains("bill") || norm.contains("fee") || norm.contains("price") || norm.contains("rate") || norm.contains("charge") || norm.contains("মাসিক") || norm.contains("বিল") || norm.contains("ফি") -> 90
                    norm.contains("amount") || norm.contains("টাকা") || norm.contains("taka") || norm.contains("tk") -> 70
                    else -> 0
                }
            }
            CustomerField.DUE_AMOUNT -> {
                when {
                    norm == "due amount" || norm == "due_amount" || norm == "existing due" || norm == "previous due" || norm == "due" || norm == "arrears" || norm == "outstanding" || norm == "বকেয়া" || norm == "বকেয়া" -> 100
                    norm.contains("due") || norm.contains("arrear") || norm.contains("outstanding") || norm.contains("বকেয়া") || norm.contains("বকেয়া") -> 85
                    else -> 0
                }
            }
            CustomerField.ADDRESS -> {
                when {
                    norm == "address" || norm == "location" || norm == "area" || norm == "ঠিকানা" -> 100
                    norm.contains("address") || norm.contains("location") || norm.contains("area") || norm.contains("ঠিকানা") || norm.contains("বাসা") -> 85
                    else -> 0
                }
            }
            CustomerField.IP_ADDRESS -> {
                when {
                    norm == "ip address" || norm == "ip_address" || norm == "ip" || norm == "static ip" || norm == "আইপি" -> 100
                    norm.contains("ip address") || norm.contains("ip_address") || norm.contains("ip-address") || norm.contains("আইপি") -> 85
                    else -> 0
                }
            }
            CustomerField.STATUS -> {
                when {
                    norm == "status" || norm == "state" || norm == "condition" || norm == "স্ট্যাটাস" -> 100
                    norm.contains("status") || norm.contains("state") || norm.contains("condition") || norm.contains("স্ট্যাটাস") || norm.contains("অবস্থা") -> 85
                    else -> 0
                }
            }
            CustomerField.JOINING_DATE -> {
                when {
                    norm == "joining date" || norm == "joining_date" || norm == "join date" || norm == "date" || norm == "তারিখ" -> 100
                    norm.contains("joining") || norm.contains("join") || norm.contains("date") || norm.contains("তারিখ") -> 80
                    else -> 0
                }
            }
            CustomerField.BILLING_MONTH -> {
                when {
                    norm == "billing month" || norm == "billing_month" || norm == "month" || norm == "বিলিং মাস" -> 100
                    norm.contains("billing month") || (norm.contains("মাস") && !norm.contains("মাসিক") && !norm.contains("প্যাকেজ")) -> 85
                    else -> 0
                }
            }
            CustomerField.NOTES -> {
                when {
                    norm == "notes" || norm == "note" || norm == "remarks" || norm == "remark" || norm == "comments" || norm == "নোট" || norm == "মন্তব্য" -> 100
                    norm.contains("note") || norm.contains("remark") || norm.contains("comment") || norm.contains("নোট") || norm.contains("মন্তব্য") -> 85
                    else -> 0
                }
            }
        }
    }

    private fun cleanFieldVal(field: CustomerField, raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""

        var cleaned = trimmed
        // Remove trailing .0 from string if Excel formatted text/numbers as float (e.g. 1001.0 -> 1001)
        if (cleaned.endsWith(".0") && cleaned.length > 2) {
            cleaned = cleaned.substring(0, cleaned.length - 2)
        }

        return when (field) {
            CustomerField.PHONE -> {
                // Strip non-digits except +
                var digits = cleaned.replace(Regex("[^0-9+]"), "")
                if (digits.startsWith("+88")) {
                    digits = digits.substring(3)
                } else if (digits.startsWith("880")) {
                    digits = "0" + digits.substring(3)
                }
                // Prepend leading zero if 10-digit Bangladeshi mobile number
                if (digits.length == 10 && digits.matches(Regex("^1[3-9]\\d{8}$"))) {
                    digits = "0$digits"
                }
                digits
            }
            CustomerField.PPPOE -> {
                cleaned.trim()
            }
            CustomerField.NAME -> {
                cleaned.trim()
            }
            CustomerField.CODE -> {
                cleaned.trim()
            }
            else -> cleaned.trim()
        }
    }

    private fun parseAmount(raw: String): Double {
        if (raw.isBlank()) return 0.0
        val cleaned = raw.replace(",", "").replace("৳", "").replace("$", "").replace("Tk", "", ignoreCase = true).replace("Taka", "", ignoreCase = true).replace("/=", "").trim()
        cleaned.toDoubleOrNull()?.let { return it }
        val match = Regex("(\\d+(?:\\.\\d+)?)").find(raw)
        return match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

    private fun extractSpeedFromName(name: String): Int? {
        val regex = Regex("(\\d+)\\s*(mbps|mb|m)?", RegexOption.IGNORE_CASE)
        val match = regex.find(name)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    fun validateRows(
        rows: List<List<String>>,
        fieldMapping: Map<CustomerField, Int>,
        existingCustomers: List<CustomerEntity>,
        existingPackages: List<IspPackageEntity>
    ): ImportValidationSummary {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val defaultPackage = existingPackages.firstOrNull() ?: IspPackageEntity(
            id = 1,
            name = "Standard Package",
            speedMbps = 10,
            monthlyPrice = 0.0,
            description = "Default standard package"
        )

        val knownPackages = existingPackages.toMutableList()
        val packageMapByName = knownPackages.associateBy { it.name.trim().lowercase(Locale.ROOT) }.toMutableMap()

        val existingByCode = existingCustomers.associateBy { it.customerCode.trim().lowercase(Locale.ROOT) }.toMutableMap()
        val existingByPppoe = existingCustomers.filter { it.pppoeUsername.isNotBlank() }
            .associateBy { it.pppoeUsername.trim().lowercase(Locale.ROOT) }.toMutableMap()

        val seenCodesInFile = mutableSetOf<String>()
        val seenPppoesInFile = mutableSetOf<String>()

        val details = mutableListOf<RowValidationDetail>()
        var validCount = 0
        var invalidCount = 0
        var duplicateCount = 0

        var autoCodeIndex = 101

        for ((index, row) in rows.withIndex()) {
            val rowIndex = index + 1 // 1-based data row index

            fun getVal(field: CustomerField): String {
                val colIdx = fieldMapping[field] ?: return ""
                return if (colIdx in row.indices) row[colIdx] else ""
            }

            val name = cleanFieldVal(CustomerField.NAME, getVal(CustomerField.NAME))
            var code = cleanFieldVal(CustomerField.CODE, getVal(CustomerField.CODE))
            val phone = cleanFieldVal(CustomerField.PHONE, getVal(CustomerField.PHONE))
            val address = cleanFieldVal(CustomerField.ADDRESS, getVal(CustomerField.ADDRESS))
            val pppoe = cleanFieldVal(CustomerField.PPPOE, getVal(CustomerField.PPPOE))
            val ip = cleanFieldVal(CustomerField.IP_ADDRESS, getVal(CustomerField.IP_ADDRESS))
            val packageNameRaw = cleanFieldVal(CustomerField.PACKAGE_NAME, getVal(CustomerField.PACKAGE_NAME))
            val monthlyFeeRaw = getVal(CustomerField.MONTHLY_FEE)
            val dueAmountRaw = getVal(CustomerField.DUE_AMOUNT)
            val billingMonthRaw = getVal(CustomerField.BILLING_MONTH)
            val joiningDateRaw = getVal(CustomerField.JOINING_DATE)
            val statusRaw = getVal(CustomerField.STATUS)
            val notes = cleanFieldVal(CustomerField.NOTES, getVal(CustomerField.NOTES))

            val parsedStatus = when {
                statusRaw.contains("suspend", ignoreCase = true) || statusRaw.contains("সাসপেন্ড", ignoreCase = true) -> "SUSPENDED"
                statusRaw.contains("inact", ignoreCase = true) || statusRaw.contains("নিষ্ক্রিয়", ignoreCase = true) || statusRaw.contains("বন্ধ", ignoreCase = true) -> "INACTIVE"
                statusRaw.contains("act", ignoreCase = true) || statusRaw.contains("সক্রিয়", ignoreCase = true) -> "ACTIVE"
                else -> "ACTIVE"
            }

            // Check required fields
            if (name.isBlank()) {
                invalidCount++
                details.add(
                    RowValidationDetail(
                        rowIndex = rowIndex,
                        candidate = null,
                        initialDueAmount = 0.0,
                        billingMonth = "",
                        isValid = false,
                        isDuplicate = false,
                        errorReasonEn = "Missing Customer Name",
                        errorReasonBn = "গ্রাহকের নাম নেই",
                        rawSummary = "Row #$rowIndex"
                    )
                )
                continue
            }

            if (code.isBlank()) {
                // Auto-generate candidate code if not provided
                while (existingByCode.containsKey("cust-$autoCodeIndex") || seenCodesInFile.contains("cust-$autoCodeIndex")) {
                    autoCodeIndex++
                }
                code = "CUST-$autoCodeIndex"
                autoCodeIndex++
            }

            var monthlyFee = parseAmount(monthlyFeeRaw)
            val parsedDue = parseAmount(dueAmountRaw)
            val parsedMonth = billingMonthRaw.trim()

            // Package matching / auto-detection
            val rawPkgKey = packageNameRaw.lowercase(Locale.ROOT)
            var matchedPackage = if (rawPkgKey.isNotBlank()) packageMapByName[rawPkgKey] else null

            if (matchedPackage == null && packageNameRaw.isBlank() && monthlyFee > 0.0) {
                matchedPackage = packageMapByName.values.find { it.monthlyPrice == monthlyFee }
            }

            if (matchedPackage == null) {
                val finalPkgName = if (packageNameRaw.isNotBlank()) packageNameRaw else if (monthlyFee > 0.0) "${monthlyFee.toInt()} Tk Package" else defaultPackage.name
                val extractedSpeed = extractSpeedFromName(finalPkgName) ?: 10
                val finalFee = if (monthlyFee > 0.0) monthlyFee else defaultPackage.monthlyPrice

                matchedPackage = IspPackageEntity(
                    id = 0L,
                    name = finalPkgName,
                    speedMbps = extractedSpeed,
                    monthlyPrice = finalFee,
                    description = "Imported package"
                )
                packageMapByName[finalPkgName.lowercase(Locale.ROOT)] = matchedPackage
            }

            if (monthlyFee <= 0.0) monthlyFee = matchedPackage.monthlyPrice
            val finalPackageName = matchedPackage.name
            val finalPackageId = matchedPackage.id

            val joiningDate = if (joiningDateRaw.isNotBlank()) joiningDateRaw else todayStr

            val candidate = CustomerEntity(
                customerCode = code,
                name = name,
                phone = phone,
                address = address,
                pppoeUsername = pppoe,
                ipAddress = ip,
                packageId = finalPackageId,
                packageName = finalPackageName,
                monthlyFee = monthlyFee,
                status = parsedStatus,
                joiningDate = joiningDate,
                notes = notes
            )

            val rawSummary = listOfNotNull(
                name.takeIf { it.isNotBlank() },
                code.takeIf { it.isNotBlank() },
                finalPackageName.takeIf { it.isNotBlank() },
                if (monthlyFee > 0) "৳${String.format(Locale.US, "%.0f", monthlyFee)}" else null,
                if (parsedDue > 0) "Due: ৳${String.format(Locale.US, "%.0f", parsedDue)}" else null
            ).joinToString(" • ")

            // Check duplicates against existing DB or previous rows in file
            val codeKey = code.trim().lowercase(Locale.ROOT)
            val pppoeKey = pppoe.trim().lowercase(Locale.ROOT)

            val matchedExisting = (if (codeKey.isNotEmpty()) existingByCode[codeKey] else null)
                ?: (if (pppoeKey.isNotEmpty()) existingByPppoe[pppoeKey] else null)

            val isDuplicateInFile = (codeKey.isNotEmpty() && seenCodesInFile.contains(codeKey)) ||
                    (pppoeKey.isNotEmpty() && seenPppoesInFile.contains(pppoeKey))

            if (matchedExisting != null || isDuplicateInFile) {
                duplicateCount++
                val duplicateName = matchedExisting?.name ?: "Duplicate in file"
                details.add(
                    RowValidationDetail(
                        rowIndex = rowIndex,
                        candidate = candidate,
                        initialDueAmount = parsedDue,
                        billingMonth = parsedMonth,
                        isValid = true,
                        isDuplicate = true,
                        duplicateCustomerName = duplicateName,
                        rawSummary = rawSummary
                    )
                )
            } else {
                validCount++
                if (codeKey.isNotEmpty()) seenCodesInFile.add(codeKey)
                if (pppoeKey.isNotEmpty()) seenPppoesInFile.add(pppoeKey)

                details.add(
                    RowValidationDetail(
                        rowIndex = rowIndex,
                        candidate = candidate,
                        initialDueAmount = parsedDue,
                        billingMonth = parsedMonth,
                        isValid = true,
                        isDuplicate = false,
                        rawSummary = rawSummary
                    )
                )
            }
        }

        return ImportValidationSummary(
            totalRows = rows.size,
            validCount = validCount + duplicateCount,
            invalidCount = invalidCount,
            duplicateCount = duplicateCount,
            details = details
        )
    }
}
