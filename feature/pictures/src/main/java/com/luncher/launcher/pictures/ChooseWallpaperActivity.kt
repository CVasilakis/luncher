package com.luncher.launcher.pictures

import com.luncher.domain.pictures.PictureUse

/**
 * Pictures opened to choose the home screen's wallpaper, from the appearance editor: OK on a
 * picture makes it the wallpaper, the first tile none, and the screen closes. A class of its own,
 * so another feature starts it by its class alone (docs/ARCHITECTURE.md#features).
 */
class ChooseWallpaperActivity : PicturesActivity() {
    override val choosing: PictureUse get() = PictureUse.Wallpaper
}
