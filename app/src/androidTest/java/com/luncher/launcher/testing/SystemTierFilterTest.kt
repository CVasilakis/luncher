package com.luncher.launcher.testing

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith

/** Outside the system package on purpose: it must run on every version, where the filter acts. */
@RunWith(AndroidJUnit4::class)
class SystemTierFilterTest {

    private val filter = SystemTierFilter()

    @Test
    fun systemTests_runOnlyFromApi24() {
        // By name: loading HomeKeyTest would load UI Automator, which the filter keeps off API 22-23.
        val homeKeyTest = Description.createTestDescription("com.luncher.launcher.system.HomeKeyTest", "test")
        assertEquals(Build.VERSION.SDK_INT >= 24, filter.shouldRun(homeKeyTest))
    }

    @Test
    fun inAppTests_runOnEveryVersion() {
        val homeActivityTest = Description.createTestDescription("com.luncher.launcher.home.HomeActivityTest", "test")
        assertTrue(filter.shouldRun(homeActivityTest))
    }
}
