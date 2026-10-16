package com.luncher.domain.jobs

import org.junit.Assert.assertEquals
import org.junit.Test

class LatestRequestTest {

    private val jobs = ManualJobs()

    /** Results the main thread hasn't run yet, as a Handler would queue them. */
    private val onMain = mutableListOf<() -> Unit>()
    private val request = LatestRequest(jobs, 0) { onMain += it }
    private val shown = mutableListOf<String>()

    private fun runMain() {
        val queued = onMain.toList()
        onMain.clear()
        queued.forEach { it() }
    }

    @Test
    fun `shows a request's result on the main thread, after its job`() {
        request.load({ "sunset" }) { shown += it }
        assertEquals(1, jobs.pending)

        jobs.runAll()
        assertEquals(emptyList<String>(), shown)
        runMain()

        assertEquals(listOf("sunset"), shown)
    }

    @Test
    fun `cancels a request that hasn't started when another comes`() {
        var ran = 0
        request.load({ ran++; "first" }) { shown += it }
        request.load({ "second" }) { shown += it }

        jobs.runAll()
        runMain()

        assertEquals(0, ran)
        assertEquals(listOf("second"), shown)
    }

    @Test
    fun `ignores a result that comes after a newer request`() {
        request.load({ "first" }) { shown += it }
        jobs.runAll()                               // finished, its result not shown yet
        request.load({ "second" }) { shown += it }
        jobs.runAll()

        runMain()

        assertEquals(listOf("second"), shown)
    }

    @Test
    fun `stop drops everything`() {
        request.load({ "queued" }) { shown += it }
        request.stop()
        jobs.runAll()
        runMain()

        request.load({ "running" }) { shown += it }
        jobs.runAll()
        request.stop()
        runMain()

        assertEquals(emptyList<String>(), shown)
        assertEquals(0, jobs.pending)
    }
}
