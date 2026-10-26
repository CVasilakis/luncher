package com.luncher.launcher.home

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Canvas
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
import com.luncher.domain.apps.FakePreparedTiles
import com.luncher.domain.apps.HomeApp
import com.luncher.domain.apps.PreparedTiles
import com.luncher.domain.apps.TileDrawing
import com.luncher.domain.apps.TileFocusShape
import com.luncher.domain.apps.TileImage
import com.luncher.domain.apps.TilePreparing
import com.luncher.domain.jobs.ManualJobs
import com.luncher.domain.layout.TileSize
import com.luncher.domain.pictures.FakePictureImages
import com.luncher.domain.pictures.FakePreparedPictures
import com.luncher.domain.pictures.Fit
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PreparedPictures
import com.luncher.launcher.apps.BannerImages
import com.luncher.launcher.apps.FilePreparedTiles
import com.luncher.launcher.pictures.WorkingCopyImages
import com.luncher.launcher.testing.TV_1080P
import com.luncher.launcher.ui.WorkingDialog
import com.luncher.launcher.ui.homeTileSize
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
 * The pictures of the library on tiles, prepared ahead and only read here: where one isn't
 * prepared for its tile, the home screen prepares it, in jobs, behind the window it shows for the
 * wallpaper; the tiles show their apps' own images meanwhile, and each is drawn again with its
 * picture once it's ready.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // to make and decode the pictures' copies, and draw the tiles
@Config(qualifiers = "en-rUS-$TV_1080P")
class HomeTileImagesTest {

    private val application = RuntimeEnvironment.getApplication()
    private val beach = PictureId("beach")
    private val news = app("news").launchable

    /** News's tile shows the beach, stretched; Movies and Music show their own images. */
    private val customizations = FakeAppCustomizations(mapOf(news to AppCustomization(image = TileImage(beach, Framing(Fit.STRETCH)))))
    private val appearance = FakeAppearanceSettings(Appearance(topLeftTiles))
    private val arrangements = FakeAppArrangements()
    private val jobs = ManualJobs()

    /** The fakes: every copy prepared, no tile kept, a tile read back all red; slow work in a job only, or they fail. */
    private val fakeCopies = FakePreparedPictures()
    private val fakeTiles = FakePreparedTiles(fakeCopies, jobs) { red(it.size) }

    /** What the tiles' pictures are prepared with: the fakes unless a test gives the real copies and files ([realCopies]). */
    private var copies: PreparedPictures = fakeCopies
    private var kept: PreparedTiles<Bitmap> = fakeTiles

    /** Every app whose own image a tile was drawn with, in order. */
    private val drawn = ArrayList<HomeApp>()

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
            override val preparedPictures get() = this@HomeTileImagesTest.copies
            override val preparedTiles get() = this@HomeTileImagesTest.kept
            override val appImages = object : AppImages<Bitmap> {
                private val images = BannerImages(application)

                override fun render(app: HomeApp, banner: Banner.Own, width: Int, height: Int): Bitmap =
                    images.render(app, banner, width, height).also { drawn += app }
            }
        }
    }

    private fun red(size: TileSize) = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }

    /** What News's tile is drawn from, at the size the home screen's tiles have now. */
    private fun newsDrawing() = TileDrawing(beach, Framing(Fit.STRETCH), TileFocusShape.TILE, application.homeTileSize(topLeftTiles))

    /**
     * The beach in the library's folder, all red, with no working copy yet, and the real copies and
     * files of the tiles' pictures, which prepare it.
     */
    private fun realCopies(): WorkingCopyImages {
        val originals = File(application.filesDir, "pictures").apply { mkdirs() }
        File(originals, beach.value).outputStream().use { red(TileSize(800, 450)).compress(Bitmap.CompressFormat.PNG, 100, it) }
        kept = FilePreparedTiles(application)
        return WorkingCopyImages(application).also { copies = it }
    }

    private fun start(): ActivityController<HomeActivity> = Robolectric.buildActivity(HomeActivity::class.java).setup()

    /** The next job run, then what it posted to the main thread. */
    private fun runNextJob() {
        jobs.runNext()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Every job, and those their results on the main thread queue, until none is left. */
    private fun runJobs() {
        while (jobs.pending > 0) {
            jobs.runAll()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    /** The window that says the home screen waits, if it shows. */
    private fun working(): Dialog? = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }

    private fun Dialog.message() = findViewById<TextView>(UiR.id.working_message).text.toString()

    private fun after(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private fun HomeActivity.tiles(): List<AppTileView> {
        val tiles = findViewById<AppTilesView>(R.id.home_apps)
        return (0 until tiles.childCount).map { tiles.getChildAt(it) as AppTileView }
    }

    /** What News's tile shows now, drawn: its picture, red, or its own card. */
    private fun HomeActivity.newsShows(): String {
        val tile = tiles().single { it.app.name == "News" }
        val image = Bitmap.createBitmap(tile.width, tile.height, Bitmap.Config.ARGB_8888)
        tile.draw(Canvas(image))
        val middle = image.getPixel(image.width / 2, image.height / 2)
        return if (Color.red(middle) > 200 && Color.green(middle) < 40 && Color.blue(middle) < 40) "picture" else "own"
    }

    @Test
    fun `with every tile's picture prepared, nothing is prepared and no window shows`() {
        fakeTiles.kept[news] = newsDrawing()

        val activity = start().get()

        assertNull(working())
        assertEquals("picture", activity.newsShows())
        runJobs()
        assertEquals(emptyList<Any>(), fakeTiles.prepared)
    }

    @Test
    fun `prepares a tile's picture that isn't, in a job, behind a window that says the tile banners are updated`() {
        // The fake fails if it's asked to prepare on the main thread, outside a job.
        start()

        assertEquals("Updating the tile banners…", working()!!.message())
        assertEquals("nothing before the jobs run", emptyList<Any>(), fakeTiles.prepared)
        runJobs()
        assertEquals(listOf(news to newsDrawing()), fakeTiles.prepared)
        assertTrue("not closed before its time", working() != null)
        after(WorkingDialog.SHORTEST)
        assertNull(working())
    }

    @Test
    fun `a see-through picture's tile is drawn over the background's color, as every picture's was`() {
        val clear = FakePreparedTiles(fakeCopies, jobs) { Bitmap.createBitmap(it.size.width, it.size.height, Bitmap.Config.ARGB_8888) }
        clear.seeThrough += beach
        clear.kept[news] = newsDrawing()
        kept = clear

        val activity = start().get()
        val tile = activity.tiles().single { it.app.name == "News" }
        val image = Bitmap.createBitmap(tile.width, tile.height, Bitmap.Config.ARGB_8888)
        tile.draw(Canvas(image))

        assertEquals(activity.getColor(UiR.color.background), image.getPixel(image.width / 2, image.height / 2))
    }

    @Test
    fun `tiles of another size, from the appearance, have their pictures prepared for it while shown`() {
        fakeTiles.kept[news] = newsDrawing()
        val activity = start().get()
        assertNull(working())

        val wider = topLeftTiles.copy(tileWidthDp = 200)
        appearance.write(Appearance(wider))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Fitting the tile banners to this screen…", working()!!.message())
        runJobs()
        assertEquals(application.homeTileSize(wider), fakeTiles.prepared.single().second.size)
        assertEquals("picture", activity.newsShows())
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
    fun `the tile shows its app's own image meanwhile, and its picture once it's ready, nothing else drawn again`() {
        realCopies()
        val activity = start().get()
        assertEquals("own", activity.newsShows())
        val drawnBefore = drawn.size

        runJobs()

        assertEquals("picture", activity.newsShows())
        assertEquals("no tile's own image drawn again", drawnBefore, drawn.size)
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

        assertEquals("picture", controller.get().newsShows())
        assertNull(working())
    }

    @Test
    fun `asks nothing again on a return to the home screen with the same pictures`() {
        fakeTiles.kept[news] = newsDrawing()
        val controller = start()
        val asked = fakeTiles.asked to fakeCopies.asked

        controller.pause().stop().restart().resume()

        assertEquals(asked, fakeTiles.asked to fakeCopies.asked)
    }

    @Test
    fun `a hidden app's picture is prepared too, so showing it again needs no wait`() {
        arrangements.arrangement = AppArrangement(order = null, hidden = listOf(news))

        start()

        assertEquals("Updating the tile banners…", working()!!.message())
        runJobs()
        assertEquals(listOf(news), fakeTiles.prepared.map { it.first })
    }

    // The TV switched resolution: Android makes the home screen again, at the new size.
    @Test
    fun `a screen of another size prepares the tiles' pictures for it, saying so, then draws them`() {
        realCopies().prepare(beach) { false }
        assertEquals(TilePreparing.DONE, kept.prepare(news, newsDrawing()))
        val controller = start()
        assertNull(working())
        assertEquals("picture", controller.get().newsShows())

        RuntimeEnvironment.setQualifiers("+tvdpi")   // 960x540 dp at 720p
        controller.configurationChange().visible()   // Robolectric shows the new window only when told

        assertEquals("Fitting the tile banners to this screen…", working()!!.message())
        assertEquals("its own image meanwhile", "own", controller.get().newsShows())
        runJobs()
        assertEquals("picture", controller.get().newsShows())
    }

    @Test
    fun `a copy found damaged as its tile is prepared is made again at once, and the tile drawn with it`() {
        realCopies().prepare(beach) { false }
        val copy = File(application.filesDir, "picture-copies/${beach.value}")
        copy.writeBytes(copy.readBytes().copyOf(24) + ByteArray(500) { 7 })   // its header, then no picture

        val activity = start().get()
        assertEquals("Updating the tile banners…", working()!!.message())
        runJobs()   // the tile's, which finds the copy damaged; the copy's; the tile's again

        assertEquals("picture", activity.newsShows())
        after(WorkingDialog.SHORTEST)
        assertNull(working())
    }

    @Test
    fun `a tile's picture found damaged as it's read is prepared again at once, behind the window, and drawn with it`() {
        realCopies().prepare(beach) { false }
        kept.prepare(news, newsDrawing())
        val file = File(application.filesDir, "tile-pictures/${news.packageName}/${news.activityName}")
        file.writeBytes(file.readBytes().let { it.copyOf(it.size / 2) })

        val activity = start().get()
        assertEquals("Updating the tile banners…", working()!!.message())
        assertEquals("own", activity.newsShows())
        runJobs()

        assertEquals("picture", activity.newsShows())
    }

    @Test
    fun `a tile saved in the picker and still being prepared as the home screen comes back is waited for, saying so`() {
        val graph = (application as HomeTestApplication).graph
        var done = false
        assertTrue(graph.preparedTileImages.save(news, newsDrawing()) { done = true })

        val activity = start().get()

        assertEquals("Processing tile banner…", working()!!.message())
        runJobs()
        assertTrue(done)
        assertEquals("prepared once", listOf(news), fakeTiles.prepared.map { it.first })
        assertEquals("picture", activity.newsShows())
        after(WorkingDialog.SHORTEST)
        assertNull(working())
    }
}
