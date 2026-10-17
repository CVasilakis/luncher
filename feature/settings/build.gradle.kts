// The settings panel and the screens it opens: Hide apps, the appearance editor, the picture
// library, and the way in for a picture another app shares (README.md).
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
