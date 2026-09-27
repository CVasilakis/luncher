package com.luncher.launcher.home

import android.content.Context
import android.text.format.DateFormat
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import com.luncher.domain.clock.Clock
import com.luncher.domain.clock.ClockReading
import com.luncher.launcher.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The time and the date, formatted the way the device's language and hour format setting show
 * them ("14:05  Sunday, 27 September", "2:05 PM  Sunday, September 27"). Its children, the
 * `home_time` and `home_date` text views, come from the layout, which also styles them.
 *
 * The screen calls [start] when it becomes visible and [stop] when it's hidden, so a hidden home
 * screen does no work, and the time is read again when it comes back. The screen's lifecycle, not
 * the view's own window visibility: Robolectric never reports that, so the JVM tests couldn't
 * cover it.
 */
class ClockView(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    private lateinit var time: TextView
    private lateinit var date: TextView

    private var clock: Clock? = null
    private val update: () -> Unit = { clock?.let { show(it.read()) } }

    // Formats of the last reading's time zone and hour format. A new language recreates the
    // activity, and with it this view.
    private var formatZone: String? = null
    private var format24Hour = false
    private lateinit var timeFormat: SimpleDateFormat
    private lateinit var dateFormat: SimpleDateFormat

    override fun onFinishInflate() {
        super.onFinishInflate()
        time = findViewById(R.id.home_time)
        date = findViewById(R.id.home_date)
    }

    /** Shows [clock]'s time, and follows it until [stop]. */
    fun start(clock: Clock) {
        stop()
        this.clock = clock
        clock.addListener(update)
        update()
    }

    fun stop() {
        clock?.removeListener(update)
        clock = null
    }

    private fun show(reading: ClockReading) {
        if (reading.timeZone != formatZone || reading.uses24Hour != format24Hour) createFormats(reading)
        val now = Date(reading.epochMillis)
        time.text = timeFormat.format(now)
        date.text = dateFormat.format(now)
    }

    private fun createFormats(reading: ClockReading) {
        val locale = Locale.getDefault()
        val zone = TimeZone.getTimeZone(reading.timeZone)
        fun format(skeleton: String) =
            SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale).apply { timeZone = zone }
        timeFormat = format(if (reading.uses24Hour) TIME_24_HOUR else TIME_12_HOUR)
        dateFormat = format(DATE)
        formatZone = reading.timeZone
        format24Hour = reading.uses24Hour
    }

    // Skeletons: which fields to show. The language decides their order and punctuation.
    private companion object {
        const val TIME_24_HOUR = "Hm"
        const val TIME_12_HOUR = "hm"
        const val DATE = "EEEEdMMMM"
    }
}
