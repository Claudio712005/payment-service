package com.clau.payment_service.web.dto.fragmento

import com.clau.payment_service.web.validation.Documento
import com.clau.payment_service.web.validation.TipoDeDocumento
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(name = "Documento", description = "{api.fragmento.documento}")
data class DocumentoDto(

    @field:NotNull(message = "{validacao.campo.obrigatorio}")
    @field:Schema(description = "{api.fragmento.documento.tipo}", example = "CPF")
    val tipo: TipoDeDocumento?,

    @field:Documento
    @field:NotNull(message = "{validacao.campo.obrigatorio}")
    @field:Schema(description = "{api.fragmento.documento.numero}", example = "529.982.247-25")
    val numero: String?
)
