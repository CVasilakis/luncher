package com.luncher.launcher.pictures

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class JpegOrientationTest {

    /** A JPEG's start: an APP0 segment, then an Exif APP1 one with [entries] in IFD0 (tag, type, value), then the image data. */
    private fun jpeg(vararg entries: Triple<Int, Int, Int>, bigEndian: Boolean = true, app0: Boolean = true): ByteArray {
        val tiff = ByteArrayOutputStream()
        DataOutputStream(tiff).run {
            fun short(v: Int) = if (bigEndian) writeShort(v) else { writeByte(v and 0xFF); writeByte(v shr 8 and 0xFF) }
            fun int(v: Int) = if (bigEndian) writeInt(v) else { short(v and 0xFFFF); short(v ushr 16) }
            writeBytes(if (bigEndian) "MM" else "II"); short(42); int(8)
            short(entries.size)
            for ((tag, type, value) in entries) { short(tag); short(type); int(1); short(value); short(0) }
            int(0)
        }
        val exif = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + tiff.toByteArray()
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        if (app0) out.write(byteArrayOf(0xFF.toByte(), 0xE0.toByte(), 0, 16) + "JFIF\u0000".toByteArray() + ByteArray(9))
        out.write(byteArrayOf(0xFF.toByte(), 0xE1.toByte(), ((exif.size + 2) shr 8).toByte(), (exif.size + 2).toByte()) + exif)
        out.write(byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0, 2))
        return out.toByteArray()
    }

    @Test
    fun `reads each orientation, in either byte order`() {
        for (value in 1..8) {
            assertEquals(value, jpegOrientation(jpeg(Triple(0x0112, 3, value))))
            assertEquals(value, jpegOrientation(jpeg(Triple(0x0112, 3, value), bigEndian = false)))
        }
    }

    @Test
    fun `finds the tag among others, after other segments or as the first`() {
        assertEquals(6, jpegOrientation(jpeg(Triple(0x010F, 3, 1), Triple(0x0112, 3, 6), Triple(0x0132, 3, 1))))
        assertEquals(8, jpegOrientation(jpeg(Triple(0x0112, 3, 8), app0 = false)))
    }

    @Test
    fun `as stored without the tag, or with one it can't use`() {
        assertEquals(1, jpegOrientation(jpeg(Triple(0x010F, 3, 6))))
        assertEquals("out of range", 1, jpegOrientation(jpeg(Triple(0x0112, 3, 9))))
        assertEquals("not a SHORT", 1, jpegOrientation(jpeg(Triple(0x0112, 4, 6))))
    }

    @Test
    fun `as stored for what isn't a JPEG with EXIF, or is cut short, never failing`() {
        val whole = jpeg(Triple(0x0112, 3, 6))
        for (cut in 0 until whole.size - 4) assertEquals(true, jpegOrientation(whole.copyOf(cut)) in 1..8)
        assertEquals(1, jpegOrientation("\u0089PNG\r\n\u001a\n".toByteArray(Charsets.ISO_8859_1)))
        assertEquals(1, jpegOrientation(ByteArray(0)))
        val noExif = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xDA.toByte(), 0, 2) + jpeg(Triple(0x0112, 3, 6))
        assertEquals("a tag after the image data isn't the picture's", 1, jpegOrientation(noExif))
    }
}
