import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin: the launcher's models, rules and ports. No Android, no libraries; the compiler
// enforces this, since nothing Android is on this module's classpath. See ARCHITECTURE.md.
plugins {
    alias(libs.plugins.kotlin.jvm)
    // src/testFixtures: fakes of the ports, shared with :app's tests (see TESTING.md).
    `java-test-fixtures`
}

// Same bytecode level as :app. Set explicitly instead of a toolchain, so any JDK 17+ that runs
// Gradle works without downloading another one.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    testImplementation(libs.junit)
}
