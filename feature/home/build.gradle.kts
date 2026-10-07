// The home screen: the top bar, the tiles and arrange mode (README.md).
plugins {
    alias(libs.plugins.luncher.android.library)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.luncher.launcher.home"
}

dependencies {
    api(project(":domain"))
    implementation(project(":ui"))
    // Opens the settings panel by its activity's class, the one thing it uses of that feature.
    implementation(project(":feature:settings"))

    testImplementation(testFixtures(project(":domain")))
    testImplementation(testFixtures(project(":ui")))
    // The real BannerImages, so the screenshots show tiles as the app draws them.
    testImplementation(project(":platform"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
}
