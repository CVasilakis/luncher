package com.luncher.launcher.ui

import android.content.Context
import com.luncher.domain.appearance.TileGeometry
import com.luncher.domain.layout.TileLayouts
import com.luncher.domain.layout.TileSize

/**
 * The size, in px, of the home screen's tiles of [geometry] on this screen: as [TileLayouts] lays
 * them out in the screen's width less the home screen's padding at the sides
 * ([R.dimen.screen_padding_horizontal]). The home screen prepares its tiles' pictures at it, and
 * the tile banner picker the one it saves, which the home screen then reads as it is.
 */
fun Context.homeTileSize(geometry: TileGeometry): TileSize {
    val padding = resources.getDimensionPixelSize(R.dimen.screen_padding_horizontal)
    return TileLayouts(geometry, resources.displayMetrics.density).tileSize(screenSize().width - 2 * padding)
}

/**
 * How much the home screen zooms its focused tile, framed or lifted (feature/home's
 * home_tile_focus.xml and home_tile_lift.xml, which say it too): what shows a focused home tile
 * elsewhere zooms it as much.
 */
const val FOCUSED_TILE_ZOOM = 1.15f
