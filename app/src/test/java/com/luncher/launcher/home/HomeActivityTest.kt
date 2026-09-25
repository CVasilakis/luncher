package com.luncher.launcher.home

import android.widget.TextView
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import com.luncher.launcher.AppGraph
import com.luncher.launcher.LuncherApplication
import com.luncher.launcher.R
import com.luncher.launcher.TV_1080P
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TV_1080P)
class HomeActivityTest {

    private val installedApps = FakeInstalledApps(app("movies"), app("music"), app("news"))

    @Before
    fun useFakeApps() {
        // Robolectric creates a fresh application per test, so there's nothing to restore.
        val application = RuntimeEnvironment.getApplication() as LuncherApplication
        application.graph = object : AppGraph(application) {
            override val installedApps = this@HomeActivityTest.installedApps
        }
    }

    private fun HomeActivity.status() = findViewById<TextView>(R.id.home_status).text.toString()

    @Test
    fun `shows how many TV apps are installed`() {
        val activity = Robolectric.buildActivity(HomeActivity::class.java).setup().get()

        assertEquals("3 TV apps found", activity.status())
    }

    @Test
    fun `counts again when the home screen comes back`() {
        val controller = Robolectric.buildActivity(HomeActivity::class.java).setup()

        controller.pause().stop()                   // the user opened an app...
        installedApps.apps += app("games")          // ...and installed another one meanwhile
        controller.restart().start().resume()

        assertEquals("4 TV apps found", controller.get().status())
    }
}
