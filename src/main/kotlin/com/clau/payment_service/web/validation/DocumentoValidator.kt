package com.clau.payment_service.web.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class DocumentoValidator : ConstraintValidator<Documento, String?> {

    private var tiposAceitos: Set<TipoDeDocumento> = emptySet()

    override fun initialize(constraintAnnotation: Documento) {
        tiposAceitos = constraintAnnotation.tipos.toSet()
    }

    override fun isValid(value: String?, context: ConstraintValidatorContext?): Boolean {
        if (value == null) {
            return true
        }
        val digitos = value.filter { it.isDigit() }
        if (digitos.length != value.filterNot { it in SEPARADORES }.length) {
            return false
        }
        val tipo = TipoDeDocumento.porQuantidadeDeDigitos(digitos.length) ?: return false
        if (tipo !in tiposAceitos) {
            return false
        }
        if (digitos.all { it == digitos[0] }) {
            return false
        }
        return when (tipo) {
            TipoDeDocumento.CPF -> cpfValido(digitos)
            TipoDeDocumento.CNPJ -> cnpjValido(digitos)
        }
    }

    private fun cpfValido(digitos: String): Boolean {
        val primeiro = digitoVerificador(digitos.take(9), 10)
        val segundo = digitoVerificador(digitos.take(10), 11)
        return digitos[9].digitToInt() == primeiro && digitos[10].digitToInt() == segundo
    }

    private fun cnpjValido(digitos: String): Boolean {
        val primeiro = digitoVerificadorCnpj(digitos.take(12))
        val segundo = digitoVerificadorCnpj(digitos.take(13))
        return digitos[12].digitToInt() == primeiro && digitos[13].digitToInt() == segundo
    }

    private fun digitoVerificador(base: String, pesoInicial: Int): Int {
        val soma = base.foldIndexed(0) { indice, acumulado, caractere ->
            acumulado + caractere.digitToInt() * (pesoInicial - indice)
        }
        val resto = soma % 11
        return if (resto < 2) 0 else 11 - resto
    }

    private fun digitoVerificadorCnpj(base: String): Int {
        val pesos = PESOS_CNPJ.takeLast(base.length)
        val soma = base.foldIndexed(0) { indice, acumulado, caractere ->
            acumulado + caractere.digitToInt() * pesos[indice]
        }
        val resto = soma % 11
        return if (resto < 2) 0 else 11 - resto
    }

    private companion object {
        val SEPARADORES = setOf('.', '-', '/')
        val PESOS_CNPJ = listOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
    }
}
