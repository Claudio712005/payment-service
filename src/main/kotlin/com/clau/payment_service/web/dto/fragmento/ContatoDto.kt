package com.clau.payment_service.web.dto.fragmento

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@Schema(name = "Contato", description = "{api.fragmento.contato}")
data class ContatoDto(

    @field:Email(message = "{validacao.email.formato}")
    @field:Size(max = 180)
    @field:Schema(description = "{api.fragmento.contato.email}", example = "ana@exemplo.com")
    val email: String? = null,

    @field:Pattern(regexp = "^\\+?[0-9 ()-]{10,20}$", message = "{validacao.telefone.formato}")
    @field:Schema(description = "{api.fragmento.contato.telefone}", example = "+55 11 99999-0000")
    val telefone: String? = null
)
