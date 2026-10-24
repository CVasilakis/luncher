package com.luncher.domain.layout

/** A rectangle in pixels: from [left] and [top] to [right] and [bottom], the two last outside it. */
data class PixelBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Which side of its tile a tile's menu is on. */
enum class MenuSide { ABOVE, BELOW, LEFT, RIGHT }

/** Where a tile's menu goes: its top-left corner, in the coordinates of the tile's bounds, and which side of the tile it's on. */
data class TileMenuPlacement(val left: Int, val top: Int, val side: MenuSide)

/**
 * Where the menu of a tile goes, [menuWidth] by [menuHeight] pixels, [gap] from the [tile],
 * within the [room] the screen leaves (the TV's overscan margin), never over the tile where the
 * room allows it:
 *
 * - above the tile, or below it where there's no room above; across, centered on the tile, or,
 *   where that would leave the room, in line with the tile's edge on that side instead (its left
 *   edge with the tile's left edge, or its right with the tile's right), so a tile at the screen's
 *   edge has its menu flush with it, past the room as far as the zoomed tile is;
 * - where it fits neither above nor below (a longer menu, a short screen), beside the tile, on the
 *   side with more room where it fits on both, centered on the tile down, moved up or down as far
 *   as the room needs;
 * - where it fits nowhere, above or below on the side with more room, as near to the tile as the
 *   room lets it, over the tile's edge.
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
    if (aboveTop >= room.top) return TileMenuPlacement(left, aboveTop, MenuSide.ABOVE)
    if (belowTop + menuHeight <= room.bottom) return TileMenuPlacement(left, belowTop, MenuSide.BELOW)
    val besideTop = ((tile.top + tile.bottom) / 2 - menuHeight / 2).coerceAtMost(room.bottom - menuHeight).coerceAtLeast(room.top)
    val rightLeft = tile.right + gap
    val leftLeft = tile.left - gap - menuWidth
    val fitsRight = menuHeight <= room.bottom - room.top && rightLeft + menuWidth <= room.right
    val fitsLeft = menuHeight <= room.bottom - room.top && leftLeft >= room.left
    return when {
        fitsRight && (!fitsLeft || room.right - tile.right >= tile.left - room.left) -> TileMenuPlacement(rightLeft, besideTop, MenuSide.RIGHT)
        fitsLeft -> TileMenuPlacement(leftLeft, besideTop, MenuSide.LEFT)
        tile.top - room.top >= room.bottom - tile.bottom -> TileMenuPlacement(left, room.top, MenuSide.ABOVE)
        else -> TileMenuPlacement(left, (room.bottom - menuHeight).coerceAtLeast(room.top), MenuSide.BELOW)
    }
}

/**
 * How tall the menu of a [tile] may be, its whole list being [menuHeight] px, [menuWidth] wide,
 * so [tileMenuPlacement] can put it clear of the tile: [menuHeight] where it fits whole above,
 * below or beside the tile; else the tallest of those spaces (the room's height beside the tile,
 * where it fits across), and the menu scrolls in it. Not under [least] (a few rows): with less
 * room everywhere (a row's middle tile in the middle of a short screen), [least], scrolling,
 * which [tileMenuPlacement] puts on the side with more room, over the tile's edge by as little as
 * it can.
 */
fun tileMenuHeight(tile: PixelBounds, menuWidth: Int, menuHeight: Int, room: PixelBounds, gap: Int, least: Int): Int {
    val above = tile.top - gap - room.top
    val below = room.bottom - tile.bottom - gap
    val fitsAcross = tile.right + gap + menuWidth <= room.right || tile.left - gap - menuWidth >= room.left
    val beside = if (fitsAcross) room.bottom - room.top else 0
    val space = maxOf(above, below, beside)
    if (menuHeight <= space) return menuHeight
    return if (space >= least) space else minOf(menuHeight, least, room.bottom - room.top)
}
