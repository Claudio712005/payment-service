package com.clau.payment_service.web.dto.fragmento

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

@Schema(name = "Pagador", description = "{api.fragmento.pagador}")
data class PagadorDto(

    @field:NotBlank(message = "{validacao.campo.obrigatorio}")
    @field:Size(min = 3, max = 120, message = "{validacao.nome.tamanho}")
    @field:Schema(description = "{api.fragmento.pagador.nome}", example = "Ana Souza")
    val nome: String?,

    @field:Valid
    @field:NotNull(message = "{validacao.campo.obrigatorio}")
    val documento: DocumentoDto?,

    @field:Valid
    val contato: ContatoDto? = null
)
