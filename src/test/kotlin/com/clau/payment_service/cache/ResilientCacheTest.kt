package com.clau.payment_service.cache

import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.springframework.cache.Cache
import org.springframework.cache.concurrent.ConcurrentMapCache
import org.springframework.dao.QueryTimeoutException

class ResilientCacheTest {

    private val clock = MutableClock()

    @Test
    fun `leitura com redis indisponivel devolve ausencia de valor em vez de erro`() {
        val cache = ResilientCache(UnavailableCache(), circuit())

        assertNull(cache.get("chave"))
        assertNull(cache.get("chave", String::class.java))
    }

    @Test
    fun `escrita e remocao com redis indisponivel nao propagam erro`() {
        val cache = ResilientCache(UnavailableCache(), circuit())

        cache.put("chave", "valor")
        cache.evict("chave")
        cache.clear()

        assertEquals(false, cache.evictIfPresent("chave"))
        assertEquals(false, cache.invalidate())
        assertNull(cache.putIfAbsent("chave", "valor"))
    }

    @Test
    fun `falha na leitura abre o circuito e o delegate deixa de ser chamado`() {
        val delegate = UnavailableCache()
        val cache = ResilientCache(delegate, circuit(failureThreshold = 1))

        cache.get("chave")
        cache.get("chave")
        cache.put("chave", "valor")

        assertEquals(1, delegate.chamadas.get())
    }

    @Test
    fun `pausa vencida devolve o trafego ao redis`() {
        val delegate = UnavailableCache()
        val cache = ResilientCache(delegate, circuit(failureThreshold = 1))

        cache.get("chave")
        clock.advance(Duration.ofSeconds(31))
        cache.get("chave")

        assertEquals(2, delegate.chamadas.get())
    }

    @Test
    fun `carregador alimenta a resposta quando o redis esta fora`() {
        val cache = ResilientCache(UnavailableCache(), circuit())
        val invocacoes = AtomicInteger()

        val valor = cache.get("chave", Callable { invocacoes.incrementAndGet(); "calculado" })

        assertEquals("calculado", valor)
        assertEquals(1, invocacoes.get())
    }

    @Test
    fun `carregador nao roda duas vezes quando a escrita falha depois da carga`() {
        val cache = ResilientCache(LoadThenFailCache(), circuit())
        val invocacoes = AtomicInteger()

        val valor = cache.get("chave", Callable { invocacoes.incrementAndGet(); "calculado" })

        assertEquals("calculado", valor)
        assertEquals(1, invocacoes.get())
    }

    @Test
    fun `erro do carregador sobe para quem chamou`() {
        val cache = ResilientCache(UnavailableCache(), circuit())

        val falha = assertFailsWith<Cache.ValueRetrievalException> {
            cache.get("chave", Callable<String> { throw IllegalStateException("provedor fora") })
        }

        assertTrue(falha.cause is IllegalStateException)
    }

    @Test
    fun `operacao bem sucedida fecha o circuito`() {
        val circuit = circuit(failureThreshold = 1)
        val indisponivel = ResilientCache(UnavailableCache(), circuit)
        indisponivel.get("chave")
        assertTrue(circuit.isOpen())

        clock.advance(Duration.ofSeconds(31))
        val disponivel = ResilientCache(ConcurrentMapCache("teste"), circuit)
        disponivel.put("chave", "valor")

        assertEquals("valor", disponivel.get("chave")?.get())
        assertTrue(circuit.available())
    }

    @Test
    fun `delegate que funciona continua entregando valor`() {
        val cache = ResilientCache(ConcurrentMapCache("teste"), circuit())

        cache.put("chave", "valor")

        assertEquals("valor", cache.get("chave", String::class.java))
        assertEquals("teste", cache.name)
    }

    private fun circuit(failureThreshold: Int = 3) =
        CacheCircuit(failureThreshold, Duration.ofSeconds(30), clock)

    private class UnavailableCache : Cache {

        val chamadas = AtomicInteger()

        override fun getName(): String = "indisponivel"

        override fun getNativeCache(): Any = this

        override fun get(key: Any): Cache.ValueWrapper = falhar()

        override fun <T : Any> get(key: Any, type: Class<T>?): T = falhar()

        override fun <T : Any> get(key: Any, valueLoader: Callable<T>): T = falhar()

        override fun put(key: Any, value: Any?) = falhar()

        override fun putIfAbsent(key: Any, value: Any?): Cache.ValueWrapper = falhar()

        override fun evict(key: Any) = falhar()

        override fun evictIfPresent(key: Any): Boolean = falhar()

        override fun clear() = falhar()

        override fun invalidate(): Boolean = falhar()

        private fun falhar(): Nothing {
            chamadas.incrementAndGet()
            throw QueryTimeoutException("redis fora do ar")
        }
    }

    private class LoadThenFailCache : Cache {

        override fun getName(): String = "carrega-e-falha"

        override fun getNativeCache(): Any = this

        override fun get(key: Any): Cache.ValueWrapper? = null

        override fun <T : Any> get(key: Any, type: Class<T>?): T? = null

        override fun <T : Any> get(key: Any, valueLoader: Callable<T>): T {
            valueLoader.call()
            throw QueryTimeoutException("redis caiu ao gravar o valor carregado")
        }

        override fun put(key: Any, value: Any?) = throw QueryTimeoutException("redis fora do ar")

        override fun evict(key: Any) = throw QueryTimeoutException("redis fora do ar")

        override fun clear() = throw QueryTimeoutException("redis fora do ar")
    }
}
