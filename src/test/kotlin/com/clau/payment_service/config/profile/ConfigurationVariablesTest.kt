package com.clau.payment_service.config.profile

import com.clau.payment_service.cache.CacheProperties
import com.clau.payment_service.resilience.HttpFailureClass
import com.clau.payment_service.resilience.ResilienceDomain
import com.clau.payment_service.resilience.ResilienceOperation
import com.clau.payment_service.resilience.ResilienceProperties
import com.clau.payment_service.resilience.SlidingWindow
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

class ConfigurationVariablesTest {

    private fun ambiente(variaveis: Map<String, Any> = emptyMap()): ConfigurableEnvironment {
        val context = GenericApplicationContext()
        if (variaveis.isNotEmpty()) {
            context.environment.propertySources.addFirst(MapPropertySource("variaveis", variaveis))
        }
        ConfigDataApplicationContextInitializer().initialize(context)
        return context.environment
    }

    private fun resiliencia(ambiente: ConfigurableEnvironment): ResilienceProperties =
        Binder.get(ambiente).bind("resilience", Bindable.of(ResilienceProperties::class.java)).get()

    private fun cache(ambiente: ConfigurableEnvironment): CacheProperties =
        Binder.get(ambiente).bind("cache", Bindable.of(CacheProperties::class.java)).get()

    @Test
    fun `nenhuma variavel do arquivo padrao fica sem resolver`() {
        val ambiente = ambiente()

        listOf(
            "spring.application.name",
            "spring.profiles.default",
            "spring.datasource.url",
            "spring.datasource.username",
            "spring.jpa.open-in-view",
            "spring.jpa.properties.hibernate.default_schema",
            "spring.jpa.properties.hibernate.jdbc.time_zone",
            "spring.data.mongodb.uri",
            "spring.data.redis.host",
            "spring.data.redis.port",
            "spring.data.redis.database",
            "spring.data.redis.connect-timeout",
            "spring.data.redis.timeout",
            "spring.data.redis.jedis.pool.max-active",
            "spring.data.redis.jedis.pool.max-wait",
            "cache.enabled",
            "cache.key-prefix",
            "cache.default-ttl",
            "cache.circuit.failure-threshold",
            "cache.circuit.cooldown",
            "resilience.bulkheads.payment-provider.max-concurrent-calls",
            "resilience.circuit-breakers.invoice-issue.wait-in-open-state",
            "resilience.retries.invoice-query.jitter",
            "logging.level.com.clau.payment_service"
        ).forEach { chave ->
            val valor = ambiente.getProperty(chave)
            assertTrue(valor != null && valor.isNotBlank(), "sem valor para $chave")
            assertFalse(valor!!.contains("\${"), "placeholder nao resolvido em $chave: $valor")
        }
    }

    @Test
    fun `valores padrao do arquivo chegam tipados nas properties`() {
        val properties = resiliencia(ambiente())

        assertEquals(16, properties.bulkheadOf(ResilienceDomain.PAYMENT_PROVIDER).maxConcurrentCalls)
        assertEquals(Duration.ofMillis(50), properties.bulkheadOf(ResilienceDomain.PAYMENT_PROVIDER).maxWait)
        assertEquals(Duration.ZERO, properties.bulkheadOf(ResilienceDomain.INVOICE_AUTHORITY).maxWait)

        val breaker = properties.circuitBreakerOf(ResilienceOperation.CHARGE_QUERY)
        assertEquals(SlidingWindow.TIME_BASED, breaker.slidingWindow)
        assertEquals(60, breaker.slidingWindowSize)
        assertEquals(Duration.ofSeconds(15), breaker.waitInOpenState)

        val retry = properties.retryOf(ResilienceOperation.INVOICE_QUERY)
        assertEquals(5, retry.maxAttempts)
        assertEquals(Duration.ofSeconds(1), retry.initialBackoff)
        assertEquals(0.5, retry.jitter)
    }

    @Test
    fun `lista de classes de falha vem de variavel separada por virgula`() {
        val properties = resiliencia(ambiente())

        assertEquals(
            setOf(HttpFailureClass.TOO_MANY_REQUESTS, HttpFailureClass.SERVER_ERROR),
            properties.circuitBreakerOf(ResilienceOperation.CHARGE_CREATE).recordedFailures
        )
    }

    @Test
    fun `variavel de ambiente sobrepoe o padrao do arquivo`() {
        val properties = resiliencia(
            ambiente(
                mapOf(
                    "PAYMENT_BULKHEAD_PROVIDER_CALLS" to "4",
                    "PAYMENT_BULKHEAD_PROVIDER_WAIT" to "250ms",
                    "PAYMENT_CB_CHARGE_QUERY_WINDOW" to "COUNT_BASED",
                    "PAYMENT_CB_CHARGE_CREATE_FAILURES" to "SERVER_ERROR",
                    "PAYMENT_RETRY_INVOICE_QUERY_ATTEMPTS" to "2"
                )
            )
        )

        assertEquals(4, properties.bulkheadOf(ResilienceDomain.PAYMENT_PROVIDER).maxConcurrentCalls)
        assertEquals(Duration.ofMillis(250), properties.bulkheadOf(ResilienceDomain.PAYMENT_PROVIDER).maxWait)
        assertEquals(
            SlidingWindow.COUNT_BASED,
            properties.circuitBreakerOf(ResilienceOperation.CHARGE_QUERY).slidingWindow
        )
        assertEquals(
            setOf(HttpFailureClass.SERVER_ERROR),
            properties.circuitBreakerOf(ResilienceOperation.CHARGE_CREATE).recordedFailures
        )
        assertEquals(2, properties.retryOf(ResilienceOperation.INVOICE_QUERY).maxAttempts)
    }

    @Test
    fun `variavel de ambiente sobrepoe conexao e nome da aplicacao`() {
        val ambiente = ambiente(
            mapOf(
                "PAYMENT_APP_NAME" to "pagamentos",
                "PAYMENT_DB_URL" to "jdbc:oracle:thin:@//banco:1521/PROD",
                "MONGODB_URI" to "mongodb://documentos:27017/pagamentos",
                "REDIS_HOST" to "cache"
            )
        )

        assertEquals("pagamentos", ambiente.getProperty("spring.application.name"))
        assertEquals("jdbc:oracle:thin:@//banco:1521/PROD", ambiente.getProperty("spring.datasource.url"))
        assertEquals("mongodb://documentos:27017/pagamentos", ambiente.getProperty("spring.data.mongodb.uri"))
        assertEquals("cache", ambiente.getProperty("spring.data.redis.host"))
    }

    @Test
    fun `padroes do cache chegam tipados nas properties`() {
        val properties = cache(ambiente())

        assertTrue(properties.enabled)
        assertEquals("payment", properties.keyPrefix)
        assertEquals(Duration.ofMinutes(10), properties.defaultTtl)
        assertEquals(3, properties.circuit.failureThreshold)
        assertEquals(Duration.ofSeconds(30), properties.circuit.cooldown)
    }

    @Test
    fun `variavel de ambiente desliga o cache e muda a pausa do circuito`() {
        val properties = cache(
            ambiente(
                mapOf(
                    "PAYMENT_CACHE_ENABLED" to "false",
                    "PAYMENT_CACHE_DEFAULT_TTL" to "45s",
                    "PAYMENT_CACHE_CIRCUIT_FAILURES" to "7",
                    "PAYMENT_CACHE_CIRCUIT_COOLDOWN" to "2m"
                )
            )
        )

        assertFalse(properties.enabled)
        assertEquals(Duration.ofSeconds(45), properties.defaultTtl)
        assertEquals(7, properties.circuit.failureThreshold)
        assertEquals(Duration.ofMinutes(2), properties.circuit.cooldown)
    }

    @Test
    fun `jedis e o cliente de redis declarado no arquivo padrao`() {
        assertEquals("jedis", ambiente().getProperty("spring.data.redis.client-type"))
    }

    @Test
    fun `saude do redis fica fora das sondas do perfil local`() {
        val ambiente = ambiente(mapOf("spring.profiles.active" to "local"))

        assertEquals("false", ambiente.getProperty("management.health.redis.enabled"))
    }

    @Test
    fun `flyway segue desligado no arquivo padrao e nao e variavel`() {
        assertEquals("false", ambiente().getProperty("spring.flyway.enabled"))
        assertEquals(
            "false",
            ambiente(mapOf("PAYMENT_FLYWAY_ENABLED" to "true")).getProperty("spring.flyway.enabled")
        )
    }
}
