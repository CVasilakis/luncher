pluginManagement {
    // The convention plugins shared by the Android modules (build-logic/README.md).
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Luncher"
include(":app", ":domain", ":ui", ":platform", ":feature:home", ":feature:settings")
