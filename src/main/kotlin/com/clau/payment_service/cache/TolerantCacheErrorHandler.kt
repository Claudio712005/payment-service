package com.clau.payment_service.cache

import org.slf4j.LoggerFactory
import org.springframework.cache.Cache
import org.springframework.cache.interceptor.CacheErrorHandler

class TolerantCacheErrorHandler(private val circuit: CacheCircuit) : CacheErrorHandler {

    override fun handleCacheGetError(exception: RuntimeException, cache: Cache, key: Any) =
        degrade("get", cache, exception)

    override fun handleCachePutError(exception: RuntimeException, cache: Cache, key: Any, value: Any?) =
        degrade("put", cache, exception)

    override fun handleCacheEvictError(exception: RuntimeException, cache: Cache, key: Any) =
        degrade("evict", cache, exception)

    override fun handleCacheClearError(exception: RuntimeException, cache: Cache) =
        degrade("clear", cache, exception)

    private fun degrade(operation: String, cache: Cache, failure: RuntimeException) {
        circuit.recordFailure()
        log.warn("cache {} degradado na operacao {}: {}", cache.name, operation, failure.message)
    }

    private companion object {

        val log = LoggerFactory.getLogger(TolerantCacheErrorHandler::class.java)
    }
}
