package com.clau.payment_service.config.doc

import com.clau.payment_service.config.i18n.SupportedLocales
import com.clau.payment_service.web.dto.request.SolicitarCobrancaRequest
import com.clau.payment_service.web.dto.response.PagamentoResponse
import com.clau.payment_service.web.error.ProblemaResponse
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.HeaderParameter
import io.swagger.v3.oas.models.tags.Tag
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title(CHAVE_TITULO)
                .description(CHAVE_DESCRICAO)
                .version(VERSAO)
                .contact(Contact().name(RESPONSAVEL))
        )
        .addTagsItem(Tag().name(TAG_COBRANCAS).description(CHAVE_TAG_COBRANCAS))

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun contratosDeclaradosCustomizer(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        val componentes = openApi.components ?: Components().also { openApi.components = it }
        listOf(
            SolicitarCobrancaRequest::class.java,
            PagamentoResponse::class.java,
            ProblemaResponse::class.java
        ).forEach { contrato ->
            ModelConverters.getInstance().readAll(contrato).forEach { (nome, esquema) ->
                componentes.addSchemas(nome, esquema)
            }
        }
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    fun idiomaDeclaradoCustomizer(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        openApi.paths?.values?.forEach { caminho ->
            caminho.readOperations().forEach { operacao ->
                val jaDeclarado = operacao.parameters?.any { it.name == SupportedLocales.HEADER } == true
                if (!jaDeclarado) {
                    operacao.addParametersItem(
                        HeaderParameter()
                            .name(SupportedLocales.HEADER)
                            .description(CHAVE_IDIOMAS)
                            .required(false)
                            .schema(Schema<String>().type("string")._default(SupportedLocales.DEFAULT.toLanguageTag()))
                    )
                }
            }
        }
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    fun traducaoDoDocumentoCustomizer(messageSource: MessageSource): OpenApiCustomizer =
        DocumentoTraduzidoCustomizer(messageSource)

    private companion object {
        const val VERSAO = "1.0.0"
        const val RESPONSAVEL = "payment-service"
        const val TAG_COBRANCAS = "cobrancas"
        const val CHAVE_TITULO = "{api.titulo}"
        const val CHAVE_DESCRICAO = "{api.descricao}"
        const val CHAVE_IDIOMAS = "{api.idiomas}"
        const val CHAVE_TAG_COBRANCAS = "{api.tag.cobrancas}"
    }
}
