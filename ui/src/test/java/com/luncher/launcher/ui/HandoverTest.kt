package com.luncher.launcher.ui

import android.app.Activity
import android.app.Dialog
import android.os.Looper
import android.view.KeyEvent
import com.luncher.domain.appearance.ThemeAppearance
import com.luncher.domain.appearance.colorRoles
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
class HandoverTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val roles = colorRoles(ThemeAppearance())

    private fun message(title: String, action: () -> Unit = {}) =
        MessageDialog(activity, roles, title, null, listOf(DialogButton("OK", DialogButton.Kind.FILLED, action = action)))

    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

    private fun Dialog.animations() = checkNotNull(window).attributes.windowAnimations

    private fun Dialog.dims() = checkNotNull(window).attributes.flags and android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0

    // The dim never drops, and nothing is bare while the next window is made.
    @Test
    fun `the window handed over from stays, with no animation and no key, until the next has drawn, then closes at once`() {
        val next = message("Next")
        val old = message("Old") { next.show() }.apply { show() }

        old.findViewById<android.view.View>(R.id.dialog_entries).let { (it as android.view.ViewGroup).getChildAt(0).performClick() }

        assertTrue("the next shows", next.isShowing)
        assertTrue("the old one stays until the next has drawn", old.isShowing)
        assertEquals("with no exit animation", 0, old.animations())
        assertFalse("under the next, the dim is the old one's alone", next.dims())
        val key = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        assertTrue("the old one takes no key", old.dispatchKeyEvent(key))

        idle()

        assertFalse("closed once the next has drawn", old.isShowing)
        assertTrue(next.isShowing)
        assertTrue("the next dims the screen on its own now", next.dims())
    }

    @Test
    fun `where the action opens no window, the window closes as it would alone, with its animation`() {
        var done = false
        val old = message("Old") { done = true }.apply { show() }

        old.findViewById<android.view.View>(R.id.dialog_entries).let { (it as android.view.ViewGroup).getChildAt(0).performClick() }

        assertTrue(done)
        assertFalse(old.isShowing)
        assertEquals(R.style.Animation_Luncher_Panel, old.animations())
    }

    // As its screen goes, the screen's own exit takes the window along; closed first, it would fade
    // away alone and leave the screen under bright for the moment Android takes to start that exit.
    @Test
    fun `where the action finishes the screen, the window stays for the screen's exit`() {
        val old = message("Old") { activity.finish() }.apply { show() }

        old.findViewById<android.view.View>(R.id.dialog_entries).let { (it as android.view.ViewGroup).getChildAt(0).performClick() }

        assertTrue(activity.isFinishing)
        assertTrue("not closed on its own", old.isShowing)
        assertEquals("with its fade, were it closed", R.style.Animation_Luncher_Panel, old.animations())
    }

    @Test
    fun `the next closed before it has drawn closes the window under it too`() {
        val next = message("Next")
        val old = message("Old") { next.show() }.apply { show() }
        old.findViewById<android.view.View>(R.id.dialog_entries).let { (it as android.view.ViewGroup).getChildAt(0).performClick() }

        next.dismiss()

        assertFalse(next.isShowing)
        assertFalse(old.isShowing)
    }

    @Test
    fun `closing the window handed over from, as its screen's code does meanwhile, leaves it to the next`() {
        val next = message("Next")
        val old = message("Old") { next.show(); old().dismiss() }
        holder = old
        old.show()
        old.findViewById<android.view.View>(R.id.dialog_entries).let { (it as android.view.ViewGroup).getChildAt(0).performClick() }

        assertTrue("not closed at once, for the next to draw", old.isShowing)
        idle()
        assertFalse(old.isShowing)
    }

    private fun Dialog.alpha() = checkNotNull(window).decorView.alpha

    // Before Android 11 a window's dim fades with its window animation: a window fading in so would
    // leave the dim ramping up from nothing as the old one's goes. It plays its entrance in itself.
    @Test
    @Config(sdk = [28])
    fun `before Android 11 the window handed over to plays its entrance in itself, and only that one`() {
        val next = message("Next")
        val old = message("Old") { next.show() }.apply { show() }
        assertEquals("a plain show keeps the window's own", R.style.Animation_Luncher_Panel, old.animations())
        assertEquals(1f, old.alpha())

        old.findViewById<android.view.View>(R.id.dialog_entries).let { (it as android.view.ViewGroup).getChildAt(0).performClick() }

        assertEquals(R.style.Animation_Luncher_Panel_ExitOnly, next.animations())
        assertEquals("not drawn yet", 0f, next.alpha())
        idle()
        assertEquals(1f, next.alpha())

        next.dismiss()
        next.show()
        assertEquals("shown alone again: the window's own entrance", R.style.Animation_Luncher_Panel, next.animations())
        assertEquals(1f, next.alpha())
    }

    @Test
    fun `from Android 11 the window handed over to keeps the window's own entrance`() {
        val next = message("Next")
        val old = message("Old") { next.show() }.apply { show() }
        old.findViewById<android.view.View>(R.id.dialog_entries).let { (it as android.view.ViewGroup).getChildAt(0).performClick() }

        assertEquals(R.style.Animation_Luncher_Panel, next.animations())
        assertEquals(1f, next.alpha())
    }

    // Robolectric renders no frames, so the old window waits for the longest it may: it doesn't stay for good.
    @Test
    @Config(sdk = [28])
    fun `before Android 11 the old window waits for the new one's first frame, and goes after a second at the latest`() {
        val next = message("Next")
        val old = message("Old") { next.show() }.apply { show() }
        old.findViewById<android.view.View>(R.id.dialog_entries).let { (it as android.view.ViewGroup).getChildAt(0).performClick() }

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(900))
        assertTrue("no frame yet", old.isShowing)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertFalse(old.isShowing)
        assertTrue(next.isShowing)
    }

    // Before Android 11 the window handed over to shows some frames before its panel does, dimming
    // the old panel meanwhile: the old panel fades out as it would closing, its window and dim staying.
    @Test
    @Config(sdk = [28])
    fun `before Android 11 the old panel fades out meanwhile, the window staying, and is whole again once gone`() {
        val next = message("Next")
        val old = message("Old") { next.show() }.apply { show() }
        old.findViewById<android.view.View>(R.id.dialog_entries).let { (it as android.view.ViewGroup).getChildAt(0).performClick() }

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertTrue("its window, and so its dim, stays", old.isShowing)
        assertEquals("the panel is gone", 0f, old.alpha())

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
        assertFalse(old.isShowing)
        assertEquals("whole for its next showing", 1f, old.alpha())
    }

    @Test
    fun `from Android 11 the old panel isn't faded`() {
        val next = message("Next")
        val old = message("Old") { next.show() }.apply { show() }
        old.findViewById<android.view.View>(R.id.dialog_entries).let { (it as android.view.ViewGroup).getChildAt(0).performClick() }

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(5))

        assertEquals(1f, old.alpha())
    }

    private var holder: Dialog? = null
    private fun old() = checkNotNull(holder)
}
