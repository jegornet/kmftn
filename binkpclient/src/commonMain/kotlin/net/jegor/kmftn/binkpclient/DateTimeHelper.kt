package net.jegor.kmftn.binkpclient

import kotlinx.datetime.*

/**
 * Helper functions for date/time operations
 */
internal object DateTimeHelper {
    /**
     * Get current instant
     */
    fun now(): Instant = Clock.System.now()

    /**
     * Format current time for Binkp M_NUL TIME command
     * Format: "EEE, dd MMM yyyy HH:mm:ss z"
     * Example: "Mon, 17 Jan 2026 12:34:56 UTC"
     */
    fun formatBinkpTime(instant: Instant = now()): String {
        val dateTime = instant.toLocalDateTime(TimeZone.UTC)

        val dayOfWeek = dateTime.dayOfWeek.name.take(3).lowercase()
            .replaceFirstChar { it.uppercase() }

        val month = dateTime.month.name.take(3).lowercase()
            .replaceFirstChar { it.uppercase() }

        val day = dateTime.dayOfMonth.toString().padStart(2, '0')
        val year = dateTime.year
        val hour = dateTime.hour.toString().padStart(2, '0')
        val minute = dateTime.minute.toString().padStart(2, '0')
        val second = dateTime.second.toString().padStart(2, '0')

        return "$dayOfWeek, $day $month $year $hour:$minute:$second UTC"
    }

    /**
     * Get Unix timestamp in seconds
     */
    fun unixTimestamp(instant: Instant = now()): Long {
        return instant.epochSeconds
    }

    /**
     * Convert Unix timestamp (seconds) to Instant
     */
    fun fromUnixTimestamp(seconds: Long): Instant {
        return Instant.fromEpochSeconds(seconds)
    }
}
