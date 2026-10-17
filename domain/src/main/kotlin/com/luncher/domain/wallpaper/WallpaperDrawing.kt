package com.luncher.domain.wallpaper

import com.luncher.domain.appearance.Background
import com.luncher.domain.pictures.PictureId
import com.luncher.domain.pictures.PictureStyle

/** What a wallpaper is drawn from: [picture] in [style], at [width] x [height] px, the screen's size. */
data class WallpaperDrawing(val picture: PictureId, val style: PictureStyle, val width: Int, val height: Int)

/** The wallpaper [background] asks for on a screen of [width] x [height] px; null without a picture. */
fun wallpaperDrawing(background: Background, width: Int, height: Int): WallpaperDrawing? =
    background.picture?.let { WallpaperDrawing(it, background.style, width, height) }

/** Why a wallpaper is being drawn. Each screen says it in its language, in an exhaustive `when`. */
enum class DrawingCause {
    /** The user saved a new one in the background editor, which is stored once it's drawn. */
    SAVING,

    /** The one kept is the same picture, drawn the same way, for a screen of another size (the TV switched to 4K, say). */
    SCREEN_CHANGED,

    /** The one kept is of something else, or drawn by another version of Luncher, or there's none. */
    OUTDATED,
}

/** Why the [kept] wallpaper won't do for the [wanted] one; null if it will, or if none is wanted. */
fun whyDraw(kept: WallpaperDrawing?, wanted: WallpaperDrawing?): DrawingCause? = when {
    wanted == null || kept == wanted -> null
    kept != null && kept.picture == wanted.picture && kept.style == wanted.style -> DrawingCause.SCREEN_CHANGED
    else -> DrawingCause.OUTDATED
}
