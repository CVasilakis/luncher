package com.luncher.launcher.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.launcher.testing.TV_1080P
import com.luncher.launcher.ui.WallpaperOutline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the home screen shows straight on the wallpaper has [WallpaperOutline]'s edge, so it reads on a
 * white picture: each item of the top bar, whatever it is (a status indicator added later too),
 * and the message without apps. Each is drawn alone on white, where only an outline is dark. The
 * shelf, drawn by the tiles' view itself, is in the screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // real drawing; without it a bitmap stays as it was
@Config(qualifiers = TV_1080P)
class HomeOutlineTest {

    private val home = run {
        val application = RuntimeEnvironment.getApplication() as HomeTestApplication
        application.graph = object : TestHomeGraph(application) {
            override val installedApps = FakeInstalledApps(app("news"))
        }
        Robolectric.buildActivity(HomeActivity::class.java).setup().get()
    }

    @Test
    fun `every item of the top bar has an outline`() {
        // Arranging, so arrange mode's key hint has its text; the clock keeps its own.
        home.findViewById<ViewGroup>(R.id.home_apps).getChildAt(0).performLongClick()
        val items = leaves(home.findViewById(R.id.home_top_bar))
        // The time, the date, arrange mode's title and hint, the settings gear.
        assertEquals(5, items.size)
        for (item in items) assertTrue("${name(item)} has no outline", hasOutline(item))
    }

    @Test
    fun `the message without apps has an outline`() {
        assertTrue(hasOutline(home.findViewById(R.id.home_empty)))
    }

    private fun leaves(view: View): List<View> =
        if (view is ViewGroup) (0 until view.childCount).flatMap { leaves(view.getChildAt(it)) } else listOf(view)

    private fun name(view: View) = view.resources.getResourceEntryName(view.id)

    private fun hasOutline(view: View): Boolean {
        // A hidden item (arrange mode's, outside it) is laid out alone, at the size it takes.
        if (view.width == 0) {
            val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            view.measure(unspecified, unspecified)
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.WHITE)
        view.draw(Canvas(image))
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        return pixels.any { Color.red(it) + Color.green(it) + Color.blue(it) < 3 * 128 }
    }
}
