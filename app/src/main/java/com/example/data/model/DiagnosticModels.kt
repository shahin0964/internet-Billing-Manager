package com.example.data.model

enum class DiagnosticStatus {
    HEALTHY,      // ✅ Healthy
    WARNING,      // ⚠️ Warning
    PROBLEM_FOUND, // ❌ Problem Found
    INFORMATION,   // ℹ️ Information
    CHECKING      // ⏳ Checking
}

enum class DiagnosticSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

data class DiagnosticCheck(
    val id: String,
    val name: String,
    val category: String,
    val status: DiagnosticStatus,
    val message: String,
    val module: String
)

data class DiagnosticResult(
    val checks: List<DiagnosticCheck>,
    val timestamp: Long = System.currentTimeMillis()
)

data class DiagnosticProblem(
    val title: String,
    val severity: DiagnosticSeverity,
    val affectedModule: String,
    val possibleCause: String,
    val evidence: String,
    val recommendedSolution: String,
    val confidence: String
)

data class DiagnosticReport(
    val problems: List<DiagnosticProblem>,
    val likelyRootCause: String?,
    val explanation: String,
    val rawAiResponse: String? = null
)
