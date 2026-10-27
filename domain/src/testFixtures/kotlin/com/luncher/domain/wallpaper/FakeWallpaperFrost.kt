package com.luncher.domain.wallpaper

/** [WallpaperFrost] as [make] makes it; [frosted] is every wallpaper it was asked for, in order. */
class FakeWallpaperFrost<Image>(private val make: (Image) -> Image) : WallpaperFrost<Image> {

    val frosted = mutableListOf<Image>()

    override fun frost(wallpaper: Image): Image {
        synchronized(frosted) { frosted += wallpaper }
        return make(wallpaper)
    }
}
