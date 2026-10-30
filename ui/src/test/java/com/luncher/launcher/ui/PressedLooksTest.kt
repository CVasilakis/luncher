package com.luncher.launcher.ui

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import com.luncher.domain.appearance.ColorRoles
import com.luncher.domain.appearance.ThemeAppearance
import com.luncher.domain.appearance.ThemeMode
import com.luncher.domain.appearance.colorRoles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/**
 * What a press looks like where OK can be pressed: an entry's or a row's fill, a button's and a
 * field's dip, and the hold that opens what a row opens on a long press (docs: ui/README.md).
 */
@RunWith(RobolectricTestRunner::class)
class PressedLooksTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val dark = colorRoles(ThemeAppearance())
    private val light = colorRoles(ThemeAppearance(mode = ThemeMode.LIGHT))

    private val focusedWindow = android.R.attr.state_window_focused
    private val focused = android.R.attr.state_focused
    private val pressed = android.R.attr.state_pressed
    private val selected = android.R.attr.state_selected

    /** The fill [background] draws in [states], or null where it draws none. */
    private fun fillIn(background: android.graphics.drawable.Drawable, vararg states: Int): Int? {
        val list = background as StateListDrawable
        list.state = states
        return (list.current as? GradientDrawable)?.color?.defaultColor
    }

    // Robolectric's attribute set builder is deprecated for resources of the test's own; here it only
    // says the attribute `zooms` of this module, which no layout of its tests sets.
    @Suppress("DEPRECATION")
    private fun entry(roles: ColorRoles, zooms: Boolean = false): ListEntry {
        val attrs = if (zooms) Robolectric.buildAttributeSet().addAttribute(R.attr.zooms, "true").build() else null
        return ListEntry(activity, attrs).also { it.style(roles) }
    }

    // The fill changes while OK is held on the focused entry, or on a list's selected row, and only then.
    @Test
    fun `an entry's fill goes to the pressed one while OK is held on it focused, or as a list's selected row`() {
        for (roles in listOf(dark, light)) {
            val background = entry(roles).background
            assertEquals(roles.inverseSurface, fillIn(background, focusedWindow, focused))
            assertEquals(roles.inverseSurfacePressed, fillIn(background, pressed, focusedWindow, focused))
            assertEquals(roles.inverseSurface, fillIn(background, selected))
            assertEquals(roles.inverseSurfacePressed, fillIn(background, pressed, selected))
        }
    }

    @Test
    fun `an entry that isn't focused has no pressed fill`() {
        assertNull(fillIn(entry(dark).background, pressed))
    }

    @Test
    fun `the pressed fill of a light theme and of a dark one both differ from the focused fill`() {
        assertTrue(light.inverseSurfacePressed != light.inverseSurface)
        assertTrue(dark.inverseSurfacePressed != dark.inverseSurface)
        // The other way round in the two modes: lighter where the focused fill is dark, darker where it is light.
        assertTrue(luminance(light.inverseSurfacePressed) > luminance(light.inverseSurface))
        assertTrue(luminance(dark.inverseSurfacePressed) < luminance(dark.inverseSurface))
    }

    private fun luminance(color: Int) = (0.2126 * (color shr 16 and 0xFF) + 0.7152 * (color shr 8 and 0xFF) + 0.0722 * (color and 0xFF)) / 255

    @Test
    fun `a menu's single-line entry has the pressed fill too`() {
        val entry = EntryText(activity).also { it.style(light) }

        assertEquals(light.inverseSurface, fillIn(entry.background, focusedWindow, focused))
        assertEquals(light.inverseSurfacePressed, fillIn(entry.background, pressed, focusedWindow, focused))
    }

    // A field dips instead (below): its fill stays the focused one.
    @Test
    fun `a field that zooms keeps its focused fill while pressed`() {
        val background = entry(dark, zooms = true).background

        assertEquals(dark.inverseSurface, fillIn(background, pressed, focusedWindow, focused))
    }

    // The list draws the focused look while it slides, and the row looks at rest meanwhile, pressed or not.
    @Test
    fun `a row the list holds at rest looks at rest even pressed`() {
        val row = entry(dark)
        row.lookHeld = true
        row.isPressed = true

        assertFalse(row.drawableState.contains(pressed))
        row.lookHeld = false
        assertTrue(row.drawableState.contains(pressed))
    }

    private fun attached(view: View): View {
        activity.setContentView(FrameLayout(activity).apply { addView(view, FrameLayout.LayoutParams(400, 100)) })
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.requestFocus()
        idle(300)
        return view
    }

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun key(action: Int) = activity.dispatchKeyEvent(KeyEvent(action, KeyEvent.KEYCODE_DPAD_CENTER))

    // OK held: the button dips under its resting size and its shadow is set down; back when let go.
    @Test
    fun `a button dips under its resting size while OK is held, and zooms back once it's let go`() {
        val button = attached(PillButton(activity).also { it.setOnClickListener {} }) as PillButton
        val zoom = typedFloat(R.dimen.button_zoom)
        val dipped = typedFloat(R.dimen.button_zoom_pressed)
        val lift = activity.resources.getDimension(R.dimen.button_lift)
        assertEquals(zoom, button.scaleX, 0.001f)
        assertEquals(lift, button.translationZ, 0.01f)

        key(KeyEvent.ACTION_DOWN)
        idle(200)

        assertEquals(dipped, button.scaleX, 0.001f)
        assertEquals(dipped, button.scaleY, 0.001f)
        assertEquals(0f, button.translationZ, 0.01f)
        assertTrue("under its resting size", button.scaleX < 1f)
        key(KeyEvent.ACTION_UP)
        idle(300)
        assertEquals(zoom, button.scaleX, 0.001f)
        assertEquals(lift, button.translationZ, 0.01f)
    }

    @Test
    fun `a field dips under its resting size while OK is held, and zooms back once it's let go`() {
        val field = attached(entry(dark, zooms = true).also { it.setOnClickListener {} }) as ListEntry
        val zoom = typedFloat(R.dimen.entry_zoom)
        val dipped = typedFloat(R.dimen.entry_zoom_pressed)
        assertEquals(zoom, field.scaleX, 0.001f)

        key(KeyEvent.ACTION_DOWN)
        idle(200)

        assertEquals(dipped, field.scaleX, 0.001f)
        assertEquals(dipped, field.scaleY, 0.001f)
        assertTrue("under its resting size", field.scaleX < 1f)
        key(KeyEvent.ACTION_UP)
        idle(300)
        assertEquals(zoom, field.scaleX, 0.001f)
    }

    // A row doesn't zoom, pressed or not: only its fill changes.
    @Test
    fun `a row doesn't dip`() {
        val row = attached(entry(dark).also { it.setOnClickListener {} }) as ListEntry

        key(KeyEvent.ACTION_DOWN)
        idle(200)

        assertEquals(1f, row.scaleX, 0f)
    }

    private fun typedFloat(id: Int) = android.util.TypedValue().also { activity.resources.getValue(id, it, true) }.float

    // A row that opens something on a long press (a picture's menu) opens it after Luncher's own
    // hold, 350 ms, not Android's long press (500 ms here).
    @Test
    fun `a row that opens something on a long press opens it after the hold, before Android's long press`() {
        var opened = 0
        var clicked = 0
        val row = attached(entry(dark).also { it.setOnClickListener { clicked++ }; it.setOnLongClickListener { opened++; true } })

        key(KeyEvent.ACTION_DOWN)
        idle(340)
        assertEquals(0, opened)
        idle(20)

        assertEquals(1, opened)
        // Android's own long press, which the hold cancelled, doesn't open it again.
        idle(400)
        assertEquals(1, opened)
        // The release comes to the row after it: it ends the press and clicks nothing.
        key(KeyEvent.ACTION_UP)
        assertEquals(0, clicked)
        assertFalse(row.isPressed)
    }

    @Test
    fun `a row let go before the hold ran out is clicked, and its menu doesn't open`() {
        var opened = 0
        var clicked = 0
        attached(entry(dark).also { it.setOnClickListener { clicked++ }; it.setOnLongClickListener { opened++; true } })

        key(KeyEvent.ACTION_DOWN)
        idle(340)
        key(KeyEvent.ACTION_UP)
        idle(600)

        assertEquals(1, clicked)
        assertEquals(0, opened)
    }

    @Test
    fun `a row with no long press just clicks, however long OK is held`() {
        var clicked = 0
        attached(entry(dark).also { it.setOnClickListener { clicked++ } })

        key(KeyEvent.ACTION_DOWN)
        idle(1500)
        key(KeyEvent.ACTION_UP)

        assertEquals(1, clicked)
    }

    @Test
    fun `a row taken out of its window while OK is held doesn't open its menu`() {
        var opened = 0
        val row = attached(entry(dark).also { it.setOnClickListener {}; it.setOnLongClickListener { opened++; true } })

        key(KeyEvent.ACTION_DOWN)
        idle(200)
        (row.parent as FrameLayout).removeView(row)
        idle(1000)

        assertEquals(0, opened)
    }

    @Test
    fun `a row that loses the focus while OK is held doesn't open its menu`() {
        var opened = 0
        val row = attached(entry(dark).also { it.setOnClickListener {}; it.setOnLongClickListener { opened++; true } })

        key(KeyEvent.ACTION_DOWN)
        idle(200)
        row.clearFocus()
        idle(1000)

        assertEquals(0, opened)
    }
}
