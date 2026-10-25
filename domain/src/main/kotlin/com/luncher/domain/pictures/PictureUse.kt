package com.luncher.domain.pictures

import com.luncher.domain.appearance.Appearance
import com.luncher.domain.apps.HomeApp
import com.luncher.domain.apps.LaunchableApp

/** What a picture of the library is used for. Each screen names it in its language, in an exhaustive `when`. */
sealed interface PictureUse {

    /** The home screen's background. */
    data object Wallpaper : PictureUse

    /** The tile of [app], in place of its own banner. */
    data class Tile(val app: LaunchableApp) : PictureUse
}

/**
 * The pictures [appearance] and [apps] use, and what for: the wallpaper's, then the tiles' of
 * [apps] (the installed ones, shown or hidden: those the user can find), in their order.
 */
fun pictureUses(appearance: Appearance, apps: List<HomeApp>): Map<PictureId, List<PictureUse>> {
    val uses = LinkedHashMap<PictureId, MutableList<PictureUse>>()
    appearance.background.picture?.let { uses.getOrPut(it) { ArrayList() } += PictureUse.Wallpaper }
    for (app in apps) {
        val picture = app.customization.image?.picture ?: continue
        uses.getOrPut(picture) { ArrayList() } += PictureUse.Tile(app.launchable)
    }
    return uses
}

/**
 * [appearance] without the pictures [deleted]: each use of one goes back to its default (the
 * wallpaper to none), and nothing else changes. The tiles': [ArrangedApps.withoutPictures][com.luncher.domain.apps.ArrangedApps.withoutPictures].
 */
fun withoutPictures(appearance: Appearance, deleted: Set<PictureId>): Appearance {
    if (appearance.background.picture !in deleted) return appearance
    return appearance.copy(background = appearance.background.copy(picture = null))
}
