package com.luncher.domain.apps

import com.luncher.domain.apps.FakeInstalledApps.Companion.app
import org.junit.Assert.assertEquals
import org.junit.Test

class BannerTest {

    @Test
    fun `an app with a banner shows its banner`() {
        assertEquals(Banner.AppBanner, bannerFor(app("movies", hasBanner = true)))
    }

    @Test
    fun `an app without a banner shows its icon`() {
        assertEquals(Banner.AppIcon, bannerFor(app("movies", hasBanner = false)))
    }
}
