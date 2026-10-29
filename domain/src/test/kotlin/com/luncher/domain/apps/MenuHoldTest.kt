package com.luncher.domain.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuHoldTest {

    @Test
    fun `Android's defaults give Luncher's own hold`() {
        assertEquals(350, menuHold(500))
        assertEquals(350, menuHold(400))
    }

    // Set in the accessibility settings: the user needs the time.
    @Test
    fun `a longer timeout is kept`() {
        assertEquals(1000, menuHold(1000))
        assertEquals(1500, menuHold(1500))
        assertEquals(501, menuHold(501))
    }

    @Test
    fun `the hold is never longer than the system's`() {
        assertEquals(300, menuHold(300))
    }
}
