package com.luncher.launcher.testing

import android.os.SystemClock
import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.FailureHandler
import androidx.test.espresso.NoMatchingRootException
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.Root
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.assertion.ViewAssertions.matches
import junit.framework.AssertionFailedError
import org.hamcrest.Matcher

/**
 * Waits until the view [view] matches satisfies [condition], in the window [root] matches (a
 * dialog, an option's list) or else the one Espresso picks, looking again every 50 ms while it
 * doesn't, isn't there yet, or its window isn't up yet. Fails after [timeoutMs] with the last
 * look's error.
 *
 * The looks before the last fail quietly (Retrying): Espresso's own handling takes a screenshot
 * and the view hierarchy at each failed look, which would slow each look again and put a line in
 * the device's log every time. The last look, and any error a new look can't change
 * (RootViewWithoutFocusException: the window picked never gets the focus), fail with Espresso's
 * handling and [FailureCapture]'s dump, once.
 *
 * Not on a screen while a window over it has the focus: Espresso waits for the focus to come back,
 * and fails after 10 s; look at that window with [root].
 */
fun waitUntilMatches(view: Matcher<View>, condition: Matcher<View>, timeoutMs: Long, root: Matcher<Root>? = null) {
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    while (true) {
        val last = SystemClock.uptimeMillis() > deadline
        try {
            look(view, root).withFailureHandler(if (last) FailureCapture.handler else Retrying).check(matches(condition))
            return
        } catch (e: AssertionFailedError) {
            if (last) throw e
        } catch (e: NoMatchingViewException) {
            if (last) throw e   // not there yet: a window coming up
        } catch (e: NoMatchingRootException) {
            if (last) throw e   // the dialog not up yet
        }
        SystemClock.sleep(LOOK_POLL_MS)
    }
}

/**
 * Whether the view [view] matches satisfies [condition] now, in [root]'s window or the one
 * Espresso picks; a look whose answer can be no, so not one is captured as a failure.
 */
fun matchesNow(view: Matcher<View>, condition: Matcher<View>, root: Matcher<Root>? = null): Boolean =
    try {
        look(view, root).withFailureHandler(Retrying).check(matches(condition))
        true
    } catch (e: AssertionFailedError) {
        false
    } catch (e: NoMatchingViewException) {
        false
    } catch (e: NoMatchingRootException) {
        false
    }

private fun look(view: Matcher<View>, root: Matcher<Root>?): ViewInteraction =
    onView(view).let { if (root != null) it.inRoot(root) else it }

/** The errors a look again can change, as they are; any other with [FailureCapture]'s handling. */
private object Retrying : FailureHandler {
    override fun handle(error: Throwable, viewMatcher: Matcher<View>) {
        if (error is AssertionFailedError || error is NoMatchingViewException || error is NoMatchingRootException) throw error
        FailureCapture.handler.handle(error, viewMatcher)
    }
}

private const val LOOK_POLL_MS = 50L
