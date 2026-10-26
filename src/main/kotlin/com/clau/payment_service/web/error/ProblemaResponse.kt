package com.clau.payment_service.web.error

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(name = "Problema", description = "{api.erro.problema}")
data class ProblemaResponse(

    @field:Schema(description = "{api.erro.tipo}", example = "https://payment-service/erros/validacao")
    val type: String,

    @field:Schema(description = "{api.erro.titulo}")
    val title: String,

    @field:Schema(description = "{api.erro.status}", example = "400")
    val status: Int,

    @field:Schema(description = "{api.erro.detalhe}")
    val detail: String,

    @field:Schema(description = "{api.erro.instancia}", example = "/pagamentos")
    val instance: String?,

    @field:Schema(description = "{api.erro.ocorrido-em}")
    val ocorridoEm: OffsetDateTime,

    @field:Schema(description = "{api.erro.correlacao}")
    val correlationId: String?,

    @field:Schema(description = "{api.erro.violacoes}")
    val violacoes: List<ViolacaoResponse> = emptyList()
) {

    @Schema(name = "Violacao", description = "{api.erro.violacao}")
    data class ViolacaoResponse(

        @field:Schema(description = "{api.erro.violacao.campo}", example = "pagador.documento.numero")
        val campo: String,

        @field:Schema(description = "{api.erro.violacao.mensagem}")
        val mensagem: String,

        @field:Schema(description = "{api.erro.violacao.recebido}")
        val recebido: String? = null
    )
}
