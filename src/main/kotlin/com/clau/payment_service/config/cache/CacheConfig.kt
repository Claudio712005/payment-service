package com.clau.payment_service.config.cache

import com.clau.payment_service.cache.CacheCircuit
import com.clau.payment_service.cache.CacheProperties
import com.clau.payment_service.cache.ResilientCacheManager
import com.clau.payment_service.cache.TolerantCacheErrorHandler
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CachingConfigurer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.interceptor.CacheErrorHandler
import org.springframework.cache.support.NoOpCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration(proxyBeanMethods = false)
@EnableCaching
@EnableConfigurationProperties(CacheProperties::class)
class CacheConfig(private val properties: CacheProperties) : CachingConfigurer {

    private val circuit = CacheCircuit(properties.circuit.failureThreshold, properties.circuit.cooldown)

    @Bean
    fun cacheCircuit(): CacheCircuit = circuit

    @Bean
    override fun errorHandler(): CacheErrorHandler = TolerantCacheErrorHandler(circuit)

    @Bean
    fun cacheManager(
        redisConnectionFactory: RedisConnectionFactory,
        genericJacksonJsonRedisSerializer: GenericJacksonJsonRedisSerializer
    ): CacheManager {
        if (!properties.enabled) {
            return NoOpCacheManager()
        }
        val defaults = cacheDefaults(genericJacksonJsonRedisSerializer)
        val perCache = properties.ttls.mapValues { (_, ttl) -> defaults.entryTtl(ttl) }
        val redisCacheManager = RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(defaults)
            .withInitialCacheConfigurations(perCache)
            .enableCreateOnMissingCache()
            .build()
        return ResilientCacheManager(redisCacheManager, circuit)
    }

    @Bean
    @ConditionalOnBean(MeterRegistry::class)
    fun cacheCircuitMetrics(meterRegistry: MeterRegistry, cacheCircuit: CacheCircuit): CacheCircuitMetricsBinder {
        meterRegistry.gauge("cache.circuit.open", cacheCircuit) { if (it.isOpen()) 1.0 else 0.0 }
        return CacheCircuitMetricsBinder()
    }

    class CacheCircuitMetricsBinder

    private fun cacheDefaults(
        valueSerializer: GenericJacksonJsonRedisSerializer
    ): RedisCacheConfiguration {
        val configuration = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(properties.defaultTtl)
            .prefixCacheNameWith("${properties.keyPrefix}:")
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer)
            )
        return if (properties.cacheNullValues) configuration else configuration.disableCachingNullValues()
    }
}
