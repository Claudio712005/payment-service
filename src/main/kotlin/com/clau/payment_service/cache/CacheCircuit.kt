package com.clau.payment_service.cache

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class CacheCircuit(
    failureThreshold: Int,
    private val cooldown: Duration,
    private val clock: Clock = Clock.systemUTC()
) {

    private val failureThreshold = failureThreshold.coerceAtLeast(1)
    private val failures = AtomicInteger(0)
    private val openUntil = AtomicReference<Instant?>(null)

    fun isOpen(): Boolean {
        val deadline = openUntil.get() ?: return false
        if (clock.instant().isBefore(deadline)) {
            return true
        }
        if (openUntil.compareAndSet(deadline, null)) {
            failures.set(0)
        }
        return false
    }

    fun available(): Boolean = !isOpen()

    fun recordSuccess() {
        failures.set(0)
        openUntil.set(null)
    }

    fun recordFailure() {
        if (failures.incrementAndGet() >= failureThreshold) {
            openUntil.set(clock.instant().plus(cooldown))
        }
    }
}
