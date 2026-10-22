package com.luncher.domain.layout

/** A rectangle in pixels: from [left] and [top] to [right] and [bottom], the two last outside it. */
data class PixelBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Where a tile's menu goes: its top-left corner, in the coordinates of the tile's bounds, and whether it's above the tile. */
data class TileMenuPlacement(val left: Int, val top: Int, val above: Boolean)

/**
 * Where the menu of a tile goes, [menuWidth] by [menuHeight] pixels: above the [tile], [gap] from
 * it, or below it where there's no room above, within the [room] the screen leaves (the TV's
 * overscan margin). Across, centered on the tile; where that would leave the room, in line with
 * the tile's edge on that side instead (its left edge with the tile's left edge, or its right
 * with the tile's right), so a tile at the screen's edge has its menu flush with it, past the
 * room as far as the zoomed tile is. Where it fits neither above nor below, it goes on the side
 * with more room, as near to the tile as the room lets it, over the tile's edge.
 *
 * [tile] is the tile as it's drawn: zoomed, with its name below it while it shows, so the menu
 * covers neither.
 */
fun tileMenuPlacement(tile: PixelBounds, menuWidth: Int, menuHeight: Int, room: PixelBounds, gap: Int): TileMenuPlacement {
    val centered = (tile.left + tile.right) / 2 - menuWidth / 2
    val left = when {
        centered < room.left -> tile.left
        centered + menuWidth > room.right -> tile.right - menuWidth
        else -> centered
    }
    val aboveTop = tile.top - gap - menuHeight
    val belowTop = tile.bottom + gap
    return when {
        aboveTop >= room.top -> TileMenuPlacement(left, aboveTop, above = true)
        belowTop + menuHeight <= room.bottom -> TileMenuPlacement(left, belowTop, above = false)
        tile.top - room.top >= room.bottom - tile.bottom -> TileMenuPlacement(left, room.top, above = true)
        else -> TileMenuPlacement(left, (room.bottom - menuHeight).coerceAtLeast(room.top), above = false)
    }
}
