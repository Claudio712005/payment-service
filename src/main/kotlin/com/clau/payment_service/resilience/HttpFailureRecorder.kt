package com.clau.payment_service.resilience

import java.io.IOException
import java.util.concurrent.TimeoutException
import java.util.function.Predicate
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.ResourceAccessException

class HttpFailureRecorder(
    private val recorded: Set<HttpFailureClass>,
    private val recordConnectionFailures: Boolean
) : Predicate<Throwable> {

    override fun test(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < MAX_DEPTH) {
            statusOf(current)?.let { status ->
                return recorded.any { it.matches(status) }
            }
            if (isConnectionFailure(current)) {
                return recordConnectionFailures
            }
            current = current.cause.takeIf { it !== current }
            depth++
        }
        return false
    }

    private fun statusOf(throwable: Throwable): Int? = when (throwable) {
        is HttpStatusAware -> throwable.httpStatus
        is HttpStatusCodeException -> throwable.statusCode.value()
        else -> null
    }

    private fun isConnectionFailure(throwable: Throwable): Boolean =
        throwable is ResourceAccessException || throwable is IOException || throwable is TimeoutException

    private companion object {
        const val MAX_DEPTH = 5
    }
}
