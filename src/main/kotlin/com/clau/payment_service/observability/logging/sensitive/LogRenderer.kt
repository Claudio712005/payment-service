package com.clau.payment_service.observability.logging.sensitive

import kotlin.reflect.KVisibility
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

object LogRenderer {

    private const val MAX_DEPTH = 4

    fun render(value: Any?): String = render(value, MAX_DEPTH)

    private fun render(value: Any?, depth: Int): String = when {
        value == null -> "null"
        value is CharSequence -> value.toString()
        value is Number || value is Boolean || value is Char || value is Enum<*> -> value.toString()
        value is Iterable<*> -> value.joinToString(", ", "[", "]") { render(it, depth) }
        value is Map<*, *> -> value.entries.joinToString(", ", "{", "}") {
            "${it.key}=${render(it.value, depth)}"
        }
        depth <= 0 -> value::class.simpleName ?: "?"
        isOpaque(value) -> value.toString()
        else -> renderProperties(value, depth)
    }

    private fun renderProperties(value: Any?, depth: Int): String {
        val type = value!!::class
        val properties = runCatching {
            type.memberProperties.filter { it.visibility == KVisibility.PUBLIC }
        }.getOrNull() ?: return value.toString()

        return properties.joinToString(", ", "${type.simpleName}(", ")") { property ->
            val annotation = property.findAnnotation<Sensitive>()
            val raw = runCatching {
                property.isAccessible = true
                property.getter.call(value)
            }.getOrNull()
            val rendered = when {
                annotation == null -> render(raw, depth - 1)
                raw == null -> "null"
                else -> SensitiveMasker.mask(raw.toString(), annotation)
            }
            "${property.name}=$rendered"
        }
    }

    private fun isOpaque(value: Any): Boolean {
        val qualified = value::class.qualifiedName ?: return true
        return OPAQUE_PREFIXES.any { qualified.startsWith(it) }
    }

    private val OPAQUE_PREFIXES = listOf("java.", "javax.", "jakarta.", "kotlin.")
}
