# Luncher

A lightweight home screen (launcher) for Android TV, written in Kotlin with plain platform Views,
small and frugal enough to run well on very weak devices.

**Goal:** the bare minimum a TV launcher needs, and nothing more:

- hide apps
- change app banners
- set wallpapers
- reorder apps
- a few settings

**Status:** early. What exists so far:

- **The home screen:** the time and date, and the TV apps as a grid of banners that OK opens
  ([`feature/home/`](feature/home/README.md)).
- **Arranging apps:** a long press of OK on an app moves apps, or hides them on a shelf below
  ([arrange mode](feature/home/README.md#arrange-mode)).
- **A settings panel:** a list of the apps to hide or show, and the way into the device's own
  settings ([`feature/settings/`](feature/settings/README.md)).

Of the goals above, hiding and reordering apps exist; custom banners, wallpapers and Luncher's
other settings don't yet.

| | |
|---|---|
| Package | `com.luncher.launcher` (debug builds: `com.luncher.launcher.debug`) |
| Supported Android versions | Android TV 5.1 (API 22) and newer |
| Release APK size | ~70 KB |

## Documentation

Read from the general to the specific; each document builds on the ones before it:

1. **This README:** what Luncher is, what to install, how to build and run it.
2. **[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md):** modules, layers and the rules for where
   code goes.
3. **[`docs/TESTING.md`](docs/TESTING.md):** test tiers, how to run them, and in CI.
4. **The other guides in [`docs/`](docs/README.md)**, as a task needs them: emulators and
   devices, the instrumented tests, translations, releasing.
5. **The folder READMEs** ([repository layout](#repository-layout)): what each folder holds, in
   detail.
6. **Comments in the code and build files:** why a particular line is there.

How the docs are written and kept up to date: [`docs/README.md`](docs/README.md#writing-the-docs).
Coding agents also read [`AGENTS.md`](AGENTS.md).

## Requirements

- **JDK 17+** to build; **21+** to run the Robolectric tests, whose Android 16 (API 36) framework
  needs Java 21. If they fail with a Java version error, point `JAVA_HOME` at a JDK 21.
- **Android SDK** with `platforms;android-36` and `platform-tools`. Gradle finds it through
  `ANDROID_HOME` or `sdk.dir` in `local.properties` (git-ignored).
- **An Android TV device or emulator on API 22+** to run the app and the instrumented tests: how
  to create and drive the emulators, with or without the
  [android-tv-wsl-dev-tools](https://github.com/CVasilakis/android-tv-wsl-dev-tools) scripts these
  docs use, is in [`docs/EMULATORS.md`](docs/EMULATORS.md).

Everything else downloads by itself on the first build and test run, into caches outside the
project: `~/.gradle` grows to ~1.5 GB (the Gradle distribution ~165 MB, the Android Gradle Plugin,
Kotlin and the test libraries, and what Gradle makes of them), and Robolectric's Android framework
jars add ~360 MB in `~/.m2/repository`: API 36's, ~204 MB, and API 33's for the layout tests,
~156 MB. The build also makes the Android Gradle Plugin install build-tools into the
SDK (~147 MB).

## Quick start

```bash
./gradlew assembleDebug
start-emulator.sh                                  # android-tv-wsl-dev-tools; returns once booted
./gradlew installDebug
adb shell pm disable-user --user 0 com.google.android.leanbacklauncher   # make Luncher the home screen
adb shell input keyevent HOME
```

Why the stock launcher has to be disabled, and its name on the other emulators:
[`docs/EMULATORS.md`](docs/EMULATORS.md#luncher-as-the-home-screen).

## Repository layout

| Path | Contents |
|---|---|
| [`app/`](app/README.md) | The application module: the composition root that joins the others, the manifest, icon and version, and the instrumented tests. |
| [`feature/`](feature/README.md) | The screens, one module per feature: the home screen, the settings panel. |
| [`platform/`](platform/README.md) | The adapters: the domain's ports on Android APIs. |
| [`ui/`](ui/README.md) | What every screen shares: the theme, colors and UI helpers. |
| [`domain/`](domain/README.md) | Pure Kotlin module: the launcher's models, rules and ports (no Android). |
| [`build-logic/`](build-logic/README.md) | Gradle convention plugins: the build settings every Android module shares. |
| [`gradle/`](gradle/README.md) | Version catalog and Gradle wrapper. |
| [`docs/`](docs/README.md) | Project-wide guides: architecture, testing, emulators, releasing; and an archive of how the design was reached. |
| [`fastlane/`](fastlane/README.md) | The store listing F-Droid and Google Play show: name, descriptions, changes per version, screenshots (not part of the build). |
| `.github/workflows/` | GitHub Actions workflows that run the tests ([`docs/TESTING.md`](docs/TESTING.md#in-ci)) and make releases ([`docs/RELEASING.md`](docs/RELEASING.md)). |
| [`AGENTS.md`](AGENTS.md) | What coding agents need on top of these docs. |
| `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradlew*` | Standard Gradle project files. |
| `lint.xml` | Lint's exceptions for every module ([`docs/TESTING.md`](docs/TESTING.md#lint-and-compiler-warnings)). |

## License

Luncher is under the MIT License ([`LICENSE`](LICENSE)), except its icon and banner, the store
listing's images and the archived launch screen's graphics, which are derived from Android Open
Source Project artwork and are under the Apache License 2.0
([`app/README.md`](app/README.md#license)).
