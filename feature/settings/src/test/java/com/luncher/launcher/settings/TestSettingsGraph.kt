package com.luncher.launcher.settings

import android.app.Application
import com.luncher.domain.apps.AppArrangements
import com.luncher.domain.apps.FakeAppArrangements
import com.luncher.domain.apps.FakeInstalledApps
import com.luncher.domain.apps.InstalledApps

/**
 * The ports a test gives the settings screens: fakes, which a test replaces with its own
 * (`object : TestSettingsGraph() { override … }`).
 */
open class TestSettingsGraph : SettingsGraph {
    override val installedApps: InstalledApps = FakeInstalledApps()
    override val appArrangements: AppArrangements = FakeAppArrangements()
}

/** The Application of this module's JVM tests (robolectric.properties): it holds the graph a test sets. */
class SettingsTestApplication : Application(), SettingsGraph.Owner {

    /** Set by each test before it starts a settings screen; the panel alone needs no port. */
    var graph: SettingsGraph = TestSettingsGraph()

    override val settingsGraph: SettingsGraph get() = graph
}
