package com.luncher.launcher.ui

import android.app.Activity
import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ForegroundTest {

    @Test
    fun `a screen is in front from its start to its stop`() {
        Foreground.register(RuntimeEnvironment.getApplication() as Application)
        assertFalse(Foreground.inFront)

        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        assertTrue(Foreground.inFront)

        controller.pause()
        assertTrue("paused, still in sight", Foreground.inFront)

        controller.stop()
        assertFalse(Foreground.inFront)
    }
}
