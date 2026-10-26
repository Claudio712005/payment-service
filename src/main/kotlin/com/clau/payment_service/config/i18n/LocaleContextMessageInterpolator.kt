package com.clau.payment_service.config.i18n

import jakarta.validation.MessageInterpolator
import java.util.Locale
import org.springframework.context.i18n.LocaleContextHolder

class LocaleContextMessageInterpolator(
    private val delegate: MessageInterpolator
) : MessageInterpolator {

    override fun interpolate(messageTemplate: String, context: MessageInterpolator.Context): String =
        delegate.interpolate(messageTemplate, context, LocaleContextHolder.getLocale())

    override fun interpolate(
        messageTemplate: String,
        context: MessageInterpolator.Context,
        locale: Locale
    ): String = delegate.interpolate(messageTemplate, context, locale)
}
