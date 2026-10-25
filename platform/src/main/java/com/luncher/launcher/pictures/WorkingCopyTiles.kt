package com.luncher.launcher.pictures

import android.content.Context
import android.graphics.Bitmap
import com.luncher.domain.jobs.JobPriority
import com.luncher.domain.jobs.Jobs
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureId
import com.luncher.launcher.ui.ScreenSize
import com.luncher.launcher.ui.color
import com.luncher.launcher.ui.screenSize
import java.io.File
import com.luncher.launcher.ui.R as UiR

/**
 * [PictureTiles] on the library's working copies ([WorkingCopies]): a tile's picture drawn from
 * its copy, decoded at about the tile's size, in a few milliseconds on the main thread (a few
 * times what an app's banner costs), and never from the original, a photo whose decode would
 * hold up the home screen. A copy not made for the screen as it is now
 * (none yet, another size, another [FilePictureImages.DRAWING]) draws nothing, and is left to be
 * made again. One that doesn't decode is damaged: it draws nothing, and is deleted, so it's made
 * again too, in a job of [JobPriority.PICTURE_LIBRARY], the priority of everything that changes the
 * library's files, and only if it's still the file that failed: a good copy that a job made
 * meanwhile, under the same name, stays.
 */
class WorkingCopyTiles(
    context: Context,
    private val jobs: Jobs,
    private val originals: File = File(context.filesDir, "pictures"),
    copies: File = File(context.filesDir, WorkingCopies.FOLDER),
    screen: () -> ScreenSize = { context.applicationContext.screenSize() },
) : PictureTiles {

    private val workingCopies = WorkingCopies(copies, context.color(UiR.color.background), screen)

    override fun draw(picture: PictureId, framing: Framing, width: Int, height: Int): Bitmap? =
        when (val tile = workingCopies.drawTile(picture.value, width, height, framing)) {
            is WorkingCopies.Tile.Drawn -> tile.image
            WorkingCopies.Tile.NotMade -> null
            is WorkingCopies.Tile.Damaged -> {
                val file = workingCopies.fileOf(picture.value)
                jobs.run(JobPriority.PICTURE_LIBRARY) {
                    if (file.length() == tile.length && file.lastModified() == tile.modified) file.delete()
                }
                null
            }
        }

    override fun exists(picture: PictureId): Boolean = File(originals, picture.value).isFile
}
