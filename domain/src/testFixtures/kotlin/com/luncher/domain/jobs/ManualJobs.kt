package com.luncher.domain.jobs

/** [Jobs] that run when the test says ([runAll]), in the order the real ones would. */
class ManualJobs : Jobs {

    private class Queued(val priority: Int, val order: Int, val job: () -> Unit)

    private val queue = mutableListOf<Queued>()
    private var count = 0

    /** How many jobs are queued, not run yet. */
    val pending: Int get() = queue.size

    /** The priorities of the queued jobs, in the order they came. */
    fun pendingPriorities(): List<Int> = queue.map { it.priority }

    override fun run(priority: Int, job: () -> Unit): Cancellable {
        val queued = Queued(priority, count++, job)
        queue += queued
        return Cancellable { queue.remove(queued) }
    }

    /** Runs the queued jobs, and those they queue, the highest priority first, then the oldest. */
    fun runAll() {
        @Suppress("ControlFlowWithEmptyBody")
        while (runNext()) {
        }
    }

    /** Runs the one job the real thread would run next, if one is queued; whether one was. */
    fun runNext(): Boolean {
        if (queue.isEmpty()) return false
        val next = queue.maxWith(compareBy<Queued> { it.priority }.thenByDescending { it.order })
        queue.remove(next)
        next.job()
        return true
    }
}
