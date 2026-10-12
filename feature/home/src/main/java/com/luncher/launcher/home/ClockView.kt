package com.luncher.launcher.home

import android.content.Context
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.luncher.domain.clock.Clock
import com.luncher.domain.clock.ClockReading
import com.luncher.domain.layout.TopBarItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The time and the date, formatted the way the device's language and hour format setting show
 * them ("14:05  Sunday, 27 September", "2:05 PM  Sunday, September 27"). Its children, the
 * `home_time` and `home_date` text views, come from the layout, which also styles them.
 *
 * The two sit side by side on a shared baseline, in the order and at the side of the view that
 * [arrange] gives, from the top bar's layout, which may leave out either or both. The time always
 * gets its room first: a date too long for what's left ends in "…", on either side. One left out
 * is invisible, not gone: the view stays as tall as with both, so hiding them never moves the
 * tiles below the bar.
 *
 * The screen calls [start] when it becomes visible and [stop] when it's hidden, so a hidden home
 * screen does no work, and the time is read again when it comes back. The screen's lifecycle, not
 * the view's own window visibility: Robolectric never reports that, so the JVM tests couldn't
 * cover it.
 */
internal class ClockView(context: Context, attrs: AttributeSet?) : ViewGroup(context, attrs) {

    private lateinit var time: TextView
    private lateinit var date: TextView

    /** Between the time and the date. */
    private val gap = resources.getDimensionPixelSize(R.dimen.home_clock_gap)

    private var timeFirst = true
    private var atRight = false
    private var showTime = true
    private var showDate = true

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

    /**
     * Shows the time and the date that are among [items], in their order, at the view's left or
     * right ([atRight]); hides those that aren't.
     */
    fun arrange(items: List<TopBarItem>, atRight: Boolean) {
        val showTime = TopBarItem.TIME in items
        val showDate = TopBarItem.DATE in items
        val timeFirst = !showDate || items.indexOf(TopBarItem.TIME) < items.indexOf(TopBarItem.DATE)
        if (timeFirst == this.timeFirst && atRight == this.atRight && showTime == this.showTime && showDate == this.showDate) return
        this.timeFirst = timeFirst
        this.atRight = atRight
        this.showTime = showTime
        this.showDate = showDate
        time.visibility = if (showTime) View.VISIBLE else View.INVISIBLE
        date.visibility = if (showDate) View.VISIBLE else View.INVISIBLE
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val room = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val limited = MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED
        fun upTo(width: Int) =
            if (limited) MeasureSpec.makeMeasureSpec(width.coerceAtLeast(0), MeasureSpec.AT_MOST) else unspecified
        time.measure(upTo(room), unspecified)
        date.measure(upTo(room - timeWidth()), unspecified)
        val height = paddingTop + aboveBaseline() + belowBaseline() + paddingBottom
        val width = paddingLeft + contentWidth() + paddingRight
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val left = if (atRight) width - paddingRight - contentWidth() else paddingLeft
        val first = if (timeFirst) time else date
        val second = if (timeFirst) date else time
        place(first, left)
        place(second, if (first.visibility == View.VISIBLE) left + first.measuredWidth + gap else left)
    }

    private fun place(view: View, left: Int) {
        val top = paddingTop + aboveBaseline() - view.baseline
        view.layout(left, top, left + view.measuredWidth, top + view.measuredHeight)
    }

    /** The room the time takes from the date: none when it's hidden. */
    private fun timeWidth() = if (showTime) time.measuredWidth + gap else 0

    private fun contentWidth(): Int = when {
        showTime && showDate -> time.measuredWidth + gap + date.measuredWidth
        showTime -> time.measuredWidth
        showDate -> date.measuredWidth
        else -> 0
    }

    private fun aboveBaseline() = maxOf(time.baseline, date.baseline)

    private fun belowBaseline() = maxOf(below(time), below(date))

    private fun below(view: View) = view.measuredHeight - view.baseline

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
