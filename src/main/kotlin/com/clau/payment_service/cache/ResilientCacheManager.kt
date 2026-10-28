package com.clau.payment_service.cache

import java.util.concurrent.ConcurrentHashMap
import org.springframework.beans.factory.InitializingBean
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager

class ResilientCacheManager(
    private val delegate: CacheManager,
    private val circuit: CacheCircuit
) : CacheManager, InitializingBean {

    private val caches = ConcurrentHashMap<String, Cache>()

    override fun afterPropertiesSet() {
        (delegate as? InitializingBean)?.afterPropertiesSet()
    }

    override fun getCache(name: String): Cache? {
        caches[name]?.let { return it }
        val target = delegate.getCache(name) ?: return null
        return caches.computeIfAbsent(name) { ResilientCache(target, circuit) }
    }

    override fun getCacheNames(): Collection<String> = delegate.cacheNames
}
