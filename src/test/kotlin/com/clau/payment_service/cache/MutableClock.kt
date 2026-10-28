package com.clau.payment_service.cache

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class MutableClock(private var moment: Instant = Instant.parse("2026-10-27T10:00:00Z")) : Clock() {

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = moment

    fun advance(amount: Duration) {
        moment = moment.plus(amount)
    }
}
