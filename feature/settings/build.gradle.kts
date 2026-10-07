// The settings panel and the screens it opens, such as Hide apps (README.md).
plugins {
    alias(libs.plugins.luncher.android.library)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.luncher.launcher.settings"
}

dependencies {
    api(project(":domain"))
    implementation(project(":ui"))

    testImplementation(testFixtures(project(":domain")))
    testImplementation(testFixtures(project(":ui")))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
}
