package com.clau.payment_service.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContratoDeMensageriaTest {

    private val contrato = ContratoDeclarado(ARQUIVO)

    @Test
    fun `toda referencia do contrato aponta para um componente que existe`() {
        val quebradas = contrato.referencias().filter { contrato.resolve(it) == null }

        assertTrue(quebradas.isEmpty(), "referencias sem destino no contrato: $quebradas")
    }

    @Test
    fun `todo canal declara ao menos uma mensagem`() {
        val semMensagem = contrato.mapa("channels")
            .filterValues { canal -> mensagensDe(canal).isEmpty() }
            .keys

        assertTrue(semMensagem.isEmpty(), "canal sem mensagem declarada: $semMensagem")
    }

    @Test
    fun `toda operacao aponta para um canal declarado`() {
        val canais = contrato.mapa("channels").keys

        val soltas = contrato.mapa("operations").mapNotNull { (nome, operacao) ->
            val referencia = ((operacao as? Map<*, *>)?.get("channel") as? Map<*, *>)?.get("\$ref") as? String
            val canal = referencia?.removePrefix("#/channels/")
            if (canal != null && canal in canais) null else "$nome -> $referencia"
        }

        assertTrue(soltas.isEmpty(), "operacao sem canal correspondente: $soltas")
    }

    @Test
    fun `o servico publica evento e consome comando, nunca o contrario`() {
        val acoes = contrato.mapa("operations").mapValues { (_, operacao) ->
            (operacao as Map<*, *>)["action"] as String
        }

        val enviados = acoes.filterValues { it == "send" }.keys
        val recebidos = acoes.filterValues { it == "receive" }.keys

        assertEquals(5, enviados.size, "eventos publicados: $enviados")
        assertEquals(setOf("consumirCobrarPagamento"), recebidos, "comandos consumidos: $recebidos")
    }

    @Test
    fun `todo evento publicado leva traceparent e id de mensagem no cabecalho`() {
        val obrigatorios = contrato.mapa("components", "messageTraits", "cabecalhoPadrao", "headers")["required"]

        assertEquals(listOf("traceparent", "idMensagem", "ocorridoEm"), obrigatorios)

        val semCabecalho = contrato.mapa("components", "messages")
            .filterValues { mensagem -> traitsDe(mensagem).isEmpty() }
            .keys

        assertTrue(semCabecalho.isEmpty(), "mensagem sem o cabecalho padrao: $semCabecalho")
    }

    @Test
    fun `todo evento carrega o envelope comum`() {
        val eventos = contrato.mapa("components", "messages").filterKeys { it != COMANDO }

        val semEnvelope = eventos.filterValues { mensagem ->
            contrato.referencias().none { it == ENVELOPE } || !usaEnvelope(mensagem)
        }.keys

        assertTrue(semEnvelope.isEmpty(), "evento sem o envelope comum: $semEnvelope")
    }

    @Test
    fun `todo canal tem endereco versionado`() {
        val semVersao = contrato.mapa("channels")
            .mapValues { (_, canal) -> (canal as Map<*, *>)["address"] as String }
            .filterValues { !it.endsWith(".v1") }

        assertTrue(semVersao.isEmpty(), "canal sem versao no endereco: $semVersao")
    }

    @Test
    fun `o comando declara prazo do orquestrador`() {
        val comando = contrato.mapa("components", "messages", COMANDO)

        @Suppress("UNCHECKED_CAST")
        val obrigatorios = (comando["payload"] as Map<String, Any?>)["required"] as List<String>

        assertTrue("expiraEm" in obrigatorios, "comando sem expiraEm obrigatorio: $obrigatorios")
    }

    @Suppress("UNCHECKED_CAST")
    private fun usaEnvelope(mensagem: Any?): Boolean {
        val payload = (mensagem as Map<String, Any?>)["payload"] as? Map<String, Any?> ?: return false
        val partes = payload["allOf"] as? List<Map<String, Any?>> ?: return false
        return partes.any { it["\$ref"] == ENVELOPE }
    }

    private fun mensagensDe(canal: Any?): Set<*> =
        ((canal as? Map<*, *>)?.get("messages") as? Map<*, *>)?.keys ?: emptySet<Any>()

    private fun traitsDe(mensagem: Any?): List<*> =
        (mensagem as? Map<*, *>)?.get("traits") as? List<*> ?: emptyList<Any>()

    private companion object {
        const val ARQUIVO = "apis/payment-service.asyncapi.yaml"
        const val COMANDO = "CobrarPagamento"
        const val ENVELOPE = "#/components/schemas/EnvelopeDoEvento"
    }
}
