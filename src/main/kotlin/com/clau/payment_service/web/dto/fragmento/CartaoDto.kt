package com.clau.payment_service.web.dto.fragmento

import com.clau.payment_service.observability.logging.sensitive.Sensitive
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@Schema(name = "Cartao", description = "{api.fragmento.cartao}")
data class CartaoDto(

    @field:NotBlank(message = "{validacao.campo.obrigatorio}")
    @field:Pattern(regexp = "^[0-9]{13,19}$", message = "{validacao.cartao.numero.formato}")
    @field:Sensitive(mode = Sensitive.Mode.KEEP_LAST, keep = 4)
    @field:Schema(
        description = "{api.fragmento.cartao.numero}",
        example = "5031433215406351",
        accessMode = Schema.AccessMode.WRITE_ONLY
    )
    val numero: String?,

    @field:NotBlank(message = "{validacao.campo.obrigatorio}")
    @field:Size(min = 3, max = 120, message = "{validacao.nome.tamanho}")
    @field:Schema(
        description = "{api.fragmento.cartao.portador}",
        example = "ANA SOUZA",
        accessMode = Schema.AccessMode.WRITE_ONLY
    )
    val nomeDoPortador: String?,

    @field:NotNull(message = "{validacao.campo.obrigatorio}")
    @field:Min(value = 1, message = "{validacao.cartao.validade.mes}")
    @field:Max(value = 12, message = "{validacao.cartao.validade.mes}")
    @field:Schema(
        description = "{api.fragmento.cartao.validade-mes}",
        example = "11",
        accessMode = Schema.AccessMode.WRITE_ONLY
    )
    val validadeMes: Int?,

    @field:NotNull(message = "{validacao.campo.obrigatorio}")
    @field:Min(value = 2026, message = "{validacao.cartao.validade.ano}")
    @field:Max(value = 2099, message = "{validacao.cartao.validade.ano}")
    @field:Schema(
        description = "{api.fragmento.cartao.validade-ano}",
        example = "2030",
        accessMode = Schema.AccessMode.WRITE_ONLY
    )
    val validadeAno: Int?,

    @field:NotBlank(message = "{validacao.campo.obrigatorio}")
    @field:Pattern(regexp = "^[0-9]{3,4}$", message = "{validacao.cartao.codigo.formato}")
    @field:Sensitive
    @field:Schema(
        description = "{api.fragmento.cartao.codigo-de-seguranca}",
        example = "123",
        accessMode = Schema.AccessMode.WRITE_ONLY
    )
    val codigoDeSeguranca: String?
)
