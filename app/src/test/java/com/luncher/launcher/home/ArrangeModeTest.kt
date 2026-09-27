package com.luncher.launcher.home

import android.content.Intent
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.TextView
import com.luncher.domain.apps.AppArrangement
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.launcher.AppGraph
import com.luncher.launcher.LuncherApplication
import com.luncher.launcher.R
import com.luncher.launcher.TV_1080P
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
import java.time.Duration

/** Arrange mode, through the home screen: five apps a to e (one row of five), and x hidden. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-$TV_1080P")
class ArrangeModeTest {

    private val installedApps = FakeInstalledApps(listOf("a", "b", "c", "d", "e", "x").map { app(it) })
    private val arrangements = FakeAppArrangements(AppArrangement(order = null, hidden = listOf(app("x").launchable)))

    @Before
    fun useFakes() {
        val application = RuntimeEnvironment.getApplication() as LuncherApplication
        application.graph = object : AppGraph(application) {
            override val installedApps = this@ArrangeModeTest.installedApps
            override val appArrangements = this@ArrangeModeTest.arrangements
        }
    }

    private fun start(): ActivityController<HomeActivity> = Robolectric.buildActivity(HomeActivity::class.java).setup()

    private fun HomeActivity.tiles(): List<AppTileView> {
        val tiles = findViewById<AppTilesView>(R.id.home_apps)
        return (0 until tiles.childCount).map { tiles.getChildAt(it) as AppTileView }
    }

    /** The tiles in order, lower case, a hidden app's in brackets and the held one's in capitals. */
    private fun HomeActivity.state() = tiles().joinToString("") {
        val name = if (it.held) it.app.label.uppercase() else it.app.label.lowercase()
        if (it.hidden) "[$name]" else name
    }

    private fun HomeActivity.tile(name: String) = tiles().single { it.app.label.equals(name, ignoreCase = true) }

    private fun HomeActivity.hint() = findViewById<TextView>(R.id.home_arrange_hint)

    private fun HomeActivity.arranging() = findViewById<View>(R.id.home_arrange_title).visibility == View.VISIBLE

    private fun HomeActivity.press(keyCode: Int) {
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    /** Holds OK on the tile of [name] until it counts as a long press, then lets go. */
    private fun HomeActivity.longPress(name: String) {
        tile(name).requestFocus()
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 50L))
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test
    fun `a long press on a tile starts arranging, holding its app, with the hidden apps on a shelf`() {
        val activity = start().get()

        activity.longPress("b")

        assertTrue(activity.arranging())
        assertEquals("aBcde[x]", activity.state())
        assertEquals(View.GONE, activity.findViewById<View>(R.id.home_settings).visibility)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.home_clock).visibility)
        assertEquals(activity.getString(R.string.home_arrange_hint_holding), activity.hint().text.toString())
    }

    @Test
    fun `the long press opens nothing`() {
        val activity = start().get()

        activity.longPress("b")

        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `arrows move the held app`() {
        val activity = start().get()
        activity.longPress("b")

        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)

        assertEquals("acdBe[x]", activity.state())
        assertTrue(activity.tile("b").isFocused)
    }

    @Test
    fun `down from the last row hides the held app, and up shows it again`() {
        val activity = start().get()
        activity.longPress("b")

        activity.press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("acde[x][B]", activity.state())   // column 1 of the shelf

        activity.press(KeyEvent.KEYCODE_DPAD_UP)
        assertEquals("aBcde[x]", activity.state())
    }

    @Test
    fun `OK puts the app down and stores the arrangement`() {
        val activity = start().get()
        activity.longPress("b")
        activity.press(KeyEvent.KEYCODE_DPAD_RIGHT)

        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertEquals("acbde[x]", activity.state())
        assertEquals(activity.getString(R.string.home_arrange_hint_browsing), activity.hint().text.toString())
        assertEquals(listOf("a", "c", "b", "d", "e").map { app(it).launchable }, arrangements.arrangement.order)
        assertEquals(listOf(app("x").launchable), arrangements.arrangement.hidden)
    }

    @Test
    fun `putting an app down where it was stores nothing`() {
        val activity = start().get()
        activity.longPress("b")

        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertNull(arrangements.arrangement.order)
    }

    @Test
    fun `OK on the focused app picks it up, hidden ones too`() {
        val activity = start().get()
        activity.longPress("b")
        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        activity.tile("x").requestFocus()
        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertEquals("abcde[X]", activity.state())
        assertEquals(activity.getString(R.string.home_arrange_hint_holding), activity.hint().text.toString())
    }

    @Test
    fun `OK opens no app while arranging`() {
        val activity = start().get()
        activity.longPress("b")
        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        activity.press(KeyEvent.KEYCODE_DPAD_CENTER)

        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `the Menu key opens no settings while arranging`() {
        val activity = start().get()
        activity.longPress("b")

        activity.press(KeyEvent.KEYCODE_MENU)

        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `Back puts the app down, stores it and ends arranging`() {
        val activity = start().get()
        activity.longPress("b")
        activity.press(KeyEvent.KEYCODE_DPAD_DOWN)

        @Suppress("DEPRECATION")
        activity.onBackPressed()

        assertFalse(activity.arranging())
        assertEquals("acde", activity.state())
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.home_settings).visibility)
        assertEquals(listOf("x", "b").map { app(it).launchable }, arrangements.arrangement.hidden)
    }

    @Test
    fun `focus on a hidden app goes to the last shown one when arranging ends`() {
        val activity = start().get()
        activity.longPress("b")
        activity.press(KeyEvent.KEYCODE_DPAD_DOWN)

        @Suppress("DEPRECATION")
        activity.onBackPressed()

        assertTrue(activity.tile("e").isFocused)
    }

    @Test
    fun `focus goes to the settings entry when arranging ends with every app hidden`() {
        installedApps.apps = listOf(app("a"), app("x"))
        val activity = start().get()
        activity.longPress("a")
        activity.press(KeyEvent.KEYCODE_DPAD_DOWN)

        @Suppress("DEPRECATION")
        activity.onBackPressed()

        assertEquals("", activity.state())
        assertTrue(activity.findViewById<View>(R.id.home_settings).isFocused)
        assertEquals(activity.getString(R.string.home_all_hidden), activity.findViewById<TextView>(R.id.home_empty).text.toString())
    }

    @Test
    fun `Home ends arranging`() {
        val controller = start()
        controller.get().longPress("b")
        controller.get().press(KeyEvent.KEYCODE_DPAD_RIGHT)

        controller.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

        assertFalse(controller.get().arranging())
        assertEquals("acbde", controller.get().state())
        assertEquals(listOf("a", "c", "b", "d", "e").map { app(it).launchable }, arrangements.arrangement.order)
    }

    @Test
    fun `leaving the home screen ends arranging`() {
        val controller = start()
        controller.get().longPress("b")

        controller.pause().stop()

        assertFalse(controller.get().arranging())
        assertEquals("abcde", controller.get().state())
    }

    @Test
    fun `apps aren't read again while arranging`() {
        val controller = start()
        controller.get().longPress("b")

        controller.pause()                      // e.g. a system dialog...
        installedApps.apps += app("f")          // ...while an app was installed
        controller.resume()

        assertEquals("aBcde[x]", controller.get().state())
    }

    @Test
    fun `the new order stays after arranging`() {
        val controller = start()
        controller.get().longPress("e")
        controller.get().press(KeyEvent.KEYCODE_DPAD_LEFT)
        @Suppress("DEPRECATION")
        controller.get().onBackPressed()

        controller.pause().stop()
        installedApps.apps += app("f")          // installed since: after the user's order
        controller.restart().start().resume()

        assertEquals("abcedf", controller.get().state())
    }
}
