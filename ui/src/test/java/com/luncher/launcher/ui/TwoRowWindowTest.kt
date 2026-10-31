package com.luncher.launcher.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.platform.app.InstrumentationRegistry
import com.luncher.domain.appearance.ThemeAppearance
import com.luncher.domain.appearance.colorRoles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * A list whose window shows two rows, both kinds of list: the focus is at the window's end it came
 * from, whole and not faded, the row ahead beyond it faded while there's more; an arrow the other
 * way moves the list and the focus a row together, the same row keeping the focus.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TwoRowWindowTest(private val kind: String) {

    private val activity by lazy { Robolectric.buildActivity(Activity::class.java).setup().get() }
    private lateinit var frame: ScrollFrame
    private lateinit var list: View

    private val rowHeight = 100
    private val gap = if (kind == "list") 8 else 0
    private val margin = 20
    private val pitch = rowHeight + gap
    private val roles = colorRoles(ThemeAppearance())

    @Before
    fun leaveTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    private fun show(rows: Int) {
        frame = ScrollFrame(activity)
        val height = 2 * pitch - gap + 2 * margin
        activity.setContentView(FrameLayout(activity).apply { addView(frame, FrameLayout.LayoutParams(400, height)) })
        frame.style(roles)
        list = if (kind == "list") listView(rows) else scrollView(rows)
        frame.addView(list, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        ((list as? ViewGroup)?.getChildAt(0).takeIf { list is LookAheadScrollView } as? ViewGroup)?.getChildAt(0)?.requestFocus() ?: list.requestFocus()
        settle()
    }

    private fun row(index: Int) = ListEntry(activity).apply {
        minimumHeight = rowHeight
        isFocusable = true
        tag = index
        style(roles)
    }

    private fun listView(rows: Int) = LookAheadListView(activity).apply {
        setPadding(0, margin, 0, margin)
        clipToPadding = false
        dividerHeight = gap
        adapter = object : BaseAdapter() {
            override fun getCount() = rows
            override fun getItem(position: Int) = position
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup) = convertView ?: row(position)
        }
    }

    private fun scrollView(rows: Int) = LookAheadScrollView(activity).apply {
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, margin, 0, margin)
            for (i in 0 until rows) addView(row(i), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        })
    }

    private fun settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
    }

    private fun press(keyCode: Int) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        settle()
    }

    private fun down() = press(KeyEvent.KEYCODE_DPAD_DOWN)
    private fun up() = press(KeyEvent.KEYCODE_DPAD_UP)

    private val focused: Int
        get() = (list as? LookAheadListView)?.selectedItemPosition
            ?: (list as ViewGroup).let { scroll -> (scroll.getChildAt(0) as ViewGroup).indexOfChild(scroll.findFocus()) }

    /** The focused row's place in the window: 0 at its start, 1 at its end. */
    private fun place(): Int {
        val view = (list as? LookAheadListView)?.selectedView ?: list.findFocus()
        val top = if (list is LookAheadScrollView) view.top - list.scrollY else view.top
        assertEquals("a row of the window", 0, (top - margin) % pitch)
        return (top - margin) / pitch
    }

    private fun assertFocus(row: Int, place: Int) {
        assertEquals("the focused row", row, focused)
        assertEquals("row $row's place in the window", place, place())
        assertEquals("the fades follow it", place.toFloat(), (list as FittedRows).focusAlong)
    }

    @Test
    fun `going down, the focus stays at the window's start, the next row below it`() {
        show(rows = 6)
        assertFocus(0, place = 0)

        for (row in 1..4) {
            down()
            assertFocus(row, place = 0)
        }
    }

    @Test
    fun `the last row goes to the edge, and from there the focus stays at the window's end`() {
        show(rows = 6)
        repeat(5) { down() }

        assertFocus(5, place = 1)
        assertFalse(list.canScrollVertically(1))
        for (row in 4 downTo 1) {
            up()
            assertFocus(row, place = 1)
        }
        up()
        assertFocus(0, place = 0)
        assertFalse(list.canScrollVertically(-1))
    }

    @Test
    fun `an arrow the other way moves the list and the focus together, the same row keeping it`() {
        show(rows = 8)
        repeat(3) { down() }
        assertFocus(3, place = 0)

        up()
        assertFocus(3, place = 1)
        up()
        assertFocus(2, place = 1)
        down()
        assertFocus(2, place = 0)
        down()
        assertFocus(3, place = 0)
    }

    @Test
    fun `up at the first row and down at the last do nothing`() {
        show(rows = 4)
        up()
        assertFocus(0, place = 0)

        repeat(3) { down() }
        assertFocus(3, place = 1)
        down()
        assertFocus(3, place = 1)
    }

    @Test
    fun `the fade is on the row ahead, not on the focused row`() {
        show(rows = 8)
        repeat(3) { down() }

        // At the window's start: the row itself clean, the one below it faded.
        val atStart = picture()
        val focusFill = roles.inverseSurface
        assertEquals("the focused row's end, not faded", focusFill, atStart.getPixel(200, margin + rowHeight - 4))
        assertEquals("the margin covered, nothing peeking in", roles.surface, atStart.getPixel(200, margin - 4))

        // Turned round: the row itself clean at the window's end.
        up()
        val atEnd = picture()
        assertEquals("the focused row's start, not faded", focusFill, atEnd.getPixel(200, margin + pitch + 4))
        assertEquals("the focused row's end, not faded", focusFill, atEnd.getPixel(200, margin + pitch + rowHeight - 4))
    }

    @Test
    fun `a row given the focus shows at the window's start, the row ahead below it`() {
        show(rows = 8)

        val shown = list
        if (shown is LookAheadListView) shown.setSelection(5) else ((shown as ViewGroup).getChildAt(0) as ViewGroup).getChildAt(5).requestFocus()
        settle()

        assertFocus(5, place = 0)
    }

    private fun picture(): Bitmap {
        (list as? LookAheadScrollView)?.findFocus()?.isSelected = true   // Robolectric's window never has the focus, a focused row's look needs it
        val bitmap = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
        frame.draw(Canvas(bitmap))
        return bitmap
    }

    @Test
    fun `a window of three rows doesn't turn round`() {
        frame = ScrollFrame(activity)
        val height = 3 * pitch - gap + 2 * margin
        activity.setContentView(FrameLayout(activity).apply { addView(frame, FrameLayout.LayoutParams(400, height)) })
        list = if (kind == "list") listView(8) else scrollView(8)
        frame.addView(list, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        ((list as? ViewGroup)?.getChildAt(0).takeIf { list is LookAheadScrollView } as? ViewGroup)?.getChildAt(0)?.requestFocus() ?: list.requestFocus()
        settle()
        repeat(4) { down() }
        val row = focused

        up()

        assertEquals(row - 1, focused)
        assertTrue((list as FittedRows).focusAlong < 0f)
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun kinds() = listOf("list", "scroll")
    }
}
