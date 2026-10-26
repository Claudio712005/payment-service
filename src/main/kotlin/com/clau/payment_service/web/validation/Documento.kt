package com.clau.payment_service.web.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [DocumentoValidator::class])
annotation class Documento(
    val message: String = "{validacao.documento.invalido}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
    val tipos: Array<TipoDeDocumento> = [TipoDeDocumento.CPF, TipoDeDocumento.CNPJ]
)
