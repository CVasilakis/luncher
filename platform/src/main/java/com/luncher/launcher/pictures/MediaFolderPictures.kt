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
 */
class MediaFolderPictures(
    context: Context,
    private val jobs: Jobs,
    private val folders: () -> List<File> = { dropFolders(context.applicationContext) },
) : FolderPictures<Uri, Bitmap> {

    private val main = Handler(Looper.getMainLooper())
    private val background = context.color(UiR.color.background)

    override fun list(done: (List<FolderPicture<Uri>>) -> Unit) {
        jobs.run(JobPriority.PICTURE_SCREENS) {
            val pictures = folders().flatMap(::picturesIn)
            main.post { done(pictures) }
        }
    }

    override fun preview(picture: FolderPicture<Uri>, width: Int, height: Int, framing: Framing): Bitmap? {
        val path = picture.source.path ?: return null
        return PictureFiles.draw(File(path), width, height, framing, background)
    }

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

/**
 * Luncher's drop folders that are there now: `Android/media/<applicationId>` on every mounted
 * volume. Asking for them creates them, so a file manager finds them. Not `Android/data`, which
 * file managers can't write from Android 11 on.
 */
internal fun dropFolders(context: Context): List<File> =
    // Deprecated from API 30 for apps that share media through MediaStore; Luncher only reads its own files there.
    @Suppress("DEPRECATION")
    context.externalMediaDirs.filterNotNull().filter { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
