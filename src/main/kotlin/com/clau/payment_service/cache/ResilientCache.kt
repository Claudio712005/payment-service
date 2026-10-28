package com.clau.payment_service.cache

import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import org.slf4j.LoggerFactory
import org.springframework.cache.Cache

class ResilientCache(
    private val delegate: Cache,
    private val circuit: CacheCircuit
) : Cache {

    override fun getName(): String = delegate.name

    override fun getNativeCache(): Any = delegate.nativeCache

    override fun get(key: Any): Cache.ValueWrapper? = tolerate("get", key) { delegate.get(key) }

    override fun <T : Any> get(key: Any, type: Class<T>?): T? = tolerate("get", key) { delegate.get(key, type) }

    override fun <T : Any> get(key: Any, valueLoader: Callable<T>): T? {
        val tracked = TrackedLoader(valueLoader)
        if (circuit.available()) {
            try {
                return delegate.get(key, tracked).also { circuit.recordSuccess() }
            } catch (failure: Cache.ValueRetrievalException) {
                throw failure
            } catch (failure: RuntimeException) {
                degrade("get", key, failure)
                if (tracked.loaded) {
                    return tracked.value
                }
            }
        }
        return load(key, tracked)
    }

    override fun retrieve(key: Any): CompletableFuture<*>? = tolerate("retrieve", key) { delegate.retrieve(key) }

    override fun <T : Any> retrieve(
        key: Any,
        valueLoader: Supplier<CompletableFuture<T>>
    ): CompletableFuture<T> =
        tolerate("retrieve", key) { delegate.retrieve(key, valueLoader) } ?: valueLoader.get()

    override fun put(key: Any, value: Any?) {
        tolerate("put", key) { delegate.put(key, value) }
    }

    override fun putIfAbsent(key: Any, value: Any?): Cache.ValueWrapper? =
        tolerate("putIfAbsent", key) { delegate.putIfAbsent(key, value) }

    override fun evict(key: Any) {
        tolerate("evict", key) { delegate.evict(key) }
    }

    override fun evictIfPresent(key: Any): Boolean =
        tolerate("evictIfPresent", key) { delegate.evictIfPresent(key) } ?: false

    override fun clear() {
        tolerate("clear", null) { delegate.clear() }
    }

    override fun invalidate(): Boolean = tolerate("invalidate", null) { delegate.invalidate() } ?: false

    private fun <T> tolerate(operation: String, key: Any?, action: () -> T): T? {
        if (!circuit.available()) {
            return null
        }
        return try {
            action().also { circuit.recordSuccess() }
        } catch (failure: RuntimeException) {
            degrade(operation, key, failure)
            null
        }
    }

    private fun <T> load(key: Any, loader: Callable<T>): T =
        try {
            loader.call()
        } catch (failure: Throwable) {
            throw Cache.ValueRetrievalException(key, loader, failure)
        }

    private fun degrade(operation: String, key: Any?, failure: RuntimeException) {
        circuit.recordFailure()
        log.warn(
            "cache {} degradado na operacao {} (chave {}): {}",
            delegate.name,
            operation,
            key,
            failure.message
        )
    }

    private class TrackedLoader<T : Any>(private val delegate: Callable<T>) : Callable<T> {

        var loaded = false
            private set

        var value: T? = null
            private set

        override fun call(): T {
            val loadedValue = delegate.call()
            value = loadedValue
            loaded = true
            return loadedValue
        }
    }

    private companion object {

        val log = LoggerFactory.getLogger(ResilientCache::class.java)
    }
}
