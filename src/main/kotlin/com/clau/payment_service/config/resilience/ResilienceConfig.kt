package com.clau.payment_service.config.resilience

import com.clau.payment_service.resilience.HttpFailureRecorder
import com.clau.payment_service.resilience.ResilienceDomain
import com.clau.payment_service.resilience.ResilienceExecutor
import com.clau.payment_service.resilience.ResilienceOperation
import com.clau.payment_service.resilience.ResilienceProperties
import com.clau.payment_service.resilience.SlidingWindow
import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadConfig
import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import io.github.resilience4j.core.IntervalFunction
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ResilienceProperties::class)
class ResilienceConfig {

    private val log = LoggerFactory.getLogger(ResilienceConfig::class.java)

    @Bean
    fun bulkheadRegistry(properties: ResilienceProperties): BulkheadRegistry {
        val registry = BulkheadRegistry.ofDefaults()
        ResilienceDomain.entries.forEach { domain ->
            val settings = properties.bulkheadOf(domain)
            registry.bulkhead(
                domain.instanceName(),
                BulkheadConfig.custom()
                    .maxConcurrentCalls(settings.maxConcurrentCalls)
                    .maxWaitDuration(settings.maxWait)
                    .writableStackTraceEnabled(false)
                    .build()
            )
        }
        return registry
    }

    @Bean
    fun circuitBreakerRegistry(properties: ResilienceProperties): CircuitBreakerRegistry {
        val registry = CircuitBreakerRegistry.ofDefaults()
        ResilienceOperation.entries.forEach { operation ->
            val settings = properties.circuitBreakerOf(operation)
            val recorder = HttpFailureRecorder(settings.recordedFailures, settings.recordConnectionFailures)
            val breaker = registry.circuitBreaker(
                operation.instanceName(),
                CircuitBreakerConfig.custom()
                    .failureRateThreshold(settings.failureRateThreshold)
                    .slowCallRateThreshold(settings.slowCallRateThreshold)
                    .slowCallDurationThreshold(settings.slowCallDuration)
                    .slidingWindowType(settings.slidingWindow.toResilience4j())
                    .slidingWindowSize(settings.slidingWindowSize)
                    .minimumNumberOfCalls(settings.minimumNumberOfCalls)
                    .waitDurationInOpenState(settings.waitInOpenState)
                    .permittedNumberOfCallsInHalfOpenState(settings.permittedCallsInHalfOpenState)
                    .recordException(recorder)
                    .ignoreException { !recorder.test(it) }
                    .build()
            )
            breaker.eventPublisher.onStateTransition { event ->
                log.warn(
                    "circuito {} transitou de {} para {}",
                    operation.name,
                    event.stateTransition.fromState,
                    event.stateTransition.toState
                )
            }
        }
        return registry
    }

    @Bean
    fun retryRegistry(properties: ResilienceProperties): RetryRegistry {
        val registry = RetryRegistry.ofDefaults()
        ResilienceOperation.entries
            .filter { properties.retryEnabledFor(it) }
            .forEach { operation ->
                val settings = properties.retryOf(operation)
                val interval = IntervalFunction.ofExponentialRandomBackoff(
                    settings.initialBackoff,
                    settings.multiplier,
                    settings.jitter,
                    settings.maxBackoff
                )
                val recorder = HttpFailureRecorder(
                    properties.circuitBreakerOf(operation).recordedFailures,
                    properties.circuitBreakerOf(operation).recordConnectionFailures
                )
                registry.retry(
                    operation.instanceName(),
                    RetryConfig.custom<Any>()
                        .maxAttempts(settings.maxAttempts)
                        .intervalFunction(interval)
                        .retryOnException(recorder)
                        .failAfterMaxAttempts(false)
                        .build()
                )
            }
        return registry
    }

    @Bean
    fun resilienceExecutor(
        bulkheadRegistry: BulkheadRegistry,
        circuitBreakerRegistry: CircuitBreakerRegistry,
        retryRegistry: RetryRegistry
    ): ResilienceExecutor {
        val bulkheads = ResilienceDomain.entries.associateWith {
            bulkheadRegistry.bulkhead(it.instanceName())
        }
        val breakers = ResilienceOperation.entries.associateWith {
            circuitBreakerRegistry.circuitBreaker(it.instanceName())
        }
        val retries = retryRegistry.allRetries
            .associateBy { retry -> ResilienceOperation.entries.first { it.instanceName() == retry.name } }
        return ResilienceExecutor(bulkheads, breakers, retries)
    }

    @Bean
    @ConditionalOnBean(MeterRegistry::class)
    fun resilienceMetrics(
        meterRegistry: MeterRegistry,
        bulkheadRegistry: BulkheadRegistry,
        circuitBreakerRegistry: CircuitBreakerRegistry,
        retryRegistry: RetryRegistry
    ): ResilienceMetricsBinder = ResilienceMetricsBinder().also {
        TaggedBulkheadMetrics.ofBulkheadRegistry(bulkheadRegistry).bindTo(meterRegistry)
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry).bindTo(meterRegistry)
        TaggedRetryMetrics.ofRetryRegistry(retryRegistry).bindTo(meterRegistry)
    }

    class ResilienceMetricsBinder

    private companion object {

        fun ResilienceDomain.instanceName(): String = name.lowercase().replace('_', '-')

        fun ResilienceOperation.instanceName(): String = name.lowercase().replace('_', '-')

        fun SlidingWindow.toResilience4j(): CircuitBreakerConfig.SlidingWindowType = when (this) {
            SlidingWindow.COUNT_BASED -> CircuitBreakerConfig.SlidingWindowType.COUNT_BASED
            SlidingWindow.TIME_BASED -> CircuitBreakerConfig.SlidingWindowType.TIME_BASED
        }
    }
}
