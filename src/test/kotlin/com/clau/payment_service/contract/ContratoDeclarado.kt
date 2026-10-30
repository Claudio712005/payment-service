package com.clau.payment_service.contract

import java.nio.file.Path
import kotlin.io.path.reader
import org.yaml.snakeyaml.Yaml

class ContratoDeclarado(arquivo: String) {

    @Suppress("UNCHECKED_CAST")
    val raiz: Map<String, Any?> = Path.of(arquivo).reader(Charsets.UTF_8)
        .use { Yaml().load(it) as Map<String, Any?> }

    @Suppress("UNCHECKED_CAST")
    fun mapa(vararg caminho: String): Map<String, Any?> {
        var atual: Any? = raiz
        caminho.forEach { passo -> atual = (atual as? Map<String, Any?>)?.get(passo) }
        return (atual as? Map<String, Any?>) ?: emptyMap()
    }

    fun referencias(): List<String> = coletar(raiz)

    fun resolve(referencia: String): Any? {
        if (!referencia.startsWith("#/")) {
            return null
        }
        var atual: Any? = raiz
        referencia.removePrefix("#/").split('/').forEach { passo ->
            @Suppress("UNCHECKED_CAST")
            atual = (atual as? Map<String, Any?>)?.get(passo.replace("~1", "/").replace("~0", "~"))
        }
        return atual
    }

    private fun coletar(no: Any?): List<String> = when (no) {
        is Map<*, *> -> no.entries.flatMap { (chave, valor) ->
            if (chave == "\$ref" && valor is String) listOf(valor) else coletar(valor)
        }
        is List<*> -> no.flatMap { coletar(it) }
        else -> emptyList()
    }
}
