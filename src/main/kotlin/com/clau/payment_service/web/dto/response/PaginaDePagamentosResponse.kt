package com.clau.payment_service.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "PaginaDePagamentos", description = "{api.response.pagina}")
data class PaginaDePagamentosResponse(

    @field:Schema(description = "{api.response.pagina.conteudo}")
    val conteudo: List<PagamentoResponse> = emptyList(),

    @field:Schema(description = "{api.response.pagina.numero}", example = "0")
    val pagina: Int,

    @field:Schema(description = "{api.response.pagina.tamanho}", example = "20")
    val tamanho: Int,

    @field:Schema(description = "{api.response.pagina.total}", example = "2")
    val total: Long,

    @field:Schema(description = "{api.response.pagina.total-de-paginas}", example = "1")
    val totalDePaginas: Int
)
