package com.clau.payment_service.web.validation

import com.clau.payment_service.model.enums.MeioDePagamento
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class CartaoCoerenteValidator : ConstraintValidator<CartaoCoerente, PagavelComCartao> {

    override fun isValid(value: PagavelComCartao?, context: ConstraintValidatorContext?): Boolean {
        val meio = value?.meio ?: return true
        val exigeCartao = meio in MEIOS_DE_CARTAO
        val temCartao = value.cartao != null

        if (exigeCartao == temCartao) {
            return true
        }

        val mensagem = if (exigeCartao) CARTAO_OBRIGATORIO else CARTAO_NAO_APLICAVEL
        context?.let {
            it.disableDefaultConstraintViolation()
            it.buildConstraintViolationWithTemplate(mensagem)
                .addPropertyNode(CAMPO)
                .addConstraintViolation()
        }
        return false
    }

    private companion object {
        const val CAMPO = "cartao"
        const val CARTAO_OBRIGATORIO = "{validacao.cartao.obrigatorio}"
        const val CARTAO_NAO_APLICAVEL = "{validacao.cartao.nao-aplicavel}"
        val MEIOS_DE_CARTAO = setOf(MeioDePagamento.CARTAO_CREDITO, MeioDePagamento.CARTAO_DEBITO)
    }
}
