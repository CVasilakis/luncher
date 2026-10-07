import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** The API level the app targets, and the one Robolectric runs the JVM tests on unless a test says otherwise. */
internal const val TARGET_SDK = 36

/** What every Android module shares: SDK levels, bytecode level, warnings as errors, and the JVM tests' setup. */
internal fun Project.configureAndroid(android: CommonExtension) {
    android.compileSdk = TARGET_SDK
    android.defaultConfig.minSdk = 22

    android.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    android.compileOptions.targetCompatibility = JavaVersion.VERSION_17

    // Every warning fails lint (`lintDebug`, run in CI); its exceptions and their reasons: the
    // lint.xml files, the root one for every module and a module's own for its files.
    android.lint.warningsAsErrors = true

    // Robolectric and Roborazzi need the merged resources and manifest.
    android.testOptions.unitTests.isIncludeAndroidResources = true
    // Robolectric's Android 16 (API 36) framework uses a JDK-internal class the JDK doesn't
    // open by default; without this every Robolectric test fails with IllegalAccessException.
    android.testOptions.unitTests.all { it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED") }

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions.allWarningsAsErrors.set(true)
    }
}
