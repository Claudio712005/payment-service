package com.clau.payment_service.web.error

enum class TipoDeProblema(
    val uri: String,
    val chaveDoTitulo: String
) {
    VALIDACAO("validacao", "erro.validacao.titulo"),
    CORPO_INVALIDO("corpo-invalido", "erro.corpo.titulo"),
    PARAMETRO_INVALIDO("parametro-invalido", "erro.parametro.titulo"),
    NAO_ENCONTRADO("nao-encontrado", "erro.nao-encontrado.titulo"),
    CONFLITO("conflito", "erro.conflito.titulo"),
    REGRA_DE_NEGOCIO("regra-de-negocio", "erro.regra-de-negocio.titulo"),
    METODO_NAO_SUPORTADO("metodo-nao-suportado", "erro.metodo.titulo"),
    DEPENDENCIA_SATURADA("dependencia-saturada", "erro.dependencia-saturada.titulo"),
    DEPENDENCIA_INDISPONIVEL("dependencia-indisponivel", "erro.dependencia-indisponivel.titulo"),
    INTERNO("interno", "erro.interno.titulo");

    fun uriCompleta(): String = "$BASE/$uri"

    private companion object {
        const val BASE = "https://payment-service/erros"
    }
}
