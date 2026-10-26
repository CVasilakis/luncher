package com.luncher.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Looper
import com.luncher.domain.apps.AppImages
import com.luncher.domain.apps.Banner
import com.luncher.domain.apps.HomeApp
import com.luncher.domain.apps.LaunchableApp
import com.luncher.domain.jobs.Cancellable
import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.Jobs
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.Pictures
import com.luncher.launcher.images.PixelFiles
import com.luncher.launcher.pictures.FilePictureImages
import com.luncher.launcher.pictures.PictureTiles
import java.io.DataInputStream
import java.io.File

/**
 * [AppImages] that keeps each tile's image in a file, so that a cold start reads it rather than
 * having [images] draw it again. A file holds one app's image as last drawn, and is used while it
 * matches: the same size and kind of image, the same install of the app and of Luncher, the same
 * name (the one the home screen shows, which a card draws). Otherwise [images] draws it, and the
 * file is written again: renaming an app draws its tile again, and only its.
 *
 * A picture of the library the user chose for the tile ([Banner.Picture]) is drawn by [pictures],
 * from its working copy, and kept the same way, its file saying the picture, its framing and its
 * drawing's version ([FilePictureImages.DRAWING]), not the app's install or name, which it doesn't
 * draw. Where [pictures] can't draw it now (its copy not prepared for this screen, or damaged), the
 * app's own image shows, read and kept as that, never as the picture, so the picture is drawn as
 * soon as it can be; a tile kept with its picture shows while its copy is missing.
 *
 * Images are read on the main thread, where [render] is called. Writing waits until the main
 * thread is idle, so that it doesn't slow the start that draws them, and is then a job of
 * [JobPriority.BANNER_CACHE] for each image. Once per process, a job also deletes the files of
 * apps no longer installed, and of tiles whose picture is gone; and again, those of tiles whose
 * picture is gone, whenever a picture [library] had is gone, so a picture deleted leaves none of
 * its pixels here; a change that leaves every picture there (one added, renamed, or found
 * see-through) deletes nothing. The file format: [read] and [write] (platform/README.md#writing-adapters).
 */
class CachedBannerImages(
    context: Context,
    private val images: AppImages<Bitmap>,
    private val pictures: PictureTiles,
    library: Pictures,
    private val jobs: Jobs,
) : AppImages<Bitmap> {

    /** What an image was drawn from: a file holds an image only for these. */
    private data class Drawing(
        val width: Int,
        val height: Int,
        val kind: Int,
        /** The app's install; 0 for a picture, which doesn't come from the app. */
        val appUpdated: Long,
        val luncherUpdated: Long,
        /** The app's name as the home screen shows it: a card draws it, as does a banner that fails to load. Empty for a picture. */
        val name: String,
        /** A picture's id, its fit and position (their ordinals) and [FilePictureImages.DRAWING]; empty and -1 for the app's own image. */
        val picture: String,
        val fit: Int,
        val position: Int,
        val pictureDrawing: Int,
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

    /** The library's pictures as of its last change; null before the first. */
    private var pictureIds: Set<PictureId>? = null

    private val pixelFiles = PixelFiles(MAGIC, FORMAT)

    init {
        // For the process: the cache lives as long as it. A change that took a picture away (any
        // change, the first time) runs one sweep, after the library's own jobs, which delete a
        // picture's files first.
        library.addListener {
            val ids = library.read().mapTo(HashSet()) { it.id }
            val gone = pictureIds?.any { it !in ids } ?: true
            pictureIds = ids
            if (gone) jobs.run(JobPriority.BANNER_CACHE) { deletePicturesGone() }
        }
    }

    override fun render(app: HomeApp, banner: Banner, width: Int, height: Int): Bitmap {
        val image = when (banner) {
            is Banner.Own -> own(app, banner, width, height)
            is Banner.Picture -> picture(banner, fileOf(app.launchable), width, height) ?: own(app, banner.own, width, height)
        }
        if (unwritten.isNotEmpty() || !cleaned) whenIdle()
        return image
    }

    /** [app]'s own image, read, or drawn by [images] and kept as that. */
    private fun own(app: HomeApp, banner: Banner.Own, width: Int, height: Int): Bitmap {
        val kind = when (banner) {
            Banner.AppBanner -> KIND_BANNER
            Banner.AppIcon -> KIND_ICON
        }
        val drawing = Drawing(width, height, kind, app.installed.updated, luncherUpdated, app.name, "", -1, -1, -1)
        val file = fileOf(app.launchable)
        return read(file, drawing) ?: images.render(app, banner, width, height).also { keep(file, drawing, it) }
    }

    /** [banner]'s picture, read from [file], or drawn by [pictures] and kept there; null where it can't be drawn now. */
    private fun picture(banner: Banner.Picture, file: File, width: Int, height: Int): Bitmap? {
        val framing = banner.framing
        val drawing = Drawing(width, height, KIND_PICTURE, 0, luncherUpdated, "", banner.picture.value, framing.fit.ordinal, framing.position.ordinal, FilePictureImages.DRAWING)
        return read(file, drawing) ?: pictures.draw(banner.picture, framing, width, height)?.also { keep(file, drawing, it) }
    }

    /** [image], drawn from [drawing], to be written to [file] once the main thread is idle: an app's newer image replaces the older. */
    private fun keep(file: File, drawing: Drawing, image: Bitmap) {
        unwritten[file] = Unwritten(file, drawing, image)
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
            readDrawing(input) == drawing
        }

    private fun readDrawing(input: DataInputStream) = Drawing(
        input.readInt(), input.readInt(), input.readInt(), input.readLong(), input.readLong(), input.readUTF(),
        input.readUTF(), input.readInt(), input.readInt(), input.readInt(),
    )

    /** A job: writes [write]'s image in [read]'s format. */
    private fun write(write: Unwritten) {
        pixelFiles.write(write.file, write.image) { output ->
            val drawing = write.drawing
            output.writeInt(drawing.width)
            output.writeInt(drawing.height)
            output.writeInt(drawing.kind)
            output.writeLong(drawing.appUpdated)
            output.writeLong(drawing.luncherUpdated)
            output.writeUTF(drawing.name)
            output.writeUTF(drawing.picture)
            output.writeInt(drawing.fit)
            output.writeInt(drawing.position)
            output.writeInt(drawing.pictureDrawing)
        }
    }

    /** A job: deletes the files of apps no longer installed, of writes that didn't finish, and of tiles whose picture is gone. */
    private fun deleteUninstalled() {
        for (packageFolder in folder.listFiles().orEmpty()) {
            for (file in packageFolder.listFiles().orEmpty()) {
                if (file.name.endsWith(PixelFiles.TEMPORARY) || !isInstalled(ComponentName(packageFolder.name, file.name)) || isOfPictureGone(file)) file.delete()
            }
            packageFolder.delete()   // only if it's empty now
        }
    }

    /** A job: deletes the files of tiles whose picture is gone. */
    private fun deletePicturesGone() {
        for (packageFolder in folder.listFiles().orEmpty()) {
            for (file in packageFolder.listFiles().orEmpty()) if (isOfPictureGone(file)) file.delete()
        }
    }

    /** Whether [file] holds a tile drawn of a picture the library no longer has. */
    private fun isOfPictureGone(file: File): Boolean {
        val picture = pixelFiles.header(file) { input -> readDrawing(input).takeIf { it.kind == KIND_PICTURE }?.picture } ?: return false
        return !pictures.exists(PictureId(picture))
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
        const val FORMAT = 2

        // What a file's image is (Drawing.kind).
        const val KIND_BANNER = 1
        const val KIND_ICON = 2
        const val KIND_PICTURE = 3
    }
}
