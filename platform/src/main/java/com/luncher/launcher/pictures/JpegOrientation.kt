package com.luncher.launcher.pictures

/**
 * The EXIF orientation tag (1 to 8) of a JPEG whose start is [bytes], or 1 (as stored) when it has
 * none, or isn't a JPEG. Read here rather than with `android.media.ExifInterface`, which up to
 * Android 6 parses in native code with known security bugs, and any app can share a crafted file
 * with Luncher. Only what the tag needs: the JPEG's segments up to its image data, the `Exif` one's
 * TIFF header (either byte order), and the first IFD's entries.
 */
internal fun jpegOrientation(bytes: ByteArray): Int {
    if (bytes.size < 4 || u8(bytes, 0) != 0xFF || u8(bytes, 1) != 0xD8) return NORMAL
    var at = 2
    while (at + 4 <= bytes.size && u8(bytes, at) == 0xFF) {
        val marker = u8(bytes, at + 1)
        if (marker == 0xDA || marker == 0xD9) return NORMAL   // the image data, or the end: no tag before
        val length = u16(bytes, at + 2, bigEndian = true)
        if (marker == 0xE1 && isExif(bytes, at + 4)) return tiffOrientation(bytes, at + 10, minOf(bytes.size, at + 2 + length))
        at += 2 + length
    }
    return NORMAL
}

private const val NORMAL = 1
private const val ORIENTATION = 0x0112
private const val SHORT = 3

private fun isExif(bytes: ByteArray, at: Int) =
    at + 6 <= bytes.size && String(bytes, at, 4, Charsets.ISO_8859_1) == "Exif" && bytes[at + 4].toInt() == 0 && bytes[at + 5].toInt() == 0

/** The orientation in the TIFF structure from [start] to [end], or 1. */
private fun tiffOrientation(bytes: ByteArray, start: Int, end: Int): Int {
    if (start + 8 > end) return NORMAL
    val bigEndian = when (String(bytes, start, 2, Charsets.ISO_8859_1)) {
        "MM" -> true
        "II" -> false
        else -> return NORMAL
    }
    if (u16(bytes, start + 2, bigEndian) != 42) return NORMAL
    val ifd = start + u32(bytes, start + 4, bigEndian)
    if (ifd < start || ifd + 2 > end) return NORMAL
    val count = u16(bytes, ifd, bigEndian)
    for (i in 0 until count) {
        val entry = ifd + 2 + 12 * i
        if (entry + 12 > end) return NORMAL
        if (u16(bytes, entry, bigEndian) != ORIENTATION) continue
        if (u16(bytes, entry + 2, bigEndian) != SHORT) return NORMAL
        val value = u16(bytes, entry + 8, bigEndian)
        return if (value in 1..8) value else NORMAL
    }
    return NORMAL
}

private fun u8(bytes: ByteArray, at: Int) = bytes[at].toInt() and 0xFF

private fun u16(bytes: ByteArray, at: Int, bigEndian: Boolean) =
    if (bigEndian) (u8(bytes, at) shl 8) or u8(bytes, at + 1) else (u8(bytes, at + 1) shl 8) or u8(bytes, at)

private fun u32(bytes: ByteArray, at: Int, bigEndian: Boolean): Int {
    if (at + 4 > bytes.size) return -1
    val high = u16(bytes, if (bigEndian) at else at + 2, bigEndian)
    val low = u16(bytes, if (bigEndian) at + 2 else at, bigEndian)
    return (high shl 16) or low
}
