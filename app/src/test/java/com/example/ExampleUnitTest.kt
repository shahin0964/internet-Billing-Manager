package com.example

import com.example.util.CustomerSortUtils
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Customer & Billing Alphabetical Real Name Sorting.
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testCustomerSortWithCodesAndPrefixes() {
    println("DEBUG: extractRealName(A6_Nafiz) = " + CustomerSortUtils.extractRealName("A6_Nafiz"))
    println("DEBUG: extractRealName(jisan_A6) = " + CustomerSortUtils.extractRealName("jisan_A6"))
    val rawList = listOf(
      "r.jisan",
      "r.taslima",
      "r.safwan",
      "r.jahangir",
      "r.rana1",
      "r.masud",
      "r.suvasish",
      "r.badhon",
      "A6_Nafiz",
      "r.mamun",
      "r.hasnat",
      "r.shorif",
      "r.muskan",
      "r.sujon",
      "r.tonmoy",
      "r.wadud"
    )

    val sorted = rawList.sortedWith { a, b ->
      CustomerSortUtils.compareCustomerNames(a, b)
    }

    val expectedRealNamesOrder = listOf(
      "r.badhon",    // Badhon (B)
      "r.hasnat",    // Hasnat (H)
      "r.jahangir",  // Jahangir (J)
      "r.jisan",     // Jisan (J)
      "r.mamun",     // Mamun (M)
      "r.masud",     // Masud (M)
      "r.muskan",    // Muskan (M)
      "A6_Nafiz",    // Nafiz (N)
      "r.rana1",     // Rana1 (R)
      "r.safwan",    // Safwan (S)
      "r.shorif",    // Shorif (S)
      "r.sujon",     // Sujon (S)
      "r.suvasish",  // Suvasish (S)
      "r.taslima",   // Taslima (T)
      "r.tonmoy",    // Tonmoy (T)
      "r.wadud"      // Wadud (W)
    )

    assertEquals(expectedRealNamesOrder, sorted)

    // Stored name values are NOT modified
    assertEquals("r.jisan", rawList[0])
    assertEquals("A6_Nafiz", rawList[8])

    // Extracted real names preserve casing of the name substring
    assertEquals("jisan", CustomerSortUtils.extractRealName("r.jisan"))
    assertEquals("Nafiz", CustomerSortUtils.extractRealName("A6_Nafiz"))
    assertEquals("mamun", CustomerSortUtils.extractRealName("r.mamun"))
    assertEquals("jahangir", CustomerSortUtils.extractRealName("r.jahangir"))
    assertEquals("JISAN", CustomerSortUtils.getSortKey("r.jisan"))
    assertEquals("NAFIZ", CustomerSortUtils.getSortKey("A6_Nafiz"))
    assertEquals("MAMUN", CustomerSortUtils.getSortKey("r.mamun"))
    assertEquals("JAHANGIR", CustomerSortUtils.getSortKey("r.jahangir"))

    // Advanced Prefix/Suffix/Formatting tests
    assertEquals("jisan", CustomerSortUtils.extractRealName("jisan_A6"))
    assertEquals("jisan", CustomerSortUtils.extractRealName("jisan A6"))
    assertEquals("jisan", CustomerSortUtils.extractRealName("jisan [A6]"))
    assertEquals("jisan", CustomerSortUtils.extractRealName("jisan (C-01)"))
    assertEquals("jisan", CustomerSortUtils.extractRealName("r jisan"))
    assertEquals("Jisan", CustomerSortUtils.extractRealName("[A6] Jisan"))
    assertEquals("Jisan", CustomerSortUtils.extractRealName("#12 Jisan"))
    assertEquals("jisan", CustomerSortUtils.extractRealName("jisan r."))
    assertEquals("jisan", CustomerSortUtils.extractRealName("jisan 101"))
  }
}

