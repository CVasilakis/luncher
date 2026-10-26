package com.luncher.launcher.home

import android.content.Intent
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import com.luncher.domain.apps.AppCustomization
import com.luncher.domain.apps.FakeAppCustomizations
import com.luncher.domain.apps.FakeAppCustomizations.Companion.named
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.apps.LaunchableApp
import com.luncher.domain.apps.MAX_APP_NAME
import com.luncher.launcher.testing.TV_1080P
import com.luncher.launcher.ui.NameDialog
import com.luncher.launcher.ui.NameField
import com.luncher.launcher.ui.PillButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import com.luncher.launcher.ui.R as UiR

/** Rename in a tile's menu, through the home screen: apps a to e, sorted by name, d named "Delta" by the user. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-$TV_1080P")
class HomeRenameTest {

    private val installedApps = FakeInstalledApps(listOf("a", "b", "c", "d", "e").map { app(it) })
    private val customizations = FakeAppCustomizations(mapOf(named(app("d"), "Delta")))

    @Before
    fun useFakes() {
        val application = RuntimeEnvironment.getApplication() as HomeTestApplication
        application.graph = object : TestHomeGraph(application) {
            override val installedApps = this@HomeRenameTest.installedApps
            override val appCustomizations = this@HomeRenameTest.customizations
        }
    }

    private fun start(): ActivityController<HomeActivity> = Robolectric.buildActivity(HomeActivity::class.java).setup()

    private fun HomeActivity.tiles(): List<AppTileView> {
        val tiles = findViewById<AppTilesView>(R.id.home_apps)
        return (0 until tiles.childCount).map { tiles.getChildAt(it) as AppTileView }
    }

    private fun HomeActivity.names() = tiles().map { it.app.name }

    private fun HomeActivity.tile(name: String) = tiles().single { it.app.name == name }

    /** Rename in the menu of the tile named [name]: the dialog it opens. */
    private fun HomeActivity.rename(name: String): NameDialog {
        tile(name).apply {
            requestFocus()
            performLongClick()
        }
        val menu = checkNotNull(shownMenu()) { "no menu" }
        menu.entries().single { it.label.text == getString(R.string.home_menu_rename) }.performClick()
        return ShadowDialog.getLatestDialog() as NameDialog
    }

    private val NameDialog.field: NameField get() = findViewById(UiR.id.dialog_field)

    private val NameDialog.note: TextView get() = findViewById(UiR.id.dialog_text)

    private fun NameDialog.buttons(): List<PillButton> {
        val row = findViewById<ViewGroup>(UiR.id.dialog_entries)
        return (0 until row.childCount).map { row.getChildAt(it) as PillButton }
    }

    private fun NameDialog.button(label: String) = buttons().single { it.text == label }

    private fun NameDialog.type(name: String) {
        field.setText(name)
        field.setSelection(field.length())
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun stored(vararg names: Pair<String, String>): Map<LaunchableApp, AppCustomization> =
        names.associate { (app, name) -> named(app(app), name) }

    @Test
    fun `Rename asks for the name the app shows, the cursor at its end`() {
        val activity = start().get()

        val dialog = activity.rename("B")

        assertTrue(dialog.isShowing)
        assertEquals("Rename app", dialog.findViewById<TextView>(UiR.id.dialog_title).text.toString())
        assertEquals("B", dialog.field.text.toString())
        assertTrue(dialog.field.isFocused)
        assertEquals(1, dialog.field.selectionStart)
        assertEquals(1, dialog.field.selectionEnd)
        assertEquals(listOf("Cancel", "Reset", "Rename"), dialog.buttons().map { it.text.toString() })
    }

    @Test
    fun `for an app with its own name, no original name shows, and Reset is dimmed and passed by`() {
        val dialog = start().get().rename("B")

        assertEquals(View.GONE, dialog.note.visibility)
        val reset = dialog.button("Reset")
        assertTrue(reset.alpha < 1f)
        assertFalse(reset.isFocusable)
        assertFalse(reset.hasOnClickListeners())
    }

    @Test
    fun `for an app the user renamed, the field holds that name and the original name shows below it`() {
        val dialog = start().get().rename("Delta")

        assertEquals("Delta", dialog.field.text.toString())
        assertEquals(5, dialog.field.selectionStart)
        assertEquals(View.VISIBLE, dialog.note.visibility)
        assertEquals("Original name: ⁨D⁩", dialog.note.text.toString())
        assertEquals(1f, dialog.button("Reset").alpha)
        assertTrue(dialog.button("Reset").isFocusable)
    }

    /** What Android tells the dialog's window as the keyboard comes ([shown]) or goes. */
    private fun NameDialog.keyboard(shown: Boolean) {
        checkNotNull(window).decorView.dispatchApplyWindowInsets(WindowInsets.Builder().setVisible(WindowInsets.Type.ime(), shown).build())
    }

    // The screen behind doesn't move for the keyboard (adjustNothing), so the dialog keeps clear of it itself.
    @Test
    fun `the dialog is centered, near the top while the keyboard shows, and centered again once it's closed`() {
        val dialog = start().get().rename("B")
        val attributes = { checkNotNull(dialog.window).attributes }
        val margin = dialog.context.resources.getDimensionPixelSize(UiR.dimen.dialog_margin)
        assertEquals(Gravity.CENTER, attributes().gravity)

        dialog.keyboard(shown = true)
        assertEquals(Gravity.TOP or Gravity.CENTER_HORIZONTAL, attributes().gravity)
        assertEquals(margin, attributes().y)

        dialog.keyboard(shown = false)
        assertEquals(Gravity.CENTER, attributes().gravity)
        assertEquals(0, attributes().y)
    }

    // Robolectric doesn't shrink a window for the keyboard, so this checks what keeps Android from
    // doing it: the window says which insets it fits (the flag a set fitInsetsTypes sets), and the
    // keyboard isn't among them. Without the flag, API 30 adds the keyboard for a window with a field.
    @Test
    fun `the dialog's window doesn't shrink to the room above the keyboard`() {
        val attributes = checkNotNull(start().get().rename("B").window).attributes
        val privateFlags = WindowManager.LayoutParams::class.java.getField("privateFlags").getInt(attributes)
        val controlled = WindowManager.LayoutParams::class.java.getField("PRIVATE_FLAG_FIT_INSETS_CONTROLLED").getInt(null)

        assertTrue("the window says which insets it fits", privateFlags and controlled != 0)
        assertEquals(0, attributes.fitInsetsTypes and WindowInsets.Type.ime())
    }

    @Test
    fun `Rename stores the name typed, and the tile shows it at once, in its place by name, focused`() {
        val activity = start().get()
        val dialog = activity.rename("B")

        dialog.type("Zulu")
        dialog.button("Rename").performClick()
        idle()

        assertFalse(dialog.isShowing)
        assertEquals(stored("d" to "Delta", "b" to "Zulu"), customizations.customizations)
        assertEquals(listOf("A", "C", "Delta", "E", "Zulu"), activity.names())
        assertTrue(activity.tile("Zulu").isFocused)
    }

    @Test
    fun `the keyboard's Done renames too`() {
        val activity = start().get()
        val dialog = activity.rename("B")

        dialog.type("Bravo")
        dialog.field.onEditorAction(EditorInfo.IME_ACTION_DONE)
        idle()

        assertFalse(dialog.isShowing)
        assertEquals(stored("d" to "Delta", "b" to "Bravo"), customizations.customizations)
        assertEquals(listOf("A", "Bravo", "C", "Delta", "E"), activity.names())
    }

    @Test
    fun `Reset gives the app its original name back`() {
        val activity = start().get()
        val dialog = activity.rename("Delta")

        dialog.button("Reset").performClick()
        idle()

        assertFalse(dialog.isShowing)
        assertEquals(emptyMap<LaunchableApp, AppCustomization>(), customizations.customizations)
        assertEquals(listOf("A", "B", "C", "D", "E"), activity.names())
        assertTrue(activity.tile("D").isFocused)
    }

    @Test
    fun `an empty field gives the app its original name back`() {
        val activity = start().get()
        val dialog = activity.rename("Delta")

        dialog.type("   ")
        dialog.button("Rename").performClick()
        idle()

        assertEquals(emptyMap<LaunchableApp, AppCustomization>(), customizations.customizations)
        assertEquals(listOf("A", "B", "C", "D", "E"), activity.names())
    }

    @Test
    fun `the original name typed keeps no name of the user's`() {
        val activity = start().get()
        val dialog = activity.rename("Delta")

        dialog.type("D")
        dialog.button("Rename").performClick()
        idle()

        assertEquals(emptyMap<LaunchableApp, AppCustomization>(), customizations.customizations)
    }

    @Test
    fun `the field takes a name of the longest length, and no more`() {
        val dialog = start().get().rename("B")

        dialog.type("a".repeat(MAX_APP_NAME + 5))

        assertEquals(MAX_APP_NAME, dialog.field.length())
    }

    @Test
    fun `Cancel closes the dialog, and changes nothing`() {
        val activity = start().get()
        val dialog = activity.rename("B")

        dialog.type("Zulu")
        dialog.button("Cancel").performClick()
        idle()

        assertFalse(dialog.isShowing)
        assertEquals(stored("d" to "Delta"), customizations.customizations)
        assertTrue(activity.tile("B").isFocused)
    }

    // Back as Android gives it to the dialog; the real key, through the keyboard, is HomeActivityTest's (Espresso).
    @Test
    fun `Back closes the dialog, and changes nothing`() {
        val activity = start().get()
        val dialog = activity.rename("B")

        dialog.type("Zulu")
        @Suppress("DEPRECATION")
        dialog.onBackPressed()
        idle()

        assertFalse(dialog.isShowing)
        assertEquals(stored("d" to "Delta"), customizations.customizations)
    }

    @Test
    fun `Home closes the dialog, and changes nothing`() {
        val controller = start()
        val dialog = controller.get().rename("B")
        dialog.type("Zulu")

        controller.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

        assertFalse(dialog.isShowing)
        assertEquals(stored("d" to "Delta"), customizations.customizations)
    }

    @Test
    fun `leaving the home screen closes the dialog`() {
        val controller = start()
        val dialog = controller.get().rename("B")

        controller.pause().stop()

        assertFalse(dialog.isShowing)
    }

    // Its app may be gone.
    @Test
    fun `an app installed while the dialog shows closes it`() {
        val activity = start().get()
        val dialog = activity.rename("B")

        installedApps.apps += app("f")

        assertFalse(dialog.isShowing)
        assertEquals(listOf("A", "B", "C", "Delta", "E", "F"), activity.names())
    }
}
