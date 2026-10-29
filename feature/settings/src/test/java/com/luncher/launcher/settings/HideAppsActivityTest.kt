package com.luncher.launcher.settings

import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import com.luncher.domain.apps.AppArrangement
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeAppCustomizations
import com.luncher.domain.apps.FakeAppCustomizations.Companion.named
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.launcher.testing.TV_1080P
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
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import com.luncher.launcher.ui.R as UiR

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-$TV_1080P")
class HideAppsActivityTest {

    private val installedApps = FakeInstalledApps(app("news"), app("movies"), app("music"))
    private val arrangements = FakeAppArrangements(
        AppArrangement(order = null, hidden = listOf(app("music").launchable)),
    )
    private val customizations = FakeAppCustomizations()

    @Before
    fun useFakes() {
        val application = RuntimeEnvironment.getApplication() as SettingsTestApplication
        application.graph = object : TestSettingsGraph() {
            override val installedApps = this@HideAppsActivityTest.installedApps
            override val appArrangements = this@HideAppsActivityTest.arrangements
            override val appCustomizations = this@HideAppsActivityTest.customizations
        }
    }

    private fun start() = Robolectric.buildActivity(HideAppsActivity::class.java).setup().get()

    private fun HideAppsActivity.list() = findViewById<ListView>(R.id.settings_apps)

    // As the settings panel, whose place it takes.
    @Test
    fun `it comes and goes with a short fade of Luncher's own`() {
        assertEquals(UiR.style.Animation_Luncher_Panel, start().window.attributes.windowAnimations)
    }

    /** Each row as "Label" or "Label (hidden)". */
    private fun HideAppsActivity.rows(): List<String> {
        val list = list()
        return (0 until list.childCount).map { list.getChildAt(it) }.map { row ->
            val label = row.findViewById<TextView>(R.id.settings_app_label).text.toString()
            val hidden = row.findViewById<View>(R.id.settings_app_hidden).visibility == View.VISIBLE
            if (hidden) "$label (hidden)" else label
        }
    }

    private fun HideAppsActivity.press(keyCode: Int) {
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    /** Lays the list out again, as the next frame would after a change. */
    private fun HideAppsActivity.relayout() {
        val list = list()
        list.measure(
            View.MeasureSpec.makeMeasureSpec(list.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(list.height, View.MeasureSpec.EXACTLY),
        )
        list.layout(list.left, list.top, list.right, list.bottom)
    }

    @Test
    fun `lists every app by name, with the hidden ones marked`() {
        assertEquals(listOf("Movies", "Music (hidden)", "News"), start().rows())
    }

    @Test
    fun `starts with the first app selected`() {
        val activity = start()

        assertTrue(activity.list().hasFocus())
        assertEquals(0, activity.list().selectedItemPosition)
    }

    @Test
    fun `OK hides the selected app, and stores it at once`() {
        val activity = start()

        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)
        activity.relayout()

        assertEquals(listOf("Movies (hidden)", "Music (hidden)", "News"), activity.rows())
        assertEquals(listOf(app("movies").launchable, app("music").launchable), arrangements.arrangement.hidden)
    }

    @Test
    fun `OK on a hidden app shows it again`() {
        val activity = start()

        activity.press(KeyEvent.KEYCODE_DPAD_DOWN)
        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)
        activity.relayout()

        assertEquals(listOf("Movies", "Music", "News"), activity.rows())
        assertEquals(AppArrangement.NONE, arrangements.arrangement)
    }

    // A TV switched from 1080p to 720p (HDMI), or a new language, recreates the panel.
    @Test
    fun `after a configuration change, lists the apps as stored, the same one selected`() {
        val controller = Robolectric.buildActivity(HideAppsActivity::class.java).setup()
        controller.get().press(KeyEvent.KEYCODE_DPAD_DOWN)
        controller.get().press(KeyEvent.KEYCODE_DPAD_DOWN)
        controller.get().press(KeyEvent.KEYCODE_DPAD_CENTER)   // hides News

        RuntimeEnvironment.setQualifiers("+tvdpi")   // 960x540 dp at 720p
        controller.configurationChange().visible()   // Robolectric shows the new window only when told

        val activity = controller.get()
        assertEquals(listOf("Movies", "Music (hidden)", "News (hidden)"), activity.rows())
        assertEquals(2, activity.list().selectedItemPosition)
    }

    /** A row and the gap below it. */
    private fun HideAppsActivity.row() = resources.getDimensionPixelSize(UiR.dimen.row_height) + resources.getDimensionPixelSize(UiR.dimen.entry_gap)

    /** The panel laid out in its side sheet, as Android lays out the sheet's window. */
    private fun HideAppsActivity.layOutInSheet(): ViewGroup {
        val panel = findViewById<ViewGroup>(R.id.settings_hide_apps_panel)
        val sheet = window.attributes
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(sheet.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(sheet.height, View.MeasureSpec.EXACTLY),
        )
        panel.layout(0, 0, sheet.width, sheet.height)
        return panel
    }

    /** Where the list's frame ends, in the panel. */
    private fun HideAppsActivity.listBottom() = (list().parent as View).bottom

    // As tall as the side sheet leaves, the last row cut where the sheet's padding starts, fading out: there's more.
    @Test
    fun `fills the sheet when the apps don't all fit, the sheet as tall as the screen less its margins`() {
        installedApps.apps = List(30) { app("app%02d".format(it)) }

        val activity = start()
        val panel = activity.layOutInSheet()

        val margin = activity.resources.getDimensionPixelSize(R.dimen.settings_sheet_margin)
        assertEquals(activity.resources.displayMetrics.heightPixels - 2 * margin, activity.window.attributes.height)
        assertEquals(panel.height - panel.paddingBottom, activity.listBottom())
        assertTrue(activity.list().height > 6 * activity.row())
        assertTrue(activity.list().canScrollVertically(1))
    }

    @Test
    fun `shows every row when they fit, with nothing to scroll`() {
        val activity = start()
        activity.layOutInSheet()

        assertEquals(3, activity.list().childCount)
        assertFalse(activity.list().canScrollVertically(1))
        assertFalse(activity.list().canScrollVertically(-1))
    }

    // A 720p screen at the 1080p density: 360 dp tall.
    @Test
    @Config(qualifiers = "en-rUS-w640dp-h360dp-land-television-xhdpi-notouch-dpad")
    fun `on a small screen the panel fills it, within its margins`() {
        installedApps.apps = List(12) { app("app%02d".format(it)) }

        val activity = start()
        val panel = activity.layOutInSheet()

        val margin = activity.resources.getDimensionPixelSize(R.dimen.settings_sheet_margin)
        assertEquals(activity.resources.displayMetrics.heightPixels - 2 * margin, activity.window.attributes.height)
        assertEquals(panel.height - panel.paddingBottom, activity.listBottom())
        assertTrue(activity.list().height > activity.row())
    }

    // The rows next to the selected one show whole, where there are: the next one shows before the
    // selection gets there. One thing moves at a time: the selection, or the list under it by a
    // row, the selection staying where it is. The window ends on a whole row.
    @Test
    fun `scrolls a row ahead of the selection, both ways, moving the selection or the list, never both`() {
        installedApps.apps = List(30) { app("app%02d".format(it)) }
        val activity = start()
        val list = activity.list()
        val pitch = list.getChildAt(1).top - list.getChildAt(0).top

        fun scrolled() = list.firstVisiblePosition * pitch + list.paddingTop - list.getChildAt(0).top

        fun assertAhead(position: Int) {
            assertEquals(position, list.selectedItemPosition)
            val index = position - list.firstVisiblePosition
            if (position > 0) {
                val previous = list.getChildAt(index - 1)
                assertTrue("the app before ${position + 1} cut at the top", previous != null && previous.top >= list.paddingTop)
            }
            if (position < list.count - 1) {
                val next = list.getChildAt(index + 1)
                assertTrue("the app after ${position + 1} cut at the bottom", next != null && next.bottom <= list.height - list.paddingBottom)
            }
            val selected = list.selectedView
            assertTrue("app ${position + 1} cut", selected.top >= list.paddingTop && selected.bottom <= list.height - list.paddingBottom)
        }

        fun pressAndCheck(keyCode: Int, position: Int) {
            val top = list.selectedView.top
            val before = scrolled()
            activity.press(keyCode)
            shadowOf(Looper.getMainLooper()).idle()
            val moved = list.selectedView.top != top
            val slid = scrolled() - before
            assertTrue("to app ${position + 1}: the selection moved ($moved) or the list slid ($slid px), one of them", moved != (slid != 0))
            if (slid != 0) assertEquals("the list slid by a row", pitch, kotlin.math.abs(slid))
            assertAhead(position)
        }

        assertTrue("the list scrolls", list.canScrollVertically(1))
        assertEquals("a window of whole rows", 0, (list.height - list.paddingTop - list.paddingBottom + list.dividerHeight) % pitch)
        for (position in 1 until 30) pressAndCheck(KeyEvent.KEYCODE_DPAD_DOWN, position)
        assertFalse("the last app at the edge, no more below", list.canScrollVertically(1))
        for (position in 28 downTo 0) pressAndCheck(KeyEvent.KEYCODE_DPAD_UP, position)
    }

    // While the list slides, the list draws the focus where it stays, and the row it goes to looks
    // at rest: its own look would move with it.
    @Test
    fun `while the list slides, the selected app looks at rest until it's under the focus`() {
        installedApps.apps = List(30) { app("app%02d".format(it)) }
        val activity = start()
        val list = activity.list()
        val looper = shadowOf(Looper.getMainLooper())
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        try {
            // Frame by frame: paused, a frame runs once per idle, however long.
            fun frames() = repeat(30) { looper.idleFor(Duration.ofMillis(16)) }
            while (!list.canScrollVertically(-1)) {
                activity.press(KeyEvent.KEYCODE_DPAD_DOWN)
                frames()
            }
            val focus = list.selectedView.top
            activity.press(KeyEvent.KEYCODE_DPAD_DOWN)
            val next = list.selectedView
            val start = next.top
            assertTrue("the next app below the focus", start > focus)
            repeat(30) { if (next.top == start) looper.idleFor(Duration.ofMillis(16)) }

            assertTrue("the list on its way: ${next.top}, from $start to $focus", next.top in focus + 1 until start)
            assertFalse("the app looks selected before it's under the focus", android.R.attr.state_selected in next.drawableState)

            frames()
            assertEquals("under the focus, where it stayed", focus, next.top)
            assertTrue("the app looks selected once there", android.R.attr.state_selected in next.drawableState)
        } finally {
            ShadowChoreographer.setPaused(false)
        }
    }

    // Unlike the home screen (HomeActivityTest), which stays left to right.
    @Test
    @Config(qualifiers = "ar-rEG-$TV_1080P")
    fun `mirrors in right-to-left languages`() {
        val list = start().list()

        val row = list.getChildAt(1)   // Music, hidden
        val label = row.findViewById<View>(R.id.settings_app_label)
        val names = label.parent as View   // the column of the app's names, in the row
        val hidden = row.findViewById<View>(R.id.settings_app_hidden)
        assertTrue("names at ${names.left}, hidden at ${hidden.right}", hidden.right <= names.left)
        val text = (label as TextView).layout
        assertEquals(text.width.toFloat(), text.getLineRight(0))   // an English name at the right too
    }

    // "Hidden" says which apps are hidden; the selected row looks focused.
    @Test
    fun `a hidden app has Hidden, a shown one doesn't`() {
        val list = start().list()

        val shown = list.getChildAt(0)   // Movies
        val hidden = list.getChildAt(1)  // Music
        assertEquals(View.GONE, shown.findViewById<View>(R.id.settings_app_hidden).visibility)
        assertEquals(View.VISIBLE, hidden.findViewById<View>(R.id.settings_app_hidden).visibility)
    }

    @Test
    fun `says so when there are no apps`() {
        installedApps.apps = emptyList()

        val activity = start()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.settings_no_apps).visibility)
    }

    @Test
    fun `lists an app installed or removed while it shows, at once`() {
        val activity = start()

        installedApps.apps = installedApps.apps.filter { it.label != "News" } + app("games")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))   // the next frames

        assertEquals(listOf("Games", "Movies", "Music (hidden)"), activity.rows())
    }

    @Test
    fun `reads the apps again when it comes back`() {
        val controller = Robolectric.buildActivity(HideAppsActivity::class.java).setup()

        controller.pause().stop()
        installedApps.apps += app("games")      // not watched while hidden
        controller.restart().start().resume()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))

        assertEquals(listOf("Games", "Movies", "Music (hidden)", "News"), controller.get().rows())
    }

    // An app the user renamed on the home screen: listed by its real name, the new one below it.

    /** Each row's second line, the name the user gave it, or null where it shows none. */
    private fun HideAppsActivity.givenNames(): List<String?> {
        val list = list()
        return (0 until list.childCount).map { list.getChildAt(it).findViewById<TextView>(R.id.settings_app_name) }
            .map { if (it.visibility == View.VISIBLE) it.text.toString() else null }
    }

    @Test
    fun `lists a renamed app by its real name, in its place by it, with the name given below`() {
        customizations.customizations = mapOf(named(app("news"), "Arts"), named(app("music"), "My songs"))

        val activity = start()

        assertEquals(listOf("Movies", "Music (hidden)", "News"), activity.rows())
        assertEquals(listOf(null, "Renamed: \u2068My songs\u2069", "Renamed: \u2068Arts\u2069"), activity.givenNames())
    }

    @Test
    fun `shows a name given meanwhile when it comes back`() {
        val controller = Robolectric.buildActivity(HideAppsActivity::class.java).setup()

        controller.pause().stop()
        customizations.customizations = mapOf(named(app("movies"), "Cinema"))
        controller.restart().start().resume()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("Renamed: \u2068Cinema\u2069", null, null), controller.get().givenNames())
    }

    // At the largest text size, on API 33, which grows it more than 34 on: two names need more than
    // a list's usual row there.
    @Test
    @Config(sdk = [33], fontScale = 1.3f)
    fun `every row is as tall as two names and a margin, renamed or not`() {
        customizations.customizations = mapOf(named(app("news"), "Arts"))
        val list = start().list()

        val row = list.getChildAt(2)   // News, renamed
        val names = row.findViewById<View>(R.id.settings_app_label).parent as View
        assertEquals(setOf(row.height), (0 until list.childCount).map { list.getChildAt(it).height }.toSet())
        val margin = RuntimeEnvironment.getApplication().resources.getDimensionPixelSize(R.dimen.settings_app_names_margin)
        // Centered, the odd pixel below.
        assertTrue("names at ${names.top}..${names.bottom} in a row ${row.height} px tall", names.top >= margin && row.height - names.bottom >= margin)
    }
}
