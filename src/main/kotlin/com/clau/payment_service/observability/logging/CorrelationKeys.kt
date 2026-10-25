package com.clau.payment_service.observability.logging

object CorrelationKeys {

    const val TRACE_ID = "traceId"
    const val SPAN_ID = "spanId"
    const val REQUEST_ID = "requestId"
    const val CORRELATION_ID = "correlationId"

    val ALL: List<String> = listOf(TRACE_ID, SPAN_ID, REQUEST_ID, CORRELATION_ID)
}
