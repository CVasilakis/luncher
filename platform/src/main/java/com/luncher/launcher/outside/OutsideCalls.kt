package com.luncher.launcher.outside

import android.os.Process
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import com.luncher.launcher.outside.OutsideCallEnded.Reason
import java.io.FileDescriptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * How Luncher waits on another app: by one clock ([now]), for data it sends ([waitForData], read
 * by [OutsideReader]), and for calls into it that can't be stopped once made (a content provider's
 * query or open, which may never return; [call]). A test makes one with its own clock and wait for
 * data, which Robolectric can't do (its `Os.poll` doesn't wait), and so decides the time of
 * everything that waits through it.
 *
 * Each such call runs on a short-lived thread of its own, which the caller, a job, waits
 * for until it answers, the caller's [Stop] is set, or a time limit passes. Then the job goes on,
 * and leaves the thread to its call. That thread ends when the other app answers, closing what
 * it answers so late, or when its process dies, which makes the call throw. Waiting, it costs no
 * CPU and some tens of KB; nothing in Java can end it sooner.
 *
 * The one place in Luncher that starts such a thread (docs/ARCHITECTURE.md#background-work-and-changes-while-shown,
 * point 6), so it can bound them: at most [MOST] are alive at once, whoever started them, the one
 * a job waits for and those left to their calls. Beyond that a call fails at once, without a
 * thread ([Reason.TOO_MANY]). The bound guards against a leak, a caller that asks an app that
 * hangs again and again, and isn't a setting to tune. A process of its own, which could be killed
 * with its threads, would cost far more on a weak TV than the threads it would free.
 *
 * One per process, made by AppGraph, so the bound holds across adapters.
 */
class OutsideCalls(
    private val most: Int = MOST,
    internal val now: () -> Long = SystemClock::uptimeMillis,
    internal val waitForData: (FileDescriptor, Int) -> Boolean = ::poll,
) {

    private val alive = AtomicInteger()
    private val started = AtomicInteger()

    /** How many outside threads are alive: waited for, or left to their calls. */
    val aliveCount: Int get() = alive.get()

    /**
     * [call]'s answer, made on a thread of its own and waited for here at most [waitMs] ms; what
     * [call] throws is thrown here. Else [OutsideCallEnded]: [stop] was set, the time passed, or
     * too many threads were alive; and then an answer that comes later goes to [close].
     */
    internal fun <T> call(stop: Stop, waitMs: Long, close: (T) -> Unit, call: () -> T): T {
        if (stop.isStopped) throw OutsideCallEnded(Reason.STOPPED)
        if (alive.incrementAndGet() > most) {
            alive.decrementAndGet()
            throw OutsideCallEnded(Reason.TOO_MANY)
        }
        val answer = Answer(close)
        try {
            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                try {
                    answer.give(runCatching(call))
                } finally {
                    alive.decrementAndGet()
                }
            }, "luncher-outside-${started.incrementAndGet()}").apply { isDaemon = true }.start()
        } catch (e: Throwable) {
            alive.decrementAndGet()   // no thread: nothing is left
            throw e
        }
        val unregister = stop.whenStopped(answer::wake)
        try {
            return answer.await(stop, waitMs)
        } finally {
            unregister()
        }
    }

    /** One call's answer, handed from its thread to the one waiting, or, once that one has left, to [close]. */
    private class Answer<T>(private val close: (T) -> Unit) {

        private val lock = ReentrantLock()
        private val changed = lock.newCondition()
        private var result: Result<T>? = null
        private var left = false

        /** On the call's thread. */
        fun give(result: Result<T>) {
            val late = lock.withLock {
                if (!left) {
                    this.result = result
                    changed.signalAll()
                }
                left
            }
            if (late) result.onSuccess { runCatching { close(it) } }
        }

        /** On the thread that stops it. */
        fun wake() = lock.withLock { changed.signalAll() }

        /** On the caller's thread. */
        fun await(stop: Stop, waitMs: Long): T {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMs)
            lock.withLock {
                while (true) {
                    result?.let { return it.getOrThrow() }
                    val remaining = deadline - System.nanoTime()
                    val reason = when {
                        stop.isStopped -> Reason.STOPPED
                        remaining <= 0 -> Reason.NO_ANSWER
                        else -> null
                    }
                    if (reason != null) {
                        left = true
                        throw OutsideCallEnded(reason)
                    }
                    changed.awaitNanos(remaining)
                }
            }
        }
    }

    companion object {
        /** The most outside threads alive at once. */
        const val MOST = 10

        /** Whether data, its end or an error came on [fd] within [timeoutMs] ms. */
        private fun poll(fd: FileDescriptor, timeoutMs: Int): Boolean {
            val wanted = StructPollfd().apply {
                this.fd = fd
                events = OsConstants.POLLIN.toShort()
            }
            return try {
                Os.poll(arrayOf(wanted), timeoutMs) > 0
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.EINTR) false else throw IOException(e)
            }
        }
    }
}
