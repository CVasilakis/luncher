# build-logic/

Luncher's Gradle convention plugins: what every Android module's build shares, written once. It's
a build of its own, included by the root `settings.gradle.kts` (`includeBuild`), and none of it
reaches the APK.

| Plugin | Applied by | What it sets |
|---|---|---|
| `luncher.android.application` | `:app` | `com.android.application`, with the shared settings below and the targetSdk |
| `luncher.android.library` | `:ui`, `:platform`, the features | `com.android.library`, with the shared settings below; the targetSdk for its JVM tests and lint; no instrumented tests |

The shared settings (`AndroidConventions.kt`): compileSdk and targetSdk 36, minSdk 22; bytecode
level 17; Kotlin and lint warnings as errors; the JVM tests' merged resources and the JDK option
Robolectric's Android 16 framework needs. Each is explained by its comment there. A module's
build file adds only what's its own: its namespace, dependencies, and for `:app` the application
ID, version and release build.

```
build-logic/
├── settings.gradle.kts           its repositories, and the root's version catalog
├── build.gradle.kts              kotlin-dsl; the plugins' IDs
└── src/main/kotlin/
    ├── AndroidConventions.kt                 the settings every Android module shares
    ├── AndroidApplicationConventionPlugin.kt luncher.android.application
    └── AndroidLibraryConventionPlugin.kt     luncher.android.library
```

The plugins compile against AGP and the Kotlin Gradle Plugin as `compileOnly` dependencies: at
build time they use the versions the root `build.gradle.kts` puts on the build's classpath, the
catalog's ([`../gradle/README.md`](../gradle/README.md#how-kotlin-is-set-up-agp-9-built-in-kotlin)).
Versions are declared only in the catalog.

A library module has no instrumented tests (`deviceTests` disabled), so
`./gradlew connectedDebugAndroidTest` builds and installs only `:app`'s test APK, where they all
are ([`../docs/TESTING.md`](../docs/TESTING.md#organizing-tests)). Lint runs from `:app` over every
module ([`../docs/TESTING.md`](../docs/TESTING.md#lint-and-compiler-warnings)).
