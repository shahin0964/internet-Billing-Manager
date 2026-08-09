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
    MONTHLY_FEE("fee", "Monthly Fee", "মাসিক ফি", false),
    JOINING_DATE("date", "Joining Date", "যোগদানের তারিখ", false),
    NOTES("notes", "Notes", "নোট", false)
}

data class ParseResult(
    val headers: List<String>,
    val rows: List<List<String>>,
    val fileName: String,
    val errorMessage: String? = null
)

data class RowValidationDetail(
    val rowIndex: Int,
    val candidate: CustomerEntity?,
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

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName.equals("row", ignoreCase = true)) {
                            currentRowCells = mutableMapOf()
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
                            val colIndex = getColumnIndexFromRef(currentCellRef)
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

    fun autoDetectMapping(headers: List<String>): Map<CustomerField, Int> {
        val mapping = mutableMapOf<CustomerField, Int>()

        for ((fieldIndex, field) in CustomerField.values().withIndex()) {
            val matchedIndex = headers.indexOfFirst { header ->
                val normalized = header.trim().lowercase(Locale.ROOT)
                when (field) {
                    CustomerField.NAME -> normalized.contains("name") || normalized.contains("নাম") || normalized.contains("subscriber") || normalized.contains("client")
                    CustomerField.CODE -> normalized.contains("code") || normalized.contains("id") || normalized.contains("কোড") || normalized.contains("আইডি")
                    CustomerField.PHONE -> normalized.contains("phone") || normalized.contains("mobile") || normalized.contains("contact") || normalized.contains("ফোন") || normalized.contains("মোবাইল")
                    CustomerField.ADDRESS -> normalized.contains("address") || normalized.contains("location") || normalized.contains("ঠিকানা")
                    CustomerField.PPPOE -> normalized.contains("pppoe") || normalized.contains("username") || normalized.contains("ইউজারনেম")
                    CustomerField.IP_ADDRESS -> normalized.contains("ip") || normalized.contains("আইপি")
                    CustomerField.PACKAGE_NAME -> normalized.contains("package") || normalized.contains("plan") || normalized.contains("প্যাকেজ")
                    CustomerField.MONTHLY_FEE -> normalized.contains("fee") || normalized.contains("price") || normalized.contains("amount") || normalized.contains("bill") || normalized.contains("ফি") || normalized.contains("টাকা")
                    CustomerField.JOINING_DATE -> normalized.contains("date") || normalized.contains("joining") || normalized.contains("তারিখ")
                    CustomerField.NOTES -> normalized.contains("note") || normalized.contains("remark") || normalized.contains("নোট")
                }
            }
            if (matchedIndex != -1) {
                mapping[field] = matchedIndex
            }
        }

        return mapping
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
            monthlyPrice = 500.0,
            description = "Default standard package"
        )

        val existingByCode = existingCustomers.associateBy { it.customerCode.trim().lowercase(Locale.ROOT) }
        val existingByPppoe = existingCustomers.filter { it.pppoeUsername.isNotBlank() }
            .associateBy { it.pppoeUsername.trim().lowercase(Locale.ROOT) }

        val details = mutableListOf<RowValidationDetail>()
        var validCount = 0
        var invalidCount = 0
        var duplicateCount = 0

        var autoCodeIndex = 101

        for ((index, row) in rows.withIndex()) {
            val rowIndex = index + 1 // 1-based data row index

            fun getVal(field: CustomerField): String {
                val colIdx = fieldMapping[field] ?: return ""
                return if (colIdx in row.indices) row[colIdx].trim() else ""
            }

            val name = getVal(CustomerField.NAME)
            var code = getVal(CustomerField.CODE)
            val phone = getVal(CustomerField.PHONE)
            val address = getVal(CustomerField.ADDRESS)
            val pppoe = getVal(CustomerField.PPPOE)
            val ip = getVal(CustomerField.IP_ADDRESS)
            val packageNameRaw = getVal(CustomerField.PACKAGE_NAME)
            val monthlyFeeRaw = getVal(CustomerField.MONTHLY_FEE)
            val joiningDateRaw = getVal(CustomerField.JOINING_DATE)
            val notes = getVal(CustomerField.NOTES)

            val rawSummary = listOfNotNull(
                name.takeIf { it.isNotBlank() },
                code.takeIf { it.isNotBlank() },
                phone.takeIf { it.isNotBlank() },
                pppoe.takeIf { it.isNotBlank() }
            ).joinToString(" • ")

            // Check required fields
            if (name.isBlank()) {
                invalidCount++
                details.add(
                    RowValidationDetail(
                        rowIndex = rowIndex,
                        candidate = null,
                        isValid = false,
                        isDuplicate = false,
                        errorReasonEn = "Missing Customer Name",
                        errorReasonBn = "গ্রাহকের নাম নেই",
                        rawSummary = rawSummary.ifBlank { "Row #$rowIndex" }
                    )
                )
                continue
            }

            if (code.isBlank()) {
                // Auto-generate candidate code if not provided
                while (existingByCode.containsKey("cust-$autoCodeIndex")) {
                    autoCodeIndex++
                }
                code = "CUST-$autoCodeIndex"
                autoCodeIndex++
            }

            var monthlyFee = monthlyFeeRaw.replace(",", ".").toDoubleOrNull() ?: 0.0
            var matchedPackage = existingPackages.find {
                it.name.equals(packageNameRaw, ignoreCase = true)
            }

            if (matchedPackage != null) {
                if (monthlyFee <= 0) monthlyFee = matchedPackage.monthlyPrice
            } else {
                matchedPackage = defaultPackage
                if (monthlyFee <= 0) monthlyFee = defaultPackage.monthlyPrice
            }

            val joiningDate = if (joiningDateRaw.isNotBlank()) joiningDateRaw else todayStr

            val candidate = CustomerEntity(
                customerCode = code,
                name = name,
                phone = phone,
                address = address,
                pppoeUsername = pppoe,
                ipAddress = ip,
                packageId = matchedPackage.id,
                packageName = if (packageNameRaw.isNotBlank()) packageNameRaw else matchedPackage.name,
                monthlyFee = monthlyFee,
                status = "ACTIVE",
                joiningDate = joiningDate,
                notes = notes
            )

            // Check duplicates
            val codeKey = code.trim().lowercase(Locale.ROOT)
            val pppoeKey = pppoe.trim().lowercase(Locale.ROOT)

            val matchedExisting = existingByCode[codeKey]
                ?: (if (pppoeKey.isNotEmpty()) existingByPppoe[pppoeKey] else null)

            if (matchedExisting != null) {
                duplicateCount++
                details.add(
                    RowValidationDetail(
                        rowIndex = rowIndex,
                        candidate = candidate,
                        isValid = true,
                        isDuplicate = true,
                        duplicateCustomerName = matchedExisting.name,
                        rawSummary = "$name ($code)"
                    )
                )
            } else {
                validCount++
                details.add(
                    RowValidationDetail(
                        rowIndex = rowIndex,
                        candidate = candidate,
                        isValid = true,
                        isDuplicate = false,
                        rawSummary = "$name ($code)"
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
