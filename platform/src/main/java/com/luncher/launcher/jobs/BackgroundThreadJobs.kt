package com.luncher.launcher.jobs

import android.os.Process
import com.luncher.domain.jobs.Cancellable
import com.luncher.domain.jobs.Jobs
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * [Jobs] on one thread, started with the first job, at Android's background thread priority: a job
 * gets the CPU the screens leave. A job that throws ends the app, as on the main thread, so a job
 * catches what it can expect (a full disk).
 */
class BackgroundThreadJobs : Jobs {

    private val count = AtomicLong()

    // One thread, kept; the queue takes the job of highest priority first.
    private val executor = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, PriorityBlockingQueue()) { runnable ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            runnable.run()
        }, "luncher-jobs")
    }

    override fun run(priority: Int, job: () -> Unit): Cancellable {
        val queued = Queued(priority, count.getAndIncrement(), job)
        executor.execute(queued)
        return Cancellable { executor.remove(queued) }
    }

    /** A job in the queue; [order] keeps the order they came in among equal priorities. */
    private class Queued(val priority: Int, val order: Long, val job: () -> Unit) : Runnable, Comparable<Queued> {
        override fun run() = job()

        override fun compareTo(other: Queued): Int =
            if (priority != other.priority) other.priority.compareTo(priority) else order.compareTo(other.order)
    }
}
