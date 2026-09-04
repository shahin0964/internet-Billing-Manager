package com.example.util

import java.util.Locale

/**
 * Utility for extracting real customer names and generating normalized sort keys.
 * Ignores customer codes, prefixes (e.g., "r.", "A6_", "c-", "101.", "#12_"),
 * and delimiters for alphabetical sorting without altering the stored or displayed name.
 */
object CustomerSortUtils {

    /**
     * Extracts the actual customer name for sorting/indexing by removing any
     * leading customer codes, prefixes, or bracketed identifiers.
     */
    fun extractRealName(rawName: String): String {
        var clean = rawName.trim()
        if (clean.isEmpty()) return ""

        // Remove surrounding quotes if any
        clean = clean.removeSurrounding("\"", "\"").removeSurrounding("'", "'").trim()

        // 1. Remove bracketed / parenthesized / hash prefix (e.g., "[A6] Nafiz", "(C-01) Nafiz", "#12 Nafiz")
        clean = clean.replace("""^(\[[^\]]+\]|\([^\)]+\)|#[A-Za-z0-9_]+)\s*[-_.:/|\\]?\s*""".toRegex(), "").trim()

        // 2. Iteratively strip leading code prefixes followed by separators: ., _, -, :, /, \, |, ~
        // Examples: "r.jisan", "A6_Nafiz", "CUST-101_Mamun", "101. Jahangir", "r-hasnat", "zone1.shorif"
        var prev = ""
        while (prev != clean) {
            prev = clean
            val prefixRegex = """^[A-Za-z0-9]+[._:\-\/\\|~]\s*""".toRegex()
            val match = prefixRegex.find(clean)
            if (match != null) {
                val candidate = clean.substring(match.range.last + 1).trim()
                // Only accept if candidate still contains at least one letter
                if (candidate.isNotEmpty() && candidate.any { it.isLetter() }) {
                    clean = candidate
                } else {
                    break
                }
            }
        }

        // 3. Strip standalone leading numbers followed by space/separator (e.g., "01 Nafiz", "12 - Nafiz")
        val leadingDigitsRegex = """^\d+\s*[-_.:/|\\]?\s*""".toRegex()
        val digitsMatch = leadingDigitsRegex.find(clean)
        if (digitsMatch != null) {
            val candidate = clean.substring(digitsMatch.range.last + 1).trim()
            if (candidate.isNotEmpty() && candidate.any { it.isLetter() }) {
                clean = candidate
            }
        }

        return if (clean.isNotEmpty()) clean else rawName.trim()
    }

    /**
     * Returns an uppercase sort key for deterministic alphabetical comparison.
     */
    fun getSortKey(rawName: String): String {
        val realName = extractRealName(rawName)
        return realName.uppercase(Locale.ROOT)
    }

    /**
     * Comparator for sorting customer names alphabetically by real name.
     */
    fun compareCustomerNames(nameA: String, nameB: String): Int {
        val keyA = getSortKey(nameA)
        val keyB = getSortKey(nameB)
        val cmp = keyA.compareTo(keyB)
        return if (cmp != 0) cmp else nameA.compareTo(nameB, ignoreCase = true)
    }
}
