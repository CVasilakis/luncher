# app/

The Luncher application module: an Android TV home screen app. It holds the UI, the adapters that
implement `:domain`'s ports on Android APIs, and the composition root; see
[`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md).

| | |
|---|---|
| Package / applicationId / namespace | `com.luncher.launcher` |
| `minSdk` | 22 (Android 5.1 TV) |
| `compileSdk` / `targetSdk` | 36 |
| Language / UI | Kotlin, platform Views |
| Dependencies | `:domain` and the Kotlin standard library (test libraries are test-only) |

## Layout

```
app/
├── build.gradle.kts                  module build: SDK levels, R8, :domain, test setup
├── proguard-rules.pro                app-specific R8 rules (none yet)
├── src/test/                         JVM tests: Robolectric, Roborazzi screenshots (docs/TESTING.md)
│   ├── java/com/luncher/launcher/    same packages as the code; TvDevice.kt: TV screen config
│   └── screenshots/<feature>/        reference images, committed
├── src/androidTest/                  instrumented tests on the emulator
│   └── java/com/luncher/launcher/
│       ├── <feature>/                Espresso: one screen, real key events
│       └── system/                   UI Automator: Home key, other apps
└── src/main/
    ├── AndroidManifest.xml           launcher registration, TV features, package visibility
    ├── java/com/luncher/launcher/
    │   ├── LuncherApplication.kt     holds the AppGraph (tests may replace it); `Activity.graph`
    │   ├── AppGraph.kt               composition root: creates adapters (lazily); open for test fakes
    │   ├── home/
    │   │   └── HomeActivity.kt       the home screen
    │   └── apps/
    │       └── PackageManagerInstalledApps.kt   InstalledApps port on PackageManager
    └── res/
        ├── drawable/
        │   ├── banner.xml            TV banner, 320×180 dp (plate between a fork and a knife)
        │   └── ic_launcher.xml       app icon (plate with a play button)
        ├── layout/home_activity.xml
        └── values/                   colors, strings, theme
```

Code is organized by feature package (`home/`, `apps/`); resource names start with their feature
(`home_…`), except app-wide ones. [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md) has the rules.

## Current state

`HomeActivity` is a placeholder home screen: it shows the app name and the number of installed TV
apps (activities with `MAIN` + `LEANBACK_LAUNCHER`, from the `InstalledApps` port), re-counted in
`onResume`. The planned features are hiding apps, changing app banners, setting wallpapers,
reordering apps and a few settings.

## Design rules

- **Built for weak devices:** minimal memory, CPU and APK size. It extends `android.app.Activity` and uses the platform theme
  `Theme.DeviceDefault.NoActionBar`, with no AndroidX, AppCompat, Leanback or Compose. Adding a library
  is a deliberate decision, not a default. Code must run on API 22, so guard newer APIs with
  `Build.VERSION.SDK_INT` checks.
- **TV input:** everything must be usable with a D-pad (arrows, OK, Back, Home, Menu). There's no
  touchscreen. Many remotes have no Menu button, so actions shouldn't depend on Menu alone
  (long-press OK is the common alternative).
- **Graphics are vectors** (`VectorDrawable` renders natively on API 21+), so no per-density PNGs are needed.

## Manifest: why each part is there

| Element | Reason (don't remove without replacing it) |
|---|---|
| `android:name=".LuncherApplication"` | Creates the `AppGraph` that activities get their ports from. |
| `uses-feature android.software.leanback required=true` | TV-only app. |
| `uses-feature android.hardware.touchscreen required=false` | Touchscreen is otherwise assumed required, which excludes TVs (e.g. on Google Play). |
| `<queries>` for `MAIN`+`LEANBACK_LAUNCHER` and `MAIN`+`LAUNCHER` | Package visibility (targetSdk 30+): without it the launcher can't see other apps. |
| `android:banner` | The 16:9 image the Android TV launcher shows for an app. |
| intent filter `MAIN` + `HOME` + `DEFAULT` | Makes the activity a home screen app. |
| intent filter `MAIN` + `LEANBACK_LAUNCHER` | Also lists it as a normal TV app, so it can be opened while another launcher is home. |
| `launchMode="singleTask"` | Pressing Home returns to the same instance instead of stacking new ones. |
| `stateNotNeeded`, `clearTaskOnLaunch`, `excludeFromRecents` | Standard for home activities: always starts clean, never in Recents. |
| `screenOrientation="landscape"` | TVs are landscape. |

A home screen must not close on Back, and `HomeActivity` needs two ways to ignore it. Up to
Android 15 (API 35), `onBackPressed()` is deliberately empty; it's deprecated, but its replacements
need AndroidX (`OnBackPressedCallback`) or API 33 (`OnBackInvokedCallback`). On Android 16 (API 36),
Back no longer calls `onBackPressed()` in apps targeting it and closes the activity instead, so
there `onCreate` registers an empty `OnBackInvokedCallback`.

## Build outputs

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk (~0.9 MB, no shrinking)
./gradlew installDebug       # install on the running emulator/device
./gradlew assembleRelease    # app/build/outputs/apk/release/app-release-unsigned.apk (~22 KB, R8 minified)
```

Release builds have no signing config yet, so the release APK is unsigned and can't be installed as is.
Signing keys must never be committed.

## Luncher as the home screen

On the Android TV and Google TV emulator images from API 23 on, pressing Home never shows a
"choose home app" prompt, and `adb shell cmd package set-home-activity …` has no effect. The stock launcher is a system app
whose HOME intent filter has priority 2, third-party apps are capped at priority 0, and Android
picks the highest priority without asking. Disable the stock launcher instead (this persists
across reboots). Its package depends on the Android version:

| Emulator | Stock launcher |
|---|---|
| API 22 to 25 (Android 5.1 to 7.1) | `com.google.android.leanbacklauncher` |
| API 26 (Android 8.0) and newer | `com.google.android.tvlauncher` |
| Google TV, every level (API 30–36) | `com.google.android.apps.tv.launcherx` |

```bash
adb shell pm disable-user --user 0 com.google.android.leanbacklauncher   # Home opens Luncher (API 23-25)
adb shell pm enable com.google.android.leanbacklauncher                  # back to the stock launcher
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME   # who is home (API 24+)
```

API 22 (Android 5.1) is the exception: its stock launcher's HOME filter has no priority, so with
Luncher installed, Home asks which home app to use. Pick Luncher there ("Always"); disabling the
stock launcher isn't needed.

Don't uninstall Luncher while the stock launcher is disabled, or Home has nowhere to go. Re-enable
the stock launcher first, or wipe the emulator's data (android-tv-wsl-dev-tools'
`start-emulator.sh -wipe-data`, or "Wipe Data" in Android Studio's Device Manager). While the stock
launcher is enabled, Luncher appears in its app row (with its banner) and can be opened like any
app. Home seems to do nothing while Luncher is already in front, because Luncher is the home screen.

On the API 26 and 27 (Android 8.0 and 8.1) Android TV emulators, Home does nothing at all, whoever
the home app is: Android ignores it until the TV setup wizard has set `tv_user_setup_complete`, and
these images never run that wizard (logcat: "Not starting activity because user setup is in
progress"). android-tv-wsl-dev-tools' `start-emulator.sh` sets it after the boot. On an emulator
started another way, set it by hand; `HomeKeyTest` sets it for its run when it's missing, and
restores it afterwards, so it works on any device:

```bash
adb shell settings put secure tv_user_setup_complete 1
```
