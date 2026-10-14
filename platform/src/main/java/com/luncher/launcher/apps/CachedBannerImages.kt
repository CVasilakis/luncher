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
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

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

    private val inflater = Inflater()
    private var packed = ByteArray(0)
    private var pixels = ByteArray(0)

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
     * file that can't be read. The format: a header ([MAGIC], [FORMAT], [drawing]'s fields), then
     * the image's pixels as they are in memory (ARGB_8888, premultiplied), compressed with
     * [Deflater], so a pixel reads back exactly as it was drawn, its transparency too.
     */
    private fun read(file: File, drawing: Drawing): Bitmap? {
        if (!file.isFile) return null
        try {
            DataInputStream(BufferedInputStream(FileInputStream(file), BUFFER)).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != FORMAT) return null
                val stored = Drawing(input.readInt(), input.readInt(), input.readInt(), input.readLong(), input.readLong(), input.readUTF())
                if (stored != drawing) return null
                val length = input.readInt()
                if (length < 0 || length > file.length()) return null
                if (packed.size < length) packed = ByteArray(length)
                input.readFully(packed, 0, length)
                val size = drawing.width * drawing.height * 4
                if (pixels.size < size) pixels = ByteArray(size)
                inflater.reset()
                inflater.setInput(packed, 0, length)
                if (inflater.inflate(pixels, 0, size) != size || !inflater.finished()) return null
                val image = Bitmap.createBitmap(drawing.width, drawing.height, Bitmap.Config.ARGB_8888)
                image.copyPixelsFromBuffer(ByteBuffer.wrap(pixels, 0, size))
                return image
            }
        } catch (e: IOException) {
            return null
        } catch (e: DataFormatException) {
            return null
        }
    }

    /** A job: writes [write]'s image in [read]'s format, to a new file that then replaces the old one at once. */
    private fun write(write: Unwritten) {
        val pixels = ByteBuffer.allocate(write.image.byteCount)
        write.image.copyPixelsToBuffer(pixels)
        val packed = ByteArrayOutputStream(pixels.capacity() / 4)
        val deflater = Deflater(Deflater.BEST_SPEED)
        deflater.setInput(pixels.array())
        deflater.finish()
        val chunk = ByteArray(BUFFER)
        while (!deflater.finished()) packed.write(chunk, 0, deflater.deflate(chunk))
        deflater.end()

        val temporary = File(write.file.parentFile, write.file.name + TEMPORARY)
        try {
            write.file.parentFile?.mkdirs()
            DataOutputStream(BufferedOutputStream(FileOutputStream(temporary), BUFFER)).use { output ->
                val drawing = write.drawing
                output.writeInt(MAGIC)
                output.writeInt(FORMAT)
                output.writeInt(drawing.width)
                output.writeInt(drawing.height)
                output.writeInt(drawing.kind)
                output.writeLong(drawing.appUpdated)
                output.writeLong(drawing.luncherUpdated)
                output.writeUTF(drawing.label)
                output.writeInt(packed.size())
                packed.writeTo(output)
            }
            if (!temporary.renameTo(write.file)) temporary.delete()
        } catch (e: IOException) {
            // A full disk, say: no file, so the next start draws the image again.
            temporary.delete()
        }
    }

    /** A job: deletes the files of apps no longer installed, and of writes that didn't finish. */
    private fun deleteUninstalled() {
        for (packageFolder in folder.listFiles().orEmpty()) {
            for (file in packageFolder.listFiles().orEmpty()) {
                if (file.name.endsWith(TEMPORARY) || !isInstalled(ComponentName(packageFolder.name, file.name))) file.delete()
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
        const val TEMPORARY = ".new"

        /** "LBNR": a file of this class. */
        const val MAGIC = 0x4C424E52

        /** The file format's version: another one is a mismatch, drawn and written again. */
        const val FORMAT = 1

        const val BUFFER = 64 * 1024
    }
}
