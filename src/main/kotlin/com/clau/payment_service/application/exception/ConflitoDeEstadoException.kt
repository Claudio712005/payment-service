package com.clau.payment_service.application.exception

class ConflitoDeEstadoException(
    val chaveDeMensagem: String,
    val argumentos: Array<out Any> = emptyArray()
) : RuntimeException(chaveDeMensagem)
