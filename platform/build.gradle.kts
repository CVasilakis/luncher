// The adapters: the domain's ports, and the images the screens show, on Android APIs. Only :app
// depends on it, where AppGraph creates them; the features see the ports only (README.md).
plugins {
    alias(libs.plugins.luncher.android.library)
}

android {
    namespace = "com.luncher.launcher.platform"
}

dependencies {
    api(project(":domain"))
    implementation(project(":ui"))

    testImplementation(testFixtures(project(":domain")))
    testImplementation(testFixtures(project(":ui")))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
