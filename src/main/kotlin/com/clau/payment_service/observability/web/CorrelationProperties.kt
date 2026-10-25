package com.clau.payment_service.observability.web

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "observability.correlation")
data class CorrelationProperties(
    val requestHeader: String = "X-Request-Id",
    val correlationHeader: String = "X-Correlation-Id",
    val echoResponseHeaders: Boolean = true,
    val maxLength: Int = 64
)
