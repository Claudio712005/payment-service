package com.clau.payment_service.config.profile

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigMapCoverageTest {

    private val variaveisNoYaml: Set<String> = ARQUIVOS_DE_PROPRIEDADE
        .flatMap { PLACEHOLDER.findAll(Path.of(it).readText()).map { match -> match.groupValues[1] } }
        .toSet()

    private val chavesNoConfigMap: Set<String> = Path.of(ARQUIVO_DE_AMBIENTE)
        .readText()
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { it.substringBefore('=') }
        .toSet()

    @Test
    fun `toda variavel das properties tem origem declarada no cluster`() {
        val semOrigem = variaveisNoYaml - chavesNoConfigMap - ENTREGUES_PELO_SECRET

        assertTrue(
            semOrigem.isEmpty(),
            "variaveis sem entrada no ConfigMap nem no Secret, cairiam no default em silencio: $semOrigem"
        )
    }

    @Test
    fun `o configmap nao carrega chave que ninguem le`() {
        val orfas = chavesNoConfigMap - variaveisNoYaml

        assertTrue(orfas.isEmpty(), "chaves no ConfigMap sem uso nas properties: $orfas")
    }

    @Test
    fun `credencial nao vaza para o configmap`() {
        val vazadas = chavesNoConfigMap intersect ENTREGUES_PELO_SECRET

        assertEquals(emptySet(), vazadas, "credencial deve vir do Secret, nao do ConfigMap")
    }

    private companion object {

        val PLACEHOLDER = Regex("""\$\{([A-Z0-9_]+)[:}]""")

        val ARQUIVOS_DE_PROPRIEDADE = listOf(
            "src/main/resources/application.yaml",
            "src/main/resources/application-local.yaml",
            "src/main/resources/application-migration.yaml"
        )

        const val ARQUIVO_DE_AMBIENTE = "k8s/payment-service.env"

        val ENTREGUES_PELO_SECRET = setOf(
            "PAYMENT_DB_USER",
            "PAYMENT_DB_PASSWORD",
            "MONGODB_USERNAME",
            "MONGODB_PASSWORD",
            "REDIS_USERNAME",
            "REDIS_PASSWORD"
        )
    }
}
