package com.luncher.launcher.ui

import android.app.Activity
import android.os.Looper
import android.view.ViewParent
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class WindowFocusTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    /** A panel that counts how often its window was made unfocusable, and says whether it's focusable now. */
    private class Panel(activity: Activity) : PanelDialog(activity, R.style.Theme_Luncher_Dialog) {
        var madeUnfocusable = 0
        private var unfocusable = false

        override fun onWindowAttributesChanged(params: WindowManager.LayoutParams) {
            super.onWindowAttributesChanged(params)
            val now = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0
            if (now && !unfocusable) madeUnfocusable++
            unfocusable = now
        }

        fun focusable() = !unfocusable
    }

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    /** Android telling the window it has the window focus, as its window manager does when it's in front. */
    private fun Panel.gainFocus() {
        val root: ViewParent = checkNotNull(window).decorView.parent
        root.javaClass.getMethod("windowFocusChanged", Boolean::class.java, Boolean::class.java).invoke(root, true, false)
        idle(0)
        assertTrue(checkNotNull(window).decorView.hasWindowFocus())
    }

    // Android 9 can leave a window that got a key as it was added without its window focus, deaf to
    // every key: Luncher asks for it again, by making the window unfocusable for a moment.
    @Test
    @Config(sdk = [28])
    fun `a window still without the window focus a moment after it shows asks Android for it again, a few times at most`() {
        val panel = Panel(activity).apply { show() }
        idle(WindowFocus.CHECK_AFTER - 50)
        assertEquals("not before it's due", 0, panel.madeUnfocusable)

        idle(49)
        shadowOf(Looper.getMainLooper()).runOneTask()
        assertEquals(1, panel.madeUnfocusable)
        assertFalse("unfocusable for this turn of the main thread", panel.focusable())
        idle(0)
        assertTrue("focusable again at the next", panel.focusable())

        idle(WindowFocus.AGAIN_AFTER * WindowFocus.TRIES * 2)
        assertEquals("tried again while it stays without", WindowFocus.TRIES, panel.madeUnfocusable)
        assertTrue(panel.focusable())
    }

    @Test
    @Config(sdk = [28])
    fun `a window that has the window focus, or that closed, is left alone`() {
        val focused = Panel(activity).apply { show() }
        focused.gainFocus()
        val closed = Panel(activity).apply { show() }
        closed.dismiss()
        idle(2000)

        assertEquals(0, focused.madeUnfocusable)
        assertEquals(0, closed.madeUnfocusable)
    }

    @Test
    @Config(sdk = [28])
    fun `a window that gets the window focus after a try is left alone from then on`() {
        val panel = Panel(activity).apply { show() }
        idle(WindowFocus.CHECK_AFTER + 50)
        assertEquals(1, panel.madeUnfocusable)

        panel.gainFocus()
        idle(2000)
        assertEquals(1, panel.madeUnfocusable)
    }

    // Only one look per showing: one shown again doesn't get the looks of the earlier showing too.
    @Test
    @Config(sdk = [28])
    fun `a window shown again is looked at from its new showing only`() {
        val panel = Panel(activity).apply { show() }
        idle(WindowFocus.CHECK_AFTER - 100)
        panel.dismiss()
        panel.show()
        idle(150)
        assertEquals("the first showing's look is gone", 0, panel.madeUnfocusable)
        idle(WindowFocus.CHECK_AFTER)
        assertEquals(1, panel.madeUnfocusable)
    }

    @Test
    @Config(sdk = [24, 30, 36])
    fun `where Android gives every window its focus, nothing is asked`() {
        assertFalse(WindowFocus.NEEDED)
        val panel = Panel(activity).apply { show() }
        idle(2000)
        assertEquals(0, panel.madeUnfocusable)
    }
}
