// The picture library's screens: Pictures, Add from folder, and the way in for a shared picture (README.md).
plugins {
    alias(libs.plugins.luncher.android.library)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.luncher.launcher.pictures"
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
