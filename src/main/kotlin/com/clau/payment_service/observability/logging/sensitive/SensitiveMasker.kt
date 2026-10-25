package com.clau.payment_service.observability.logging.sensitive

import java.security.MessageDigest

object SensitiveMasker {

    const val PLACEHOLDER = "***"

    fun mask(value: String, annotation: Sensitive): String = when (annotation.mode) {
        Sensitive.Mode.FULL -> PLACEHOLDER
        Sensitive.Mode.KEEP_LAST -> keepLast(value, annotation.keep)
        Sensitive.Mode.HASH -> hash(value)
    }

    private fun keepLast(value: String, keep: Int): String {
        val significant = value.count { it.isLetterOrDigit() }
        if (keep <= 0 || significant <= keep) {
            return PLACEHOLDER
        }
        var remaining = significant - keep
        return buildString(value.length) {
            for (character in value) {
                when {
                    !character.isLetterOrDigit() -> append(character)
                    remaining > 0 -> {
                        append('*')
                        remaining--
                    }
                    else -> append(character)
                }
            }
        }
    }

    private fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return "sha256:" + digest.take(6).joinToString("") { "%02x".format(it) }
    }
}
