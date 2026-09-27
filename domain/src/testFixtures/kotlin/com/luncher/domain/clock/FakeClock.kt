package com.luncher.domain.clock

import java.util.Calendar
import java.util.TimeZone

/** [Clock] at a time the test controls; [set] moves it, as the device does at each new minute. */
class FakeClock(reading: ClockReading = at(2026, 9, 27, 14, 5)) : Clock {

    private var reading = reading
    private val listeners = mutableListOf<() -> Unit>()

    /** How many listeners are registered: 0 while nothing shows the time. */
    val listenerCount: Int get() = listeners.size

    override fun read(): ClockReading = reading

    override fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    override fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    /** Changes the reading and tells the listeners. */
    fun set(reading: ClockReading) {
        this.reading = reading
        listeners.toList().forEach { it() }
    }

    companion object {
        /** [hour]:[minute] on [year]-[month]-[day] in [timeZone]; [month] counts from 1. */
        fun at(
            year: Int,
            month: Int,
            day: Int,
            hour: Int,
            minute: Int,
            timeZone: String = "UTC",
            uses24Hour: Boolean = true,
        ): ClockReading {
            val calendar = Calendar.getInstance(TimeZone.getTimeZone(timeZone)).apply {
                clear()
                set(year, month - 1, day, hour, minute)
            }
            return ClockReading(calendar.timeInMillis, timeZone, uses24Hour)
        }
    }
}
