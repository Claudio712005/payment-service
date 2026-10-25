package com.clau.payment_service.observability.logging.sensitive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogRendererTest {

    private data class Pagador(
        val nome: String,
        @Sensitive val cpf: String,
        @Sensitive(mode = Sensitive.Mode.HASH) val email: String
    )

    private data class Cobranca(
        val id: String,
        val valor: java.math.BigDecimal,
        @Sensitive(mode = Sensitive.Mode.KEEP_LAST, keep = 4) val cartao: String,
        @Sensitive val cvv: String?,
        val pagador: Pagador
    )

    private val cobranca = Cobranca(
        id = "e1f0",
        valor = java.math.BigDecimal("150.00"),
        cartao = "4111 1111 1111 1234",
        cvv = "123",
        pagador = Pagador("Ana", "123.456.789-09", "ana@exemplo.com")
    )

    @Test
    fun `mascara campo anotado e preserva o resto`() {
        val rendered = LogRenderer.render(cobranca)

        assertTrue(rendered.contains("id=e1f0"), rendered)
        assertTrue(rendered.contains("valor=150.00"), rendered)
        assertFalse(rendered.contains("123.456.789-09"), rendered)
        assertFalse(rendered.contains("4111 1111 1111"), rendered)
        assertFalse(rendered.contains("ana@exemplo.com"), rendered)
    }

    @Test
    fun `mascara integralmente por padrao`() {
        assertTrue(LogRenderer.render(cobranca).contains("cvv=***"))
    }

    @Test
    fun `preserva os ultimos digitos quando pedido`() {
        assertTrue(LogRenderer.render(cobranca).contains("cartao=**** **** **** 1234"))
    }

    @Test
    fun `aplica hash estavel para correlacionar sem expor`() {
        val primeiro = LogRenderer.render(cobranca)
        val segundo = LogRenderer.render(cobranca)

        assertTrue(primeiro.contains("email=sha256:"), primeiro)
        assertEquals(primeiro, segundo)
    }

    @Test
    fun `desce em objeto aninhado`() {
        val rendered = LogRenderer.render(cobranca)

        assertTrue(rendered.contains("pagador=Pagador("), rendered)
        assertTrue(rendered.contains("nome=Ana"), rendered)
    }

    @Test
    fun `mascara dentro de colecao`() {
        val rendered = LogRenderer.render(listOf(cobranca.pagador))

        assertTrue(rendered.startsWith("[Pagador("), rendered)
        assertFalse(rendered.contains("123.456.789-09"), rendered)
    }

    @Test
    fun `nulo anotado nao vira placeholder`() {
        val rendered = LogRenderer.render(cobranca.copy(cvv = null))

        assertTrue(rendered.contains("cvv=null"), rendered)
    }

    @Test
    fun `renderiza tipos simples sem reflexao`() {
        assertEquals("null", LogRenderer.render(null))
        assertEquals("42", LogRenderer.render(42))
        assertEquals("texto", LogRenderer.render("texto"))
    }

    @Test
    fun `loggable so renderiza quando convertido em texto`() {
        val envelope = cobranca.loggable()

        assertFalse(envelope.toString().contains("123.456.789-09"))
    }
}
