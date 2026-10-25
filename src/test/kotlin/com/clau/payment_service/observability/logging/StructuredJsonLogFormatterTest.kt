package com.clau.payment_service.observability.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.pattern.ThrowableProxyConverter
import ch.qos.logback.classic.spi.LoggingEvent
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.slf4j.MDC
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

class StructuredJsonLogFormatterTest {

    private val loggerContext = LoggerContext().apply { mdcAdapter = MDC.getMDCAdapter() }
    private val throwableProxyConverter = ThrowableProxyConverter().apply { start() }

    private val environment = StandardEnvironment().apply {
        propertySources.addFirst(
            MapPropertySource("test", mapOf("spring.application.name" to "payment-service"))
        )
        setActiveProfiles("local")
    }

    private val formatter = StructuredJsonLogFormatter(environment, throwableProxyConverter, null)

    @AfterTest
    fun limpar() {
        MDC.clear()
    }

    @Test
    fun `emite envelope json com os campos do evento`() {
        val json = formatter.format(event("pagamento confirmado"))

        assertTrue(json.contains(""""level":"INFO""""), json)
        assertTrue(json.contains(""""logger":"com.clau.pagamento""""), json)
        assertTrue(json.contains(""""application":"payment-service""""), json)
        assertTrue(json.contains(""""environment":"local""""), json)
        assertTrue(json.contains(""""message":"pagamento confirmado""""), json)
    }

    @Test
    fun `promove os quatro identificadores de rastreio para o topo`() {
        MDC.put(CorrelationKeys.TRACE_ID, "4bf92f3577b34da6a3ce929d0e0e4736")
        MDC.put(CorrelationKeys.SPAN_ID, "00f067aa0ba902b7")
        MDC.put(CorrelationKeys.REQUEST_ID, "req-1")
        MDC.put(CorrelationKeys.CORRELATION_ID, "corr-1")

        val json = formatter.format(event("ok"))

        assertTrue(json.contains(""""traceId":"4bf92f3577b34da6a3ce929d0e0e4736""""), json)
        assertTrue(json.contains(""""spanId":"00f067aa0ba902b7""""), json)
        assertTrue(json.contains(""""requestId":"req-1""""), json)
        assertTrue(json.contains(""""correlationId":"corr-1""""), json)
    }

    @Test
    fun `omite identificador ausente em vez de emitir vazio`() {
        val json = formatter.format(event("ok"))

        assertFalse(json.contains("traceId"), json)
        assertFalse(json.contains("correlationId"), json)
    }

    @Test
    fun `aninha o mdc restante em context`() {
        MDC.put(CorrelationKeys.REQUEST_ID, "req-2")
        MDC.put("pagamentoId", "e1f0")

        val json = formatter.format(event("ok"))

        assertTrue(json.contains(""""context":{"pagamentoId":"e1f0"}"""), json)
    }

    @Test
    fun `emite erro aninhado com tipo mensagem e stacktrace`() {
        val json = formatter.format(event("erro", IllegalStateException("falha ao cobrar")))

        assertTrue(json.contains(""""error":{"""), json)
        assertTrue(json.contains(""""type":"java.lang.IllegalStateException""""), json)
        assertTrue(json.contains(""""message":"falha ao cobrar""""), json)
        assertTrue(json.contains("stackTrace"), json)
    }

    @Test
    fun `nao emite erro quando nao ha excecao`() {
        assertFalse(formatter.format(event("ok")).contains(""""error""""))
    }

    private fun event(message: String, throwable: Throwable? = null): LoggingEvent = LoggingEvent(
        StructuredJsonLogFormatterTest::class.java.name,
        loggerContext.getLogger("com.clau.pagamento"),
        Level.INFO,
        message,
        throwable,
        null
    )
}
