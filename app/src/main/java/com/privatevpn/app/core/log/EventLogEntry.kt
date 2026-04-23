package com.privatevpn.app.core.log

data class EventLogEntry(
    val id: Long,
    val timestampMs: Long,
    val level: LogLevel,
    val message: String
)

enum class LogLevel {
    INFO,
    ERROR
}
