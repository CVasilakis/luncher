plugins {
    `kotlin-dsl`
}

dependencies {
    // compileOnly: the root build.gradle.kts puts AGP and the Kotlin Gradle Plugin on the build's
    // classpath, at the catalog's versions, and the plugins here use those.
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "luncher.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "luncher.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
    }
}
