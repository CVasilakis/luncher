package com.luncher.domain.wallpaper

/**
 * Port: the frosted copy of a wallpaper, the backdrop of the tiles whose look is Frosted: the
 * wallpaper small and strongly blurred, which a frosted tile draws its part of, stretched to the
 * screen, so it shows the wallpaper behind it, blurred. Made with the wallpaper, in its job, and
 * kept with it ([StoredWallpaper]), so a start reads both. [Image] is the UI's own image type (a
 * Bitmap on Android).
 */
interface WallpaperFrost<Image> {

    /** [wallpaper]'s frosted copy, a new image, opaque as the wallpaper is; [wallpaper] stays as it is. Slow: a job. */
    fun frost(wallpaper: Image): Image
}
