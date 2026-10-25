package com.clau.payment_service.observability.web

import com.clau.payment_service.observability.logging.CorrelationKeys
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import org.slf4j.MDC
import org.springframework.web.filter.OncePerRequestFilter

class CorrelationFilter(
    private val properties: CorrelationProperties,
    private val sanitizer: IdentifierSanitizer = IdentifierSanitizer(properties.maxLength)
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val requestId = sanitizer.sanitize(request.getHeader(properties.requestHeader)) ?: newIdentifier()
        val correlationId = sanitizer.sanitize(request.getHeader(properties.correlationHeader)) ?: requestId

        MDC.put(CorrelationKeys.REQUEST_ID, requestId)
        MDC.put(CorrelationKeys.CORRELATION_ID, correlationId)

        if (properties.echoResponseHeaders) {
            response.setHeader(properties.requestHeader, requestId)
            response.setHeader(properties.correlationHeader, correlationId)
        }

        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(CorrelationKeys.REQUEST_ID)
            MDC.remove(CorrelationKeys.CORRELATION_ID)
        }
    }

    private fun newIdentifier(): String = UUID.randomUUID().toString().replace("-", "")
}
