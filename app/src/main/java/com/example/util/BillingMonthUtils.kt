package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility for locale-independent billing month handling and cross-locale month matching.
 * Guarantees that billing period comparisons are stable across device language changes
 * and backwards compatible with existing historical month strings in English, Bengali, or numeric formats.
 */
object BillingMonthUtils {

    private val banglaToEnglishDigits = mapOf(
        '০' to '0', '১' to '1', '২' to '2', '৩' to '3', '৪' to '4',
        '৫' to '5', '৬' to '6', '৭' to '7', '৮' to '8', '৯' to '9'
    )

    private val monthNameToNumber = mapOf(
        // English full & short
        "january" to "01", "jan" to "01",
        "february" to "02", "feb" to "02",
        "march" to "03", "mar" to "03",
        "april" to "04", "apr" to "04",
        "may" to "05",
        "june" to "06", "jun" to "06",
        "july" to "07", "jul" to "07",
        "august" to "08", "aug" to "08",
        "september" to "09", "sep" to "09", "sept" to "09",
        "october" to "10", "oct" to "10",
        "november" to "11", "nov" to "11",
        "december" to "12", "dec" to "12",

        // Bengali month names
        "জানুয়ারি" to "01", "জানুয়ারী" to "01",
        "ফেব্রুয়ারি" to "02", "ফেব্রুয়ারী" to "02",
        "মার্চ" to "03",
        "এপ্রিল" to "04",
        "মে" to "05",
        "জুন" to "06",
        "জুলাই" to "07",
        "আগস্ট" to "08", "আগষ্ট" to "08",
        "সেপ্টেম্বর" to "09",
        "অক্টোবর" to "10",
        "নভেম্বর" to "11",
        "ডিসেম্বর" to "12"
    )

    /**
     * Converts any month string (e.g. "August 2026", "আগস্ট ২০২৬", "2026-08")
     * into a canonical "YYYY-MM" representation for reliable cross-locale comparison.
     */
    fun normalizeMonthKey(rawMonth: String): String {
        val trimmed = rawMonth.trim()
        if (trimmed.isEmpty()) return ""

        // Convert Bengali digits to English digits
        val converted = trimmed.map { banglaToEnglishDigits[it] ?: it }.joinToString("")
        val lower = converted.lowercase(Locale.ROOT)

        // Check if already in YYYY-MM format
        val yyyyMmRegex = """(\d{4})[-/](\d{1,2})""".toRegex()
        val matchYm = yyyyMmRegex.find(lower)
        if (matchYm != null) {
            val year = matchYm.groupValues[1]
            val month = matchYm.groupValues[2].padStart(2, '0')
            return "$year-$month"
        }

        // Find 4 digit year
        val yearRegex = """\b(20\d{2}|19\d{2})\b""".toRegex()
        val yearMatch = yearRegex.find(lower)
        val year = yearMatch?.groupValues?.get(1)

        // Find month component
        var foundMonth: String? = null
        for ((name, num) in monthNameToNumber) {
            if (lower.contains(name)) {
                foundMonth = num
                break
            }
        }

        if (year != null && foundMonth != null) {
            return "$year-$foundMonth"
        }

        // Fallback: return clean lowercased string
        return lower
    }

    /**
     * Checks if two billing month representations refer to the exact same billing period.
     */
    fun isSameMonth(monthA: String, monthB: String): Boolean {
        if (monthA.isBlank() || monthB.isBlank()) return false
        if (monthA.equals(monthB, ignoreCase = true)) return true
        val keyA = normalizeMonthKey(monthA)
        val keyB = normalizeMonthKey(monthB)
        return keyA.isNotEmpty() && keyA == keyB
    }

    /**
     * Locale-independent standard month string for new bills (e.g. "August 2026").
     */
    fun formatStandardMonth(date: Date = Date()): String {
        return SimpleDateFormat("MMMM yyyy", Locale.US).format(date)
    }

    /**
     * Locale-independent standard due date (e.g. "2026-08-10").
     */
    fun formatStandardDueDate(date: Date = Date()): String {
        return SimpleDateFormat("yyyy-MM-10", Locale.US).format(date)
    }
}
