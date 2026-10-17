package com.luncher.launcher.home

import android.app.Dialog
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.widget.TextView
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Background
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.jobs.ManualJobs
import com.luncher.domain.pictures.FakePictureImages
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureStyle
import com.luncher.domain.wallpaper.FakeStoredWallpaper
import com.luncher.domain.wallpaper.WallpaperDrawing
import com.luncher.launcher.testing.TV_1080P
import com.luncher.launcher.ui.WorkingDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
import java.time.Duration
import com.luncher.launcher.ui.R as UiR

/** The home screen's wallpaper: the background's picture as its window's background, kept, or drawn while a window says so. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-$TV_1080P")
class HomeWallpaperTest {

    private val sunset = PictureId("sunset")
    private val appearance = FakeAppearanceSettings(Appearance(topLeftTiles, background = Background(picture = sunset, dim = 20)))
    private val drawing = WallpaperDrawing(sunset, PictureStyle(dim = 20), 1920, 1080)
    private val images = FakePictureImages(::wallpaper)
    private val stored = FakeStoredWallpaper<android.graphics.Bitmap>()
    private val jobs = ManualJobs()

    @Before
    fun useFakes() {
        val application = RuntimeEnvironment.getApplication() as HomeTestApplication
        application.graph = object : TestHomeGraph(application) {
            override val appearanceSettings = this@HomeWallpaperTest.appearance
            override val pictureImages = this@HomeWallpaperTest.images
            override val storedWallpaper = this@HomeWallpaperTest.stored
            override val jobs = this@HomeWallpaperTest.jobs
        }
    }

    private fun start(): ActivityController<HomeActivity> = Robolectric.buildActivity(HomeActivity::class.java).setup()

    private fun HomeActivity.background() = window.decorView.background

    /** The background's jobs run, and their results shown. */
    private fun drawInTheBackground() {
        jobs.runAll()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** The window that says the wallpaper is being drawn, if it shows. */
    private fun working(): Dialog? = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }

    private fun Dialog.message() = findViewById<TextView>(UiR.id.working_message).text.toString()

    private fun after(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    @Test
    fun `shows the wallpaper kept at once, read on the main thread, with no window over it`() {
        val kept = wallpaper(sunset, 1920, 1080, drawing.style)
        stored.kept = drawing to kept

        val activity = start().get()

        assertSame(kept, (activity.background() as BitmapDrawable).bitmap)
        assertEquals(0, jobs.pending)
        assertNull(working())
    }

    @Test
    fun `draws one not kept at the screen's size, saying so over the plain color, then shows it`() {
        val activity = start().get()
        assertTrue(activity.background() is ColorDrawable)
        assertEquals("Updating the background…", working()!!.message())

        drawInTheBackground()

        val wallpaper = (activity.background() as BitmapDrawable).bitmap
        assertEquals(1920 to 1080, wallpaper.width to wallpaper.height)
        assertEquals(listOf(sunset to PictureStyle(dim = 20)), images.rendered)
        assertEquals(drawing, stored.kept?.first)
    }

    @Test
    fun `the window shows for half a second at least, then closes`() {
        start()
        drawInTheBackground()
        assertTrue("not closed before its time", working() != null)

        after(WorkingDialog.SHORTEST)

        assertNull(working())
    }

    @Test
    fun `says the screen changed when the one kept is for a screen of another size`() {
        stored.kept = drawing.copy(width = 1280, height = 720) to wallpaper(sunset, 1280, 720, drawing.style)

        start()

        assertEquals("Fitting the background to this screen…", working()!!.message())
    }

    @Test
    fun `the window takes every key, Back too`() {
        start()
        val working = working()!!

        @Suppress("DEPRECATION")   // what Back does to a dialog without predictive Back
        working.onBackPressed()

        assertTrue(working.isShowing)
        assertFalse(shadowOf(working).isCancelable)
    }

    @Test
    fun `without a picture, or with one that's gone, shows the plain color`() {
        appearance.appearance = Appearance(topLeftTiles)
        assertTrue(start().get().background() is ColorDrawable)
        assertNull(working())

        appearance.appearance = Appearance(topLeftTiles, background = Background(picture = PictureId("deleted")))
        images.missing += PictureId("deleted")
        val activity = start().get()
        drawInTheBackground()
        after(WorkingDialog.SHORTEST)
        assertTrue(activity.background() is ColorDrawable)
        assertNull(working())
    }

    @Test
    fun `follows the background while shown, and draws nothing for an unchanged one`() {
        val activity = start().get()
        drawInTheBackground()

        appearance.write(appearance.appearance.copy(background = appearance.appearance.background.copy(dim = 40)))
        drawInTheBackground()
        appearance.write(appearance.appearance.copy(tileGeometry = topLeftTiles.copy(fullBottomRow = true)))
        drawInTheBackground()

        assertEquals("the tiles' change draws no wallpaper", listOf(20, 40), images.rendered.map { it.second.dim })
        assertTrue(activity.background() is BitmapDrawable)
        appearance.write(appearance.appearance.copy(background = Background()))
        assertTrue(activity.background() is ColorDrawable)
    }

    @Test
    fun `a drawing goes on while the home screen is left, and shows when it comes back`() {
        val controller = start()
        controller.pause().stop()
        assertNull("its window closes with the screen", working())

        drawInTheBackground()
        controller.restart().start().resume()

        assertTrue(controller.get().background() is BitmapDrawable)
        assertNull(working())
        assertEquals(1, images.rendered.size)
    }
}
