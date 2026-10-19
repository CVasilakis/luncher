package com.luncher.launcher.ui

import android.os.SystemClock

/**
 * How long a wait has shown (a working window, the share window's spinning wheel), so that it
 * never shows for less than [SHORTEST] ms, which would only flash: a screen [start]s it as the
 * wait shows, and ends the wait only once [left] is 0, at once when it never showed. A decision
 * known at once shows no wait at all, so it's never held up by this
 * (docs/ARCHITECTURE.md#background-work-and-changes-while-shown, point 9). Main thread only.
 */
class ShortestShowing(private val now: () -> Long = SystemClock::uptimeMillis) {

    private var since = NOT_SHOWING

    /** Whether the wait shows. */
    val isShowing: Boolean get() = since != NOT_SHOWING

    /** The wait shows from now, unless it did already. */
    fun start() {
        if (since == NOT_SHOWING) since = now()
    }

    /** The wait is over, and doesn't show any more. */
    fun stop() {
        since = NOT_SHOWING
    }

    /** How many ms are left before the wait may end: 0 once it has shown [SHORTEST] ms, or when it doesn't show. */
    val left: Long get() = if (since == NOT_SHOWING) 0 else (since + SHORTEST - now()).coerceAtLeast(0)

    /** The time at which the wait may end, in [now]'s clock. */
    val endsAt: Long get() = if (since == NOT_SHOWING) now() else since + SHORTEST

    companion object {
        /** How long a wait shows at least, in ms. */
        const val SHORTEST = 500L

        private const val NOT_SHOWING = Long.MIN_VALUE
    }
}
