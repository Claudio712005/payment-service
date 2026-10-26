package com.clau.payment_service.web.validation

import com.clau.payment_service.model.enums.MeioDePagamento
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class ParcelamentoCoerenteValidator : ConstraintValidator<ParcelamentoCoerente, Parcelavel> {

    override fun isValid(value: Parcelavel?, context: ConstraintValidatorContext?): Boolean {
        if (value == null || value.meio == null) {
            return true
        }
        if (value.parcelas <= 1) {
            return true
        }
        val coerente = value.meio == MeioDePagamento.CARTAO_CREDITO
        if (!coerente && context != null) {
            context.disableDefaultConstraintViolation()
            context.buildConstraintViolationWithTemplate(context.defaultConstraintMessageTemplate)
                .addPropertyNode(CAMPO)
                .addConstraintViolation()
        }
        return coerente
    }

    private companion object {
        const val CAMPO = "parcelas"
    }
}
