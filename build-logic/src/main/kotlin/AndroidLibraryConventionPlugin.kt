import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.DeviceTestBuilder
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * A library module (:ui, :platform, a feature): an Android library with the settings every
 * Android module shares. A library has no targetSdk of its own; its JVM tests and lint get the
 * app's, so Robolectric runs them on the same API level as the app's tests, not on minSdk.
 *
 * No instrumented tests: they need the installed app, as the device's home screen, so they're all
 * in :app (docs/TESTING.md). Without a test APK of its own, a library adds nothing to
 * `connectedDebugAndroidTest`.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            configureAndroid(this)
            testOptions.targetSdk = TARGET_SDK
            lint.targetSdk = TARGET_SDK
        }
        extensions.configure<LibraryAndroidComponentsExtension> {
            beforeVariants { variant ->
                variant.deviceTests[DeviceTestBuilder.ANDROID_TEST_TYPE]?.enable = false
            }
        }
    }
}
