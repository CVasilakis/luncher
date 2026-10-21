package com.luncher.launcher.pictures

import java.io.InputStream
import java.util.zip.CRC32

/**
 * Whether the picture file [input] reads is whole, as far as its format tells: not cut short, its
 * structure intact. A JPEG, PNG or WebP cut short or damaged decodes without an error on most
 * Android versions, the missing part black, transparent or gray, so this is read before it's
 * decoded. Reads at most to the file's end, without decoding anything; any other format is
 * whole here, for its decoder to judge. A read that fails ends it with an `IOException`.
 *
 * - **JPEG:** its segments up to the first scan, then the scans' data up to the end marker (EOI),
 *   every marker met there one that may come between scans, the restart markers in order. A
 *   marker anywhere else ends the scan for the decoder, which fills the rest gray. What follows
 *   the end marker (a motion photo's video, padding) isn't read.
 * - **PNG:** its chunks up to the end chunk (IEND), each inside the file, the checksums of those
 *   the decoder needs (IHDR, PLTE, IDAT, IEND): a damaged one makes the decoder fail, partly draw,
 *   or, on API 22 and 23, run forever without reading, which no stop can end. A damaged optional
 *   chunk is skipped by decoders, and here.
 * - **WebP:** as long as its RIFF header says.
 */
internal fun isWhole(input: InputStream): Boolean {
    val bytes = Bytes(input)
    val first = bytes.read()
    val second = bytes.read()
    return when {
        first == 0xFF && second == SOI -> wholeJpeg(bytes)
        first == 0x89 && second == 'P'.code -> wholePng(bytes)
        first == 'R'.code && second == 'I'.code -> wholeWebp(bytes)
        else -> true
    }
}

private fun wholeJpeg(bytes: Bytes): Boolean {
    var components = 0
    var progressive = false
    var scanned = 0
    // The segments up to the first scan.
    while (true) {
        val marker = bytes.marker()
        if (marker < 0 || marker == EOI) return false
        if (marker in RST0..RST7 || marker == TEM) continue   // no length
        val length = bytes.u16()
        if (length < 2) return false
        when (marker) {
            in FRAMES -> {
                if (length < 8) return false
                if (!bytes.skip(5)) return false   // precision, height, width
                components = bytes.read()
                progressive = marker in PROGRESSIVE
                if (!bytes.skip(length - 8)) return false
            }
            SOS -> {
                if (length < 3) return false
                scanned += bytes.read()
                if (!bytes.skip(length - 3)) return false
                break
            }
            else -> if (!bytes.skip(length - 2)) return false
        }
    }
    // The scans' data, and what comes between scans, up to the end marker.
    var restart = 0
    while (true) {
        val marker = bytes.marker()
        when {
            marker < 0 -> return false
            marker == 0 -> continue   // a 0xFF byte of the data
            marker in RST0..RST7 -> {
                if (marker - RST0 != restart) return false
                restart = (restart + 1) % 8
                continue
            }
            // A sequential picture's scans are over once each component had its own.
            marker == EOI -> return progressive || scanned >= components
            !progressive && scanned >= components && marker != DNL -> return false
            marker != SOS && marker !in BETWEEN_SCANS -> return false
        }
        val length = bytes.u16()
        if (length < 2) return false
        if (marker == SOS) {
            if (length < 3) return false
            scanned += bytes.read()
            if (!bytes.skip(length - 3)) return false
            restart = 0
        } else if (!bytes.skip(length - 2)) {
            return false
        }
    }
}

private fun wholePng(bytes: Bytes): Boolean {
    if (!bytes.skip(6)) return false   // the rest of the signature
    val crc = CRC32()
    while (true) {
        val length = bytes.u32()
        val type = bytes.u32()
        if (length < 0 || type < 0) return false
        // A critical chunk's type starts with an upper-case letter.
        if (type ushr 24 and 0x20 == 0L) {
            crc.reset()
            for (shift in intArrayOf(24, 16, 8, 0)) crc.update((type ushr shift and 0xFF).toInt())
            if (!bytes.read(length, crc) || bytes.u32() != crc.value) return false
        } else if (!bytes.skip(length + 4)) {
            return false
        }
        if (type == IEND) return true
    }
}

private fun wholeWebp(bytes: Bytes): Boolean {
    if (bytes.read() != 'F'.code || bytes.read() != 'F'.code) return true   // not a RIFF file
    val size = bytes.u32le()
    if (size < 0) return false
    return bytes.skip(size)
}

/** [input]'s bytes, one at a time, through a buffer of its own; -1 past the end. */
private class Bytes(private val input: InputStream) {
    private val buffer = ByteArray(64 * 1024)
    private var at = 0
    private var size = 0

    fun read(): Int {
        if (at == size && !fill()) return -1
        return buffer[at++].toInt() and 0xFF
    }

    /** The next marker's code: what follows a 0xFF and its fill bytes, the bytes before skipped; -1 past the end. */
    fun marker(): Int {
        while (true) {
            if (at == size && !fill()) return -1
            // The bytes up to the next 0xFF, at once: most of a JPEG is data between markers.
            while (at < size && buffer[at] != FF) at++
            if (at < size) break
        }
        var code: Int
        do code = read() while (code == 0xFF)
        return code
    }

    /** Whether [count] bytes could be skipped: the file is at least that long from here. */
    fun skip(count: Long): Boolean {
        var left = count
        while (left > 0) {
            if (at == size && !fill()) return false
            val here = minOf(left, (size - at).toLong()).toInt()
            at += here
            left -= here
        }
        return true
    }

    fun skip(count: Int) = skip(count.toLong())

    /** Reads [count] bytes into [crc]; whether there were that many. */
    fun read(count: Long, crc: CRC32): Boolean {
        var left = count
        while (left > 0) {
            if (at == size && !fill()) return false
            val here = minOf(left, (size - at).toLong()).toInt()
            crc.update(buffer, at, here)
            at += here
            left -= here
        }
        return true
    }

    /** Two bytes, most significant first; -1 past the end. */
    fun u16(): Int {
        val high = read()
        val low = read()
        return if (low < 0) -1 else (high shl 8) or low
    }

    /** Four bytes, most significant first; -1 past the end. */
    fun u32(): Long {
        val high = u16()
        val low = u16()
        return if (high < 0 || low < 0) -1 else (high.toLong() shl 16) or low.toLong()
    }

    /** Four bytes, least significant first, as RIFF's sizes; -1 past the end. */
    fun u32le(): Long {
        var value = 0L
        for (shift in intArrayOf(0, 8, 16, 24)) {
            val byte = read()
            if (byte < 0) return -1
            value = value or (byte.toLong() shl shift)
        }
        return value
    }

    private fun fill(): Boolean {
        val read = input.read(buffer)
        at = 0
        size = maxOf(read, 0)
        return read > 0
    }
}

private const val FF = 0xFF.toByte()

// JPEG markers: the start and end of the image, a scan's start, the restart markers.
private const val SOI = 0xD8
private const val EOI = 0xD9
private const val SOS = 0xDA
private const val RST0 = 0xD0
private const val RST7 = 0xD7
private const val TEM = 0x01

/** Where a picture's height is given after the first scan: a scan of its own may follow. */
private const val DNL = 0xDC

/** The frames' markers (SOF0 to SOF15, but DHT, JPG and DAC, which share their range). */
private val FRAMES = (0xC0..0xCF).toSet() - setOf(0xC4, 0xC8, 0xCC)

/** The frames whose scans each add detail to the whole picture, in any number. */
private val PROGRESSIVE = setOf(0xC2, 0xC6, 0xCA, 0xCE)

/** The segments that may come between two scans: tables, a restart interval, comments, application data. */
private val BETWEEN_SCANS = setOf(0xC4, 0xCC, 0xDB, 0xDD, DNL, 0xFE) + (0xE0..0xEF)

/** "IEND", as [isWhole] reads a PNG chunk's type. */
private const val IEND = 0x49454E44L
