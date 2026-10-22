package com.luncher.launcher.testing

import android.os.Looper
import android.view.View
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.robolectric.Shadows.shadowOf

/**
 * The window in front given the input focus, the others not, as Android does on a TV. Robolectric
 * gives no window the focus, and a focused entry looks focused only while its window has it
 * (:ui's Themed): a dialog or a list over a screen takes the keys, and the screen's entry under it
 * no longer looks focused.
 */
fun focusFrontWindow() {
    val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
    val roots = global.javaClass.getDeclaredField("mRoots").apply { isAccessible = true }.get(global) as List<*>
    roots.forEachIndexed { index, root -> windowFocusChanged(checkNotNull(root), index == roots.lastIndex) }
    shadowOf(Looper.getMainLooper()).idle()
}

/** Tells a window (its ViewRootImpl) it has the focus or not, as Robolectric's ActivityController does. */
private fun windowFocusChanged(root: Any, focused: Boolean) {
    val method = root.javaClass.declaredMethods.first { it.name == "windowFocusChanged" }
    method.isAccessible = true
    if (method.parameterCount == 2) method.invoke(root, focused, false) else method.invoke(root, focused)
}

/** [captureRoboImage], the front window focused first ([focusFrontWindow]). */
fun View.captureFocused(path: String) {
    focusFrontWindow()
    captureRoboImage(path)
}

/** [captureScreenRoboImage]: every window, the front one focused first ([focusFrontWindow]). */
@OptIn(ExperimentalRoborazziApi::class)
fun captureScreenFocused(path: String) {
    focusFrontWindow()
    captureScreenRoboImage(path)
}
