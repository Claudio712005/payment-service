package com.clau.payment_service.observability.logging

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.logging.LoggingInitializationContext
import org.springframework.boot.logging.logback.LogbackLoggingSystem
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

class LogbackConfigurationTest {

    private val loggingSystem = LogbackLoggingSystem(javaClass.classLoader)

    @AfterTest
    fun restaurar() {
        MDC.clear()
        loggingSystem.cleanUp()
    }

    @Test
    fun `modo json emite envelope estruturado com os quatro identificadores`() {
        val saida = capturar(mapOf("observability.logging.root-appender" to "CONSOLE_JSON"))

        assertTrue(saida.contains(""""application":"payment-service""""), saida)
        assertTrue(saida.contains(""""traceId":"4bf92f3577b34da6a3ce929d0e0e4736""""), saida)
        assertTrue(saida.contains(""""spanId":"00f067aa0ba902b7""""), saida)
        assertTrue(saida.contains(""""requestId":"req-1""""), saida)
        assertTrue(saida.contains(""""correlationId":"corr-1""""), saida)
    }

    @Test
    fun `modo json aninha o mdc restante em context`() {
        val saida = capturar(mapOf("observability.logging.root-appender" to "CONSOLE_JSON"))

        assertTrue(saida.contains(""""context":{"pagamentoId":"e1f0"}"""), saida)
    }

    @Test
    fun `modo plain emite os quatro identificadores e o stacktrace`() {
        val saida = capturar(
            mapOf(
                "observability.logging.root-appender" to "CONSOLE_PLAIN",
                "observability.logging.console-appender" to "CONSOLE_PLAIN"
            )
        )

        assertTrue(
            saida.contains("[payment-service,4bf92f3577b34da6a3ce929d0e0e4736,00f067aa0ba902b7,req-1,corr-1]"),
            saida
        )
        assertTrue(saida.contains("java.lang.IllegalStateException"), saida)
    }

    @Test
    fun `appender assincrono e o padrao e escoa os eventos`() {
        val saida = capturar(emptyMap(), esperaMillis = 1000)

        assertTrue(saida.contains(""""level":"INFO""""), saida)
    }

    private fun capturar(extras: Map<String, Any>, esperaMillis: Long = 0): String {
        val original = System.out
        val buffer = ByteArrayOutputStream()
        val environment = StandardEnvironment().apply {
            propertySources.addFirst(
                MapPropertySource(
                    "test",
                    mapOf("spring.application.name" to "payment-service") + extras
                )
            )
            setActiveProfiles("local")
        }
        try {
            System.setOut(PrintStream(buffer, true))
            loggingSystem.cleanUp()
            loggingSystem.beforeInitialize()
            loggingSystem.initialize(LoggingInitializationContext(environment), CONFIGURACAO, null)

            MDC.put(CorrelationKeys.TRACE_ID, "4bf92f3577b34da6a3ce929d0e0e4736")
            MDC.put(CorrelationKeys.SPAN_ID, "00f067aa0ba902b7")
            MDC.put(CorrelationKeys.REQUEST_ID, "req-1")
            MDC.put(CorrelationKeys.CORRELATION_ID, "corr-1")
            MDC.put("pagamentoId", "e1f0")

            val logger = LoggerFactory.getLogger("com.clau.payment_service.Cobranca")
            logger.info("cobranca registrada")
            logger.error("falha ao cobrar", IllegalStateException("provedor indisponivel"))

            if (esperaMillis > 0) {
                Thread.sleep(esperaMillis)
            }
        } finally {
            System.setOut(original)
        }
        return buffer.toString()
    }

    private companion object {
        const val CONFIGURACAO = "classpath:logback-spring.xml"
    }
}
