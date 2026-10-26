package com.clau.payment_service.web.dto.request

import com.clau.payment_service.model.enums.MeioDePagamento
import com.clau.payment_service.web.dto.fragmento.PagadorDto
import com.clau.payment_service.web.dto.fragmento.ValorDto
import com.clau.payment_service.web.validation.ParcelamentoCoerente
import com.clau.payment_service.web.validation.Parcelavel
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.util.UUID

@ParcelamentoCoerente
@Schema(name = "SolicitarCobranca", description = "{api.request.solicitar-cobranca}")
data class SolicitarCobrancaRequest(

    @field:NotNull(message = "{validacao.campo.obrigatorio}")
    @field:Schema(description = "{api.request.ordem-servico-id}", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    val ordemServicoId: UUID?,

    @field:NotNull(message = "{validacao.campo.obrigatorio}")
    @field:Schema(description = "{api.request.meio}", example = "PIX")
    override val meio: MeioDePagamento?,

    @field:Valid
    @field:NotNull(message = "{validacao.campo.obrigatorio}")
    val valor: ValorDto?,

    @field:Valid
    @field:NotNull(message = "{validacao.campo.obrigatorio}")
    val pagador: PagadorDto?,

    @field:Min(value = 1, message = "{validacao.parcelas.faixa}")
    @field:Max(value = 24, message = "{validacao.parcelas.faixa}")
    @field:Schema(description = "{api.request.parcelas}", example = "1", defaultValue = "1")
    override val parcelas: Int = 1
) : Parcelavel
