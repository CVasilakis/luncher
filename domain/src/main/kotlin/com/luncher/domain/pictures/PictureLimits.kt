package com.luncher.domain.pictures

/**
 * The largest picture the library takes: a larger file takes long to copy and room on the TV, and
 * more pixels take long to decode on a weak one, for no visible gain on a TV screen. And the most
 * pictures one share brings.
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

    /**
     * The most pictures one share brings: more are refused whole, so the user shares again the
     * ones they want. Far below what Android itself can pass in one share (it drops a share of
     * 1000 on API 22 to 28 before Luncher sees it), and a batch a TV adds in reasonable time.
     */
    const val MAX_SHARED = 100

    /** Whether a share of [count] pictures is small enough. */
    fun allowsShare(count: Int): Boolean = count <= MAX_SHARED

    /** Which limit a file of [bytes], of [width] x [height] px, is over: its file's first; null within both. */
    fun exceeded(bytes: Long, width: Int, height: Int): SizeLimit? = when {
        !allowsBytes(bytes) -> SizeLimit.BYTES
        !allowsSize(width, height) -> SizeLimit.PIXELS
        else -> null
    }
}

/** A limit of [PictureLimits] a picture can be over: its file's size ([PictureLimits.MAX_BYTES]), or its pixels ([PictureLimits.MAX_PIXELS]). */
enum class SizeLimit { BYTES, PIXELS }
