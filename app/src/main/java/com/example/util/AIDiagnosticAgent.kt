package com.example.util

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hardened production-ready AI Diagnostic Agent.
 * 
 * Direct client-side Gemini API calls are safely disabled to comply with production-grade 
 * security standards and prevent API key leakage. Safe AI root-cause analysis requires 
 * routing requests through a trusted, authenticated backend proxy server.
 */
object AIDiagnosticAgent {
    private const val TAG = "AIDiagnosticAgent"

    // Privacy-by-design: strict allowlist of allowed diagnostic modules and check IDs
    private val ALLOWED_CHECK_IDS = setOf(
        "app_startup",
        "firebase_auth",
        "firestore_conn",
        "cloud_sync_status",
        "local_database",
        "network_conn",
        "app_update",
        "recent_errors",
        "billing_calc",
        "customer_errors",
        "payment_errors",
        "sms_errors",
        "printing_errors",
        "navigation_check",
        "permission_errors",
        "performance_issues",
        "internal_check"
    )

    private val ALLOWED_CATEGORIES = setOf(
        "Application",
        "Firebase",
        "Database",
        "Network",
        "Invoicing",
        "Customers",
        "Transactions",
        "Notifications",
        "Printing",
        "Permissions",
        "Performance"
    )

    // Robust regex-based sanitization fallback filter for error logs
    fun sanitize(text: String): String {
        var sanitized = text
        // Redact email addresses
        sanitized = sanitized.replace(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}"), "[REDACTED_EMAIL]")
        // Redact mobile numbers (Bangladesh 11-digit or general patterns)
        sanitized = sanitized.replace(Regex("(?:\\+?88)?01[3-9]\\d{8}"), "[REDACTED_PHONE]")
        // Redact generic Google API keys
        sanitized = sanitized.replace(Regex("AIzaSy[A-Za-z0-9_\\-]{33}"), "[REDACTED_API_KEY]")
        // Redact Firestore/Firebase UIDs or hashes (28 chars alphanumeric)
        sanitized = sanitized.replace(Regex("[A-Za-z0-9]{28}"), "[REDACTED_ID]")
        return sanitized
    }

    suspend fun analyzeDiagnostics(
        context: Context,
        result: DiagnosticResult
    ): DiagnosticReport = withContext(Dispatchers.IO) {
        Log.i(TAG, "Initiating diagnostic payload validation and data minimization.")

        // 1. Structural Payload Validation (Privacy Allowlist Check)
        val verifiedChecks = mutableListOf<DiagnosticCheck>()
        for (check in result.checks) {
            if (check.id in ALLOWED_CHECK_IDS && check.category in ALLOWED_CATEGORIES) {
                // Ensure text is structurally sanitized first
                val cleanMessage = sanitize(check.message)
                verifiedChecks.add(
                    check.copy(message = cleanMessage)
                )
            } else {
                Log.w(TAG, "Security Alert: Diagnostic checkpoint rejected due to unrecognized ID or Category: id=${check.id}, cat=${check.category}")
            }
        }

        // 2. Direct client-side Gemini analysis check and disablement
        // As no server backend currently exists in this client project, direct calls to Google's endpoint 
        // are disabled to comply with 'Zero Client-Side Secrets' rules and protect API keys.
        Log.w(TAG, "Direct client-side Gemini requests are disabled for security hardening.")

        val problems = listOf(
            DiagnosticProblem(
                title = "Backend Server Proxy Required / ব্যাকএন্ড প্রক্সি প্রয়োজন",
                severity = DiagnosticSeverity.CRITICAL,
                affectedModule = "AI Diagnostics",
                possibleCause = "Direct client-side API requests have been safely disabled to prevent credential leakage. An authenticated backend server proxy is required to perform AI assessments safely.",
                evidence = "Direct API Call Blocked / ক্লায়েন্ট-সাইড অ্যাক্সেস ব্লকড",
                recommendedSolution = "Configure a secure diagnostic gateway on your production server. Set up your client app to authenticate against your secure server proxy which will then communicate safely with the Gemini model.",
                confidence = "100%"
            )
        )

        val localizedExplanation = """
            নিরাপত্তার স্বার্থে সরাসরি ক্লায়েন্ট-সাইড Gemini API কল নিষ্ক্রিয় করা হয়েছে। অ্যাপের এপিআই কী সুরক্ষার জন্য এআই ডায়াগনস্টিকস চালানোর জন্য একটি নিরাপদ ব্যাকএন্ড প্রক্সি (Backend Proxy) প্রয়োজন।

            সিস্টেমের স্থানীয় ডায়াগনস্টিকস (Local Diagnostics) এবং চেকলিস্টসমূহ সম্পূর্ণ সচল আছে এবং আপনি উপরে প্রতিটি মডিউলের বিস্তারিত স্ট্যাটাস দেখতে পাচ্ছেন।

            Direct client-side Gemini API execution has been safely disabled to protect application credentials. Secure AI analysis requires a backend server proxy configuration. Local diagnostic checks remain fully operational.
        """.trimIndent()

        DiagnosticReport(
            problems = problems,
            likelyRootCause = "Backend Proxy Setup Pending",
            explanation = localizedExplanation,
            rawAiResponse = "SECURITY_HARDENING_ACTIVE: Direct client-side calls to generativelanguage.googleapis.com are disabled."
        )
    }
}
