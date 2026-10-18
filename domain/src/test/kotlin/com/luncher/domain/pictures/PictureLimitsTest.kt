package com.luncher.domain.pictures

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureLimitsTest {

    @Test
    fun `takes files up to 50 MB`() {
        assertTrue(PictureLimits.allowsBytes(50_000_000))
        assertFalse(PictureLimits.allowsBytes(50_000_001))
    }

    @Test
    fun `takes pictures up to 64 megapixels, counted without overflow`() {
        assertTrue(PictureLimits.allowsSize(8000, 8000))
        assertFalse(PictureLimits.allowsSize(8001, 8000))
        assertFalse(PictureLimits.allowsSize(100_000, 100_000))
    }

    @Test
    fun `takes shares of up to 100 pictures`() {
        assertTrue(PictureLimits.allowsShare(100))
        assertFalse(PictureLimits.allowsShare(101))
    }
}
