package com.clau.payment_service.resilience

enum class HttpFailureClass(private val range: IntRange) {
    TOO_MANY_REQUESTS(429..429),
    SERVER_ERROR(500..599),
    CLIENT_ERROR(400..428);

    fun matches(status: Int): Boolean = status in range
}
