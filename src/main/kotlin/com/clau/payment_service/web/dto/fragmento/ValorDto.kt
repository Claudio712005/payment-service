package com.clau.payment_service.web.dto.fragmento

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.math.BigDecimal

@Schema(name = "Valor", description = "{api.fragmento.valor}")
data class ValorDto(

    @field:NotNull(message = "{validacao.campo.obrigatorio}")
    @field:DecimalMin(value = "0.01", message = "{validacao.valor.minimo}")
    @field:Digits(integer = 13, fraction = 2)
    @field:Schema(description = "{api.fragmento.valor.quantia}", example = "150.00")
    val quantia: BigDecimal?,

    @field:Pattern(regexp = "^[A-Z]{3}$", message = "{validacao.moeda.formato}")
    @field:Schema(description = "{api.fragmento.valor.moeda}", example = "BRL", defaultValue = "BRL")
    val moeda: String = "BRL"
)
