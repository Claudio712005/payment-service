package com.clau.payment_service.observability.logging.sensitive

class Loggable(private val value: Any?) {

    override fun toString(): String = LogRenderer.render(value)
}

fun Any?.loggable(): Loggable = Loggable(this)
