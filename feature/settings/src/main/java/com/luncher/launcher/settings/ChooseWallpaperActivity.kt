package com.luncher.launcher.settings

import com.luncher.domain.pictures.PictureUse

/**
 * Pictures opened to choose the home screen's wallpaper, from the appearance editor: OK on a
 * picture makes it the wallpaper, the first tile none, and the screen closes.
 */
class ChooseWallpaperActivity : PicturesActivity() {
    override val choosing: PictureUse get() = PictureUse.Wallpaper
}
