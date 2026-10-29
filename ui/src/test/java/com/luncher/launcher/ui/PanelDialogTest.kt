package com.luncher.launcher.ui

import android.app.Activity
import android.app.Dialog
import android.os.Looper
import com.luncher.domain.appearance.ThemeAppearance
import com.luncher.domain.appearance.colorRoles
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class PanelDialogTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val roles = colorRoles(ThemeAppearance())

    private fun message() = MessageDialog(activity, roles, "Delete all pictures?", null, listOf(DialogButton("Cancel", DialogButton.Kind.TONAL) {}))

    private fun Dialog.windowAnimations() = checkNotNull(window).attributes.windowAnimations

    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

    // Luncher's short fade, not Android's dialog animation, which is slower.
    @Test
    fun `a dialog and the working window come and go with a short fade of Luncher's own`() {
        assertEquals(R.style.Animation_Luncher_Panel, message().apply { show() }.windowAnimations())

        WorkingDialog(activity, roles).show("Saving")
        assertEquals(R.style.Animation_Luncher_Panel, ShadowDialog.getLatestDialog().windowAnimations())
    }

    // Android 5.1 and 6.0 play no app dialog's window entrance: the dialog plays it in its window,
    // starting as it's first drawn, and the window only fades as it goes; again each time it shows.
    @Test
    @Config(sdk = [23])
    fun `where Android plays no dialog's entrance, the dialog plays it in its window, each time it shows`() {
        val working = WorkingDialog(activity, roles)
        working.show("Saving")
        val dialog = ShadowDialog.getLatestDialog()
        val decor = checkNotNull(dialog.window).decorView

        assertEquals(R.style.Animation_Luncher_Panel_ExitOnly, dialog.windowAnimations())
        assertEquals("not drawn yet", 0f, decor.alpha)
        idle()
        assertEquals(1f, decor.alpha)

        working.dismiss()
        working.show("Saving")
        assertEquals("shown again", 0f, decor.alpha)
        idle()
        assertEquals(1f, decor.alpha)

        val message = message().apply { show() }
        assertEquals(R.style.Animation_Luncher_Panel_ExitOnly, message.windowAnimations())
        assertEquals(0f, checkNotNull(message.window).decorView.alpha)
        idle()
        assertEquals(1f, checkNotNull(message.window).decorView.alpha)
    }
}
