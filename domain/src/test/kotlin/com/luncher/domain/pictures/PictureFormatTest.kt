package com.luncher.domain.pictures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PictureFormatTest {

    /** An ISO base media file's start: an `ftyp` box with [major] and [compatible] brands, then [after]. */
    private fun ftyp(major: String, vararg compatible: String, after: String = "....meta"): ByteArray {
        val size = 16 + 4 * compatible.size
        val box = listOf(0, 0, 0, size).map { it.toByte() }.toByteArray() +
            "ftyp$major".toByteArray() + byteArrayOf(0, 0, 0, 0) + compatible.joinToString("").toByteArray()
        return box + after.toByteArray()
    }

    @Test
    fun `recognizes an iPhone's HEIC photo`() {
        assertEquals(PictureFormat.HEIF, pictureFormatOf(ftyp("heic", "mif1", "heic")))
    }

    @Test
    fun `recognizes every HEIF brand, major or compatible`() {
        for (brand in listOf("heic", "heix", "hevc", "mif1", "msf1")) {
            assertEquals(brand, PictureFormat.HEIF, pictureFormatOf(ftyp(brand)))
            assertEquals(brand, PictureFormat.HEIF, pictureFormatOf(ftyp("isom", "iso8", brand)))
        }
    }

    @Test
    fun `recognizes AVIF, although it lists a HEIF brand too`() {
        assertEquals(PictureFormat.AVIF, pictureFormatOf(ftyp("avif", "mif1", "miaf")))
        assertEquals(PictureFormat.AVIF, pictureFormatOf(ftyp("mif1", "avif")))
        assertEquals(PictureFormat.AVIF, pictureFormatOf(ftyp("avis", "msf1")))
    }

    @Test
    fun `doesn't take a brand from past the ftyp box`() {
        assertNull(pictureFormatOf(ftyp("isom", "mp41", after = "heicheicheic")))
    }

    @Test
    fun `knows nothing else`() {
        assertNull("an MP4 video", pictureFormatOf(ftyp("isom", "iso2", "mp41")))
        assertNull("a JPEG", pictureFormatOf(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(60)))
        assertNull("a PNG", pictureFormatOf("\u0089PNG\r\n\u001a\n".toByteArray(Charsets.ISO_8859_1) + ByteArray(56)))
        assertNull("cut short", pictureFormatOf(ftyp("heic").copyOf(12)))
        assertNull("empty", pictureFormatOf(ByteArray(0)))
    }

    @Test
    fun `needs no more than the header it asks for`() {
        val header = ftyp("heic", "mif1", "heic", "MiHE", "MiPr", "miaf", "MiHB", "heix")
        assertEquals(PictureFormat.HEIF, pictureFormatOf(header.copyOf(PICTURE_HEADER_BYTES)))
    }
}
