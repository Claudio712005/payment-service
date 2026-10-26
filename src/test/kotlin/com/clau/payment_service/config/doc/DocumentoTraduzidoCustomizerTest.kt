package com.clau.payment_service.config.doc

import com.clau.payment_service.config.i18n.MessageSourceConfig
import com.clau.payment_service.config.i18n.SupportedLocales
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.HeaderParameter
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.context.i18n.LocaleContextHolder

class DocumentoTraduzidoCustomizerTest {

    private val messageSource = MessageSourceConfig().messageSource()
    private val customizer = DocumentoTraduzidoCustomizer(messageSource)
    private val configuracaoOpenApi = OpenApiConfig()

    @AfterTest
    fun limpar() {
        LocaleContextHolder.resetLocaleContext()
    }

    private fun documento(): OpenAPI = configuracaoOpenApi.openApi().apply {
        paths = Paths().addPathItem(
            "/pagamentos",
            PathItem().post(
                Operation()
                    .summary("{api.request.solicitar-cobranca}")
                    .addParametersItem(
                        HeaderParameter().name(SupportedLocales.HEADER).description("{api.idiomas}")
                    )
            )
        )
        configuracaoOpenApi.contratosDeclaradosCustomizer().customise(this)
    }

    private fun traduzido(idioma: Locale): OpenAPI {
        LocaleContextHolder.setLocale(idioma)
        return documento().also { customizer.customise(it) }
    }

    @Test
    fun `titulo e descricao do documento saem no idioma do contexto`() {
        assertEquals("API de pagamentos e notas fiscais", traduzido(SupportedLocales.DEFAULT).info.title)
        assertEquals("Payments and invoices API", traduzido(Locale.ENGLISH).info.title)
        assertEquals("API de pagos y facturas", traduzido(Locale.forLanguageTag("es")).info.title)
    }

    @Test
    fun `a tag e traduzida`() {
        assertEquals("Cobranças", traduzido(SupportedLocales.DEFAULT).tags.single().description)
        assertEquals("Charges", traduzido(Locale.ENGLISH).tags.single().description)
    }

    @Test
    fun `resumo da operacao e descricao do parametro sao traduzidos`() {
        val operacao = traduzido(Locale.ENGLISH).paths.getValue("/pagamentos").post

        assertEquals("Request to charge a service order", operacao.summary)
        assertEquals(
            "Language of messages and descriptions. Accepts pt-BR, en and es; the default is pt-BR.",
            operacao.parameters.single().description
        )
    }

    @Test
    fun `descricao de schema e de propriedade sao traduzidas`() {
        val esquemas = traduzido(Locale.forLanguageTag("es")).components.schemas

        assertEquals("Documento fiscal del pagador, CPF o CNPJ", esquemas.getValue("Documento").description)
        assertEquals(
            "Número con o sin puntuación; el dígito verificador es validado",
            esquemas.getValue("Documento").properties.getValue("numero").description
        )
    }

    @Test
    fun `descricao dentro de allOf tambem e traduzida`() {
        val esquemas = traduzido(Locale.ENGLISH).components.schemas
        val pix = esquemas.getValue("InstrucaoPix")

        assertEquals("Pix payment instruction", pix.description)
        assertEquals(
            "Copy and paste code",
            pix.allOf[1].properties.getValue("copiaECola").description
        )
    }

    @Test
    fun `nenhuma chave sobra sem traducao no documento final`() {
        SupportedLocales.ALL.forEach { idioma ->
            val pendentes = mutableListOf<String>()
            val documento = traduzido(idioma)
            documento.components.schemas.values.forEach { coletar(it, pendentes, 0) }
            pendentes += listOfNotNull(documento.info.title, documento.info.description)
                .filter { CHAVE.matches(it) }

            assertTrue(pendentes.isEmpty(), "chaves nao traduzidas em $idioma: $pendentes")
        }
    }

    @Test
    fun `texto que nao e chave passa intacto`() {
        LocaleContextHolder.setLocale(Locale.ENGLISH)
        val documento = OpenAPI().info(io.swagger.v3.oas.models.info.Info().title("Titulo literal"))

        customizer.customise(documento)

        assertEquals("Titulo literal", documento.info.title)
    }

    private fun coletar(esquema: Schema<*>?, pendentes: MutableList<String>, profundidade: Int) {
        if (esquema == null || profundidade > 10) {
            return
        }
        esquema.description?.takeIf { CHAVE.matches(it) }?.let { pendentes += it }
        esquema.properties?.values?.forEach { coletar(it, pendentes, profundidade + 1) }
        esquema.items?.let { coletar(it, pendentes, profundidade + 1) }
        esquema.allOf?.forEach { coletar(it, pendentes, profundidade + 1) }
        esquema.oneOf?.forEach { coletar(it, pendentes, profundidade + 1) }
    }

    private companion object {
        val CHAVE = Regex("""\{[a-zA-Z0-9._-]+}""")
    }
}
