package com.clau.payment_service.config.observability

import com.clau.payment_service.observability.web.CorrelationFilter
import com.clau.payment_service.observability.web.CorrelationProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorrelationProperties::class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class ObservabilityConfig {

    @Bean
    fun correlationFilterRegistration(
        properties: CorrelationProperties
    ): FilterRegistrationBean<CorrelationFilter> {
        val registration = FilterRegistrationBean(CorrelationFilter(properties))
        registration.order = Ordered.HIGHEST_PRECEDENCE
        registration.addUrlPatterns("/*")
        return registration
    }
}
