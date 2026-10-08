# Building and running

What to install to build Luncher, and how to build it and run it on an emulator. Making it the
home screen on each Android version, and driving it from the command line:
[`EMULATORS.md`](EMULATORS.md).

## Requirements

- **JDK 17+** to build; **21+** to run the Robolectric tests, whose Android 16 (API 36) framework
  needs Java 21. If they fail with a Java version error, point `JAVA_HOME` at a JDK 21.
- **Android SDK** with `platforms;android-36` and `platform-tools`. Gradle finds it through
  `ANDROID_HOME` or `sdk.dir` in `local.properties` (git-ignored).
- **An Android TV device or emulator on API 22+** to run the app and the instrumented tests: how
  to create and drive the emulators, with or without the
  [android-tv-wsl-dev-tools](https://github.com/CVasilakis/android-tv-wsl-dev-tools) scripts these
  docs use, is in [`EMULATORS.md`](EMULATORS.md).

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
[`EMULATORS.md`](EMULATORS.md#luncher-as-the-home-screen).
