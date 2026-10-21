package com.luncher.launcher.pictures

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)   // to make pictures as Android's encoders do
class WholeFilesTest {

    private val picture = TestPictureFiles.stacked(96, 54, Color.RED, Color.BLUE)
    private val jpeg = encode(Bitmap.CompressFormat.JPEG)
    private val png = TestPictureFiles.png(picture)

    @Suppress("DEPRECATION")   // WEBP: the one that exists on every API level
    private val webp = encode(Bitmap.CompressFormat.WEBP)

    private fun encode(format: Bitmap.CompressFormat) =
        ByteArrayOutputStream().also { picture.compress(format, 90, it) }.toByteArray()

    private fun whole(bytes: ByteArray) = isWhole(ByteArrayInputStream(bytes))

    @Test
    fun `a JPEG, PNG or WebP as an encoder writes it is whole`() {
        assertTrue(whole(jpeg))
        assertTrue(whole(TestPictureFiles.jpeg(picture, orientation = 6)))
        assertTrue(whole(png))
        assertTrue(whole(webp))
    }

    @Test
    fun `cut short anywhere, it isn't`() {
        for ((name, bytes) in listOf("jpeg" to jpeg, "png" to png, "webp" to webp)) {
            for (cut in 12 until bytes.size) assertFalse("$name cut at $cut of ${bytes.size}", whole(bytes.copyOf(cut)))
        }
    }

    @Test
    fun `what follows a JPEG's end isn't its own, a motion photo's video or padding`() {
        assertTrue(whole(jpeg + byteArrayOf(0xFF.toByte(), 0x47, 0, 0, 0, 0x18) + "ftypmp42".toByteArray() + ByteArray(1000) { (it * 7).toByte() }))
        assertTrue(whole(jpeg + ByteArray(4096)))
    }

    @Test
    fun `a JPEG with a marker inside its scan is damaged, its decoder filling the rest gray`() {
        assertTrue(whole(sequential(data(0x12, 0xFF, 0x00, 0x34))))
        assertFalse(whole(sequential(data(0x12, 0xFF, 0x47, 0x34))))
        assertFalse("a segment between scans, after the last", whole(sequential(data(0x12, 0xFF, 0xC4, 0, 2, 0x34))))
    }

    @Test
    fun `a JPEG's restart markers come in order`() {
        assertTrue(whole(sequential(data(1, 0xFF, 0xD0, 2, 0xFF, 0xD1, 3, 0xFF, 0xD2, 4, 0xFF, 0xD3, 5, 0xFF, 0xD4, 6, 0xFF, 0xD5, 7, 0xFF, 0xD6, 8, 0xFF, 0xD7, 9, 0xFF, 0xD0))))
        assertFalse(whole(sequential(data(1, 0xFF, 0xD0, 2, 0xFF, 0xD3, 3))))
    }

    @Test
    fun `a sequential JPEG ends once each of its components had a scan`() {
        val scans = segment(0xDA, 1, 1, 0, 0, 63, 0) + data(5) + segment(0xDA, 1, 2, 0, 0, 63, 0) + data(6) + segment(0xDA, 1, 3, 0, 0, 63, 0) + data(7)
        assertTrue(whole(start(0xC0) + scans + END))
        assertFalse(whole(start(0xC0) + segment(0xDA, 1, 1, 0, 0, 63, 0) + data(5) + END))
    }

    @Test
    fun `a progressive JPEG's scans may have tables between them, nothing else`() {
        val scan = segment(0xDA, 3, 1, 0, 2, 0x11, 3, 0x11, 0, 0, 0) + data(5, 0xFF, 0x00, 6)
        val table = segment(0xC4, 0x10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        assertTrue(whole(start(0xC2) + scan + table + scan + END))
        assertFalse(whole(start(0xC2) + scan + data(0xFF, 0x47) + scan + END))
    }

    @Test
    fun `a PNG with a damaged chunk the decoder needs is damaged, not with one it skips`() {
        val flipped = png.copyOf().apply { this[indexOf("IDAT") + 6] = (this[indexOf("IDAT") + 6].toInt() xor 0x10).toByte() }
        assertFalse(whole(flipped))
        val note = chunk("tEXt", "Comment\u0000hello".toByteArray())
        note[note.size - 1] = (note[note.size - 1].toInt() xor 1).toByte()
        val iend = png.indexOf("IEND") - 4
        assertTrue(whole(png.copyOfRange(0, iend) + note + png.copyOfRange(iend, png.size)))
    }

    @Test
    fun `any other file is for its decoder to judge`() {
        assertTrue(whole(TestPictureFiles.heic()))
        assertTrue(whole("not a picture".toByteArray()))
        assertTrue(whole(ByteArray(0)))
    }

    /** A JPEG's start up to its first scan: its frame of [marker] with three components, and a table. */
    private fun start(marker: Int) =
        bytes(0xFF, 0xD8) + segment(0xE0, 'J'.code, 'F'.code, 'I'.code, 'F'.code, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0) +
            segment(0xDB, 0, *IntArray(64) { 1 }) + segment(marker, 8, 0, 16, 0, 16, 3, 1, 0x22, 0, 2, 0x11, 0, 3, 0x11, 0)

    /** A sequential JPEG, its three components in one scan, the scan's data [data]. */
    private fun sequential(data: ByteArray) = start(0xC0) + segment(0xDA, 3, 1, 0, 2, 0x11, 3, 0x11, 0, 63, 0) + data + END

    private fun segment(marker: Int, vararg payload: Int) =
        bytes(0xFF, marker, (payload.size + 2) shr 8, (payload.size + 2) and 0xFF) + bytes(*payload)

    private fun data(vararg values: Int) = bytes(*values)

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).run {
            writeInt(data.size)
            writeBytes(type)
            write(data)
            writeInt(CRC32().apply { update(type.toByteArray()); update(data) }.value.toInt())
        }
        return out.toByteArray()
    }

    private fun ByteArray.indexOf(text: String): Int {
        val pattern = text.toByteArray()
        return (0..size - pattern.size).first { at -> pattern.indices.all { this[at + it] == pattern[it] } }
    }

    private companion object {
        val END = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
    }
}
