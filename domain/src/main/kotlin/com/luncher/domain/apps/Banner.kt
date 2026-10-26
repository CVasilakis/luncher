package com.luncher.domain.apps

import com.luncher.domain.layout.TileSize
import com.luncher.domain.pictures.Framing
import com.luncher.domain.pictures.PictureId

/** Which image the home screen shows for an app. [AppImages] draws it at the tile's size. */
sealed interface Banner {

    /** The app's own image: what it shows unless the user chose a picture, and when that can't be drawn. */
    sealed interface Own : Banner

    /** The TV banner the app declares. */
    data object AppBanner : Own

    /** No banner: the app's icon and name (the one the home screen shows) on a plain card. */
    data object AppIcon : Own

    /**
     * The [picture] of the library the user chose for the tile, put into it as [framing] says, the
     * tile showing it with its [look]; where it isn't prepared now ([PreparedTileImages]: not for
     * this screen, damaged), the app's [own] image.
     */
    data class Picture(val picture: PictureId, val framing: Framing, val look: TileLook, val own: Own) : Banner {

        /** What this tile's picture is prepared from, for a tile of [size]. */
        fun drawing(size: TileSize): TileDrawing = TileDrawing(picture, framing, look.focus, size)
    }
}

/** The image to show for [app]: the picture the user chose for it, else its own ([ownBanner]). */
fun bannerFor(app: HomeApp): Banner {
    val own = ownBanner(app)
    val image = app.customization.image ?: return own
    val picture = image.picture ?: return own
    return Banner.Picture(picture, image.framing, image.look, own)
}

/** [app]'s own image: its banner when it has one, otherwise its icon and name. */
fun ownBanner(app: HomeApp): Banner.Own = if (app.installed.hasBanner) Banner.AppBanner else Banner.AppIcon
