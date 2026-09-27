package com.luncher.domain.clock

/** What the device's clock says at one moment, with what's needed to show it the user's way. */
data class ClockReading(
    /** Milliseconds since 1970-01-01T00:00:00Z. */
    val epochMillis: Long,
    /** The device's time zone, as `java.util.TimeZone` names it ("Europe/Athens"). */
    val timeZone: String,
    /** Whether the user chose 24-hour time in the device settings (or their locale uses it). */
    val uses24Hour: Boolean,
)
