package com.clau.payment_service.config.cache

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import tools.jackson.module.kotlin.KotlinModule

@Configuration(proxyBeanMethods = false)
class RedisConfig {

    @Bean
    fun genericJacksonJsonRedisSerializer(): GenericJacksonJsonRedisSerializer {
        val typeValidator = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("com.clau.payment_service.")
            .allowIfSubType("java.util.")
            .allowIfSubType("java.time.")
            .allowIfSubType("java.math.")
            .build()
        return GenericJacksonJsonRedisSerializer.builder()
            .enableSpringCacheNullValueSupport()
            .enableDefaultTyping(typeValidator)
            .customize { mapper -> mapper.addModule(KotlinModule.Builder().build()) }
            .build()
    }

    @Bean
    fun redisTemplate(
        redisConnectionFactory: RedisConnectionFactory,
        genericJacksonJsonRedisSerializer: GenericJacksonJsonRedisSerializer
    ): RedisTemplate<String, Any> {
        val template = RedisTemplate<String, Any>()
        template.setConnectionFactory(redisConnectionFactory)
        template.keySerializer = StringRedisSerializer()
        template.hashKeySerializer = StringRedisSerializer()
        template.valueSerializer = genericJacksonJsonRedisSerializer
        template.hashValueSerializer = genericJacksonJsonRedisSerializer
        template.setEnableTransactionSupport(false)
        return template
    }
}
