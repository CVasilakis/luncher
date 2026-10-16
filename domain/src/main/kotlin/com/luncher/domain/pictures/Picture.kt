package com.luncher.domain.pictures

/**
 * A picture of the library, by its content: the same picture added twice is one picture, with one
 * id. Opaque: what it's made of is the library's adapter's business.
 */
data class PictureId(val value: String)

/** A picture in Luncher's library, which the wallpaper (and later banners) are chosen from. */
data class Picture(
    val id: PictureId,
    /** What the user knows it by: the file's name when it was added; empty when it had none. */
    val name: String,
    /** Its size as shown, in px: upright, as a camera's orientation tag turns it. */
    val width: Int,
    val height: Int,
    /** The file's size. */
    val bytes: Long,
    /** When it was added, in ms since 1970. */
    val added: Long,
)
