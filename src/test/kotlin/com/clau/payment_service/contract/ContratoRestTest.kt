package com.clau.payment_service.contract

import com.clau.payment_service.web.api.PagamentosApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContratoRestTest {

    private val contrato = ContratoDeclarado(ARQUIVO)

    @Test
    fun `toda referencia do contrato aponta para um componente que existe`() {
        val quebradas = contrato.referencias().filter { contrato.resolve(it) == null }

        assertTrue(quebradas.isEmpty(), "referencias sem destino no contrato: $quebradas")
    }

    @Test
    fun `todo operationId do contrato existe na interface gerada`() {
        val metodos = PagamentosApi::class.java.methods.map { it.name }.toSet()
        val semImplementacao = operacoes().keys - metodos

        assertTrue(
            semImplementacao.isEmpty(),
            "operacoes do contrato ausentes em PagamentosApi: $semImplementacao"
        )
    }

    @Test
    fun `todo schema do contrato tem uma classe kotlin mapeada no build`() {
        val mapeados = mapeamentoDoBuild()
        val declarados = contrato.mapa("components", "schemas").keys

        val semMapeamento = declarados - mapeados.keys
        assertTrue(
            semMapeamento.isEmpty(),
            "schema no contrato sem classe mapeada em build.gradle.kts: $semMapeamento"
        )

        val semClasse = mapeados.filterKeys { it in declarados }.values.filterNot { existe(it) }
        assertTrue(semClasse.isEmpty(), "classes mapeadas que nao existem: $semClasse")
    }

    @Test
    fun `nenhum mapeamento do build sobra sem schema no contrato`() {
        val orfaos = mapeamentoDoBuild().keys - contrato.mapa("components", "schemas").keys

        assertTrue(orfaos.isEmpty(), "mapeamento no build sem schema no contrato: $orfaos")
    }

    @Test
    fun `toda resposta de erro sai como problem json`() {
        val forjadas = operacoes().flatMap { (operationId, operacao) ->
            @Suppress("UNCHECKED_CAST")
            val respostas = operacao["responses"] as? Map<String, Any?> ?: emptyMap()
            respostas.keys
                .filter { it.first() in setOf('4', '5') }
                .mapNotNull { codigo ->
                    val tipos = tiposDeMidiaDa(codigo, respostas)
                    if (tipos.isEmpty() || tipos == setOf(PROBLEM_JSON)) null else "$operationId/$codigo=$tipos"
                }
        }

        assertTrue(forjadas.isEmpty(), "erro fora do formato RFC 9457: $forjadas")
    }

    @Test
    fun `todo campo de cartao e somente de escrita`() {
        @Suppress("UNCHECKED_CAST")
        val cartao = contrato.mapa("components", "schemas", "Cartao")["properties"] as Map<String, Map<String, Any?>>

        val devolvidos = cartao.filterValues { it["writeOnly"] != true }.keys

        assertTrue(devolvidos.isEmpty(), "campo de cartao sem writeOnly, voltaria em resposta: $devolvidos")
    }

    @Test
    fun `a criacao exige a chave de idempotencia`() {
        @Suppress("UNCHECKED_CAST")
        val parametros = (operacoes().getValue("solicitarCobranca")["parameters"] as List<Map<String, Any?>>)
            .mapNotNull { it["\$ref"] as? String }
            .mapNotNull { contrato.resolve(it) as? Map<*, *> }

        val idempotencia = parametros.single { it["name"] == "Idempotency-Key" }

        assertEquals("header", idempotencia["in"])
        assertEquals(true, idempotencia["required"])
    }

    @Suppress("UNCHECKED_CAST")
    private fun tiposDeMidiaDa(codigo: String, respostas: Map<String, Any?>): Set<String> {
        val resposta = respostas[codigo] as? Map<String, Any?> ?: return emptySet()
        val resolvida = (resposta["\$ref"] as? String)
            ?.let { contrato.resolve(it) as? Map<String, Any?> }
            ?: resposta
        return ((resolvida["content"] as? Map<String, Any?>)?.keys ?: emptySet())
    }

    @Suppress("UNCHECKED_CAST")
    private fun operacoes(): Map<String, Map<String, Any?>> =
        contrato.mapa("paths").values
            .filterIsInstance<Map<String, Any?>>()
            .flatMap { caminho -> caminho.values.filterIsInstance<Map<String, Any?>>() }
            .filter { it.containsKey("operationId") }
            .associateBy { it["operationId"] as String }

    private fun mapeamentoDoBuild(): Map<String, String> =
        MAPEAMENTO.findAll(java.nio.file.Path.of("build.gradle.kts").toFile().readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    private fun existe(classe: String): Boolean {
        val partes = classe.split('.')
        val aninhada = partes.dropLast(1).joinToString(".") + "\$" + partes.last()
        return listOf(classe, aninhada).any { runCatching { Class.forName(it) }.isSuccess }
    }

    private companion object {
        const val ARQUIVO = "apis/payment-service.openapi.yaml"
        const val PROBLEM_JSON = "application/problem+json"
        val MAPEAMENTO = Regex(""""([A-Za-z]+)" to "(com\.clau\.payment_service[A-Za-z.]+)"""")
    }
}
