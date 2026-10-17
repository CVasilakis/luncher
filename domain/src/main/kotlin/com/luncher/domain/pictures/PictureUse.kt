package com.luncher.domain.pictures

import com.luncher.domain.appearance.Appearance

/** What a picture of the library is used for. Each screen names it in its language, in an exhaustive `when`. */
sealed interface PictureUse {

    /** The home screen's background. */
    data object Wallpaper : PictureUse
}

/** Every use a picture can be put to, in the order a picture just received offers them. */
val pictureUseMenu: List<PictureUse> = listOf(PictureUse.Wallpaper)

/** The pictures [appearance] uses, and what for. */
fun pictureUses(appearance: Appearance): Map<PictureId, List<PictureUse>> {
    val picture = appearance.background.picture ?: return emptyMap()
    return mapOf(picture to listOf(PictureUse.Wallpaper))
}

/**
 * [appearance] without the pictures [deleted]: each use of one goes back to its default (the
 * wallpaper to none), and nothing else changes.
 */
fun withoutPictures(appearance: Appearance, deleted: Set<PictureId>): Appearance {
    if (appearance.background.picture !in deleted) return appearance
    return appearance.copy(background = appearance.background.copy(picture = null))
}
