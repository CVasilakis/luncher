// What every feature shares, UI only: the app-wide theme and colors, shared views and helpers
// such as color(id). Depends on no other module (README.md).
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
    testFixturesImplementation(libs.junit)
}
