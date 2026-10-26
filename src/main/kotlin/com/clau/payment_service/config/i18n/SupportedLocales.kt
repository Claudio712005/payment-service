package com.clau.payment_service.config.i18n

import java.util.Locale

object SupportedLocales {

    val DEFAULT: Locale = Locale.forLanguageTag("pt-BR")

    val ALL: List<Locale> = listOf(
        DEFAULT,
        Locale.ENGLISH,
        Locale.forLanguageTag("es")
    )

    const val HEADER = "Accept-Language"
}
