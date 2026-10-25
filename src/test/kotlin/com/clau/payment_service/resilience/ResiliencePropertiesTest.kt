package com.clau.payment_service.resilience

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

class ResiliencePropertiesTest {

    private fun bind(valores: Map<String, Any>): ResilienceProperties = Binder(
        MapConfigurationPropertySource(valores)
    ).bind("resilience", Bindable.of(ResilienceProperties::class.java)).get()

    @Test
    fun `liga chave kebab-case do yaml ao enum de dominio`() {
        val properties = bind(
            mapOf(
                "resilience.bulkheads.payment-provider.max-concurrent-calls" to "16",
                "resilience.bulkheads.payment-provider.max-wait" to "50ms",
                "resilience.bulkheads.invoice-authority.max-concurrent-calls" to "6"
            )
        )

        val provedor = properties.bulkheadOf(ResilienceDomain.PAYMENT_PROVIDER)
        assertEquals(16, provedor.maxConcurrentCalls)
        assertEquals(Duration.ofMillis(50), provedor.maxWait)
        assertEquals(6, properties.bulkheadOf(ResilienceDomain.INVOICE_AUTHORITY).maxConcurrentCalls)
    }

    @Test
    fun `liga chave kebab-case do yaml ao enum de operacao`() {
        val properties = bind(
            mapOf(
                "resilience.circuit-breakers.charge-query.sliding-window" to "TIME_BASED",
                "resilience.circuit-breakers.charge-query.failure-rate-threshold" to "60",
                "resilience.retries.invoice-query.max-attempts" to "5",
                "resilience.retries.invoice-query.initial-backoff" to "1s",
                "resilience.retries.invoice-query.jitter" to "0.5"
            )
        )

        val breaker = properties.circuitBreakerOf(ResilienceOperation.CHARGE_QUERY)
        assertEquals(SlidingWindow.TIME_BASED, breaker.slidingWindow)
        assertEquals(60f, breaker.failureRateThreshold)

        val retry = properties.retryOf(ResilienceOperation.INVOICE_QUERY)
        assertEquals(5, retry.maxAttempts)
        assertEquals(Duration.ofSeconds(1), retry.initialBackoff)
        assertEquals(0.5, retry.jitter)
    }

    @Test
    fun `operacao sem entrada cai no default`() {
        val properties = bind(mapOf("resilience.default-bulkhead.max-concurrent-calls" to "4"))

        assertEquals(4, properties.bulkheadOf(ResilienceDomain.PAYMENT_PROVIDER).maxConcurrentCalls)
        assertEquals(
            SlidingWindow.COUNT_BASED,
            properties.circuitBreakerOf(ResilienceOperation.INVOICE_CANCEL).slidingWindow
        )
    }

    @Test
    fun `apenas 429 e 5xx entram no circuito por padrao`() {
        val recorded = ResilienceProperties().defaultCircuitBreaker.recordedFailures

        assertEquals(setOf(HttpFailureClass.TOO_MANY_REQUESTS, HttpFailureClass.SERVER_ERROR), recorded)
    }

    @Test
    fun `classe de falha pode ser declarada no yaml`() {
        val properties = bind(
            mapOf("resilience.circuit-breakers.charge-create.recorded-failures" to "SERVER_ERROR")
        )

        assertEquals(
            setOf(HttpFailureClass.SERVER_ERROR),
            properties.circuitBreakerOf(ResilienceOperation.CHARGE_CREATE).recordedFailures
        )
    }

    @Test
    fun `retry segue a idempotencia da operacao quando nao declarado`() {
        val properties = ResilienceProperties()

        assertTrue(properties.retryEnabledFor(ResilienceOperation.CHARGE_QUERY))
        assertTrue(properties.retryEnabledFor(ResilienceOperation.CHARGE_REFUND))
        assertFalse(properties.retryEnabledFor(ResilienceOperation.CHARGE_CREATE))
        assertFalse(properties.retryEnabledFor(ResilienceOperation.INVOICE_ISSUE))
    }

    @Test
    fun `retry pode ser desligado em operacao idempotente`() {
        val properties = bind(mapOf("resilience.retries.charge-query.enabled" to "false"))

        assertFalse(properties.retryEnabledFor(ResilienceOperation.CHARGE_QUERY))
    }

    @Test
    fun `retry em operacao nao idempotente e recusado com a chave no erro`() {
        val properties = bind(mapOf("resilience.retries.invoice-issue.enabled" to "true"))

        val erro = assertFailsWith<IllegalStateException> {
            properties.retryEnabledFor(ResilienceOperation.INVOICE_ISSUE)
        }
        assertTrue(erro.message!!.contains("resilience.retries.invoice-issue.enabled=true"), erro.message!!)
    }

    @Test
    fun `classe de falha casa a faixa de status`() {
        assertTrue(HttpFailureClass.TOO_MANY_REQUESTS.matches(429))
        assertFalse(HttpFailureClass.TOO_MANY_REQUESTS.matches(428))
        assertTrue(HttpFailureClass.SERVER_ERROR.matches(500))
        assertTrue(HttpFailureClass.SERVER_ERROR.matches(599))
        assertFalse(HttpFailureClass.SERVER_ERROR.matches(499))
        assertTrue(HttpFailureClass.CLIENT_ERROR.matches(404))
        assertFalse(HttpFailureClass.CLIENT_ERROR.matches(429))
    }
}
