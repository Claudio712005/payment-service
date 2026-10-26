package com.clau.payment_service.application.exception

class RecursoNaoEncontradoException(
    val recurso: String,
    val identificador: String
) : RuntimeException("$recurso $identificador")
