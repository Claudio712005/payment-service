package com.clau.payment_service.observability.logging.sensitive

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Sensitive(val mode: Mode = Mode.FULL, val keep: Int = 0) {

    enum class Mode {
        FULL,
        KEEP_LAST,
        HASH
    }
}
