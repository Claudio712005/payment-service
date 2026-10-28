package com.clau.payment_service.cache

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cache")
data class CacheProperties(
    val enabled: Boolean = true,
    val keyPrefix: String = "payment",
    val defaultTtl: Duration = Duration.ofMinutes(10),
    val cacheNullValues: Boolean = true,
    val ttls: Map<String, Duration> = emptyMap(),
    val circuit: CircuitProperties = CircuitProperties()
) {

    data class CircuitProperties(
        val failureThreshold: Int = 3,
        val cooldown: Duration = Duration.ofSeconds(30)
    )
}
