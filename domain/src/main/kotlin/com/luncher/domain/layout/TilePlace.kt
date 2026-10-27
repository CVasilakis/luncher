package com.luncher.domain.layout

/**
 * Where a tile shows on the home screen: its top left corner at ([x], [y]) px of a screen
 * [screenWidth] x [screenHeight] px, at rest (before its zoom), scrolled as it showed. The home
 * screen gives it with the tile, as it opens a screen about that tile (the tile banner picker),
 * which then shows the tile over the part of the wallpaper it's on there. Only the home screen
 * knows it: its top bar is as tall as its text, and its scroll is where the focus went.
 */
data class TilePlace(val x: Int, val y: Int, val screenWidth: Int, val screenHeight: Int) {

    companion object {
        /**
         * Where to show a tile of [size] on a screen [screenWidth] x [screenHeight] px: at [found],
         * the place the home screen gave, if it gave it on a screen of that size; else (none given,
         * or the screen changed size since) in the screen's middle, where the tiles usually are.
         */
        fun on(found: TilePlace?, size: TileSize, screenWidth: Int, screenHeight: Int): TilePlace =
            found?.takeIf { it.screenWidth == screenWidth && it.screenHeight == screenHeight }
                ?: TilePlace((screenWidth - size.width) / 2, (screenHeight - size.height) / 2, screenWidth, screenHeight)
    }
}
