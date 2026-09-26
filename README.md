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
  The instrumented tests are run on emulators of API 25, 28, 30, 33 and 36 (Android 7.1, 9, 11,
  13 and 16); see [below](#emulators-and-the-android-tv-wsl-dev-tools-scripts).

The first build and test run fill two caches outside the project: `~/.gradle` grows to ~1.1 GB
(the Gradle distribution ~165 MB, plus the Android Gradle Plugin, Kotlin and the test libraries),
and Robolectric's Android framework jar adds ~204 MB in `~/.m2/repository`. The build also makes
the Android Gradle Plugin install build-tools into the SDK (~147 MB).

## Emulators and the android-tv-wsl-dev-tools scripts

`create-avd.sh`, `start-emulator.sh` and `remote.sh` in these docs are scripts from
[**android-tv-wsl-dev-tools**](https://github.com/CVasilakis/android-tv-wsl-dev-tools), a separate repository of command-line tools for Android TV development under WSL2, which also
has `SETUP.md`, a setup of the whole toolchain (JDK, SDK, emulator) without Android Studio. The
scripts are a convenience, not a requirement. To use them, clone that repository
anywhere and put its `bin/` folder on your `PATH` (its README explains how), or call them by their
path. Without them, anything that does the same job works, e.g. Android Studio's Device Manager:

| Script | What it does | Without it |
|---|---|---|
| `create-avd.sh --api <level>` | Creates the `tv_api<level>` Android TV emulator. | Create an emulator from the Android TV system image of that API level with the TV 1080p device profile, in landscape, with hardware keyboard and D-pad input enabled. |
| `start-emulator.sh [avd]` | Boots it and returns once Android has fully booted. | Start the emulator, and wait until Android reports that it has finished booting before installing or testing: an install started earlier fails. |
| `remote.sh` | A TV remote in the terminal. | Use the emulator window's keyboard (arrows, Enter, Ctrl+Backspace for Back), or send Android key events with adb. |

The docs name the emulators `tv_api25`, `tv_api28`, `tv_api30`, `tv_api33` and `tv_api36`; with emulators of
your own, use their names instead. A physical Android TV device on API 25+ works too.

## Quick start

```bash
./gradlew assembleDebug
start-emulator.sh                                  # android-tv-wsl-dev-tools; returns once booted
./gradlew installDebug
adb shell pm disable-user --user 0 com.google.android.leanbacklauncher   # make Luncher the home screen
adb shell input keyevent HOME
```

Why the stock launcher has to be disabled, and its name on the other emulators:
[`app/README.md`](app/README.md#luncher-as-the-home-screen).
Tests: [`TESTING.md`](docs/TESTING.md) (`./gradlew :domain:test :app:testDebugUnitTest`, …).

## Repository layout

| Path | Contents |
|---|---|
| [`app/`](app/README.md) | The Android app module: UI, adapters on Android APIs, composition root. |
| [`domain/`](domain/README.md) | Pure Kotlin module: the launcher's models, rules and ports (no Android). |
| [`gradle/`](gradle/README.md) | Version catalog and Gradle wrapper. |
| [`docs/`](docs/README.md) | Project-wide guides: architecture and testing. |
| [`.github/`](.github/README.md) | GitHub Actions workflows that run the tests. |
| [`AGENTS.md`](AGENTS.md) | Guidance for coding agents. |
| `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradlew*` | Standard Gradle project files. |
