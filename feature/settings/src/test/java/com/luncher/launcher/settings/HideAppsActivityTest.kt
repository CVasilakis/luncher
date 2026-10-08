package com.luncher.launcher.settings

import android.view.KeyEvent
import android.view.View
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
import org.robolectric.annotation.Config

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

    @Test
    fun `shows six and a half rows at most, so the half row says there's more`() {
        installedApps.apps = List(12) { app("app%02d".format(it)) }

        val activity = start()

        val row = activity.resources.getDimensionPixelSize(R.dimen.settings_entry_height)
        assertEquals(row * 13 / 2, activity.list().layoutParams.height)
    }

    @Test
    fun `is only as tall as its rows when they fit`() {
        val activity = start()

        val row = activity.resources.getDimensionPixelSize(R.dimen.settings_entry_height)
        assertEquals(row * 3, activity.list().layoutParams.height)
    }

    // A 720p screen at the 1080p density: 360 dp tall, too little for six and a half rows.
    @Test
    @Config(qualifiers = "en-rUS-w640dp-h360dp-land-television-xhdpi-notouch-dpad")
    fun `on a small screen the panel stays within it, still ending on half a row`() {
        installedApps.apps = List(12) { app("app%02d".format(it)) }

        val activity = start()

        val row = activity.resources.getDimensionPixelSize(R.dimen.settings_entry_height)
        val margin = activity.resources.getDimensionPixelSize(R.dimen.settings_panel_margin)
        val panel = activity.findViewById<View>(R.id.settings_hide_apps_panel)
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        panel.forceLayout()   // measured already with the same specs, before the list had its height
        activity.list().forceLayout()
        panel.measure(unspecified, unspecified)
        assertTrue(panel.measuredHeight <= activity.resources.displayMetrics.heightPixels - 2 * margin)
        assertEquals(row / 2, activity.list().layoutParams.height % row)
        assertTrue(activity.list().layoutParams.height > row)
    }

    // Unlike the home screen (HomeActivityTest), which stays left to right.
    @Test
    @Config(qualifiers = "ar-rEG-ldrtl-$TV_1080P")
    fun `mirrors in right-to-left languages, the scrollbar's strip on the left`() {
        val list = start().list()

        val row = list.getChildAt(1)   // Music, hidden
        val label = row.findViewById<View>(R.id.settings_app_label)
        val hidden = row.findViewById<View>(R.id.settings_app_hidden)
        assertTrue("label at ${label.left}, hidden at ${hidden.right}", hidden.right <= label.left)
        val text = (label as TextView).layout
        assertEquals(text.width.toFloat(), text.getLineRight(0))   // an English name at the right too
        assertEquals(list.resources.getDimensionPixelSize(R.dimen.settings_list_scrollbar), list.paddingLeft)
        assertEquals(0, list.paddingRight)
    }

    @Test
    fun `says so when there are no apps`() {
        installedApps.apps = emptyList()

        val activity = start()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.settings_no_apps).visibility)
    }
}
