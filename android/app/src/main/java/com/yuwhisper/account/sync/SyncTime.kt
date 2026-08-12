package com.yuwhisper.account.sync

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

internal object SyncTime {
    private val utcFormatter = DateTimeFormatter.ISO_INSTANT

    fun format(instant: Instant): String = utcFormatter.format(instant)

    fun parse(raw: String): Instant {
        val value = raw.trim()
        if (value.isEmpty()) return Instant.EPOCH
        return try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(value).toInstant()
            } catch (_: DateTimeParseException) {
                LocalDateTime.parse(value).toInstant(ZoneOffset.UTC)
            }
        }
    }
}
