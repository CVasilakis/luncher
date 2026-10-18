package com.luncher.launcher.outside

import android.content.ContentResolver
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import com.luncher.launcher.outside.OutsideCallEnded.Reason
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Asks and reads a Uri another app answers for, for one job, which gives up once [stop] is set or
 * the other app has answered nothing for [limitMs] ms: then what it calls throws
 * [OutsideCallEnded]. Its query and its open are made through [calls], each on a thread of its
 * own, as neither can be stopped once made; their `CancellationSignal` is cancelled when the job
 * gives up, so an app that heeds it stops its work too. What the app sends is read only once
 * [calls]' wait for data says some came, waiting at most [SLICE_MS] ms at a time, so the job sees a stop
 * soon: closing a stream doesn't end a read that waits on another thread (API 22, 24 and 28; it
 * does on 36). Each
 * answer (the query's, the open's, each piece of data) starts the time limit again, so a slow
 * transfer that goes on isn't cut. Time is [calls]' clock. Used on one thread, a job's.
 */
internal class OutsideReader(
    private val resolver: ContentResolver,
    private val calls: OutsideCalls,
    private val stop: Stop,
    private val limitMs: Long = LIMIT_MS,
) {

    private var answeredAt = calls.now()

    /** What [read] makes of the rows [uri] answers to [projection] with, null for none. */
    fun <T> query(uri: Uri, projection: Array<String>, read: (Cursor?) -> T): T =
        ask(close = {}) { signal -> resolver.query(uri, projection, null, null, null, signal).use(read) }

    /** What [uri] sends, read to its end or until the job gives up. The caller closes it. */
    fun open(uri: Uri): InputStream {
        val descriptor = ask(close = { it?.close() }) { signal -> resolver.openAssetFileDescriptor(uri, "r", signal) }
        return Sent(descriptor ?: throw FileNotFoundException("no stream for $uri"))
    }

    private fun <T> ask(close: (T) -> Unit, call: (CancellationSignal) -> T): T {
        val signal = CancellationSignal()
        val answer = try {
            calls.call(stop, remainingMs(), close) { call(signal) }
        } catch (e: OutsideCallEnded) {
            signal.cancel()
            throw e
        }
        answered()
        return answer
    }

    /** What's left of the time limit, in ms; [OutsideCallEnded] if nothing is. */
    private fun remainingMs(): Long {
        val remaining = limitMs - (calls.now() - answeredAt)
        if (remaining <= 0) throw OutsideCallEnded(Reason.NO_ANSWER)
        return remaining
    }

    private fun answered() {
        answeredAt = calls.now()
    }

    /** The stream of [descriptor], each read waiting for data first, up to its declared length if it has one. */
    private inner class Sent(private val descriptor: AssetFileDescriptor) : InputStream() {

        private val input = descriptor.createInputStream()
        private var left = descriptor.declaredLength

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            // At the end it declared, the pipe behind it may still be open: no wait for data that won't come.
            if (left == 0L) return -1
            while (true) {
                if (stop.isStopped) throw OutsideCallEnded(Reason.STOPPED)
                if (calls.waitForData(descriptor.fileDescriptor, minOf(remainingMs(), SLICE_MS.toLong()).toInt())) break
            }
            val n = input.read(buffer, offset, length)
            answered()
            if (n > 0 && left != AssetFileDescriptor.UNKNOWN_LENGTH) left -= n
            return n
        }

        override fun close() = input.close()
    }

    companion object {
        /** How long another app may answer nothing, in ms. */
        const val LIMIT_MS = 15_000L

        /** How long a read waits for data before it looks whether to give up, in ms. */
        const val SLICE_MS = 250
    }
}
