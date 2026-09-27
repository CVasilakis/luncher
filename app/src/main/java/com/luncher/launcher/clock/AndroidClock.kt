package com.luncher.launcher.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import com.luncher.domain.clock.Clock
import com.luncher.domain.clock.ClockReading
import java.util.TimeZone

/**
 * [Clock] on Android's system time, the hour format setting and the time broadcasts. The
 * broadcast receiver is registered only while there are listeners, so a hidden home screen costs
 * nothing.
 */
class AndroidClock(private val context: Context) : Clock {

    private val listeners = mutableListOf<() -> Unit>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            listeners.toList().forEach { it() }
        }
    }

    // Android updates the default time zone of every process before it sends TIMEZONE_CHANGED.
    override fun read() = ClockReading(
        epochMillis = System.currentTimeMillis(),
        timeZone = TimeZone.getDefault().id,
        uses24Hour = DateFormat.is24HourFormat(context),
    )

    override fun addListener(listener: () -> Unit) {
        if (listeners.isEmpty()) {
            // TIME_TICK comes at every new minute, and only to receivers registered in code.
            // TIME_SET also comes when the user switches between 12- and 24-hour time.
            val changes = IntentFilter(Intent.ACTION_TIME_TICK).apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            context.registerReceiver(receiver, changes)
        }
        listeners += listener
    }

    override fun removeListener(listener: () -> Unit) {
        if (listeners.remove(listener) && listeners.isEmpty()) context.unregisterReceiver(receiver)
    }
}
