package com.plynexa.agent.core.logging

import kotlinx.serialization.Serializable

@Serializable
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

@Serializable
data class LogEntry(
    val timestampEpochMillis: Long,
    val level: LogLevel,
    val component: String,
    val event: String,
    val correlationId: String? = null,
    val fields: Map<String, String> = emptyMap(),
)

interface StructuredLogger {
    fun log(entry: LogEntry)
}

class SafeStructuredLogger(
    private val sink: (LogEntry) -> Unit,
) : StructuredLogger {
    private val forbidden = setOf("token", "secret", "password", "api_key", "authorization")

    override fun log(entry: LogEntry) {
        sink(entry.copy(fields = entry.fields.mapValues { (key, value) ->
            if (forbidden.any { key.lowercase().contains(it) }) "[REDACTED]" else value
        }))
    }
}
