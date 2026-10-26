package com.clau.payment_service.web.dto.response

import com.clau.payment_service.model.enums.MeioDePagamento
import com.clau.payment_service.model.enums.SituacaoDoPagamento
import com.clau.payment_service.web.dto.fragmento.PagadorDto
import com.clau.payment_service.web.dto.fragmento.ValorDto
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.UUID

@Schema(name = "Pagamento", description = "{api.response.pagamento}")
data class PagamentoResponse(

    @field:Schema(description = "{api.response.pagamento.id}")
    val id: UUID,

    @field:Schema(description = "{api.response.pagamento.ordem-servico-id}")
    val ordemServicoId: UUID,

    @field:Schema(description = "{api.response.pagamento.situacao}")
    val situacao: SituacaoDoPagamento,

    @field:Schema(description = "{api.response.pagamento.meio}")
    val meio: MeioDePagamento,

    val valor: ValorDto,

    val pagador: PagadorDto,

    @field:Schema(description = "{api.response.pagamento.parcelas}")
    val parcelas: List<ParcelaResponse> = emptyList(),

    val instrucao: InstrucaoDePagamentoResponse? = null,

    @field:Schema(description = "{api.response.pagamento.criado-em}")
    val criadoEm: OffsetDateTime
)
