package com.luncher.launcher.wallpaper

import android.graphics.Bitmap
import com.luncher.domain.pictures.Blur
import com.luncher.domain.wallpaper.WallpaperFrost
import com.luncher.launcher.pictures.PictureFiles

/**
 * [WallpaperFrost] as an eighth of the wallpaper's size, strongly blurred. Halved three times,
 * each pixel the average of the 2 x 2 under it ([PictureFiles.half]), so every pixel of the
 * wallpaper counts: shrunk in one step, a filter would read only a few of each 8 x 8, and what it
 * skips would show as blocks. Then blurred as the wallpaper's Strong blur is ([PictureFiles.blur],
 * a share of the width, so it looks the same at any size). The blur leaves no detail an eighth
 * can't hold: stretched back over the screen, filtered, it looks as one blurred at full size, for
 * a 64th of the memory and of the blur's time (130 KB at 1080p).
 */
class BlurredWallpaperFrost : WallpaperFrost<Bitmap> {

    override fun frost(wallpaper: Bitmap): Bitmap {
        var image = wallpaper
        repeat(HALVINGS) {
            val half = PictureFiles.half(image)
            if (image !== wallpaper) image.recycle()
            image = half
        }
        PictureFiles.blur(image, Blur.STRONG)
        return image
    }

    internal companion object {
        /** How many times the wallpaper is halved: to an eighth. */
        const val HALVINGS = 3
    }
}
