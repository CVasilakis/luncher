package com.luncher.launcher.outside

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopTest {

    private val stop = Stop()

    @Test
    fun `isn't stopped until it's set`() {
        assertFalse(stop.isStopped)
        stop.stop()
        assertTrue(stop.isStopped)
    }

    @Test
    fun `runs what waits for it once, when it's set`() {
        var ran = 0
        stop.whenStopped { ran++ }

        stop.stop()
        stop.stop()

        assertEquals(1, ran)
    }

    @Test
    fun `runs at once what comes after it's set`() {
        stop.stop()
        var ran = 0

        stop.whenStopped { ran++ }

        assertEquals(1, ran)
    }

    @Test
    fun `runs nothing that was removed`() {
        var ran = 0
        val remove = stop.whenStopped { ran++ }

        remove()
        stop.stop()

        assertEquals(0, ran)
    }
}
