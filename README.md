# Luncher

A lightweight home screen (launcher) for Android TV, written in Kotlin with plain platform Views,
small and frugal enough to run well on very weak devices.

**Goal:** the bare minimum a TV launcher needs, and nothing more:

- hide apps
- change app banners
- set wallpapers
- reorder apps
- a few settings

**Status:** early. The home screen shows the time and date, and the TV apps as a grid of banners
that it opens. A long press on an app arranges the apps: move them, or hide them on a shelf below;
a settings panel lists the apps to hide or show, and opens the device's own settings. Of the goals
above, hiding and reordering apps exist so far ([`app/README.md`](app/README.md#current-state)).

| | |
|---|---|
| Package | `com.luncher.launcher` (debug builds: `com.luncher.launcher.debug`) |
| Supported Android versions | Android TV 5.1 (API 22) and newer |
| Release APK size | ~58 KB |

## Documentation

Read from the general to the specific; each document builds on the ones before it:

1. **This README:** what Luncher is, what to install, how to build and run it.
2. **[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md):** modules, layers and the rules for where code goes.
3. **[`docs/TESTING.md`](docs/TESTING.md):** test tiers, how to run them, on which emulators, and in CI.
4. **The folder READMEs** ([repository layout](#repository-layout)): what each folder holds, in detail.
5. **Comments in the code and build files:** why a particular line is there.

How the docs are written and kept up to date: [`docs/README.md`](docs/README.md#writing-the-docs).
Coding agents also read [`AGENTS.md`](AGENTS.md).

## Requirements

- **JDK 17+** to build; **21+** to run the Robolectric tests, whose Android 16 (API 36) framework
  needs Java 21. If they fail with a Java version error, point `JAVA_HOME` at a JDK 21.
- **Android SDK** with `platforms;android-36` and `platform-tools`. Gradle finds it through
  `ANDROID_HOME` or `sdk.dir` in `local.properties` (git-ignored).
- **An Android TV device or emulator on API 22+** to run the app and the instrumented tests
  ([which emulators the tests use](docs/TESTING.md#on-several-android-versions); how to create
  them: [below](#emulators-and-the-android-tv-wsl-dev-tools-scripts)).

Everything else downloads by itself on the first build and test run, into caches outside the
project: `~/.gradle` grows to ~1.1 GB (the Gradle distribution ~165 MB, plus the Android Gradle
Plugin, Kotlin and the test libraries), and Robolectric's Android framework jar adds ~204 MB in
`~/.m2/repository`. The build also makes the Android Gradle Plugin install build-tools into the
SDK (~147 MB).

## Emulators and the android-tv-wsl-dev-tools scripts

`create-avd.sh`, `start-emulator.sh`, `stop-emulator.sh` and `remote.sh` in these docs are scripts
from [**android-tv-wsl-dev-tools**](https://github.com/CVasilakis/android-tv-wsl-dev-tools), a
separate repository of command-line tools for Android TV development under WSL2, which also has
`SETUP.md`, a setup of the whole toolchain (JDK, SDK, emulator) without Android Studio. The
scripts are a convenience, not a requirement. To use them, clone that repository anywhere and put
its `bin/` folder on your `PATH` (its README explains how), or call them by their path. Without
them, anything that does the same job works, e.g. Android Studio's Device Manager:

| Script | What it does | Without it |
|---|---|---|
| `create-avd.sh --api <level>` | Creates the `tv_api<level>` Android TV emulator. | Create an emulator from the Android TV system image of that API level with the TV 1080p device profile, in landscape, with hardware keyboard and D-pad input enabled. |
| `start-emulator.sh [avd]` | Boots it and returns once Android has fully booted. | Start the emulator, and wait until Android reports that it has finished booting before installing or testing: an install started earlier fails ([how, and what else the tests need](docs/TESTING.md#without-android-tv-wsl-dev-tools)). |
| `stop-emulator.sh [avd]` | Stops it and returns once it has exited. | Run `adb emu kill`, and wait until `adb devices` no longer lists the emulator before starting the same one again. |
| `remote.sh` | A TV remote in the terminal; `remote.sh --long-press <key>` holds a key as a long press. | Use the emulator window's keyboard (arrows, Enter, Ctrl+Backspace for Back; hold a key for a long press), or send Android key events with adb. adb's long press (`adb shell input keyevent --longpress`) holds the key only from API 30 on; before, it's a short press. |

The docs name emulators `tv_api<level>`, as `create-avd.sh` does; with emulators of your own, use
their names instead. A physical Android TV device on API 22+ works too.

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

## Repository layout

| Path | Contents |
|---|---|
| [`app/`](app/README.md) | The Android app module: UI, adapters on Android APIs, composition root. |
| [`domain/`](domain/README.md) | Pure Kotlin module: the launcher's models, rules and ports (no Android). |
| [`gradle/`](gradle/README.md) | Version catalog and Gradle wrapper. |
| [`docs/`](docs/README.md) | Project-wide guides: architecture, testing and releases. |
| `.github/workflows/` | GitHub Actions workflows that run the tests ([`docs/TESTING.md`](docs/TESTING.md#in-ci)) and make releases ([`docs/RELEASING.md`](docs/RELEASING.md)). |
| [`AGENTS.md`](AGENTS.md) | What coding agents need on top of these docs. |
| `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradlew*` | Standard Gradle project files. |

## License

Luncher is under the MIT License ([`LICENSE`](LICENSE)).
