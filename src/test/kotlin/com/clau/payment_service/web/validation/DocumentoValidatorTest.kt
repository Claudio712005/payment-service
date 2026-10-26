package com.clau.payment_service.web.validation

import jakarta.validation.Validation
import jakarta.validation.Validator
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentoValidatorTest {

    private data class Qualquer(@field:Documento val numero: String?)

    private data class SomenteCnpj(
        @field:Documento(tipos = [TipoDeDocumento.CNPJ]) val numero: String?
    )

    private val factory = Validation.buildDefaultValidatorFactory()
    private val validator: Validator = factory.validator

    @AfterTest
    fun fechar() {
        factory.close()
    }

    private fun aceita(numero: String?): Boolean = validator.validate(Qualquer(numero)).isEmpty()

    @Test
    fun `aceita cpf valido com e sem pontuacao`() {
        assertTrue(aceita("52998224725"))
        assertTrue(aceita("529.982.247-25"))
        assertTrue(aceita("111.444.777-35"))
        assertTrue(aceita("123.456.789-09"))
    }

    @Test
    fun `recusa cpf com digito verificador errado`() {
        assertFalse(aceita("52998224726"))
        assertFalse(aceita("111.444.777-30"))
    }

    @Test
    fun `aceita cnpj valido com e sem pontuacao`() {
        assertTrue(aceita("11222333000181"))
        assertTrue(aceita("11.222.333/0001-81"))
        assertTrue(aceita("04.252.011/0001-10"))
    }

    @Test
    fun `recusa cnpj com digito verificador errado`() {
        assertFalse(aceita("11222333000182"))
        assertFalse(aceita("04.252.011/0001-11"))
    }

    @Test
    fun `recusa documento com todos os digitos iguais`() {
        assertFalse(aceita("11111111111"))
        assertFalse(aceita("00000000000"))
        assertFalse(aceita("11111111111111"))
    }

    @Test
    fun `aceita cnpj de radical repetido com digito verificador valido`() {
        assertTrue(aceita("11111111111180"))
    }

    @Test
    fun `recusa quantidade de digitos fora de cpf e cnpj`() {
        assertFalse(aceita("1234567890"))
        assertFalse(aceita("123456789012"))
        assertFalse(aceita(""))
    }

    @Test
    fun `recusa caractere que nao e digito nem separador`() {
        assertFalse(aceita("529.982.247-2X"))
        assertFalse(aceita("529 982 247 25"))
    }

    @Test
    fun `nulo e aceito porque obrigatoriedade e outra restricao`() {
        assertTrue(aceita(null))
    }

    @Test
    fun `respeita a restricao de tipo declarada na anotacao`() {
        assertTrue(validator.validate(SomenteCnpj("11222333000181")).isEmpty())
        assertFalse(validator.validate(SomenteCnpj("52998224725")).isEmpty())
    }

    @Test
    fun `a violacao aponta o campo e a chave de mensagem`() {
        val violacao = validator.validate(Qualquer("52998224726")).single()

        assertEquals("numero", violacao.propertyPath.toString())
        assertEquals("{validacao.documento.invalido}", violacao.messageTemplate)
    }
}
