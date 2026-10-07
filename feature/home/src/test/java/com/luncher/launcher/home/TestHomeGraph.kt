package com.luncher.launcher.home

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import com.luncher.domain.apps.AppArrangements
import com.luncher.domain.apps.AppImages
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.InstalledApps
import com.luncher.domain.clock.Clock
import com.luncher.domain.clock.FakeClock
import com.luncher.launcher.apps.BannerImages

/**
 * The ports a test gives the home screen: fakes, which a test replaces with its own
 * (`object : TestHomeGraph(application) { override … }`), and the real images, so the screenshots
 * show the tiles as the app draws them.
 */
open class TestHomeGraph(context: Context) : HomeGraph {
    override val installedApps: InstalledApps = FakeInstalledApps()
    override val appArrangements: AppArrangements = FakeAppArrangements()
    override val appImages: AppImages<Bitmap> = BannerImages(context)
    override val clock: Clock = FakeClock()
}

/** The Application of this module's JVM tests (robolectric.properties): it holds the graph a test sets. */
class HomeTestApplication : Application(), HomeGraph.Owner {

    /** Set by each test before it starts the home screen. */
    lateinit var graph: HomeGraph

    override val homeGraph: HomeGraph get() = graph
}
