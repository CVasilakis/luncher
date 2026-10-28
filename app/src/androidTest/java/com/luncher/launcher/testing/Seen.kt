package com.luncher.launcher.testing

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.SearchCondition
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/**
 * Waits until UI Automator sees a view whose text contains [text], focused if [focused]; fails
 * after [timeoutMs]. For a screen that animates without end, as a spinning wheel does: on API 24
 * the wheel keeps the main thread busy (from API 28 on it doesn't), and Espresso waits for it to
 * be idle before each look, so an Espresso check would see nothing until the wheel stops. UI
 * Automator looks without waiting. Fails with [FailureCapture]'s dump.
 */
fun waitUntilSeen(text: String, focused: Boolean = false, timeoutMs: Long = SEEN_TIMEOUT_MS) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val wanted = By.textContains(text).let { if (focused) it.focused(true) else it }
    if (!device.wait(Until.hasObject(wanted), timeoutMs)) {
        throw AssertionError("\"$text\"${if (focused) ", focused," else ""} not seen in ${timeoutMs / 1000} s").also(FailureCapture::dump)
    }
}

/**
 * Waits until UI Automator's [condition] holds (`Until.hasObject`, `Until.gone`), as the system
 * tests look at another app's screens; fails after [timeoutMs] with the message [failure] gives,
 * and [FailureCapture]'s dump. A bare `assertTrue(device.wait(…))` fails without one, and the screen
 * it failed on is gone once the test has ended.
 */
fun UiDevice.waitUntil(condition: SearchCondition<Boolean>, timeoutMs: Long, failure: () -> String) {
    if (!wait(condition, timeoutMs)) throw AssertionError(failure()).also(FailureCapture::dump)
}

private const val SEEN_TIMEOUT_MS = 10_000L
