# Luncher

A lightweight home screen (launcher) for Android TV, written in Kotlin with plain platform Views,
small and frugal enough to run well on very weak devices.

**Goal:** the bare minimum a TV launcher needs, and nothing more:

- hide apps
- change app banners
- set wallpapers
- reorder apps
- a few settings

**Status:** early. The project builds, installs and runs as the home screen on an Android TV
emulator; the home screen itself is still a placeholder (see [`app/README.md`](app/README.md)).

| | |
|---|---|
| Package | `com.luncher.launcher` |
| Supported Android versions | Android TV 7.1 (API 25) and newer |
| Dependencies | Kotlin standard library only (no AndroidX, Leanback or Compose) |
| Release APK size | ~22 KB |

## Requirements

- **JDK 17+** to build, **21+** to run the Robolectric tests.
- **Android SDK** with `platforms;android-36` and `platform-tools`. Gradle finds it through
  `ANDROID_HOME` or `sdk.dir` in `local.properties` (git-ignored).
- **An Android TV device or emulator on API 25+** to run the app and the instrumented tests.

The emulator and SDK setup (WSL2 included) lives in a separate repository,
**android-cli-dev-tools**: its `setup.md` installs the toolchain, and its scripts create, boot and
drive an Android TV emulator. The commands below assume it's cloned next to this repository, as
`../android-cli-dev-tools`; any other place, or its scripts on your `PATH`, works too.

The first build and test run fill two caches outside the project: `~/.gradle` grows to ~1.1 GB
(the Gradle distribution ~165 MB, plus the Android Gradle Plugin, Kotlin and the test libraries),
and Robolectric's Android framework jar adds ~204 MB in `~/.m2/repository`. The build also makes
the Android Gradle Plugin install build-tools into the SDK (~147 MB).

## Quick start

```bash
./gradlew assembleDebug
../android-cli-dev-tools/bin/start-emulator.sh     # returns once Android has booted
./gradlew installDebug
adb shell pm disable-user --user 0 com.google.android.leanbacklauncher   # make Luncher the home screen
adb shell input keyevent HOME
```

Why the stock launcher has to be disabled: [`app/README.md`](app/README.md#luncher-as-the-home-screen).
Tests: [`TESTING.md`](TESTING.md) (`./gradlew :domain:test :app:testDebugUnitTest`, …).

## Repository layout

| Path | Contents |
|---|---|
| [`app/`](app/README.md) | The Android app module: UI, adapters on Android APIs, composition root. |
| [`domain/`](domain/README.md) | Pure Kotlin module: the launcher's models, rules and ports (no Android). |
| [`gradle/`](gradle/README.md) | Version catalog and Gradle wrapper. |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Modules, layers and the rules for where code goes. |
| [`TESTING.md`](TESTING.md) | Test tiers: what goes where, what they need, how to run them. |
| [`AGENTS.md`](AGENTS.md) | Guidance for coding agents (`CLAUDE.md` links to it). |
| `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradlew*` | Standard Gradle project files. |
