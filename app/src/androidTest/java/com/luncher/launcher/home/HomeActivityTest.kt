package com.luncher.launcher.home

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.launcher.AppGraph
import com.luncher.launcher.LuncherApplication
import com.luncher.launcher.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The home screen on a real device, driven by real key events. */
@RunWith(AndroidJUnit4::class)
class HomeActivityTest {

    private val application = ApplicationProvider.getApplicationContext<LuncherApplication>()

    @Before
    fun useFakeApps() {
        application.graph = object : AppGraph(application) {
            override val installedApps = FakeInstalledApps(app("movies"), app("music"))
        }
    }

    @After
    fun restoreRealApps() {
        // The process outlives the test, unlike under Robolectric.
        application.graph = AppGraph(application)
    }

    // Only testable here: on the real home screen, Android restarts a closed home activity at once,
    // so from the outside (UI Automator) it would look as if Back had done nothing.
    @Test
    fun backKey_doesNotCloseTheHomeScreen() {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            pressBackUnconditionally()

            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            onView(withId(R.id.home_status)).check(matches(isDisplayed()))
        }
    }
}
