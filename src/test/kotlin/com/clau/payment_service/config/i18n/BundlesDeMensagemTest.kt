package com.clau.payment_service.config.i18n

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.reader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BundlesDeMensagemTest {

    private fun bundle(sufixo: String): Properties = Properties().apply {
        Path.of("src/main/resources/messages/messages$sufixo.properties")
            .reader(StandardCharsets.UTF_8)
            .use { load(it) }
    }

    private val referencia = bundle("_pt_BR")
    private val traducoes = mapOf("en" to bundle("_en"), "es" to bundle("_es"))

    @Test
    fun `os tres idiomas tem exatamente as mesmas chaves`() {
        traducoes.forEach { (idioma, bundle) ->
            val faltando = referencia.stringPropertyNames() - bundle.stringPropertyNames()
            val sobrando = bundle.stringPropertyNames() - referencia.stringPropertyNames()

            assertTrue(faltando.isEmpty(), "chaves ausentes em $idioma: $faltando")
            assertTrue(sobrando.isEmpty(), "chaves extra em $idioma: $sobrando")
        }
    }

    @Test
    fun `os acentos sobrevivem a leitura em utf-8`() {
        assertTrue(referencia.getProperty("erro.parametro.titulo").contains("â"))
        assertTrue(traducoes.getValue("es").getProperty("erro.parametro.titulo").contains("á"))
    }

    @Test
    fun `nenhuma mensagem fica vazia`() {
        (traducoes + ("pt-BR" to referencia)).forEach { (idioma, bundle) ->
            val vazias = bundle.stringPropertyNames().filter { bundle.getProperty(it).isNullOrBlank() }

            assertTrue(vazias.isEmpty(), "mensagens vazias em $idioma: $vazias")
        }
    }

    @Test
    fun `nenhuma traducao repete o texto em portugues`() {
        traducoes.forEach { (idioma, bundle) ->
            val naoTraduzidas = referencia.stringPropertyNames()
                .filter { bundle.getProperty(it) == referencia.getProperty(it) }

            assertTrue(naoTraduzidas.isEmpty(), "sem traducao em $idioma: $naoTraduzidas")
        }
    }

    @Test
    fun `os marcadores de argumento sao os mesmos em todos os idiomas`() {
        traducoes.forEach { (idioma, bundle) ->
            referencia.stringPropertyNames().forEach { chave ->
                assertEquals(
                    marcadores(referencia.getProperty(chave)),
                    marcadores(bundle.getProperty(chave)),
                    "marcadores divergentes em $idioma na chave $chave"
                )
            }
        }
    }

    @Test
    fun `o bundle sem idioma e identico ao portugues`() {
        assertEquals(referencia, bundle(""))
    }

    private fun marcadores(texto: String): Set<String> =
        MARCADOR.findAll(texto).map { it.value }.toSet()

    private companion object {
        val MARCADOR = Regex("""\{[0-9a-zA-Z]+}""")
    }
}
