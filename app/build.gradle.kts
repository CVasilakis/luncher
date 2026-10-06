plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.luncher.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.luncher.launcher"
        minSdk = 22
        targetSdk = 36
        // The version, changed by hand before each release (docs/RELEASING.md#versions). Both are
        // written out, as literals and before any other line naming them: F-Droid finds a
        // release's version by reading this file. The check after the android block keeps them
        // in step.
        versionCode = 1_000_000
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The system tier (UI Automator, the system/ package) runs only from API 24; below that
        // this filter leaves it out on each device. Reasons: SystemTierFilter.
        testInstrumentationRunnerArguments["filter"] = "com.luncher.launcher.SystemTierFilter"
        // A backstop against a test that hangs: the runner fails a test method still running after
        // 15 minutes, and goes on with the next test. A test once hung for over 10 minutes on CI,
        // cause unknown, until the job's own time limit ended the whole run. It times the test
        // method alone, not @Before, @After or rules (a RetryWhenCovered retry gets its own), and
        // is longer than all of a test method's own waits together, waitForHomeScreen()'s 10
        // minutes included, so a test that runs out of one of those still fails with its message.
        testInstrumentationRunnerArguments["timeout_msec"] = "900000"
    }

    buildTypes {
        // A debug build is an app of its own, "Luncher (debug)" (src/debug/), so it installs next
        // to a release, which is signed with another key.
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    testOptions {
        // Robolectric and Roborazzi need the merged resources and manifest.
        unitTests.isIncludeAndroidResources = true
        // Robolectric's Android 16 (API 36) framework uses a JDK-internal class the JDK doesn't
        // open by default; without this every Robolectric test fails with IllegalAccessException.
        unitTests.all { it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// versionName is X.Y.Z, each part 0 to 999, and versionCode follows it: X * 1,000,000 + Y * 1,000
// + Z. So a higher version always has a higher versionCode, which Android needs to update an app.
// Checked whenever Gradle reads this file, so a mismatch fails every build, not only a release's.
android.defaultConfig.run {
    val name = versionName.orEmpty()
    val (major, minor, patch) = Regex("""(0|[1-9]\d{0,2})\.(0|[1-9]\d{0,2})\.(0|[1-9]\d{0,2})""")
        .matchEntire(name)?.destructured
        ?: throw GradleException("versionName must be X.Y.Z, each part 0 to 999, e.g. 1.2.3, not \"$name\"")
    val code = major.toInt() * 1_000_000 + minor.toInt() * 1_000 + patch.toInt()
    if (versionCode != code) {
        val written = code.toString().reversed().chunked(3).joinToString("_").reversed()
        throw GradleException(
            "versionCode must be $written for versionName $name, not $versionCode (docs/RELEASING.md#versions)",
        )
    }
}

dependencies {
    implementation(project(":domain"))

    // Tests only: none of this reaches the APK. Tiers and their tools: docs/TESTING.md.
    testImplementation(testFixtures(project(":domain")))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)

    androidTestImplementation(testFixtures(project(":domain")))
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.uiautomator)
}

// Instrumented test runs (connectedDebugAndroidTest and friends; see docs/TESTING.md).
// 1. AGP's test engine installs with `adb install -t`, without -r, so Android 9 and older (the
//    API 22 to 28 emulators) refuse the install whenever the app is already there, e.g. after
//    `installDebug`. Every run therefore starts by uninstalling; AGP uninstalls after the run anyway.
// 2. When the install fails, AGP runs no test on that device but still reports success. Fail the
//    build instead. With several devices (e.g. the API 22 to 36 emulators) the others' results
//    would hide it, so every device must have run tests: each gets a folder with its
//    device-info.pb, and a TEST-<folder name>.xml next to it once tests ran there.
val connectedTestResults = layout.buildDirectory.dir("outputs/androidTest-results/connected")
val checkConnectedTestsRan = tasks.register("checkConnectedTestsRan") {
    description = "Fails if the last instrumented test run ran no test on one of the devices."
    val results = connectedTestResults
    doLast {
        val suite = Regex("""<testsuite [^>]*\btests="(\d+)"""")
        fun testsIn(report: File) =
            if (report.isFile) suite.findAll(report.readText()).sumOf { it.groupValues[1].toInt() } else 0
        val root = results.get().asFile
        val ran = root.walk()
            .filter { it.isFile && it.name.startsWith("TEST-") && it.name.endsWith(".xml") }
            .sumOf(::testsIn)
        val devicesWithoutTests = root.walk()
            .filter { File(it, "device-info.pb").isFile }
            .filter { testsIn(File(it.parentFile, "TEST-${it.name}.xml")) == 0 }
            .map { it.name }
            .toList()
        if (ran == 0 || devicesWithoutTests.isNotEmpty()) {
            val where = if (devicesWithoutTests.isEmpty()) "" else " on ${devicesWithoutTests.joinToString()}"
            throw GradleException(
                "No instrumented test ran$where. Look above for the cause (e.g. INSTALL_FAILED_...).",
            )
        }
    }
}
tasks.named { it.matches(Regex("connected\\w*AndroidTest")) }.configureEach {
    dependsOn("uninstallAll")
    finalizedBy(checkConnectedTestsRan)
}
