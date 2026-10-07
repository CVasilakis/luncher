package com.luncher.launcher.testing

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.launcher.AppGraph
import com.luncher.launcher.LuncherApplication
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** [RetryWhenCovered] on a real device, with the stock launcher brought over a test's screen on purpose. */
@RunWith(AndroidJUnit4::class)
class RetryWhenCoveredTest {

    /** While set, holds the rule's reads of the focus until released, as a slow `dumpsys window` would. */
    @Volatile
    private var slowReads: CountDownLatch? = null

    @get:Rule
    val retryWhenCovered = RetryWhenCovered {
        slowReads?.await(SLOW_READ_LIMIT_S, TimeUnit.SECONDS)
        focus()
    }

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
     * Like [coveredByTheStockLauncher_passesOnTheRetry], with the rule's read of the focus slow, as
     * on a starved emulator, where one `dumpsys window` took 18 s: the read begins while the stock
     * launcher covers the screen, and returns only once the test has failed and closed its screen.
     * The rule judges the read by the state when it began, so it's a cover.
     */
    @Test
    fun coverReadOnlyAfterTheScreenClosed_passesOnTheRetry() {
        attempts++
        application.graph = object : AppGraph(application) {
            override val installedApps = FakeInstalledApps(app("games"))
            override val appArrangements = FakeAppArrangements()
        }
        if (attempts > 1) {
            ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
                waitForFocus(HomeActivity::class.java)
                assertEquals("The home screen under test isn't in front", Lifecycle.State.RESUMED, scenario.state)
            }
            return
        }
        val reads = CountDownLatch(1).also { slowReads = it }
        try {
            val scenario = ActivityScenario.launch(HomeActivity::class.java)
            waitForFocus(HomeActivity::class.java)
            coverWithTheStockLauncher()   // the rule's look begins a read, held until released
            scenario.close()              // as `use` does once a covered test has failed
            waitForHomeScreen()           // what the read will see
            reads.countDown()
            assertEquals("The home screen under test isn't in front", Lifecycle.State.RESUMED, scenario.state)
        } finally {
            slowReads = null
            reads.countDown()
        }
    }

    /**
     * What the stock launcher does when it comes back on its own: a HOME intent, which brings its
     * home task over the test's (on API 22, the "choose home app" dialog). Then waits until that
     * has settled in front, so the test fails only once it's covered.
     *
     * On API 22, first until the "choose home app" dialog the test's screen has just covered has
     * begun to finish ([homeChooserNotFinishing]): a HOME intent that reached it before was lost
     * with it, nothing covered the test's screen, and the wait for the home screen ran out its 10
     * minutes, on 2 of 10 boots with the dialog left open by the boot. Re-sending the intent
     * instead would let the rule's held read begin before the cover, and see none. From API 23 on
     * there's no such dialog, and the wait ends at its first look.
     */
    private fun coverWithTheStockLauncher() {
        val deadline = SystemClock.uptimeMillis() + CHOOSER_FINISHING_MS
        while (homeChooserNotFinishing(shell("dumpsys activity activities")) && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(CHOOSER_POLL_MS)
        }
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

    private companion object {
        /** Longer than the test's steps while a read is held; a bound, so a mistake can't hang. */
        const val SLOW_READ_LIMIT_S = 120L

        /**
         * For the "choose home app" dialog to begin finishing once covered: 0.7 s on the emulator.
         * Past it, the HOME intent is sent anyway, as before.
         */
        const val CHOOSER_FINISHING_MS = 10_000L
        const val CHOOSER_POLL_MS = 100L
    }
}
