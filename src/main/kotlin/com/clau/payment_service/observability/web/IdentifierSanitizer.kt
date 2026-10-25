package com.clau.payment_service.observability.web

class IdentifierSanitizer(private val maxLength: Int) {

    fun sanitize(value: String?): String? {
        if (value.isNullOrBlank()) {
            return null
        }
        val cleaned = value.trim().filter { it.isLetterOrDigit() || it in ALLOWED }
        return cleaned.take(maxLength).takeIf { it.isNotEmpty() }
    }

    private companion object {
        val ALLOWED = charArrayOf('-', '_', '.', ':')
    }
}
