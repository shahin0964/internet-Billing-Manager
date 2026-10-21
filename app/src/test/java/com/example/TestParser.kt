package com.example

import org.junit.Test
import org.junit.Assert.*

class TestParser {
    @Test
    fun testParse() {
        val sampleJson = """{"status":true,"message":"Login successful!","user":{"id":"usr_1","name":"NEXTWAVE","email":"nextwaveinternet11@gmail.com","api_token":"tok_123"}}"""

        // 1. UTF-8 BOM prefix
        val (bomCleaned, bomValid) = com.example.data.remote.ApiClient.sanitizeJsonString("\uFEFF$sampleJson")
        assertTrue(bomValid)
        assertEquals(sampleJson, bomCleaned)

        // 2. PHP Warning + CSS before JSON
        val phpWarningBody = """<style>{body{background-color:#000}}</style><br /><b>Warning</b>: PDO::exec() in <b>/home/db.php</b> on line 10<br />$sampleJson"""
        val (phpCleaned, phpValid) = com.example.data.remote.ApiClient.sanitizeJsonString(phpWarningBody)
        assertTrue(phpValid)
        assertEquals(sampleJson, phpCleaned)

        // 3. Double-encoded JSON string
        val doubleEncoded = com.google.gson.Gson().toJson(sampleJson)
        val (doubleCleaned, doubleValid) = com.example.data.remote.ApiClient.sanitizeJsonString(doubleEncoded)
        assertTrue(doubleValid)
        assertEquals(sampleJson, doubleCleaned)

        // 4. Raw GZIP compressed bytes
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(baos).use { it.write(sampleJson.toByteArray(Charsets.UTF_8)) }
        val (gzipCleaned, gzipValid) = com.example.data.remote.ApiClient.sanitizeResponseBytes(baos.toByteArray())
        assertTrue(gzipValid)
        assertEquals(sampleJson, gzipCleaned)

        // 5. Pure HTML / string error fallback (must still produce valid JSON Object for Gson)
        val (htmlFallback, htmlValid) = com.example.data.remote.ApiClient.sanitizeJsonString("<html><body>502 Bad Gateway</body></html>")
        assertFalse(htmlValid)
        assertTrue(com.example.data.remote.ApiClient.isStrictJsonObject(htmlFallback))
        val parsedFallback = com.google.gson.Gson().fromJson(htmlFallback, com.example.data.remote.LoginResponse::class.java)
        assertFalse(parsedFallback.status)
        assertNotNull(parsedFallback.message)
    }
}
