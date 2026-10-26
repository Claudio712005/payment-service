package com.clau.payment_service.web.dto

import com.clau.payment_service.web.dto.request.SolicitarCobrancaRequest
import com.clau.payment_service.web.dto.response.PagamentoResponse
import com.clau.payment_service.web.error.ProblemaResponse
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.media.Schema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ComposicaoDoContratoTest {

    private fun esquemas(tipo: Class<*>): Map<String, Schema<*>> =
        ModelConverters.getInstance().readAll(tipo)

    @Test
    fun `a requisicao referencia os fragmentos em vez de repeti-los`() {
        val esquemas = esquemas(SolicitarCobrancaRequest::class.java)

        assertTrue(esquemas.containsKey("SolicitarCobranca"), esquemas.keys.toString())
        assertTrue(esquemas.containsKey("Valor"), esquemas.keys.toString())
        assertTrue(esquemas.containsKey("Pagador"), esquemas.keys.toString())
        assertTrue(esquemas.containsKey("Documento"), esquemas.keys.toString())

        val propriedades = esquemas.getValue("SolicitarCobranca").properties
        assertEquals("#/components/schemas/Valor", propriedades.getValue("valor").`$ref`)
        assertEquals("#/components/schemas/Pagador", propriedades.getValue("pagador").`$ref`)
    }

    @Test
    fun `o fragmento de documento e reaproveitado pelo pagador`() {
        val pagador = esquemas(SolicitarCobrancaRequest::class.java).getValue("Pagador")

        assertEquals("#/components/schemas/Documento", pagador.properties.getValue("documento").`$ref`)
        assertEquals("#/components/schemas/Contato", pagador.properties.getValue("contato").`$ref`)
    }

    @Test
    fun `a instrucao de pagamento e declarada como oneOf com discriminador`() {
        val esquemas = esquemas(PagamentoResponse::class.java)
        val instrucao = esquemas["InstrucaoDePagamento"]

        assertNotNull(instrucao, esquemas.keys.toString())
        assertEquals(3, instrucao.oneOf?.size, instrucao.oneOf.toString())
        assertEquals("meio", instrucao.discriminator?.propertyName)

        val referenciados = instrucao.oneOf!!.map { it.`$ref` }
        assertTrue(referenciados.contains("#/components/schemas/InstrucaoPix"), referenciados.toString())
        assertTrue(referenciados.contains("#/components/schemas/InstrucaoBoleto"), referenciados.toString())
        assertTrue(referenciados.contains("#/components/schemas/InstrucaoCartao"), referenciados.toString())
    }

    @Test
    fun `cada instrucao herda o discriminador por allOf e acrescenta os campos do seu meio`() {
        val esquemas = esquemas(PagamentoResponse::class.java)

        mapOf(
            "InstrucaoPix" to "copiaECola",
            "InstrucaoBoleto" to "linhaDigitavel",
            "InstrucaoCartao" to "codigoDeAutorizacao"
        ).forEach { (nome, campoProprio) ->
            val partes = esquemas.getValue(nome).allOf
            assertNotNull(partes, nome)
            assertEquals("#/components/schemas/InstrucaoDePagamento", partes[0].`$ref`, nome)
            assertTrue(partes[1].properties.containsKey(campoProprio), "$nome sem $campoProprio")
        }
    }

    @Test
    fun `instrucao de um meio nao expoe campo de outro`() {
        val esquemas = esquemas(PagamentoResponse::class.java)

        fun camposProprios(nome: String) = esquemas.getValue(nome).allOf[1].properties.keys

        assertTrue(!camposProprios("InstrucaoPix").contains("linhaDigitavel"))
        assertTrue(!camposProprios("InstrucaoBoleto").contains("copiaECola"))
        assertTrue(!camposProprios("InstrucaoCartao").contains("qrCodeBase64"))
    }

    @Test
    fun `o discriminador vive no pai e nao se repete nos filhos`() {
        val esquemas = esquemas(PagamentoResponse::class.java)

        assertEquals("meio", esquemas.getValue("InstrucaoDePagamento").discriminator?.propertyName)
        assertTrue(esquemas.getValue("InstrucaoPix").discriminator == null)
    }

    @Test
    fun `a resposta de pagamento compoe valor pagador parcela e instrucao`() {
        val pagamento = esquemas(PagamentoResponse::class.java).getValue("Pagamento")

        assertEquals("#/components/schemas/Valor", pagamento.properties.getValue("valor").`$ref`)
        assertEquals("#/components/schemas/Pagador", pagamento.properties.getValue("pagador").`$ref`)
        assertEquals(
            "#/components/schemas/Parcela",
            pagamento.properties.getValue("parcelas").items.`$ref`
        )
        assertEquals(
            "#/components/schemas/InstrucaoDePagamento",
            pagamento.properties.getValue("instrucao").`$ref`
        )
    }

    @Test
    fun `o problema declara as violacoes como lista de um fragmento proprio`() {
        val esquemas = esquemas(ProblemaResponse::class.java)
        val problema = esquemas.getValue("Problema")

        assertTrue(esquemas.containsKey("Violacao"), esquemas.keys.toString())
        assertEquals(
            "#/components/schemas/Violacao",
            problema.properties.getValue("violacoes").items.`$ref`
        )
    }

    @Test
    fun `toda descricao do contrato e uma chave de mensagem`() {
        val todos = esquemas(SolicitarCobrancaRequest::class.java) +
            esquemas(PagamentoResponse::class.java) +
            esquemas(ProblemaResponse::class.java)

        val descricoes = todos.values.flatMap { esquema ->
            listOfNotNull(esquema.description) +
                (esquema.properties?.values?.mapNotNull { it.description } ?: emptyList())
        }

        val literais = descricoes.filterNot { CHAVE.matches(it) }
        assertTrue(literais.isEmpty(), "descricoes sem chave de traducao: $literais")
    }

    private companion object {
        val CHAVE = Regex("""\{[a-zA-Z0-9._-]+}""")
    }
}
