package com.luncher.launcher

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.launcher.home.HomeActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/** [RetryWhenCovered] on a real device, with the stock launcher brought over a test's screen on purpose. */
@RunWith(AndroidJUnit4::class)
class RetryWhenCoveredTest {

    @get:Rule
    val retryWhenCovered = RetryWhenCovered()

    private val application = ApplicationProvider.getApplicationContext<LuncherApplication>()

    /** Kept across the retry, which runs on the same instance. */
    private var attempts = 0

    @Before
    fun startFromTheHomeScreen() {
        waitForHomeScreen()
    }

    @After
    fun restoreRealApps() {
        // The process outlives the test, unlike under Robolectric.
        application.graph = AppGraph(application)
    }

    /**
     * Covers its own screen with the stock launcher on its first attempt only, then checks that
     * its screen is in front. Without the rule it fails; with it, the second attempt passes.
     */
    @Test
    fun coveredByTheStockLauncher_passesOnTheRetry() {
        attempts++
        application.graph = object : AppGraph(application) {
            override val installedApps = FakeInstalledApps(app("games"))
            override val appArrangements = FakeAppArrangements()
        }
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            waitForFocus(HomeActivity::class.java)
            if (attempts == 1) coverWithTheStockLauncher()

            assertEquals("The home screen under test isn't in front", Lifecycle.State.RESUMED, scenario.state)
        }
    }

    /**
     * What the stock launcher does when it comes back on its own: a HOME intent, which brings its
     * home task over the test's (on API 22, the "choose home app" dialog). Then waits until that
     * has settled in front, so the test fails only once it's covered.
     */
    private fun coverWithTheStockLauncher() {
        shell("am start -a android.intent.action.MAIN -c android.intent.category.HOME")
        waitForHomeScreen()
    }

    @Test
    fun anotherFailure_isRethrownUnchanged_withoutARetry() {
        val failure = AssertionError("not covered")
        var runs = 0
        val test = object : Statement() {
            override fun evaluate() {
                runs++
                throw failure
            }
        }

        val thrown = assertThrows(AssertionError::class.java) {
            RetryWhenCovered().apply(test, Description.EMPTY).evaluate()
        }

        assertSame(failure, thrown)
        assertEquals(1, runs)
    }
}
