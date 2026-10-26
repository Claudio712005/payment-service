package com.clau.payment_service.web.validation

import com.clau.payment_service.model.enums.MeioDePagamento
import jakarta.validation.Validation
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParcelamentoCoerenteValidatorTest {

    @ParcelamentoCoerente
    private data class Cobranca(
        override val meio: MeioDePagamento?,
        override val parcelas: Int
    ) : Parcelavel

    private val factory = Validation.buildDefaultValidatorFactory()
    private val validator = factory.validator

    @AfterTest
    fun fechar() {
        factory.close()
    }

    @Test
    fun `parcela unica e coerente com qualquer meio`() {
        MeioDePagamento.entries.forEach { meio ->
            assertTrue(validator.validate(Cobranca(meio, 1)).isEmpty(), meio.name)
        }
    }

    @Test
    fun `parcelamento e aceito no cartao de credito`() {
        assertTrue(validator.validate(Cobranca(MeioDePagamento.CARTAO_CREDITO, 6)).isEmpty())
    }

    @Test
    fun `parcelamento e recusado em pix boleto e debito`() {
        listOf(
            MeioDePagamento.PIX,
            MeioDePagamento.BOLETO,
            MeioDePagamento.CARTAO_DEBITO
        ).forEach { meio ->
            assertFalse(validator.validate(Cobranca(meio, 3)).isEmpty(), meio.name)
        }
    }

    @Test
    fun `a violacao aponta o campo parcelas e nao a classe`() {
        val violacao = validator.validate(Cobranca(MeioDePagamento.PIX, 3)).single()

        assertEquals("parcelas", violacao.propertyPath.toString())
        assertEquals("{validacao.parcelamento.incoerente}", violacao.messageTemplate)
    }

    @Test
    fun `meio ausente deixa a decisao para a restricao de obrigatoriedade`() {
        assertTrue(validator.validate(Cobranca(null, 5)).isEmpty())
    }
}
