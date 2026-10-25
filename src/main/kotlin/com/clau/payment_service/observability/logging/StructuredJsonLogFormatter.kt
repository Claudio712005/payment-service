package com.clau.payment_service.observability.logging

import ch.qos.logback.classic.pattern.ThrowableProxyConverter
import ch.qos.logback.classic.spi.ILoggingEvent
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.function.Consumer
import org.slf4j.Marker
import org.springframework.boot.json.JsonWriter
import org.springframework.boot.logging.structured.JsonWriterStructuredLogFormatter
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer
import org.springframework.core.env.Environment

class StructuredJsonLogFormatter(
    environment: Environment,
    throwableProxyConverter: ThrowableProxyConverter,
    customizer: StructuredLoggingJsonMembersCustomizer<*>?
) : JsonWriterStructuredLogFormatter<ILoggingEvent>(
    Consumer<JsonWriter.Members<ILoggingEvent>> { members ->
        configure(members, applicationOf(environment), environmentOf(environment), throwableProxyConverter)
    },
    customizer
) {

    companion object {

        private val TIMESTAMP = DateTimeFormatter.ISO_OFFSET_DATE_TIME

        private fun applicationOf(environment: Environment): String =
            environment.getProperty("spring.application.name") ?: "application"

        private fun environmentOf(environment: Environment): String =
            environment.activeProfiles.firstOrNull() ?: "default"

        private fun configure(
            members: JsonWriter.Members<ILoggingEvent>,
            application: String,
            environmentName: String,
            throwableProxyConverter: ThrowableProxyConverter
        ) {
            members.add("@timestamp", JsonWriter.Extractor { asTimestamp(it.instant) })
            members.add("level", JsonWriter.Extractor { it.level.toString() })
            members.add("logger", JsonWriter.Extractor { it.loggerName })
            members.add("thread", JsonWriter.Extractor { it.threadName })
            members.add("application", application)
            members.add("environment", environmentName)
            members.add("message", JsonWriter.Extractor { it.formattedMessage ?: "" })

            CorrelationKeys.ALL.forEach { key ->
                members.add(key, JsonWriter.Extractor { mdcOf(it)[key] }).whenHasLength()
            }

            members.add("context", JsonWriter.Extractor { context(it) }).whenNotEmpty()
            members.add("tags", JsonWriter.Extractor { tags(it) }).whenNotEmpty()

            members.add("error")
                .whenNotNull { it?.throwableProxy }
                .usingMembers { error ->
                    error.add("type", JsonWriter.Extractor { it.throwableProxy?.className }).whenHasLength()
                    error.add("message", JsonWriter.Extractor { it.throwableProxy?.message }).whenHasLength()
                    error.add(
                        "stackTrace",
                        JsonWriter.Extractor { stackTrace(it, throwableProxyConverter) }
                    ).whenHasLength()
                }
        }

        private fun mdcOf(event: ILoggingEvent): Map<String, String?> =
            runCatching { event.mdcPropertyMap }.getOrNull() ?: emptyMap()

        private fun asTimestamp(instant: Instant): String =
            TIMESTAMP.format(OffsetDateTime.ofInstant(instant, ZoneOffset.UTC))

        private fun context(event: ILoggingEvent): Map<String, String> {
            val entries = LinkedHashMap<String, String>()
            mdcOf(event)
                .filterKeys { it !in CorrelationKeys.ALL }
                .forEach { (key, value) -> entries[key] = value ?: "" }
            event.keyValuePairs?.forEach { pair ->
                entries[pair.key] = pair.value?.toString() ?: ""
            }
            return entries
        }

        private fun tags(event: ILoggingEvent): Set<String> {
            val markers = event.markerList ?: return emptySet()
            val names = LinkedHashSet<String>()
            markers.forEach { collect(it, names) }
            return names
        }

        private fun collect(marker: Marker, names: MutableSet<String>) {
            names.add(marker.name)
            if (marker.hasReferences()) {
                marker.iterator().forEach { collect(it, names) }
            }
        }

        private fun stackTrace(
            event: ILoggingEvent,
            throwableProxyConverter: ThrowableProxyConverter
        ): String {
            if (event.throwableProxy == null) {
                return ""
            }
            return throwableProxyConverter.convert(event) ?: ""
        }
    }
}
