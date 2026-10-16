package com.luncher.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Looper
import com.luncher.domain.apps.AppImages
import com.luncher.domain.apps.Banner
import com.luncher.domain.apps.InstalledApp
import com.luncher.domain.apps.LaunchableApp
import com.luncher.domain.jobs.Cancellable
import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.Jobs
import com.luncher.launcher.images.PixelFiles
import java.io.File

/**
 * [AppImages] that keeps each tile's image in a file, so that a cold start reads it rather than
 * having [images] draw it again. A file holds one app's image as last drawn, and is used while it
 * matches: the same size and kind of image, the same install of the app and of Luncher, the same
 * label. Otherwise [images] draws it, and the file is written again.
 *
 * Images are read on the main thread, where [render] is called. Writing waits until the main
 * thread is idle, so that it doesn't slow the start that draws them, and is then a job of
 * [JobPriority.BANNER_CACHE] for each image. Once per process, a job also deletes the files of
 * apps no longer installed. The file format: [read] and [write]
 * (platform/README.md#writing-adapters).
 */
class CachedBannerImages(
    context: Context,
    private val images: AppImages<Bitmap>,
    private val jobs: Jobs,
) : AppImages<Bitmap> {

    /** What an image was drawn from: a file holds an image only for these. */
    private data class Drawing(
        val width: Int,
        val height: Int,
        val kind: Int,
        val appUpdated: Long,
        val luncherUpdated: Long,
        val label: String,
    )

    private class Unwritten(val file: File, val drawing: Drawing, val image: Bitmap)

    private val packageManager = context.packageManager

    private val folder = File(context.cacheDir, FOLDER)

    /** Luncher's own install: an update may draw the images otherwise. */
    private val luncherUpdated = context.applicationInfo.installTime()

    // The main thread's, as render is called there; jobs see only what they're given.

    /** Images drawn since the main thread was last idle, by file: an app's newer image replaces the older. */
    private val unwritten = LinkedHashMap<File, Unwritten>()

    /** The latest write queued for each file, cancelled when a newer image of that app comes first. */
    private val writes = HashMap<File, Cancellable>()

    private var waitingForIdle = false
    private var cleaned = false

    private val pixelFiles = PixelFiles(MAGIC, FORMAT)

    override fun render(app: InstalledApp, banner: Banner, width: Int, height: Int): Bitmap {
        val kind = when (banner) {
            Banner.AppBanner -> 1
            Banner.AppIcon -> 2
        }
        val drawing = Drawing(width, height, kind, app.updated, luncherUpdated, app.label)
        val file = fileOf(app.launchable)
        val image = read(file, drawing) ?: images.render(app, banner, width, height).also {
            unwritten[file] = Unwritten(file, drawing, it)
        }
        if (unwritten.isNotEmpty() || !cleaned) whenIdle()
        return image
    }

    /** One folder per package, a file per activity: what [deleteUninstalled] needs to find the app. */
    private fun fileOf(app: LaunchableApp) = File(File(folder, app.packageName), app.activityName)

    private fun whenIdle() {
        if (waitingForIdle) return
        waitingForIdle = true
        Looper.myQueue().addIdleHandler {
            waitingForIdle = false
            queueJobs()
            false
        }
    }

    private fun queueJobs() {
        for (write in unwritten.values) {
            writes[write.file]?.cancel()
            // The tile's image, which nothing changes once drawn, read by the job as it is.
            writes[write.file] = jobs.run(JobPriority.BANNER_CACHE) { write(write) }
        }
        unwritten.clear()
        if (!cleaned) {
            cleaned = true
            jobs.run(JobPriority.BANNER_CACHE) { deleteUninstalled() }
        }
    }

    /**
     * [file]'s image if it was drawn from [drawing], else null: none yet, another drawing, or a
     * file that can't be read. The format: [PixelFiles], with [drawing]'s fields as the header.
     */
    private fun read(file: File, drawing: Drawing): Bitmap? =
        pixelFiles.read(file, drawing.width, drawing.height) { input ->
            Drawing(input.readInt(), input.readInt(), input.readInt(), input.readLong(), input.readLong(), input.readUTF()) == drawing
        }

    /** A job: writes [write]'s image in [read]'s format. */
    private fun write(write: Unwritten) {
        pixelFiles.write(write.file, write.image) { output ->
            val drawing = write.drawing
            output.writeInt(drawing.width)
            output.writeInt(drawing.height)
            output.writeInt(drawing.kind)
            output.writeLong(drawing.appUpdated)
            output.writeLong(drawing.luncherUpdated)
            output.writeUTF(drawing.label)
        }
    }

    /** A job: deletes the files of apps no longer installed, and of writes that didn't finish. */
    private fun deleteUninstalled() {
        for (packageFolder in folder.listFiles().orEmpty()) {
            for (file in packageFolder.listFiles().orEmpty()) {
                if (file.name.endsWith(PixelFiles.TEMPORARY) || !isInstalled(ComponentName(packageFolder.name, file.name))) file.delete()
            }
            packageFolder.delete()   // only if it's empty now
        }
    }

    private fun isInstalled(component: ComponentName): Boolean =
        try {
            packageManager.getActivityInfo(component, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    private companion object {
        const val FOLDER = "banners"

        /** "LBNR": a file of this class. */
        const val MAGIC = 0x4C424E52

        /** The file format's version: another one is a mismatch, drawn and written again. */
        const val FORMAT = 1
    }
}
