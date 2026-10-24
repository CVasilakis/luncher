package com.luncher.launcher.apps

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.provider.Settings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PackageManagerAppInfoScreenTest {

    private val context = RuntimeEnvironment.getApplication()

    /** A settings app whose activity takes [action], for any `package:` Uri, as its manifest would declare. */
    private fun installSettings(action: String, default: Boolean = true) {
        val component = ComponentName("com.android.tv.settings", "com.android.tv.settings.AppManagementActivity")
        val packages = shadowOf(context.packageManager)
        packages.addOrUpdateActivity(ActivityInfo().apply { packageName = component.packageName; name = component.className })
        packages.addIntentFilterForActivity(
            component,
            IntentFilter(action).apply {
                if (default) addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("package")
            },
        )
    }

    @Test
    fun `the page exists where a settings app has it`() {
        installSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)

        assertTrue(PackageManagerAppInfoScreen(context).exists())
    }

    // Android TV 5.1's settings: only the list of all apps.
    @Test
    fun `the page doesn't exist where the settings have only the list of apps`() {
        installSettings(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)

        assertFalse(PackageManagerAppInfoScreen(context).exists())
    }

    // startActivity wouldn't find it either: an implicit intent asks for CATEGORY_DEFAULT.
    @Test
    fun `the page doesn't exist where its activity can't be started by its action`() {
        installSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, default = false)

        assertFalse(PackageManagerAppInfoScreen(context).exists())
    }
}
