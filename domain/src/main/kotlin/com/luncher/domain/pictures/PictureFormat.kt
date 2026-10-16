package com.luncher.domain.pictures

/**
 * A picture format that some TVs can't show, which Luncher names when one can't, so the user knows
 * to use another (JPEG, PNG). Android decodes HEIF (an iPhone's HEIC photos) from Android 9 (API
 * 28) on, AVIF from Android 13 (API 33) on; whether this TV can is found by decoding, never by the
 * version.
 */
enum class PictureFormat { HEIF, AVIF }

/**
 * The format of a file that starts with [header], when it's one of [PictureFormat]: an ISO base
 * media file (`ftyp` box) whose major or compatible brands name it. Never judged by a file's name.
 * AVIF files list a HEIF brand (`mif1`) too, so an AVIF brand comes first. Null for anything else,
 * a header cut short included.
 */
fun pictureFormatOf(header: ByteArray): PictureFormat? {
    if (header.size < 16 || brandAt(header, 4) != "ftyp") return null
    val boxSize = int(header, 0).toLong() and 0xFFFFFFFFL
    // The major brand at 8, its version at 12, then the compatible brands, to the box's end.
    val end = minOf(header.size.toLong(), if (boxSize >= 16) boxSize else header.size.toLong()).toInt()
    val brands = (listOf(8) + (16..end - 4 step 4)).map { brandAt(header, it) }
    return when {
        brands.any { it in AVIF_BRANDS } -> PictureFormat.AVIF
        brands.any { it in HEIF_BRANDS } -> PictureFormat.HEIF
        else -> null
    }
}

/** How many bytes of a file's start [pictureFormatOf] needs at most: an `ftyp` box rarely lists more brands. */
const val PICTURE_HEADER_BYTES = 64

private val HEIF_BRANDS = setOf("heic", "heix", "hevc", "mif1", "msf1")
private val AVIF_BRANDS = setOf("avif", "avis")

private fun brandAt(bytes: ByteArray, at: Int): String =
    String(CharArray(4) { (bytes[at + it].toInt() and 0xFF).toChar() })

private fun int(bytes: ByteArray, at: Int): Int =
    (0 until 4).fold(0) { value, i -> (value shl 8) or (bytes[at + i].toInt() and 0xFF) }
