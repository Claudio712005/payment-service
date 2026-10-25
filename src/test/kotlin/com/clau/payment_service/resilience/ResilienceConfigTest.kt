package com.clau.payment_service.resilience

import com.clau.payment_service.config.resilience.ResilienceConfig
import io.github.resilience4j.bulkhead.BulkheadFullException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException

class ResilienceConfigTest {

    private val config = ResilienceConfig()

    private fun executor(properties: ResilienceProperties): ResilienceExecutor {
        val bulkheads = config.bulkheadRegistry(properties)
        val breakers = config.circuitBreakerRegistry(properties)
        val retries = config.retryRegistry(properties)
        return config.resilienceExecutor(bulkheads, breakers, retries)
    }

    @Test
    fun `circuito abre com 5xx`() {
        val properties = ResilienceProperties(
            defaultCircuitBreaker = ResilienceProperties.CircuitBreakerProperties(
                slidingWindowSize = 4,
                minimumNumberOfCalls = 4,
                failureRateThreshold = 50f
            ),
            defaultRetry = ResilienceProperties.RetryProperties(enabled = false)
        )
        val executor = executor(properties)

        repeat(4) {
            assertFailsWith<HttpServerErrorException> {
                executor.execute(ResilienceOperation.CHARGE_QUERY) {
                    throw HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE)
                }
            }
        }

        assertFailsWith<CallNotPermittedException> {
            executor.execute(ResilienceOperation.CHARGE_QUERY) { "nunca chega aqui" }
        }
    }

    @Test
    fun `circuito abre com 429`() {
        val properties = ResilienceProperties(
            defaultCircuitBreaker = ResilienceProperties.CircuitBreakerProperties(
                slidingWindowSize = 4,
                minimumNumberOfCalls = 4
            ),
            defaultRetry = ResilienceProperties.RetryProperties(enabled = false)
        )
        val executor = executor(properties)

        repeat(4) {
            assertFailsWith<HttpClientErrorException> {
                executor.execute(ResilienceOperation.CHARGE_QUERY) {
                    throw HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS)
                }
            }
        }

        assertFailsWith<CallNotPermittedException> {
            executor.execute(ResilienceOperation.CHARGE_QUERY) { "bloqueado" }
        }
    }

    @Test
    fun `4xx de negocio nao abre o circuito`() {
        val properties = ResilienceProperties(
            defaultCircuitBreaker = ResilienceProperties.CircuitBreakerProperties(
                slidingWindowSize = 4,
                minimumNumberOfCalls = 4
            ),
            defaultRetry = ResilienceProperties.RetryProperties(enabled = false)
        )
        val breakers = config.circuitBreakerRegistry(properties)
        val executor = config.resilienceExecutor(
            config.bulkheadRegistry(properties),
            breakers,
            config.retryRegistry(properties)
        )

        repeat(20) {
            assertFailsWith<HttpClientErrorException> {
                executor.execute(ResilienceOperation.CHARGE_QUERY) {
                    throw HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY)
                }
            }
        }

        val breaker = breakers.circuitBreaker("charge-query")
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state)
        assertEquals(0, breaker.metrics.numberOfBufferedCalls)
    }

    @Test
    fun `retry repete operacao idempotente com backoff exponencial`() {
        val properties = ResilienceProperties(
            defaultRetry = ResilienceProperties.RetryProperties(
                maxAttempts = 3,
                initialBackoff = Duration.ofMillis(20),
                multiplier = 2.0,
                maxBackoff = Duration.ofMillis(200)
            )
        )
        val executor = executor(properties)
        val tentativas = AtomicInteger()
        val inicio = System.nanoTime()

        assertFailsWith<HttpServerErrorException> {
            executor.execute(ResilienceOperation.CHARGE_QUERY) {
                tentativas.incrementAndGet()
                throw HttpServerErrorException(HttpStatus.BAD_GATEWAY)
            }
        }

        val decorridoMillis = (System.nanoTime() - inicio) / 1_000_000
        assertEquals(3, tentativas.get())
        assertTrue(decorridoMillis >= 60, "esperado ao menos 20ms + 40ms de espera, foi ${decorridoMillis}ms")
    }

    @Test
    fun `retry nao repete 4xx de negocio`() {
        val executor = executor(ResilienceProperties())
        val tentativas = AtomicInteger()

        assertFailsWith<HttpClientErrorException> {
            executor.execute(ResilienceOperation.CHARGE_QUERY) {
                tentativas.incrementAndGet()
                throw HttpClientErrorException(HttpStatus.NOT_FOUND)
            }
        }

        assertEquals(1, tentativas.get())
    }

    @Test
    fun `operacao nao idempotente nao recebe retry`() {
        val executor = executor(ResilienceProperties())
        val tentativas = AtomicInteger()

        assertFailsWith<HttpServerErrorException> {
            executor.execute(ResilienceOperation.CHARGE_CREATE) {
                tentativas.incrementAndGet()
                throw HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE)
            }
        }

        assertEquals(1, tentativas.get())
    }

    @Test
    fun `habilitar retry em operacao nao idempotente falha na subida`() {
        val properties = ResilienceProperties(
            retries = mapOf(
                ResilienceOperation.CHARGE_CREATE to ResilienceProperties.RetryProperties(enabled = true)
            )
        )

        val erro = assertFailsWith<IllegalStateException> { config.retryRegistry(properties) }
        assertTrue(erro.message!!.contains("CHARGE_CREATE"), erro.message!!)
    }

    @Test
    fun `bulkhead limita a concorrencia por dominio`() {
        val properties = ResilienceProperties(
            bulkheads = mapOf(
                ResilienceDomain.PAYMENT_PROVIDER to ResilienceProperties.BulkheadProperties(
                    maxConcurrentCalls = 1,
                    maxWait = Duration.ZERO
                )
            )
        )
        val executor = executor(properties)
        val dentro = CountDownLatch(1)
        val libera = CountDownLatch(1)

        val ocupante = Thread {
            executor.execute(ResilienceOperation.CHARGE_QUERY) {
                dentro.countDown()
                libera.await(5, TimeUnit.SECONDS)
            }
        }
        ocupante.start()
        assertTrue(dentro.await(5, TimeUnit.SECONDS))

        assertFailsWith<BulkheadFullException> {
            executor.execute(ResilienceOperation.CHARGE_CANCEL) { "mesma origem, mesmo pool" }
        }

        libera.countDown()
        ocupante.join(5_000)
    }

    @Test
    fun `bulkhead de um dominio nao afeta o outro`() {
        val properties = ResilienceProperties(
            bulkheads = mapOf(
                ResilienceDomain.PAYMENT_PROVIDER to ResilienceProperties.BulkheadProperties(
                    maxConcurrentCalls = 1,
                    maxWait = Duration.ZERO
                ),
                ResilienceDomain.INVOICE_AUTHORITY to ResilienceProperties.BulkheadProperties(
                    maxConcurrentCalls = 1,
                    maxWait = Duration.ZERO
                )
            )
        )
        val executor = executor(properties)
        val dentro = CountDownLatch(1)
        val libera = CountDownLatch(1)

        val ocupante = Thread {
            executor.execute(ResilienceOperation.CHARGE_QUERY) {
                dentro.countDown()
                libera.await(5, TimeUnit.SECONDS)
            }
        }
        ocupante.start()
        assertTrue(dentro.await(5, TimeUnit.SECONDS))

        assertEquals("ok", executor.execute(ResilienceOperation.INVOICE_QUERY) { "ok" })

        libera.countDown()
        ocupante.join(5_000)
    }

    @Test
    fun `sucesso atravessa as tres camadas`() {
        val executor = executor(ResilienceProperties())

        assertEquals("cobrado", executor.execute(ResilienceOperation.CHARGE_CREATE) { "cobrado" })
    }
}
