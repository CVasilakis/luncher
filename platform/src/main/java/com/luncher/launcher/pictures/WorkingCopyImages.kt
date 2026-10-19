package com.luncher.launcher.pictures

import android.content.Context
import android.graphics.Bitmap
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureImages
import com.luncher.domain.pictures.PictureStyle
import com.luncher.domain.pictures.PreparedPictures
import com.luncher.launcher.ui.ScreenSize
import com.luncher.launcher.ui.color
import com.luncher.launcher.ui.screenSize
import java.io.File
import com.luncher.launcher.ui.R as UiR

/**
 * [PictureImages] for the screens, drawn from the library's working copies in [copies]
 * ([WorkingCopies]), and [PreparedPictures] on them: a thumbnail, or a preview at each step of an
 * option, is framed, blurred and dimmed from a picture covering half the [screen] in
 * milliseconds, where drawing it from the original decodes a photo every time. A picture without
 * a working copy for the screen as it is gets one first; one whose copy can't be written (a full
 * disk) is drawn from its original in [originals] instead. Keeps nothing else: [CachedPictureImages]
 * keeps the thumbnails, in front of it.
 */
class WorkingCopyImages(
    context: Context,
    private val originals: File = File(context.filesDir, "pictures"),
    copies: File = File(context.filesDir, WorkingCopies.FOLDER),
    screen: () -> ScreenSize = { context.applicationContext.screenSize() },
) : PictureImages<Bitmap>, PreparedPictures {

    private val background = context.color(UiR.color.background)
    private val workingCopies = WorkingCopies(copies, background, screen)

    override fun isPrepared(picture: PictureId): Boolean = workingCopies.made(picture.value)

    override fun prepare(picture: PictureId, stopped: () -> Boolean): Boolean {
        if (workingCopies.made(picture.value)) return true
        val original = File(originals, picture.value)
        val shape = PictureFiles.shape(original) ?: return false
        return workingCopies.make(original, shape, workingCopies.fileOf(picture.value), stopped) == WorkingCopies.Made.MADE
    }

    override fun cached(picture: PictureId, width: Int, height: Int, style: PictureStyle): Bitmap? = null

    override fun render(picture: PictureId, width: Int, height: Int, style: PictureStyle, stopped: () -> Boolean): Bitmap? {
        val copy = workingCopies.read(picture.value)
            ?: if (prepare(picture, stopped)) workingCopies.read(picture.value) else null
        if (copy == null) {
            if (stopped()) return null
            val original = File(originals, picture.value)
            return PictureFiles.styled(width, height, style) { w, h -> PictureFiles.draw(original, w, h, style.framing, background, stopped) }
        }
        try {
            return PictureFiles.styled(width, height, style) { w, h -> PictureFiles.draw(copy, w, h, style.framing, background) }
        } finally {
            copy.recycle()
        }
    }
}
