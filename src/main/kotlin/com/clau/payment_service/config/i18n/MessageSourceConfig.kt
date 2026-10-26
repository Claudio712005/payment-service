package com.clau.payment_service.config.i18n

import org.hibernate.validator.messageinterpolation.ResourceBundleMessageInterpolator
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.validation.beanvalidation.MessageSourceResourceBundleLocator
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver

@Configuration(proxyBeanMethods = false)
class MessageSourceConfig {

    @Bean
    fun messageSource(): MessageSource = ResourceBundleMessageSource().apply {
        setBasenames(BASENAME)
        setDefaultEncoding("UTF-8")
        setDefaultLocale(SupportedLocales.DEFAULT)
        setFallbackToSystemLocale(false)
        setUseCodeAsDefaultMessage(false)
    }

    @Bean
    fun localeResolver(): LocaleResolver = AcceptHeaderLocaleResolver().apply {
        setDefaultLocale(SupportedLocales.DEFAULT)
        supportedLocales = SupportedLocales.ALL
    }

    @Bean
    fun validator(messageSource: MessageSource): LocalValidatorFactoryBean =
        LocalValidatorFactoryBean().apply {
            messageInterpolator = LocaleContextMessageInterpolator(
                ResourceBundleMessageInterpolator(MessageSourceResourceBundleLocator(messageSource))
            )
        }

    private companion object {
        const val BASENAME = "messages/messages"
    }
}
