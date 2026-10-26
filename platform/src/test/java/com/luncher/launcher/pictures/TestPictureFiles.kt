package com.luncher.launcher.pictures

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32

/** Picture files for the tests, made as cameras and apps make them. */
internal object TestPictureFiles {

    /** A [width] x [height] picture, its left half [left], its right half [right]. */
    fun halves(width: Int, height: Int, left: Int, right: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(right)
        canvas.drawRect(0f, 0f, width / 2f, height.toFloat(), Paint().apply { color = left })
        return bitmap
    }

    /** A [width] x [height] picture, its top half [top], its bottom half [bottom]. */
    fun stacked(width: Int, height: Int, top: Int, bottom: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bottom)
        canvas.drawRect(0f, 0f, width.toFloat(), height / 2f, Paint().apply { color = top })
        return bitmap
    }

    /** A [width] x [height] logo: a red square in its middle, the rest transparent. */
    fun logo(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(width / 4f, height / 4f, width * 3 / 4f, height * 3 / 4f, Paint().apply { color = Color.RED })
        return bitmap
    }

    fun png(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    /** [bitmap] as a JPEG with the EXIF orientation tag [orientation], as a phone writes a photo taken on its side. */
    fun jpeg(bitmap: Bitmap, orientation: Int): ByteArray {
        val jpeg = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
        val tiff = ByteArrayOutputStream()
        DataOutputStream(tiff).run {
            writeBytes("MM"); writeShort(42); writeInt(8)        // big-endian TIFF, IFD0 at 8
            writeShort(1)                                          // one entry
            writeShort(0x0112); writeShort(3); writeInt(1)         // Orientation, SHORT, one value
            writeShort(orientation); writeShort(0)
            writeInt(0)                                            // no next IFD
        }
        val app1 = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + tiff.toByteArray()
        val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), ((app1.size + 2) shr 8).toByte(), (app1.size + 2).toByte()) + app1
        return jpeg.copyOfRange(0, 2) + segment + jpeg.copyOfRange(2, jpeg.size)
    }

    /**
     * A PNG's start that says it's [width] x [height] px: enough for BitmapFactory to read the size,
     * as of a huge picture, without the test making one.
     */
    fun pngHeader(width: Int, height: Int): ByteArray {
        val ihdr = ByteArrayOutputStream()
        DataOutputStream(ihdr).run {
            writeBytes("IHDR"); writeInt(width); writeInt(height)
            writeByte(8); writeByte(6); writeByte(0); writeByte(0); writeByte(0)   // 8-bit RGBA
        }
        val chunk = ihdr.toByteArray()
        val crc = CRC32().apply { update(chunk) }.value.toInt()
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        DataOutputStream(out).run { writeInt(13); write(chunk); writeInt(crc) }
        // Image data that's cut short (reading the size stops before it), then the end.
        for (next in listOf("IDAT".toByteArray() + byteArrayOf(0x78, 0x9C.toByte(), 3, 0), "IEND".toByteArray())) {
            DataOutputStream(out).run { writeInt(next.size - 4); write(next); writeInt(CRC32().apply { update(next) }.value.toInt()) }
        }
        return out.toByteArray()
    }

    /** The start of an iPhone's HEIC photo, which the JVM tests' Android can't decode, as API 22 to 27 can't. */
    fun heic(): ByteArray =
        byteArrayOf(0, 0, 0, 24) + "ftypheic".toByteArray() + byteArrayOf(0, 0, 0, 0) + "mif1heic".toByteArray() + ByteArray(200)

    fun isRed(color: Int) = Color.red(color) > 200 && Color.green(color) < 60 && Color.blue(color) < 60

    fun isBlue(color: Int) = Color.blue(color) > 200 && Color.red(color) < 60 && Color.green(color) < 60
}
