package com.clau.payment_service.web.dto.response

import com.clau.payment_service.model.enums.SituacaoDaParcela
import com.clau.payment_service.web.dto.fragmento.ValorDto
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(name = "Parcela", description = "{api.response.parcela}")
data class ParcelaResponse(

    @field:Schema(description = "{api.response.parcela.numero}", example = "1")
    val numero: Int,

    val valor: ValorDto,

    @field:Schema(description = "{api.response.parcela.situacao}")
    val situacao: SituacaoDaParcela,

    @field:Schema(description = "{api.response.parcela.vencimento}")
    val vencimento: LocalDate?
)
