package com.luncher.domain.wallpaper

/**
 * [StoredWallpaper] in memory: [kept] is what's kept, set by a test or by [store]. [unreadable]
 * makes [read] fail while [drawing] still reads, as a damaged file's header would.
 */
class FakeStoredWallpaper<Image>(var kept: Pair<WallpaperDrawing, Image>? = null) : StoredWallpaper<Image> {

    var unreadable = false

    /** How many times the image was read. */
    var reads = 0
        private set

    /** Every drawing [store] kept, in order. */
    val stored = mutableListOf<WallpaperDrawing>()

    override fun drawing(): WallpaperDrawing? = kept?.first

    override fun read(drawing: WallpaperDrawing): Image? {
        reads++
        val kept = kept ?: return null
        return if (kept.first == drawing && !unreadable) kept.second else null
    }

    override fun store(drawing: WallpaperDrawing, image: Image) {
        kept = drawing to image
        unreadable = false
        stored += drawing
    }

    override fun delete() {
        kept = null
    }
}
