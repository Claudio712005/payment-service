package com.clau.payment_service.resilience

enum class ResilienceOperation(
    val domain: ResilienceDomain,
    val idempotent: Boolean
) {
    CHARGE_CREATE(ResilienceDomain.PAYMENT_PROVIDER, idempotent = false),
    CHARGE_QUERY(ResilienceDomain.PAYMENT_PROVIDER, idempotent = true),
    CHARGE_CANCEL(ResilienceDomain.PAYMENT_PROVIDER, idempotent = true),
    CHARGE_REFUND(ResilienceDomain.PAYMENT_PROVIDER, idempotent = true),
    INVOICE_ISSUE(ResilienceDomain.INVOICE_AUTHORITY, idempotent = false),
    INVOICE_QUERY(ResilienceDomain.INVOICE_AUTHORITY, idempotent = true),
    INVOICE_CANCEL(ResilienceDomain.INVOICE_AUTHORITY, idempotent = true)
}
