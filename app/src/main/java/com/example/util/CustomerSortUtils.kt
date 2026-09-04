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
     * leading or trailing customer codes, prefixes, suffixes, or bracketed identifiers.
     */
    fun extractRealName(rawName: String): String {
        var clean = rawName.trim()
        if (clean.isEmpty()) return ""

        // Remove surrounding quotes if any
        clean = clean.removeSurrounding("\"", "\"").removeSurrounding("'", "'").trim()

        // --- START OF SUFFIX CLEANING (Run first to resolve ambiguities like jisan_A6) ---
        var prevSuffix = ""
        while (prevSuffix != clean) {
            prevSuffix = clean

            // 1. Remove trailing bracketed / parenthesized suffixes (e.g., "Nafiz [A6]", "Nafiz (C-01)")
            val trailingBracketsRegex = """\s*(\[[^\]]+\]|\([^\)]+\))\s*$""".toRegex()
            clean = clean.replace(trailingBracketsRegex, "").trim()

            // 2. Remove trailing alphanumeric suffixes containing at least one digit or pure digits, preceded by space/separator (e.g., "Nafiz_A6", "Nafiz 101", "Nafiz A6", "Nafiz - A6")
            val trailingAlphaNumericRegex = """(?:\s+|[-_.:/|\\~])\s*(?:[A-Za-z]*\d[A-Za-z0-9]*)\s*$""".toRegex()
            val tmatch = trailingAlphaNumericRegex.find(clean)
            if (tmatch != null && tmatch.range.first > 0) {
                val candidate = clean.substring(0, tmatch.range.first).trim()
                if (candidate.isNotEmpty() && candidate.any { it.isLetter() }) {
                    clean = candidate
                }
            }

            // 3. Remove trailing single letter prefix with dot/separator (e.g., "jisan r.", "jisan _r", "jisan r")
            val trailingSingleLetterRegex = """(?:\s+|[-_.:/|\\~])\s*[A-Za-z]\s*[-_.:/|\\~]?\s*$""".toRegex()
            val tlmatch = trailingSingleLetterRegex.find(clean)
            if (tlmatch != null && tlmatch.range.first > 0) {
                val candidate = clean.substring(0, tlmatch.range.first).trim()
                if (candidate.isNotEmpty() && candidate.any { it.isLetter() }) {
                    clean = candidate
                }
            }
        }
        // --- END OF SUFFIX CLEANING ---

        // --- START OF PREFIX CLEANING ---
        var prevPrefix = ""
        while (prevPrefix != clean) {
            prevPrefix = clean

            // 1. Remove leading bracketed / parenthesized / hash prefix (e.g., "[A6] Nafiz", "(C-01) Nafiz", "#12 Nafiz")
            val bracketsRegex = """^(\[[^\]]+\]|\([^\)]+\)|#[A-Za-z0-9_]+)\s*[-_.:/|\\]?\s*""".toRegex()
            clean = clean.replace(bracketsRegex, "").trim()

            // 2. Strip leading alphanumeric containing at least one digit, followed by optional separator/space (e.g., "A6 Nafiz", "CUST101 Nafiz", "101. Jahangir")
            val alphanumericWithDigitRegex = """^(?:[A-Za-z]*\d[A-Za-z0-9]*)\s*[-_.:/|\\]?\s*""".toRegex()
            val amatch = alphanumericWithDigitRegex.find(clean)
            if (amatch != null) {
                val candidate = clean.substring(amatch.range.last + 1).trim()
                if (candidate.isNotEmpty() && candidate.any { it.isLetter() }) {
                    clean = candidate
                }
            }

            // 3. Strip leading alphanumeric prefixes followed by separators: ., _, -, :, /, \, |, ~
            // (e.g., "r.jisan", "A6_Nafiz", "CUST-101_Mamun", "r-hasnat", "zone1.shorif")
            // Constrains letters-only prefix to <= 4 chars so real names are not matched as prefix codes.
            val prefixWithSeparatorRegex = """^(?:[A-Za-z0-9]*\d[A-Za-z0-9]*|[A-Za-z]{1,4})\s*[-_.:/|\\~]\s*""".toRegex()
            val smatch = prefixWithSeparatorRegex.find(clean)
            if (smatch != null) {
                val candidate = clean.substring(smatch.range.last + 1).trim()
                if (candidate.isNotEmpty() && candidate.any { it.isLetter() }) {
                    clean = candidate
                }
            }

            // 4. Strip single letter prefix followed by space (e.g., "r jisan")
            val singleLetterRegex = """^[A-Za-z]\s+""".toRegex()
            val lmatch = singleLetterRegex.find(clean)
            if (lmatch != null) {
                val candidate = clean.substring(lmatch.range.last + 1).trim()
                if (candidate.isNotEmpty() && candidate.any { it.isLetter() }) {
                    clean = candidate
                }
            }
        }
        // --- END OF PREFIX CLEANING ---

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
