package com.luncher.launcher

import android.os.SystemClock
import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Holds OK on the focused view until it counts as a long press, then lets go: real key events, as
 * from a remote. Not `adb shell input keyevent --longpress`: before API 30 it sends the release at
 * once, and views count a long press by how long the key stays down.
 */
fun longPressOk() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val down = SystemClock.uptimeMillis()
    instrumentation.sendKeySync(KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
    SystemClock.sleep(ViewConfiguration.getLongPressTimeout() * 2L)
    instrumentation.sendKeySync(KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
}
