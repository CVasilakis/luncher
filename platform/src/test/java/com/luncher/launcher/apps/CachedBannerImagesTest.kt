package com.luncher.launcher.apps

import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import com.luncher.domain.apps.AppImages
import com.luncher.domain.apps.Banner
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.jobs.ManualJobs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // to read pixels back
class CachedBannerImagesTest {

    private val context = RuntimeEnvironment.getApplication()
    private val jobs = ManualJobs()

    /** Draws each image in a color of its own, half transparent, and counts what it draws. */
    private class CountingImages : AppImages<Bitmap> {
        var drawn = 0

        override fun render(app: InstalledApp, banner: Banner, width: Int, height: Int): Bitmap {
            drawn++
            val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            image.eraseColor(Color.argb(128, drawn * 40 % 256, 200, if (banner == Banner.AppBanner) 50 else 150))
            image.setPixel(0, 0, Color.argb(10, 255, 255, 255))   // nearly transparent
            return image
        }
    }

    private var images = CountingImages()
    private var cache = CachedBannerImages(context, images, jobs)

    /** As the next cold start: a new process, with what the files hold. */
    private fun restart() {
        images = CountingImages()
        cache = CachedBannerImages(context, images, jobs)
    }

    /**
     * The main thread has nothing left to do, as once the home screen is drawn. Robolectric calls
     * the idle handlers only after a message, as the main thread has one at least (the frame).
     */
    private fun idle() {
        Handler(Looper.getMainLooper()).post {}
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Everything queued gets written: the main thread idle, then the jobs run. */
    private fun writeEverything() {
        idle()
        jobs.runAll()
    }

    /** [appName], installed, so that the cache keeps its file. */
    private fun installed(appName: String, updated: Long = 0, label: String? = null): InstalledApp {
        val app = app(appName, hasBanner = true, updated = updated).let { if (label == null) it else it.copy(label = label) }
        val application = ApplicationInfo().apply { packageName = app.launchable.packageName }
        val shadow = shadowOf(context.packageManager)
        shadow.installPackage(PackageInfo().apply { packageName = app.launchable.packageName; applicationInfo = application })
        shadow.addOrUpdateActivity(
            ActivityInfo().apply {
                packageName = app.launchable.packageName
                name = app.launchable.activityName
                applicationInfo = application
            },
        )
        return app
    }

    private fun fileOf(app: InstalledApp) =
        File(context.cacheDir, "banners/${app.launchable.packageName}/${app.launchable.activityName}")

    @Test
    fun `draws an image it doesn't have`() {
        val movies = installed("movies")

        cache.render(movies, Banner.AppBanner, 300, 169)

        assertEquals(1, images.drawn)
    }

    @Test
    fun `writes only once the main thread is idle`() {
        val movies = installed("movies")
        cache.render(movies, Banner.AppBanner, 300, 169)
        assertEquals(0, jobs.pending)

        idle()
        assertTrue(jobs.pending > 0)
        assertFalse(fileOf(movies).exists())

        jobs.runAll()
        assertTrue(fileOf(movies).exists())
    }

    @Test
    fun `reads the image back on the next start, exactly as drawn, transparency included`() {
        val movies = installed("movies")
        val drawn = cache.render(movies, Banner.AppBanner, 300, 169)
        writeEverything()
        restart()

        val read = cache.render(movies, Banner.AppBanner, 300, 169)

        assertEquals(0, images.drawn)
        assertTrue(read.sameAs(drawn))
        assertEquals(drawn.getPixel(0, 0), read.getPixel(0, 0))
    }

    @Test
    fun `draws again when the size, the kind of image, the app's install or its label isn't the one written`() {
        val movies = installed("movies", updated = 1)
        cache.render(movies, Banner.AppBanner, 300, 169)
        writeEverything()
        val others = listOf(
            Triple(movies, Banner.AppBanner, 400),
            Triple(movies, Banner.AppIcon, 300),
            Triple(installed("movies", updated = 2), Banner.AppBanner, 300),
            Triple(installed("movies", updated = 1, label = "Films"), Banner.AppBanner, 300),
        )

        for ((app, banner, width) in others) {
            restart()
            cache.render(app, banner, width, 169)
            assertEquals("$app $banner $width", 1, images.drawn)
        }
    }

    @Test
    fun `draws again when the file can't be read`() {
        val movies = installed("movies")
        cache.render(movies, Banner.AppBanner, 300, 169)
        writeEverything()
        fileOf(movies).writeBytes(fileOf(movies).readBytes().copyOf(40))   // cut short
        restart()

        cache.render(movies, Banner.AppBanner, 300, 169)

        assertEquals(1, images.drawn)
    }

    @Test
    fun `writes only an app's newest image`() {
        val movies = installed("movies")
        cache.render(movies, Banner.AppBanner, 300, 169)
        idle()
        val queued = jobs.pending
        cache.render(movies, Banner.AppBanner, 400, 225)
        cache.render(movies, Banner.AppBanner, 500, 281)

        idle()
        assertEquals("the first write cancelled, one for the newest", queued, jobs.pending)
        jobs.runAll()
        restart()
        val read = cache.render(movies, Banner.AppBanner, 500, 281)

        assertEquals(0, images.drawn)
        assertEquals(500, read.width)
    }

    @Test
    fun `deletes the files of apps no longer installed`() {
        val movies = installed("movies")
        val gone = app("gone", hasBanner = true)
        cache.render(movies, Banner.AppBanner, 300, 169)
        cache.render(gone, Banner.AppBanner, 300, 169)

        writeEverything()

        assertTrue(fileOf(movies).exists())
        assertFalse(fileOf(gone).exists())
        assertFalse(fileOf(gone).parentFile!!.exists())
    }

}
