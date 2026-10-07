package com.luncher.launcher.testing

import android.os.SystemClock
import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Holds OK on the focused view until it counts as a long press, then lets go: real key events, as
 * from a remote. Not `adb shell input keyevent --longpress`: before API 30 it sends the release at
 * once, and views count a long press by how long the key stays down.
 *
 * Only once a window of the app under test has the focus, else it fails, saying what has it.
 * `sendKeySync` returns when the focused window has handled the key: with none focused, Android
 * waits for one, up to 60 s for an app under test (a long press hung that long on CI's API 34),
 * then drops the key without an error; before API 30, another app's window makes it throw a
 * SecurityException.
 */
fun longPressOk() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    waitForFocus(instrumentation.targetContext.packageName)
    val down = SystemClock.uptimeMillis()
    instrumentation.sendKeySync(KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
    SystemClock.sleep(ViewConfiguration.getLongPressTimeout() * 2L)
    instrumentation.sendKeySync(KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
}
