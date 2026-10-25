package com.clau.payment_service.observability.web

import com.clau.payment_service.observability.logging.CorrelationKeys
import jakarta.servlet.FilterChain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.slf4j.MDC
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class CorrelationFilterTest {

    private val properties = CorrelationProperties()
    private val filter = CorrelationFilter(properties)

    @Test
    fun `gera requestId quando o cabecalho nao vem`() {
        val response = MockHttpServletResponse()
        val capturado = capturar()

        filter.doFilter(MockHttpServletRequest("GET", "/pagamentos"), response, capturado.chain)

        val requestId = capturado.valores[CorrelationKeys.REQUEST_ID]
        assertNotNull(requestId)
        assertEquals(32, requestId.length)
        assertEquals(requestId, response.getHeader(properties.requestHeader))
    }

    @Test
    fun `reaproveita requestId e correlationId recebidos`() {
        val request = MockHttpServletRequest("GET", "/pagamentos")
        request.addHeader(properties.requestHeader, "req-abc")
        request.addHeader(properties.correlationHeader, "corr-xyz")
        val capturado = capturar()

        filter.doFilter(request, MockHttpServletResponse(), capturado.chain)

        assertEquals("req-abc", capturado.valores[CorrelationKeys.REQUEST_ID])
        assertEquals("corr-xyz", capturado.valores[CorrelationKeys.CORRELATION_ID])
    }

    @Test
    fun `usa o requestId como correlationId quando so um chega`() {
        val request = MockHttpServletRequest("GET", "/pagamentos")
        request.addHeader(properties.requestHeader, "req-abc")
        val capturado = capturar()

        filter.doFilter(request, MockHttpServletResponse(), capturado.chain)

        assertEquals("req-abc", capturado.valores[CorrelationKeys.CORRELATION_ID])
    }

    @Test
    fun `descarta caractere invalido e limita o tamanho do cabecalho`() {
        val request = MockHttpServletRequest("GET", "/pagamentos")
        request.addHeader(properties.requestHeader, "req\n\"inject\" " + "a".repeat(200))
        val capturado = capturar()

        filter.doFilter(request, MockHttpServletResponse(), capturado.chain)

        val requestId = capturado.valores[CorrelationKeys.REQUEST_ID]
        assertNotNull(requestId)
        assertEquals(properties.maxLength, requestId.length)
        assertTrue(!requestId.contains('"'))
        assertTrue(!requestId.contains('\n'))
    }

    @Test
    fun `limpa o mdc ao final da requisicao`() {
        filter.doFilter(MockHttpServletRequest("GET", "/pagamentos"), MockHttpServletResponse(), MockFilterChain())

        assertNull(MDC.get(CorrelationKeys.REQUEST_ID))
        assertNull(MDC.get(CorrelationKeys.CORRELATION_ID))
    }

    @Test
    fun `limpa o mdc mesmo quando a cadeia falha`() {
        val explosiva = FilterChain { _, _ -> throw IllegalStateException("boom") }

        runCatching {
            filter.doFilter(MockHttpServletRequest("GET", "/pagamentos"), MockHttpServletResponse(), explosiva)
        }

        assertNull(MDC.get(CorrelationKeys.REQUEST_ID))
    }

    private fun capturar(): Captura {
        val valores = mutableMapOf<String, String?>()
        val chain = FilterChain { _, _ ->
            CorrelationKeys.ALL.forEach { valores[it] = MDC.get(it) }
        }
        return Captura(chain, valores)
    }

    private class Captura(val chain: FilterChain, val valores: MutableMap<String, String?>)
}
