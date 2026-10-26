package com.luncher.launcher.pictures

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import com.luncher.domain.pictures.Blur
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureImages
import com.luncher.domain.pictures.PictureStyle
import com.luncher.domain.pictures.PreparedPictures
import com.luncher.domain.pictures.PreparingCause
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
 * a working copy for the screen as it is gets one first (every picture has one as it's added, but
 * the screen may have changed since); one whose copy can't be written then (a full disk) is drawn
 * from its original in [originals] instead. Each copy it makes tells, on the main thread, whether
 * its picture is see-through ([onMade]), which the library keeps ([FilesPictures.made]): a picture
 * an earlier version added gets it so. Every picture is drawn over the background color, as the
 * wallpaper will be; [keepingSeeThrough] draws the same, but leaves a see-through picture clear.
 * Keeps nothing else: [CachedPictureImages] keeps the thumbnails, in front of it.
 */
class WorkingCopyImages(
    context: Context,
    private val originals: File = File(context.filesDir, "pictures"),
    copies: File = File(context.filesDir, WorkingCopies.FOLDER),
    private val onMade: (picture: PictureId, seeThrough: Boolean) -> Unit = { _, _ -> },
    screen: () -> ScreenSize = { context.applicationContext.screenSize() },
) : PictureImages<Bitmap>, PreparedPictures {

    private val background = context.color(UiR.color.background)
    private val workingCopies = WorkingCopies(copies, background, screen)
    private val main = Handler(Looper.getMainLooper())

    override fun whyPrepare(picture: PictureId): PreparingCause? = workingCopies.whyMake(picture.value)

    override fun prepare(picture: PictureId, stopped: () -> Boolean): Boolean {
        if (workingCopies.made(picture.value)) return true
        val original = File(originals, picture.value)
        val shape = PictureFiles.shape(original) ?: return false
        val made = workingCopies.make(original, shape, workingCopies.fileOf(picture.value), stopped) as? WorkingCopies.Made.Written ?: return false
        main.post { onMade(picture, made.seeThrough) }
        return true
    }

    override fun cached(picture: PictureId, width: Int, height: Int, style: PictureStyle): Bitmap? = null

    override fun render(picture: PictureId, width: Int, height: Int, style: PictureStyle, stopped: () -> Boolean): Bitmap? =
        render(picture, width, height, style, keepClear = false, stopped)

    /**
     * The pictures drawn as [render] draws them, but a see-through one over nothing, its transparent
     * parts left clear: the lists' thumbnails, which show it over the row. Only from its copy, a PNG
     * with alpha, and in a plain style: a picture drawn from its original isn't known to be
     * see-through, and a blurred or dimmed one is drawn over the background, as blur needs. An opaque
     * picture is drawn as [render] draws it, pixel for pixel.
     */
    val keepingSeeThrough: PictureImages<Bitmap> = object : PictureImages<Bitmap> {
        override fun cached(picture: PictureId, width: Int, height: Int, style: PictureStyle): Bitmap? = null

        override fun render(picture: PictureId, width: Int, height: Int, style: PictureStyle, stopped: () -> Boolean): Bitmap? =
            render(picture, width, height, style, keepClear = true, stopped)
    }

    private fun render(picture: PictureId, width: Int, height: Int, style: PictureStyle, keepClear: Boolean, stopped: () -> Boolean): Bitmap? {
        val copy = workingCopies.read(picture.value)
            ?: if (prepare(picture, stopped)) workingCopies.read(picture.value) else null
        if (copy == null) {
            if (stopped()) return null
            val original = File(originals, picture.value)
            return PictureFiles.styled(width, height, style) { w, h -> PictureFiles.draw(original, w, h, style.framing, background, stopped) }
        }
        val clear = keepClear && copy.hasAlpha() && style.dim == 0 && style.blur == Blur.OFF
        try {
            return PictureFiles.styled(width, height, style) { w, h -> PictureFiles.draw(copy, w, h, style.framing, if (clear) Color.TRANSPARENT else background) }
        } finally {
            copy.recycle()
        }
    }
}
