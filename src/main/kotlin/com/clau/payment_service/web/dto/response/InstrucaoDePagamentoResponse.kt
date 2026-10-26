package com.clau.payment_service.web.dto.response

import com.clau.payment_service.model.enums.MeioDePagamento
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(
    name = "InstrucaoDePagamento",
    description = "{api.response.instrucao}",
    discriminatorProperty = "meio",
    oneOf = [
        InstrucaoPixResponse::class,
        InstrucaoBoletoResponse::class,
        InstrucaoCartaoResponse::class
    ]
)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "meio",
    visible = true
)
@JsonSubTypes(
    JsonSubTypes.Type(value = InstrucaoPixResponse::class, name = "PIX"),
    JsonSubTypes.Type(value = InstrucaoBoletoResponse::class, name = "BOLETO"),
    JsonSubTypes.Type(value = InstrucaoCartaoResponse::class, name = "CARTAO_CREDITO")
)
sealed interface InstrucaoDePagamentoResponse {

    val meio: MeioDePagamento
}

@Schema(name = "InstrucaoPix", description = "{api.response.instrucao.pix}")
data class InstrucaoPixResponse(

    @field:Schema(description = "{api.response.instrucao.pix.copia-e-cola}")
    val copiaECola: String,

    @field:Schema(description = "{api.response.instrucao.pix.qrcode}")
    val qrCodeBase64: String?,

    @field:Schema(description = "{api.response.instrucao.expira-em}")
    val expiraEm: OffsetDateTime
) : InstrucaoDePagamentoResponse {

    override val meio: MeioDePagamento = MeioDePagamento.PIX
}

@Schema(name = "InstrucaoBoleto", description = "{api.response.instrucao.boleto}")
data class InstrucaoBoletoResponse(

    @field:Schema(description = "{api.response.instrucao.boleto.linha-digitavel}")
    val linhaDigitavel: String,

    @field:Schema(description = "{api.response.instrucao.boleto.url}")
    val urlDoBoleto: String?,

    @field:Schema(description = "{api.response.instrucao.boleto.vencimento}")
    val vencimento: java.time.LocalDate
) : InstrucaoDePagamentoResponse {

    override val meio: MeioDePagamento = MeioDePagamento.BOLETO
}

@Schema(name = "InstrucaoCartao", description = "{api.response.instrucao.cartao}")
data class InstrucaoCartaoResponse(

    @field:Schema(description = "{api.response.instrucao.cartao.autorizacao}")
    val codigoDeAutorizacao: String?,

    @field:Schema(description = "{api.response.instrucao.cartao.bandeira}")
    val bandeira: String?,

    @field:Schema(description = "{api.response.instrucao.cartao.parcelas}")
    val parcelas: Int
) : InstrucaoDePagamentoResponse {

    override val meio: MeioDePagamento = MeioDePagamento.CARTAO_CREDITO
}
