package com.luncher.domain.jobs

/**
 * One thing a screen loads in the background (a preview, a thumbnail), of which only the latest
 * request counts (docs/ARCHITECTURE.md#background-work-and-changes-while-shown, point 4): a new
 * request drops the one before, cancelled if it hasn't started, its result ignored if it finishes
 * late. So what the screen ends up showing depends only on the last request, never on which job
 * finished first. The screen calls [stop] in `onStop`, which drops everything.
 *
 * Used on the main thread only. [onMain] runs a job's result there (a Handler's `post`).
 */
class LatestRequest(private val jobs: Jobs, private val priority: Int, private val onMain: (() -> Unit) -> Unit) {

    /** Counts the requests: a result whose request isn't the latest any more is dropped. */
    private var latest = 0
    private var queued: Cancellable? = null

    /** Runs [work] as a job, then [done] with its result on the main thread, unless another request or [stop] came first. */
    fun <T> load(work: () -> T, done: (T) -> Unit) {
        stop()
        val request = latest
        queued = jobs.run(priority) {
            val result = work()
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
        val dropped = queued != null
        queued?.cancel()
        queued = null
        return dropped
    }
}
