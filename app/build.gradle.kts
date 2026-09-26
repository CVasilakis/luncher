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
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The system tier (UI Automator, the system/ package) runs only from API 24; below that
        // this filter leaves it out on each device. Reasons: docs/TESTING.md.
        testInstrumentationRunnerArguments["filter"] = "com.luncher.launcher.SystemTierFilter"
    }

    buildTypes {
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
