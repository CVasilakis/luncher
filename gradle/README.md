# gradle/

Build tooling shared by the whole project.

| File | Purpose |
|---|---|
| [`libs.versions.toml`](libs.versions.toml) | Version catalog: the version of every plugin and library (AGP, Kotlin, the test libraries), referenced as `libs.…` from the build scripts, `build-logic/`'s included. The same `kotlin` version drives built-in Kotlin in the Android modules and the Kotlin/JVM plugin in `:domain`. |
| [`wrapper/gradle-wrapper.properties`](wrapper/gradle-wrapper.properties) | Gradle version used by `./gradlew`, with the distribution's SHA-256. |
| [`wrapper/gradle-wrapper.jar`](wrapper/gradle-wrapper.jar) | Small bootstrap that downloads that Gradle version into `~/.gradle`. Committed on purpose, as for every Gradle project. |

Versions are declared only in these two files. The JDK comes from the environment
([`docs/BUILDING.md`](../docs/BUILDING.md#requirements)); the SDK levels of every Android module are in
[`../build-logic/`](../build-logic/README.md).

`gradle.properties` (repository root) sets the Gradle JVM heap, and enables the build cache,
configuration cache and non-transitive R classes. It also keeps the APKs installed after
instrumented tests (reason in its comment).

## How Kotlin is set up (AGP 9 built-in Kotlin)

AGP 9 compiles Kotlin itself ("built-in Kotlin"), so the Android modules apply **only**
`com.android.application` or `com.android.library`, through Luncher's convention plugins
([`../build-logic/`](../build-logic/README.md)). Don't add `org.jetbrains.kotlin.android` to an
Android module or a convention plugin: with built-in Kotlin enabled, AGP 9 rejects it. The pure-Kotlin `:domain` module isn't an Android
module and applies `org.jetbrains.kotlin.jvm` (`libs.plugins.kotlin.jvm`) as usual.

AGP bundles an older Kotlin Gradle Plugin (KGP) than the catalog's. The root
[`../build.gradle.kts`](../build.gradle.kts) declares `kotlin-android` (and `kotlin-jvm`, and the
other plugins the modules apply) with `apply false`. That line doesn't apply anything, but it puts
the catalog's KGP version on the build classpath, and AGP's built-in Kotlin then uses it instead of
its own; so do the convention plugins, which only compile against AGP and KGP. It looks unused; don't remove
it. Check which versions actually resolve with:

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
