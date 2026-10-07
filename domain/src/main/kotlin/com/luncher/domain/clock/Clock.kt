package com.luncher.domain.clock

/**
 * Port: the device's clock. Implemented in :platform on the system time, settings and broadcasts.
 *
 * Something that shows the time reads it once, then again whenever a listener is called, so
 * nothing has to poll. Listen only while showing it: an implementation watches the device only
 * while it has listeners.
 */
interface Clock {

    fun read(): ClockReading

    /**
     * Calls [listener] on the main thread whenever a new reading shows something else: a new
     * minute, or the user set the time, the time zone or the hour format.
     */
    fun addListener(listener: () -> Unit)

    fun removeListener(listener: () -> Unit)
}
