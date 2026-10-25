package com.luncher.launcher.home

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import android.widget.TextView
import com.luncher.domain.appearance.Appearance
import com.luncher.domain.appearance.Background
import com.luncher.domain.appearance.FakeAppearanceSettings
import com.luncher.domain.apps.AppArrangement
import com.luncher.domain.apps.AppCustomization
import com.luncher.domain.apps.AppImages
import com.luncher.domain.apps.Banner
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeAppCustomizations
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.apps.HomeApp
import com.luncher.domain.apps.TileImage
import com.luncher.domain.jobs.ManualJobs
import com.luncher.domain.pictures.FakePictureImages
import com.luncher.domain.pictures.FakePreparedPictures
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PreparedPictures
import com.luncher.launcher.apps.BannerImages
import com.luncher.launcher.pictures.WorkingCopyImages
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.time.Duration
import com.luncher.launcher.ui.R as UiR

/**
 * The pictures of the library on tiles, prepared by the home screen where they aren't for this
 * screen, behind the window it shows for the wallpaper: the tiles show their apps' own images
 * meanwhile, and each is drawn again with its picture once it's ready.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // to make and decode the pictures' copies
@Config(qualifiers = "en-rUS-$TV_1080P")
class HomeTileImagesTest {

    private val application = RuntimeEnvironment.getApplication()
    private val beach = PictureId("beach")

    /** News's tile shows the beach, stretched; Movies and Music show their own images. */
    private val customizations = FakeAppCustomizations(mapOf(app("news").launchable to AppCustomization(image = TileImage(beach, Framing(Fit.STRETCH)))))
    private val appearance = FakeAppearanceSettings(Appearance(topLeftTiles))
    private val arrangements = FakeAppArrangements()
    private val jobs = ManualJobs()

    /** What the tiles' pictures are prepared with: the fake unless a test gives the real copies. */
    private var prepared: PreparedPictures = FakePreparedPictures(beach)

    /** Every image the tiles were drawn with, for which app, in order. */
    private val drawn = ArrayList<Pair<HomeApp, Bitmap>>()

    @Before
    fun useFakes() {
        val application = application as HomeTestApplication
        application.graph = object : TestHomeGraph(application) {
            override val installedApps = FakeInstalledApps(app("news"), app("movies"), app("music"))
            override val appCustomizations = this@HomeTileImagesTest.customizations
            override val appArrangements = this@HomeTileImagesTest.arrangements
            override val appearanceSettings = this@HomeTileImagesTest.appearance
            override val pictureImages = FakePictureImages(::wallpaper)
            override val jobs = this@HomeTileImagesTest.jobs
            override val preparedPictures get() = this@HomeTileImagesTest.prepared
            override val appImages = object : AppImages<Bitmap> {
                private val images = BannerImages(application, pictureTiles)

                override fun render(app: HomeApp, banner: Banner, width: Int, height: Int): Bitmap =
                    images.render(app, banner, width, height).also { drawn += app to it }
            }
        }
    }

    /** The beach in the library's folder, all red, with no working copy yet: the real copies prepare it. */
    private fun realCopies(): WorkingCopyImages {
        val originals = File(application.filesDir, "pictures").apply { mkdirs() }
        val red = Bitmap.createBitmap(800, 450, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        File(originals, beach.value).outputStream().use { red.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return WorkingCopyImages(application).also { prepared = it }
    }

    private fun start(): ActivityController<HomeActivity> = Robolectric.buildActivity(HomeActivity::class.java).setup()

    /** The next job run, then what it posted to the main thread. */
    private fun runNextJob() {
        jobs.runNext()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun runJobs() {
        jobs.runAll()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** The window that says the home screen waits, if it shows. */
    private fun working(): Dialog? = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }

    private fun Dialog.message() = findViewById<TextView>(UiR.id.working_message).text.toString()

    private fun after(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    /** The images News's tile was drawn with, in order: its picture, red, or its own card. */
    private fun newsDrawn(): List<String> = drawn.filter { it.first.name == "News" }.map { (_, image) ->
        val middle = image.getPixel(image.width / 2, image.height / 2)
        if (Color.red(middle) > 200 && Color.green(middle) < 40 && Color.blue(middle) < 40) "picture" else "own"
    }

    @Test
    fun `with every tile's picture prepared, nothing is prepared and no window shows`() {
        prepared = FakePreparedPictures()

        start()

        assertNull(working())
        runJobs()
        assertEquals(emptyList<PictureId>(), (prepared as FakePreparedPictures).prepared)
    }

    @Test
    fun `prepares a tile's picture that isn't, behind a window that says the tile banners are updated`() {
        start()

        assertEquals("Updating the tile banners…", working()!!.message())
        runJobs()
        assertEquals(listOf(beach), (prepared as FakePreparedPictures).prepared)
        assertTrue("not closed before its time", working() != null)
        after(WorkingDialog.SHORTEST)
        assertNull(working())
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
    fun `the tile shows its app's own image meanwhile, and is drawn again with its picture once it's ready, the others not`() {
        realCopies()
        val activity = start().get()
        assertEquals(listOf("own"), newsDrawn())
        val drawnBefore = drawn.size

        runJobs()

        assertEquals(listOf("own", "picture"), newsDrawn())
        assertEquals("News's tile alone drawn again", listOf("News"), drawn.drop(drawnBefore).map { it.first.name })
        assertTrue(activity.tiles().first().isFocused)   // Movies, focused at the start, still is
    }

    @Test
    fun `with the wallpaper drawn too, one window says the background first, then the tile banners, and closes after both`() {
        appearance.appearance = Appearance(topLeftTiles, background = Background(picture = PictureId("sunset")))
        start()
        val window = working()!!
        assertEquals("Updating the background…", window.message())

        runNextJob()   // the wallpaper's, first
        assertSame("the same window, still showing", window, working())
        assertEquals("Updating the tile banners…", window.message())
        after(WorkingDialog.SHORTEST)
        assertSame("open while the tiles' pictures are prepared", window, working())

        runJobs()
        after(WorkingDialog.SHORTEST)
        assertNull(working())
        assertEquals("one window, never closed and opened again", 1, ShadowDialog.getShownDialogs().size)
    }

    @Test
    fun `a picture prepared while the home screen was left is on its tile as it comes back, with no window`() {
        realCopies()
        val controller = start()
        controller.pause().stop()
        assertNull("its window closes with the screen", working())

        runJobs()
        controller.restart().resume()

        assertEquals(listOf("own", "picture"), newsDrawn())
        assertNull(working())
    }

    @Test
    fun `asks nothing again on a return to the home screen with the same pictures`() {
        prepared = FakePreparedPictures()
        val controller = start()
        val asked = (prepared as FakePreparedPictures).asked

        controller.pause().stop().restart().resume()

        assertEquals(asked, (prepared as FakePreparedPictures).asked)
    }

    @Test
    fun `a hidden app's picture is prepared too, so showing it again needs no wait`() {
        arrangements.arrangement = AppArrangement(order = null, hidden = listOf(app("news").launchable))

        start()

        assertEquals("Updating the tile banners…", working()!!.message())
    }

    // The TV switched resolution: Android makes the home screen again, at the new size.
    @Test
    fun `a screen of another size prepares the tiles' pictures for it, saying so, then draws them`() {
        realCopies().prepare(beach) { false }
        val controller = start()
        assertNull(working())
        drawn.clear()

        RuntimeEnvironment.setQualifiers("+tvdpi")   // 960x540 dp at 720p
        controller.configurationChange().visible()   // Robolectric shows the new window only when told

        assertEquals("Fitting the tile banners to this screen…", working()!!.message())
        assertEquals("its own image meanwhile", listOf("own"), newsDrawn())
        runJobs()
        assertEquals(listOf("own", "picture"), newsDrawn())
    }

    @Test
    fun `a copy found damaged as its tile is drawn is made again at once, and the tile drawn with it`() {
        realCopies().prepare(beach) { false }
        val copy = File(application.filesDir, "picture-copies/${beach.value}")
        copy.writeBytes(copy.readBytes().copyOf(20) + ByteArray(500) { 7 })   // its header, then no picture

        start()
        assertNull("nothing known before it's deleted", working())
        runJobs()   // deleted, then the home screen told

        assertEquals("Updating the tile banners…", working()!!.message())
        runJobs()
        assertEquals(listOf("own", "picture"), newsDrawn())
    }

    private fun HomeActivity.tiles(): List<AppTileView> {
        val tiles = findViewById<AppTilesView>(R.id.home_apps)
        return (0 until tiles.childCount).map { tiles.getChildAt(it) as AppTileView }
    }
}
