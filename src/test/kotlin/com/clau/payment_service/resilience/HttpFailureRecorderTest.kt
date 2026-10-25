package com.clau.payment_service.resilience

import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException

class HttpFailureRecorderTest {

    private val padrao = HttpFailureRecorder(
        recorded = setOf(HttpFailureClass.TOO_MANY_REQUESTS, HttpFailureClass.SERVER_ERROR),
        recordConnectionFailures = true
    )

    @Test
    fun `429 conta como falha`() {
        assertTrue(padrao.test(HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS)))
    }

    @Test
    fun `5xx conta como falha`() {
        assertTrue(padrao.test(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)))
        assertTrue(padrao.test(HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE)))
        assertTrue(padrao.test(HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT)))
    }

    @Test
    fun `4xx de negocio nao conta como falha`() {
        assertFalse(padrao.test(HttpClientErrorException(HttpStatus.BAD_REQUEST)))
        assertFalse(padrao.test(HttpClientErrorException(HttpStatus.NOT_FOUND)))
        assertFalse(padrao.test(HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY)))
    }

    @Test
    fun `falha de conexao conta quando habilitado`() {
        assertTrue(padrao.test(ResourceAccessException("conexao recusada", IOException("boom"))))
        assertTrue(padrao.test(SocketTimeoutException("read timed out")))
    }

    @Test
    fun `falha de conexao e ignorada quando desabilitado`() {
        val estrito = HttpFailureRecorder(
            recorded = setOf(HttpFailureClass.TOO_MANY_REQUESTS, HttpFailureClass.SERVER_ERROR),
            recordConnectionFailures = false
        )

        assertFalse(estrito.test(ResourceAccessException("conexao recusada")))
        assertFalse(estrito.test(SocketTimeoutException("read timed out")))
    }

    @Test
    fun `encontra o status em excecao encadeada`() {
        val encadeada = IllegalStateException(
            "falha ao cobrar",
            HttpServerErrorException(HttpStatus.BAD_GATEWAY)
        )

        assertTrue(padrao.test(encadeada))
    }

    @Test
    fun `aceita excecao propria que expoe o status`() {
        class FalhaDoProvedor(override val httpStatus: Int) : RuntimeException(), HttpStatusAware

        assertTrue(padrao.test(FalhaDoProvedor(503)))
        assertTrue(padrao.test(FalhaDoProvedor(429)))
        assertFalse(padrao.test(FalhaDoProvedor(409)))
    }

    @Test
    fun `excecao sem status nem conexao nao conta como falha`() {
        assertFalse(padrao.test(IllegalArgumentException("valor invalido")))
    }

    @Test
    fun `configuracao pode incluir 4xx quando o time quiser`() {
        val amplo = HttpFailureRecorder(
            recorded = setOf(HttpFailureClass.CLIENT_ERROR),
            recordConnectionFailures = false
        )

        assertTrue(amplo.test(HttpClientErrorException(HttpStatus.NOT_FOUND)))
        assertFalse(amplo.test(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)))
    }
}
