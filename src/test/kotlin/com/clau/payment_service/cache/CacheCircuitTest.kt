package com.clau.payment_service.cache

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CacheCircuitTest {

    private val clock = MutableClock()

    @Test
    fun `circuito comeca fechado`() {
        val circuit = CacheCircuit(3, Duration.ofSeconds(30), clock)

        assertTrue(circuit.available())
        assertFalse(circuit.isOpen())
    }

    @Test
    fun `falha isolada nao abre o circuito antes do limite`() {
        val circuit = CacheCircuit(3, Duration.ofSeconds(30), clock)

        circuit.recordFailure()
        circuit.recordFailure()

        assertTrue(circuit.available())
    }

    @Test
    fun `circuito abre no limite de falhas e fecha depois da pausa`() {
        val circuit = CacheCircuit(2, Duration.ofSeconds(30), clock)

        circuit.recordFailure()
        circuit.recordFailure()
        assertTrue(circuit.isOpen())

        clock.advance(Duration.ofSeconds(29))
        assertTrue(circuit.isOpen())

        clock.advance(Duration.ofSeconds(2))
        assertTrue(circuit.available())
    }

    @Test
    fun `sucesso zera as falhas acumuladas`() {
        val circuit = CacheCircuit(2, Duration.ofSeconds(30), clock)

        circuit.recordFailure()
        circuit.recordSuccess()
        circuit.recordFailure()

        assertTrue(circuit.available())
    }

    @Test
    fun `limite menor que um e tratado como uma falha`() {
        val circuit = CacheCircuit(0, Duration.ofSeconds(30), clock)

        circuit.recordFailure()

        assertTrue(circuit.isOpen())
    }
}
