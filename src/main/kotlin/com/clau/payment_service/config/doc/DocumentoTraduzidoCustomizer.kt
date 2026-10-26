package com.clau.payment_service.config.doc

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder

class DocumentoTraduzidoCustomizer(private val messageSource: MessageSource) : OpenApiCustomizer {

    override fun customise(openApi: OpenAPI) {
        openApi.info?.let { info ->
            info.title = traduzir(info.title)
            info.description = traduzir(info.description)
        }
        openApi.tags?.forEach { tag -> tag.description = traduzir(tag.description) }

        openApi.paths?.values?.forEach { caminho ->
            caminho.readOperations().forEach { operacao ->
                operacao.summary = traduzir(operacao.summary)
                operacao.description = traduzir(operacao.description)
                operacao.parameters?.forEach { parametro ->
                    parametro.description = traduzir(parametro.description)
                }
                operacao.responses?.values?.forEach { resposta ->
                    resposta.description = traduzir(resposta.description)
                }
            }
        }

        openApi.components?.schemas?.values?.forEach { traduzirEsquema(it, 0) }
    }

    private fun traduzirEsquema(esquema: Schema<*>?, profundidade: Int) {
        if (esquema == null || profundidade > PROFUNDIDADE_MAXIMA) {
            return
        }
        esquema.description = traduzir(esquema.description)
        esquema.properties?.values?.forEach { traduzirEsquema(it, profundidade + 1) }
        esquema.items?.let { traduzirEsquema(it, profundidade + 1) }
        esquema.oneOf?.forEach { traduzirEsquema(it, profundidade + 1) }
        esquema.allOf?.forEach { traduzirEsquema(it, profundidade + 1) }
        (esquema.additionalProperties as? Schema<*>)?.let { traduzirEsquema(it, profundidade + 1) }
    }

    private fun traduzir(valor: String?): String? {
        if (valor == null) {
            return null
        }
        val chave = CHAVE.matchEntire(valor.trim())?.groupValues?.get(1) ?: return valor
        return messageSource.getMessage(chave, null, chave, LocaleContextHolder.getLocale())
    }

    private companion object {
        val CHAVE = Regex("""\{([a-zA-Z0-9._-]+)}""")
        const val PROFUNDIDADE_MAXIMA = 10
    }
}
