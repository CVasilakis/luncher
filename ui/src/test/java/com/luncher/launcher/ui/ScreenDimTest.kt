package com.luncher.launcher.ui

import android.app.Activity
import android.os.Looper
import android.view.ViewGroup
import android.view.WindowManager
import com.luncher.domain.appearance.ThemeAppearance
import com.luncher.domain.appearance.colorRoles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class ScreenDimTest {

    private val controller = Robolectric.buildActivity(Activity::class.java).setup()
    private val activity = controller.get()
    private val roles = colorRoles(ThemeAppearance())

    private fun message() = MessageDialog(activity, roles, "Delete all pictures?", null, listOf(DialogButton("Cancel", DialogButton.Kind.TONAL) {}))

    private fun dim() = ScreenDim.of(activity)

    // Android's dim is a layer the screen's exit doesn't fade: every dialog dims through the window.
    @Test
    fun `a dialog and the working window dim the screen with the window, not with Android's dim`() {
        val message = message().apply { show() }

        assertTrue(dim().isShowing)
        assertEquals(0, checkNotNull(message.window).attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        message.dismiss()
        assertFalse(dim().isShowing)

        val working = WorkingDialog(activity, roles)
        working.show("Saving")
        assertTrue(dim().isShowing)
        working.dismiss()
        assertFalse(dim().isShowing)
    }

    // Added after a dialog, it would cover it.
    @Test
    fun `the dim is there before the dialog's window is added, so it's below it`() {
        var dimmedFirst = false
        val dialog = object : PanelDialog(activity, R.style.Theme_Luncher_Dialog) {
            override fun onStart() {
                dimmedFirst = dim().isShowing
            }
        }

        dialog.show()

        assertTrue(dimmedFirst)
    }

    @Test
    fun `it stays until the last of the screen's dialogs closes`() {
        val first = message().apply { show() }
        val second = message().apply { show() }

        first.dismiss()
        assertTrue(dim().isShowing)
        second.dismiss()
        assertFalse(dim().isShowing)
    }

    @Test
    fun `a dialog shown again, or closed twice, counts once`() {
        val first = message().apply { show() }
        val second = message().apply { show() }
        first.show()

        first.dismiss()
        first.dismiss()

        assertTrue("the second holds it", dim().isShowing)
        second.dismiss()
        assertFalse(dim().isShowing)
    }

    @Test
    fun `a dialog shown again dims the screen again`() {
        val dialog = message().apply { show() }
        dialog.dismiss()

        dialog.show()

        assertTrue(dim().isShowing)
    }

    @Test
    fun `each screen has its own`() {
        val other = Robolectric.buildActivity(Activity::class.java).setup().get()

        assertNotSame(ScreenDim.of(other), dim())
        message().show()

        assertFalse(ScreenDim.of(other).isShowing)
    }

    // A window the screen left showing would be a leak.
    @Test
    fun `it goes with its screen, even under a dialog left showing`() {
        val before = dim()
        message().show()

        controller.pause().stop().destroy()

        assertFalse(before.isShowing)
        assertNotSame("forgotten with its screen", before, dim())
    }

    // The dialogs' windows must work as they would alone: the keys and the focus stay theirs.
    @Test
    fun `its window covers the screen and takes neither focus nor keys, and comes and goes as a dialog does`() {
        val attributes = dim().attributes()

        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, attributes.width)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, attributes.height)
        assertTrue(attributes.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(attributes.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertEquals(0, attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION, attributes.type)
        assertEquals(R.style.Animation_Luncher_Panel, attributes.windowAnimations)
    }

    @Test
    @Config(sdk = [23])
    fun `where Android plays no dialog's entrance, its window keeps only the exit`() {
        assertEquals(R.style.Animation_Luncher_Panel_ExitOnly, dim().attributes().windowAnimations)

        message().show()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        assertTrue(dim().isShowing)
    }
}
