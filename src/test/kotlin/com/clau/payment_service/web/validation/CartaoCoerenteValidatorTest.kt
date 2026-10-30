package com.clau.payment_service.web.validation

import com.clau.payment_service.model.enums.MeioDePagamento
import com.clau.payment_service.web.dto.fragmento.CartaoDto
import jakarta.validation.Validation
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CartaoCoerenteValidatorTest {

    @CartaoCoerente
    private data class Cobranca(
        override val meio: MeioDePagamento?,
        override val cartao: CartaoDto?
    ) : PagavelComCartao

    private val cartao = CartaoDto(
        numero = "5031433215406351",
        nomeDoPortador = "ANA SOUZA",
        validadeMes = 11,
        validadeAno = 2030,
        codigoDeSeguranca = "123"
    )

    private val factory = Validation.buildDefaultValidatorFactory()
    private val validator = factory.validator

    @AfterTest
    fun fechar() {
        factory.close()
    }

    @Test
    fun `meio ausente nao e julgado aqui`() {
        assertTrue(validator.validate(Cobranca(null, null)).isEmpty())
        assertTrue(validator.validate(Cobranca(null, cartao)).isEmpty())
    }

    @Test
    fun `credito e debito exigem o cartao`() {
        listOf(MeioDePagamento.CARTAO_CREDITO, MeioDePagamento.CARTAO_DEBITO).forEach { meio ->
            val violacoes = validator.validate(Cobranca(meio, null))

            assertEquals(1, violacoes.size, meio.name)
            assertEquals("cartao", violacoes.first().propertyPath.toString(), meio.name)
            assertEquals("{validacao.cartao.obrigatorio}", violacoes.first().message, meio.name)
        }
    }

    @Test
    fun `credito e debito com cartao sao aceitos`() {
        listOf(MeioDePagamento.CARTAO_CREDITO, MeioDePagamento.CARTAO_DEBITO).forEach { meio ->
            assertTrue(validator.validate(Cobranca(meio, cartao)).isEmpty(), meio.name)
        }
    }

    @Test
    fun `pix e boleto recusam o cartao`() {
        listOf(MeioDePagamento.PIX, MeioDePagamento.BOLETO).forEach { meio ->
            val violacoes = validator.validate(Cobranca(meio, cartao))

            assertEquals(1, violacoes.size, meio.name)
            assertEquals("cartao", violacoes.first().propertyPath.toString(), meio.name)
            assertEquals("{validacao.cartao.nao-aplicavel}", violacoes.first().message, meio.name)
        }
    }

    @Test
    fun `pix e boleto sem cartao sao aceitos`() {
        listOf(MeioDePagamento.PIX, MeioDePagamento.BOLETO).forEach { meio ->
            assertTrue(validator.validate(Cobranca(meio, null)).isEmpty(), meio.name)
        }
    }
}
