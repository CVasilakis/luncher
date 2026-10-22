// What every feature shares, UI only: the app-wide theme and colors, the themed views every
// screen is made of, and helpers such as color(id). Depends on :domain only, for the theme's
// colors (ColorRoles), which its views take (README.md).
plugins {
    alias(libs.plugins.luncher.android.library)
}

android {
    namespace = "com.luncher.launcher.ui"
    // src/testFixtures: what the JVM tests of the screens share (the TV screens, layout checks).
    testFixtures {
        enable = true
    }
}

dependencies {
    api(project(":domain"))
    testFixturesImplementation(libs.junit)
    // The screenshots' capture with the front window focused (Captures.kt): the libraries the
    // features' tests have already.
    testFixturesImplementation(libs.robolectric)
    testFixturesImplementation(libs.roborazzi)
}
