package com.clau.payment_service.resilience

import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.retry.Retry
import java.util.function.Supplier

class ResilienceExecutor(
    private val bulkheads: Map<ResilienceDomain, Bulkhead>,
    private val circuitBreakers: Map<ResilienceOperation, CircuitBreaker>,
    private val retries: Map<ResilienceOperation, Retry>
) {

    fun <T> execute(operation: ResilienceOperation, block: () -> T): T {
        var supplier = Supplier { block() }

        bulkheads[operation.domain]?.let { supplier = Bulkhead.decorateSupplier(it, supplier) }
        circuitBreakers[operation]?.let { supplier = CircuitBreaker.decorateSupplier(it, supplier) }
        retries[operation]?.let { supplier = Retry.decorateSupplier(it, supplier) }

        return supplier.get()
    }
}
