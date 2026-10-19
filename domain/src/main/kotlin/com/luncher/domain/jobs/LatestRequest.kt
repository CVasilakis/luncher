package com.luncher.domain.jobs

/**
 * One thing a screen loads in the background (a preview, a thumbnail), of which only the latest
 * request counts (docs/ARCHITECTURE.md#background-work-and-changes-while-shown, point 4): a new
 * request drops the one before, cancelled if it hasn't started, its result ignored if it finishes
 * late. So what the screen ends up showing depends only on the last request, never on which job
 * finished first. The screen calls [stop] in `onStop`, which drops everything. A job that's
 * running when its request is dropped is told so (the `stopped` its work is given), so a decode
 * can stop at once rather than hold the jobs' thread for a result nobody will see.
 *
 * Used on the main thread only. [onMain] runs a job's result there (a Handler's `post`).
 */
class LatestRequest(private val jobs: Jobs, private val priority: Int, private val onMain: (() -> Unit) -> Unit) {

    /** Counts the requests: a result whose request isn't the latest any more is dropped. */
    private var latest = 0
    private var queued: Cancellable? = null

    /** Whether the latest request was dropped: set on the main thread, read by its job. */
    private class Dropped {
        @Volatile
        var value = false
    }

    private var dropped = Dropped()

    /**
     * Runs [work] as a job, then [done] with its result on the main thread, unless another request
     * or [stop] came first. [work] is given `stopped`, which says, from the job's thread, whether
     * that happened meanwhile: then its result is ignored, and it may give up.
     */
    fun <T> load(work: (stopped: () -> Boolean) -> T, done: (T) -> Unit) {
        stop()
        val request = latest
        val dropped = Dropped().also { this.dropped = it }
        queued = jobs.run(priority) {
            val result = work { dropped.value }
            onMain {
                if (request == latest) {
                    queued = null
                    done(result)
                }
            }
        }
    }

    /**
     * Drops the latest request: cancelled if it hasn't started, its result ignored if it has.
     * Whether one was dropped, rather than done or dropped before.
     */
    fun stop(): Boolean {
        latest++
        dropped.value = true
        val dropped = queued != null
        queued?.cancel()
        queued = null
        return dropped
    }
}
