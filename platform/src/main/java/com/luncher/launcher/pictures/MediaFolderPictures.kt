package com.luncher.launcher.pictures

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.Jobs
import com.luncher.domain.pictures.FolderPicture
import com.luncher.domain.pictures.FolderPictures
import com.luncher.domain.pictures.Framing
import com.luncher.launcher.images.PixelFiles
import com.luncher.launcher.ui.color
import java.io.File
import com.luncher.launcher.ui.R as UiR

/**
 * [FolderPictures] in Luncher's media folders, `Android/media/<applicationId>` on the TV's storage
 * and on each USB drive or SD card ([dropFolders]): a file manager, or a computer writing a USB
 * drive, puts pictures there, and Luncher reads them without any permission. Only files Luncher
 * can read: on Android 16 one another app put there can't be.
 *
 * Listing is a job of [JobPriority.PICTURE_SCREENS] (a drive is slow), which reads each file's
 * size and its format; a file BitmapFactory can't read is listed only if it's in a format Luncher
 * names ([PictureFiles.format]), marked as one this TV can't show. A picture's [Uri] is a
 * `file://` one, which [FilesPictures] adds from these folders only.
 *
 * The previews it draws are kept in [previews], one file per picture (in the [PixelFiles] format,
 * the picture, its size and the preview's size and framing in the header, so a file of another
 * matches nothing), while Add from folder shows: a picture selected again, or drawn ahead, shows
 * at once. [forgetPreviews] deletes them, as the screen closes and as it opens anew (a process
 * that was killed while it showed left some), so none are left behind.
 */
class MediaFolderPictures(
    context: Context,
    private val jobs: Jobs,
    private val folders: () -> List<File> = { dropFolders(context.applicationContext) },
    private val previews: File = File(context.cacheDir, PREVIEWS),
) : FolderPictures<Uri, Bitmap> {

    private val main = Handler(Looper.getMainLooper())
    private val background = context.color(UiR.color.background)

    // Its buffers are the main thread's, where cachedPreview reads.
    private val pixelFiles = PixelFiles(MAGIC, FORMAT)

    /** What a kept preview was drawn from: a file holds one only for these. */
    private data class Drawing(val path: String, val bytes: Long, val pictureWidth: Int, val pictureHeight: Int, val width: Int, val height: Int, val fit: String, val position: String) {
        constructor(picture: FolderPicture<Uri>, width: Int, height: Int, framing: Framing) :
            this(picture.source.toString(), picture.bytes, picture.width, picture.height, width, height, framing.fit.name, framing.position.name)
    }

    override fun list(done: (List<FolderPicture<Uri>>) -> Unit) {
        jobs.run(JobPriority.PICTURE_SCREENS) {
            val pictures = folders().flatMap(::picturesIn)
            main.post { done(pictures) }
        }
    }

    override fun preview(picture: FolderPicture<Uri>, width: Int, height: Int, framing: Framing, stopped: () -> Boolean): Bitmap? {
        val path = picture.source.path ?: return null
        val image = PictureFiles.draw(File(path), width, height, framing, background, stopped) ?: return null
        if (stopped()) return null
        val drawing = Drawing(picture, width, height, framing)
        // Only for writing: the reading buffers stay the main thread's.
        PixelFiles(MAGIC, FORMAT).write(fileOf(picture), image) { output ->
            output.writeUTF(drawing.path)
            output.writeLong(drawing.bytes)
            output.writeInt(drawing.pictureWidth)
            output.writeInt(drawing.pictureHeight)
            output.writeInt(drawing.width)
            output.writeInt(drawing.height)
            output.writeUTF(drawing.fit)
            output.writeUTF(drawing.position)
        }
        return image
    }

    override fun cachedPreview(picture: FolderPicture<Uri>, width: Int, height: Int, framing: Framing): Bitmap? {
        val drawing = Drawing(picture, width, height, framing)
        return pixelFiles.read(fileOf(picture), width, height) { input ->
            Drawing(input.readUTF(), input.readLong(), input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readUTF(), input.readUTF()) == drawing
        }
    }

    override fun forgetPreviews() {
        jobs.run(JobPriority.PICTURE_SCREENS) { previews.deleteRecursively() }
    }

    /** A picture's kept preview: named by its path's hash; the header tells two of one hash apart. */
    private fun fileOf(picture: FolderPicture<Uri>) = File(previews, Integer.toHexString(picture.source.toString().hashCode()))

    private fun picturesIn(folder: File): List<FolderPicture<Uri>> {
        val removable = try {
            Environment.isExternalStorageRemovable(folder)
        } catch (e: IllegalArgumentException) {
            false
        }
        return folder.listFiles().orEmpty()
            .filter { it.isFile && !it.isHidden && it.canRead() }
            .mapNotNull { file ->
                val shape = PictureFiles.shape(file)
                val unsupported = if (shape == null) PictureFiles.format(file) ?: return@mapNotNull null else null
                FolderPicture(file.name, removable, file.length(), shape?.width ?: 0, shape?.height ?: 0, unsupported, Uri.fromFile(file))
            }
    }
}

/** The kept previews' folder, in the cache. */
private const val PREVIEWS = "folder-previews"

/** "LFPV": a file of its previews. */
private const val MAGIC = 0x4C465056
private const val FORMAT = 1

/**
 * Luncher's drop folders that are there now: `Android/media/<applicationId>` on every mounted
 * volume. Asking for them creates them, so a file manager finds them. Not `Android/data`, which
 * file managers can't write from Android 11 on.
 */
internal fun dropFolders(context: Context): List<File> =
    // Deprecated from API 30 for apps that share media through MediaStore; Luncher only reads its own files there.
    @Suppress("DEPRECATION")
    context.externalMediaDirs.filterNotNull().filter { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
