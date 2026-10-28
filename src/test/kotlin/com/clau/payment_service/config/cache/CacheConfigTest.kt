package com.clau.payment_service.config.cache

import com.clau.payment_service.cache.CacheCircuit
import com.clau.payment_service.cache.ResilientCacheManager
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.support.NoOpCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory

class CacheConfigTest {

    private val runner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration::class.java))
        .withUserConfiguration(
            RedisConfig::class.java,
            CacheConfig::class.java,
            ConsultaConfig::class.java
        )
        .withPropertyValues(
            "spring.data.redis.client-type=jedis",
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=$PORTA_SEM_REDIS",
            "spring.data.redis.connect-timeout=100ms",
            "spring.data.redis.timeout=100ms",
            "spring.data.redis.jedis.pool.max-wait=100ms",
            "cache.circuit.failure-threshold=1"
        )

    @Test
    fun `a aplicacao sobe com jedis mesmo sem redis alcancavel`() {
        runner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasSingleBean(JedisConnectionFactory::class.java)
            assertThat(context.getBean(CacheManager::class.java)).isInstanceOf(ResilientCacheManager::class.java)
        }
    }

    @Test
    fun `metodo cacheado responde com redis fora e registra a degradacao`() {
        runner.run { context ->
            val consulta = context.getBean(Consulta::class.java)

            assertEquals("valor-um", consulta.buscar("um"))
            assertEquals("valor-um", consulta.buscar("um"))

            assertEquals(2, context.getBean(AtomicInteger::class.java).get())
            assertTrue(context.getBean(CacheCircuit::class.java).isOpen())
        }
    }

    @Test
    fun `cache desligado troca o gerenciador por um que nao guarda nada`() {
        runner.withPropertyValues("cache.enabled=false").run { context ->
            val cacheManager = context.getBean(CacheManager::class.java)

            assertThat(cacheManager).isInstanceOf(NoOpCacheManager::class.java)
            cacheManager.getCache("teste")!!.put("chave", "valor")
            assertNull(cacheManager.getCache("teste")!!.get("chave"))
        }
    }

    @Test
    fun `ttl declarado por cache entra na configuracao do redis`() {
        runner.withPropertyValues("cache.ttls.cobrancas=2m").run { context ->
            val cacheManager = context.getBean(CacheManager::class.java)

            assertThat(cacheManager.cacheNames).contains("cobrancas")
        }
    }

    @Configuration(proxyBeanMethods = false)
    class ConsultaConfig {

        @Bean
        fun execucoes(): AtomicInteger = AtomicInteger()

        @Bean
        fun consulta(execucoes: AtomicInteger): Consulta = Consulta(execucoes)
    }

    open class Consulta(private val execucoes: AtomicInteger) {

        @Cacheable("consultas")
        open fun buscar(chave: String): String {
            execucoes.incrementAndGet()
            return "valor-$chave"
        }
    }

    private companion object {

        const val PORTA_SEM_REDIS = 63799
    }
}
