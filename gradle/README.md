# gradle/

Build tooling shared by the whole project.

| File | Purpose |
|---|---|
| [`libs.versions.toml`](libs.versions.toml) | Version catalog: plugin (and future library) versions, referenced as `libs.…` from the build scripts. The same `kotlin` version drives built-in Kotlin in `:app` and the Kotlin/JVM plugin in `:domain`. |
| [`wrapper/gradle-wrapper.properties`](wrapper/gradle-wrapper.properties) | Gradle version used by `./gradlew`, with the distribution's SHA-256. |
| [`wrapper/gradle-wrapper.jar`](wrapper/gradle-wrapper.jar) | Small bootstrap that downloads that Gradle version into `~/.gradle`. Committed on purpose, as for every Gradle project. |

## Versions

| Tool | Version | Declared in |
|---|---|---|
| Gradle | 9.8.0 | `wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin (AGP) | 9.4.1 | `libs.versions.toml` (`agp`) |
| Kotlin | 2.4.20 | `libs.versions.toml` (`kotlin`) |
| JDK | 17+ to build, **21+ to run the Robolectric tests** (21 used) | environment, see [`../README.md`](../README.md#requirements) |

Test libraries, all test-only (never in the APK); [`../docs/TESTING.md`](../docs/TESTING.md) says which tier
uses which:

| Library | Version | Catalog entry |
|---|---|---|
| JUnit | 4.13.2 | `junit` |
| Robolectric | 4.17 | `robolectric` |
| Roborazzi (library and Gradle plugin) | 1.75.0 | `roborazzi` |
| AndroidX Test core, runner | 1.7.0 | `androidx-test` |
| AndroidX Test ext JUnit | 1.3.0 | `androidx-test-ext-junit` |
| Espresso | 3.7.0 | `espresso` |
| UI Automator | 2.4.0 | `uiautomator` |

The app's SDK levels (`minSdk 22`, `compileSdk`/`targetSdk 36`) are in [`../app/build.gradle.kts`](../app/build.gradle.kts).

## How Kotlin is set up (AGP 9 built-in Kotlin)

AGP 9 compiles Kotlin itself ("built-in Kotlin"), so the app module applies **only**
`com.android.application`. Don't add `org.jetbrains.kotlin.android` to `app/build.gradle.kts`:
with built-in Kotlin enabled, AGP 9 rejects it. The pure-Kotlin `:domain` module isn't an Android
module and applies `org.jetbrains.kotlin.jvm` (`libs.plugins.kotlin.jvm`) as usual.

AGP 9.4.1 bundles Kotlin Gradle Plugin 2.2.10. The root [`../build.gradle.kts`](../build.gradle.kts) declares
`kotlin-android` (and `kotlin-jvm`) with `apply false`. That line doesn't apply anything, but it puts the catalog's KGP
version on the build classpath, and AGP's built-in Kotlin then uses it (2.2.10 → 2.4.20). It looks unused; don't remove it.
Check which versions actually resolve with:

```bash
./gradlew -q buildEnvironment | grep -E 'kotlin-gradle-plugin|com.android.tools.build:gradle:'
```

## Upgrading

- **Gradle:** `./gradlew wrapper --gradle-version <x.y.z> --gradle-distribution-sha256-sum <sha>`,
  where the checksum comes from <https://gradle.org/release-checksums/>. The wrapper verifies the
  downloaded distribution against it, so keep `distributionSha256Sum` in the properties file.
- **AGP:** change `agp` in the catalog. Check the minimum Gradle version it needs, and whether it
  supports the app's `compileSdk`, in the AGP release notes.
- **Kotlin:** change `kotlin` in the catalog, then check `buildEnvironment` as shown above.
- **Test libraries:** change their entry in the catalog. Robolectric must support the app's
  `compileSdk`/`targetSdk`; a newer Android API may need a newer JDK (see its release notes).
- After any upgrade, run `./gradlew assembleDebug assembleRelease`, all test tiers
  ([`../docs/TESTING.md`](../docs/TESTING.md)), and install on the emulator.

`gradle.properties` (repository root) sets the Gradle JVM heap, and enables the build cache and
configuration cache and non-transitive R classes. It also keeps the APKs installed after
instrumented tests (`android.injected.androidTest.leaveApksInstalledAfterRun`; reason in
[`../docs/TESTING.md`](../docs/TESTING.md#instrumented-tests-espresso-ui-automator)).
