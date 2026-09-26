# gradle/

Build tooling shared by the whole project.

| File | Purpose |
|---|---|
| [`libs.versions.toml`](libs.versions.toml) | Version catalog: the version of every plugin and library (AGP, Kotlin, the test libraries), referenced as `libs.…` from the build scripts. The same `kotlin` version drives built-in Kotlin in `:app` and the Kotlin/JVM plugin in `:domain`. |
| [`wrapper/gradle-wrapper.properties`](wrapper/gradle-wrapper.properties) | Gradle version used by `./gradlew`, with the distribution's SHA-256. |
| [`wrapper/gradle-wrapper.jar`](wrapper/gradle-wrapper.jar) | Small bootstrap that downloads that Gradle version into `~/.gradle`. Committed on purpose, as for every Gradle project. |

Versions are declared only in these two files. The JDK comes from the environment
([`../README.md`](../README.md#requirements)); the app's SDK levels are in
[`../app/build.gradle.kts`](../app/build.gradle.kts).

`gradle.properties` (repository root) sets the Gradle JVM heap, and enables the build cache,
configuration cache and non-transitive R classes. It also keeps the APKs installed after
instrumented tests (reason in its comment).

## How Kotlin is set up (AGP 9 built-in Kotlin)

AGP 9 compiles Kotlin itself ("built-in Kotlin"), so the app module applies **only**
`com.android.application`. Don't add `org.jetbrains.kotlin.android` to `app/build.gradle.kts`:
with built-in Kotlin enabled, AGP 9 rejects it. The pure-Kotlin `:domain` module isn't an Android
module and applies `org.jetbrains.kotlin.jvm` (`libs.plugins.kotlin.jvm`) as usual.

AGP bundles an older Kotlin Gradle Plugin (KGP) than the catalog's. The root
[`../build.gradle.kts`](../build.gradle.kts) declares `kotlin-android` (and `kotlin-jvm`) with
`apply false`. That line doesn't apply anything, but it puts the catalog's KGP version on the build
classpath, and AGP's built-in Kotlin then uses it instead of its own. It looks unused; don't remove
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
