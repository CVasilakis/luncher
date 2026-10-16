package com.luncher.domain.pictures

/**
 * The largest picture the library takes: a larger file takes long to copy and room on the TV, and
 * more pixels take long to decode on a weak one, for no visible gain on a TV screen.
 */
object PictureLimits {

    /** 50 MB, as Android counts them from Android 8 on: 1,000,000 bytes each. */
    const val MAX_BYTES: Long = 50_000_000

    /** 64 megapixels. */
    const val MAX_PIXELS: Long = 64_000_000

    /** Whether a file of [bytes] is small enough. */
    fun allowsBytes(bytes: Long): Boolean = bytes <= MAX_BYTES

    /** Whether a picture of [width] x [height] px has few enough pixels. */
    fun allowsSize(width: Int, height: Int): Boolean = width.toLong() * height <= MAX_PIXELS
}
