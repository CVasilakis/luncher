package com.luncher.domain.apps

/**
 * How a tile shows a see-through picture of the library: what's behind it ([backdrop]), and what
 * the focus follows ([focus]). The tile's own, not the picture's: another picture chosen for the
 * tile keeps it ([TileImage.starting]). It never chooses the focus style: the appearance's focus
 * frame does, for every tile. The defaults are a light glass card and the tile's shape.
 */
data class TileLook(
    val backdrop: TileBackdrop = TileBackdrop.LIGHT_GLASS,
    val focus: TileFocusShape = TileFocusShape.TILE,
)

/** What's behind a see-through picture on its tile, inside the tile's rounded shape. */
enum class TileBackdrop {
    /** Nothing: the wallpaper shows through the picture's see-through parts. */
    NONE,

    /** A translucent light card. */
    LIGHT_GLASS,

    /** A translucent dark card. */
    DARK_GLASS,

    /** The wallpaper exactly behind the tile, blurred, with a light tint. */
    FROSTED,
}

/**
 * What the focus frame, or the lifted tile's shadow, follows on a see-through picture's tile: the
 * tile's rounded shape, as on every tile, or the picture's own outline. (Not where the focus goes
 * among the tiles: that's [TileFocus][com.luncher.domain.layout.TileFocus].)
 */
enum class TileFocusShape { TILE, SHAPE }
