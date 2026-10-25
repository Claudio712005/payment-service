package com.clau.payment_service.resilience

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "resilience")
data class ResilienceProperties(
    val bulkheads: Map<ResilienceDomain, BulkheadProperties> = emptyMap(),
    val circuitBreakers: Map<ResilienceOperation, CircuitBreakerProperties> = emptyMap(),
    val retries: Map<ResilienceOperation, RetryProperties> = emptyMap(),
    val defaultBulkhead: BulkheadProperties = BulkheadProperties(),
    val defaultCircuitBreaker: CircuitBreakerProperties = CircuitBreakerProperties(),
    val defaultRetry: RetryProperties = RetryProperties()
) {

    data class BulkheadProperties(
        val maxConcurrentCalls: Int = 8,
        val maxWait: Duration = Duration.ZERO
    )

    data class CircuitBreakerProperties(
        val failureRateThreshold: Float = 50f,
        val slowCallRateThreshold: Float = 100f,
        val slowCallDuration: Duration = Duration.ofSeconds(10),
        val slidingWindow: SlidingWindow = SlidingWindow.COUNT_BASED,
        val slidingWindowSize: Int = 20,
        val minimumNumberOfCalls: Int = 10,
        val waitInOpenState: Duration = Duration.ofSeconds(30),
        val permittedCallsInHalfOpenState: Int = 3,
        val recordedFailures: Set<HttpFailureClass> = setOf(
            HttpFailureClass.TOO_MANY_REQUESTS,
            HttpFailureClass.SERVER_ERROR
        ),
        val recordConnectionFailures: Boolean = true
    )

    data class RetryProperties(
        val enabled: Boolean? = null,
        val maxAttempts: Int = 3,
        val initialBackoff: Duration = Duration.ofMillis(200),
        val multiplier: Double = 2.0,
        val maxBackoff: Duration = Duration.ofSeconds(2),
        val jitter: Double = 0.0
    )

    fun bulkheadOf(domain: ResilienceDomain): BulkheadProperties =
        bulkheads[domain] ?: defaultBulkhead

    fun circuitBreakerOf(operation: ResilienceOperation): CircuitBreakerProperties =
        circuitBreakers[operation] ?: defaultCircuitBreaker

    fun retryOf(operation: ResilienceOperation): RetryProperties =
        retries[operation] ?: defaultRetry

    fun retryEnabledFor(operation: ResilienceOperation): Boolean {
        val configured = retryOf(operation).enabled
        if (configured == true && !operation.idempotent) {
            throw IllegalStateException(
                "resilience.retries.${operation.name.lowercase().replace('_', '-')}.enabled=true " +
                    "em operacao nao idempotente: repetir ${operation.name} duplica efeito no provedor. " +
                    "Para permitir, a operacao precisa ser declarada idempotente em ResilienceOperation."
            )
        }
        return configured ?: operation.idempotent
    }
}
