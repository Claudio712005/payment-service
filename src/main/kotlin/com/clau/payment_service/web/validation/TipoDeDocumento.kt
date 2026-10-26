package com.clau.payment_service.web.validation

enum class TipoDeDocumento(val digitos: Int) {
    CPF(11),
    CNPJ(14);

    companion object {

        fun porQuantidadeDeDigitos(digitos: Int): TipoDeDocumento? =
            entries.firstOrNull { it.digitos == digitos }
    }
}
