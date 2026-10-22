package com.luncher.launcher.settings

import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import com.luncher.domain.apps.AppArrangement
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.launcher.testing.TV_1080P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-$TV_1080P")
class HideAppsActivityTest {

    private val installedApps = FakeInstalledApps(app("news"), app("movies"), app("music"))
    private val arrangements = FakeAppArrangements(
        AppArrangement(order = null, hidden = listOf(app("music").launchable)),
    )

    @Before
    fun useFakes() {
        val application = RuntimeEnvironment.getApplication() as SettingsTestApplication
        application.graph = object : TestSettingsGraph() {
            override val installedApps = this@HideAppsActivityTest.installedApps
            override val appArrangements = this@HideAppsActivityTest.arrangements
        }
    }

    private fun start() = Robolectric.buildActivity(HideAppsActivity::class.java).setup().get()

    private fun HideAppsActivity.list() = findViewById<ListView>(R.id.settings_apps)

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

    /** A row and the gap below it, and the list's padding above and below, room for the focused row's zoom. */
    private fun HideAppsActivity.row() = resources.getDimensionPixelSize(R.dimen.settings_row_height) + resources.getDimensionPixelSize(R.dimen.settings_entry_gap)

    private fun HideAppsActivity.listPadding() = list().paddingTop + list().paddingBottom

    private fun View.forceLayoutAll() {
        forceLayout()
        if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).forceLayoutAll()
    }

    /** The panel's height with the list as tall as it was made, at the sheet's width. */
    private fun HideAppsActivity.panelHeight(): Int {
        val panel = findViewById<View>(R.id.settings_hide_apps_panel)
        panel.forceLayoutAll()   // measured already with the same specs, before the list had its height
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(window.attributes.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        return panel.measuredHeight
    }

    // As tall as the side sheet leaves, the last row cut where the sheet's padding starts, fading out: there's more.
    @Test
    fun `fills the sheet when the apps don't all fit, the sheet as tall as the screen less its margins`() {
        installedApps.apps = List(30) { app("app%02d".format(it)) }

        val activity = start()

        val margin = activity.resources.getDimensionPixelSize(R.dimen.settings_sheet_margin)
        assertEquals(activity.resources.displayMetrics.heightPixels - 2 * margin, activity.window.attributes.height)
        assertEquals(activity.window.attributes.height, activity.panelHeight())
        assertTrue(activity.list().layoutParams.height - activity.listPadding() > 6 * activity.row())
    }

    @Test
    fun `is only as tall as its rows when they fit`() {
        val activity = start()

        assertEquals(activity.row() * 3 + activity.listPadding(), activity.list().layoutParams.height)
    }

    // A 720p screen at the 1080p density: 360 dp tall.
    @Test
    @Config(qualifiers = "en-rUS-w640dp-h360dp-land-television-xhdpi-notouch-dpad")
    fun `on a small screen the panel fills it, within its margins`() {
        installedApps.apps = List(12) { app("app%02d".format(it)) }

        val activity = start()

        val margin = activity.resources.getDimensionPixelSize(R.dimen.settings_sheet_margin)
        assertEquals(activity.resources.displayMetrics.heightPixels - 2 * margin, activity.panelHeight())
        assertTrue(activity.list().layoutParams.height - activity.listPadding() > activity.row())
    }

    // The rows next to the selected one show whole, where there are: the next one shows before the selection gets there.
    @Test
    fun `scrolls a row ahead of the selection, both ways, to the last app and back to the first`() {
        installedApps.apps = List(30) { app("app%02d".format(it)) }
        val activity = start()
        val list = activity.list()

        fun assertAhead(position: Int) {
            shadowOf(Looper.getMainLooper()).idle()
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

        assertTrue("the list scrolls", list.canScrollVertically(1))
        for (position in 1 until 30) {
            activity.press(KeyEvent.KEYCODE_DPAD_DOWN)
            assertAhead(position)
        }
        for (position in 28 downTo 0) {
            activity.press(KeyEvent.KEYCODE_DPAD_UP)
            assertAhead(position)
        }
    }

    // Unlike the home screen (HomeActivityTest), which stays left to right.
    @Test
    @Config(qualifiers = "ar-rEG-$TV_1080P")
    fun `mirrors in right-to-left languages`() {
        val list = start().list()

        val row = list.getChildAt(1)   // Music, hidden
        val label = row.findViewById<View>(R.id.settings_app_label)
        val hidden = row.findViewById<View>(R.id.settings_app_hidden)
        assertTrue("label at ${label.left}, hidden at ${hidden.right}", hidden.right <= label.left)
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
}
