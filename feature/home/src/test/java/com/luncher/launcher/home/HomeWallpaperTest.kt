package com.luncher.launcher.home

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Background
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.jobs.ManualJobs
import com.luncher.domain.pictures.FakePictureImages
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureStyle
import com.luncher.launcher.testing.TV_1080P
import org.junit.Assert.assertEquals
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

/** The home screen's wallpaper: the background's picture as its window's background. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-$TV_1080P")
class HomeWallpaperTest {

    private val sunset = PictureId("sunset")
    private val appearance = FakeAppearanceSettings(Appearance(topLeftTiles, background = Background(picture = sunset, dim = 20)))
    private val images = FakePictureImages(::wallpaper)
    private val jobs = ManualJobs()

    @Before
    fun useFakes() {
        val application = RuntimeEnvironment.getApplication() as HomeTestApplication
        application.graph = object : TestHomeGraph(application) {
            override val appearanceSettings = this@HomeWallpaperTest.appearance
            override val pictureImages = this@HomeWallpaperTest.images
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

    @Test
    fun `shows a wallpaper drawn before at once, read on the main thread, at the screen's size`() {
        val kept = wallpaper(sunset, 1920, 1080, appearance.appearance.background.style)
        images.cache[sunset to appearance.appearance.background.style] = kept

        val activity = start().get()

        assertSame(kept, (activity.background() as BitmapDrawable).bitmap)
        assertEquals(0, jobs.pending)
    }

    @Test
    fun `draws one it doesn't have in the background, the plain color meanwhile`() {
        val activity = start().get()
        assertTrue(activity.background() is ColorDrawable)

        drawInTheBackground()

        val wallpaper = (activity.background() as BitmapDrawable).bitmap
        assertEquals(1920 to 1080, wallpaper.width to wallpaper.height)
        assertEquals(listOf(sunset to PictureStyle(dim = 20)), images.rendered)
    }

    @Test
    fun `without a picture, or one that's gone, shows the plain color`() {
        appearance.appearance = Appearance(topLeftTiles)
        assertTrue(start().get().background() is ColorDrawable)

        appearance.appearance = Appearance(topLeftTiles, background = Background(picture = PictureId("deleted")))
        images.missing += PictureId("deleted")
        val activity = start().get()
        drawInTheBackground()
        assertTrue(activity.background() is ColorDrawable)
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
    fun `a wallpaper still being drawn when the home screen is left is drawn when it comes back`() {
        val controller = start()
        controller.pause().stop()
        assertEquals("dropped", 0, jobs.pending)

        controller.restart().start().resume()
        drawInTheBackground()

        assertTrue(controller.get().background() is BitmapDrawable)
    }
}
