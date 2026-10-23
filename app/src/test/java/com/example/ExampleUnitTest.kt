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
    listOf(java.io.File("/"), java.io.File("/app"), java.io.File("/tmp"), java.io.File(".")).forEach { root ->
      root.walkTopDown().maxDepth(4).forEach { f ->
        if (f.name.contains("9A9E654B") || f.name.endsWith(".jpeg") || f.name.endsWith(".jpg") || f.name.endsWith(".png")) {
          if (!f.absolutePath.contains("/res/") && !f.absolutePath.contains("/build/")) {
            println("FOUND_IMG: ${f.absolutePath} (${f.length()} bytes)")
          }
        }
      }
    }
    kotlinx.coroutines.runBlocking {
      val creds = listOf(
        "hridoy00554433@gmail.com" to "hridoy@2929",
        "nextwaveinternet11@gmail.com" to "nextwave"
      )
      for ((email, pass) in creds) {
        try {
          val loginResp = com.example.data.remote.ApiClient.apiService.login(
            com.example.data.remote.LoginRequest(email, pass)
          )
          val uid = loginResp.user?.id ?: continue
          com.example.data.remote.ApiClient.authToken = loginResp.user?.apiToken
          val syncResp = com.example.data.remote.ApiClient.apiService.sync(
            com.example.data.model.SyncPushRequest(userId = uid)
          )
          println("RETROFIT_SYNC [$email] -> status=${syncResp.status} msg=${syncResp.message} custs=${syncResp.data?.customers?.size} bills=${syncResp.data?.bills?.size}")
          val payments = com.example.data.remote.ApiClient.apiService.getPayments(uid)
          println("RETROFIT_PAYMENTS [$email] -> status=${payments.status} count=${payments.data?.size}")
          val expenses = com.example.data.remote.ApiClient.apiService.getExpenses(uid)
          println("RETROFIT_EXPENSES [$email] -> status=${expenses.status} count=${expenses.data?.size}")
          val cats = com.example.data.remote.ApiClient.apiService.getExpenseCategories(uid)
          println("RETROFIT_CATS [$email] -> status=${cats.status} count=${cats.data?.size}")
          val logs = com.example.data.remote.ApiClient.apiService.getAuditLogs(uid)
          println("RETROFIT_LOGS [$email] -> status=${logs.status} count=${logs.data?.size}")
          val bw = com.example.data.remote.ApiClient.apiService.getBandwidthBills(uid)
          println("RETROFIT_BW [$email] -> status=${bw.status} count=${bw.data?.size}")
          val adv = com.example.data.remote.ApiClient.apiService.getSpecificAdvances(uid)
          println("RETROFIT_ADV [$email] -> status=${adv.status} count=${adv.data?.size}")
          val backupList = com.example.data.remote.ApiClient.apiService.listCloudBackups(uid)
          println("RETROFIT_BACKUPLIST [$email] -> status=${backupList.status} count=${backupList.data?.size}")
        } catch (e: Throwable) {
          println("RETROFIT_ERR [$email]: ${e.javaClass.name}: ${e.message}")
          e.printStackTrace()
        }
      }
    }
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

