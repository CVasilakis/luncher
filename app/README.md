# app/

The application module, `:app`: `LuncherApplication`, and `AppGraph`, the composition root that
creates the adapters of `:platform` and gives each feature the ports it needs. Also what the
installed app is as a whole: its manifest, icon and banner, version, release build, and the
instrumented tests, which need the installed app. How the modules fit together:
[`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md).

## Layout

```
app/
├── build.gradle.kts                  application ID, version, debug app ID, R8, the modules it joins, instrumented test setup
├── LICENSE-APACHE-2.0, NOTICE        license of the icon and banner artwork (Apache 2.0, from AOSP)
├── proguard-rules.pro                app-specific R8 rules (none yet)
├── lint.xml                          lint's exceptions for this module's files
├── src/test/                         JVM tests of the whole app: ThemesTest (docs/TESTING.md)
├── src/androidTest/                  instrumented tests on the emulator, of every module
│   └── java/com/luncher/launcher/
│       ├── <feature>/                Espresso: one screen, real key events
│       ├── system/                   UI Automator: Home key, other apps
│       └── testing/                  shared by the tests: waits for the home screen and the focus, keys,
│                                     RetryWhenCovered, SystemTierFilter; and their own tests
├── src/debug/res/
│   ├── values/strings.xml            the debug build's name, "Luncher (debug)"
│   └── drawable/                     its banner and icon: the release's with an amber DEBUG stripe
└── src/main/
    ├── AndroidManifest.xml           TV features, the app's name, icon and banner, the home screen's intent filters
    ├── java/com/luncher/launcher/
    │   ├── LuncherApplication.kt     holds the AppGraph (instrumented tests may replace it); each feature's graph
    │   └── AppGraph.kt               composition root: creates adapters (lazily); open for test fakes
    └── res/
        ├── drawable/
        │   ├── banner.xml            TV banner, 320×180 dp: the logo's TV and the name
        │   ├── ic_launcher.xml       app icon before Android 8.0: the TV on a sky-blue square
        │   └── ic_launcher_foreground.xml   the adaptive icon's foreground: the TV
        ├── drawable-anydpi-v26/ic_launcher.xml   app icon from 8.0 on: adaptive, the TV on sky blue
        └── values/                   the app's name; the icon's background color
```

The other modules' manifests (activities, package visibility) merge into this one when the app is
built.

## Current state

The home screen shows the time and date in a top bar, in the device's language and hour format, and
below it the installed TV apps (activities with `MAIN` + `LEANBACK_LAUNCHER`) as a grid of banners,
sorted by name, as many per row as fit at about 154 dp wide (five on a 16:9 TV, more on a screen
wider in dp, e.g. 1080p at 160 dpi); OK opens the focused app. A gear at the end of the top bar, or
the Menu key, opens the settings panel. Its entries are Hide apps, a list of every app where OK
hides one from the home screen or shows it again, and the device's own settings. When every app is
hidden, the home screen says where to show them again. A long press of OK on an app starts
[arrange mode](../feature/home/README.md#arrange-mode), where the user moves apps and hides them on
a shelf. Custom banners, wallpapers and Luncher's other settings don't exist yet.

## While Luncher starts

While a cold-started app's process starts, Android may show a starting window until the app has
drawn. The home screen has none on any Android version: the previous screen (an app, the stock
launcher, the boot animation) stays until Luncher has drawn, and the home screen then appears
whole.

- Up to Android 11 (API 30), the starting window would be the theme's `windowBackground`.
  `Theme.Luncher` (`:ui`) sets `windowDisablePreview`, for which Android adds none (AOSP
  `ActivityRecord.addStartingWindow`).
- From Android 12 (API 31) on, an activity of type home never gets a splash screen, whatever its
  theme (AOSP `ActivityRecord.getStartingWindowType`).
- At boot, on every version, the first home activity gets no starting window: the boot animation
  stays until Luncher draws.

Opened as an app rather than as the home screen, on Android 12 and 13 it still gets a splash
screen: from 12 on, Android ignores `windowDisablePreview` for an activity started from the
launcher or the system, and on TV that splash screen is only a colour (AOSP
`TvStartingWindowTypeAlgorithm`), which `windowSplashScreenBackground` keeps the home screen's.
From Android 14 on, TV shows none.

A launch screen, a drawing as the starting window up to Android 11, was removed: Android fades it
out over the first frame, which on a slow TV looks like the two screens flickering. What it
taught, and its graphics: [`../docs/archive/launch-screen/`](../docs/archive/launch-screen/README.md).

## Platform choices

- **Plain platform classes:** activities extend `android.app.Activity` and use the platform theme
  `Theme.DeviceDefault.NoActionBar`, not AppCompat.
- **Graphics are vectors** (`VectorDrawable` renders natively on API 21+), so no per-density PNGs
  are needed.
- **Never a white screen:** every background Android may show for a screen before it draws
  (`windowBackground`, `colorBackground`, `windowSplashScreenBackground`) is set, dark, in
  the themes (`:ui`'s, a feature's own), rather than left to `Theme.DeviceDefault`, which device
  makers may restyle. `ThemesTest`, here, checks every activity of the merged manifest.

## Manifest: why each part is there

| Element | Reason (don't remove without replacing it) |
|---|---|
| `android:name=".LuncherApplication"` | Creates the `AppGraph` that activities get their ports from. |
| `uses-feature android.software.leanback required=true` | TV-only app. |
| `uses-feature android.hardware.touchscreen required=false` | Touchscreen is otherwise assumed required, which excludes TVs (e.g. on Google Play). |
| `android:banner` | The 16:9 image the Android TV launcher shows for an app. |
| `supportsRtl="false"` | Right-to-left isn't supported yet: the tiles fill from the left, and arrange mode's Left and Right go along their order. So in Arabic or Hebrew every screen stays left to right, rather than a top bar mirrored over tiles that aren't. Supporting it takes mirroring `TileGrid` and those keys. |
| `HomeActivity`'s intent filter `MAIN` + `HOME` + `DEFAULT` | Makes Luncher a home screen app. |
| `HomeActivity`'s intent filter `MAIN` + `LEANBACK_LAUNCHER` | Also lists it as a normal TV app, so it can be opened while another launcher is home. |

The intent filters are the app's to declare, as its entry points; how the home screen's activity
behaves is declared with it, in [`:feature:home`](../feature/home/README.md#manifest), and the
build merges the two. The settings panels' entries:
[`:feature:settings`](../feature/settings/README.md#manifest); package visibility:
[`:platform`](../platform/README.md#manifest).

## Build outputs

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk (~1 MB, no shrinking)
./gradlew installDebug       # install on the running emulator/device
./gradlew assembleRelease    # app/build/outputs/apk/release/app-release-unsigned.apk (R8 minified)
```

A debug build is an app of its own, `com.luncher.launcher.debug`, labeled "Luncher (debug)"
and with an amber DEBUG stripe across its icon and banner (`src/debug/`), so it installs next to
a release, which is signed with another key. Its classes keep the package `com.luncher.launcher`,
so its activities' full name is e.g.
`com.luncher.launcher.debug/com.luncher.launcher.home.HomeActivity`.

The icon and banner drawables are flat: each path has its coordinates and stroke width already
moved and scaled, with no `<group>` and no `<clip-path>`. Vector drawables before Android 7.0
(API 24) don't scale a stroke's width with its group's scale, and keep a group's clip path for
everything drawn after the group; on API 22 the first drew every line several times too thick and
the second left half the drawing out. Edit them the same way. They're derived from AOSP artwork
under the Apache License 2.0 ([`NOTICE`](NOTICE)), and each keeps its AOSP header.

Builds have the version set in `build.gradle.kts`, the last release's between releases
([`docs/RELEASING.md`](../docs/RELEASING.md#versions)). The release APK is unsigned and can't be
installed as is: the release workflow signs it ([`docs/RELEASING.md`](../docs/RELEASING.md)).

## Luncher as the home screen

On the Android TV and Google TV emulator images from API 23 on, pressing Home never shows a
"choose home app" prompt, and `adb shell cmd package set-home-activity …` has no effect. The stock launcher is a system app
whose HOME intent filter has priority 2, third-party apps are capped at priority 0, and Android
picks the highest priority without asking. Disable the stock launcher instead (this persists
across reboots, once saved: see below). Its package depends on the Android version:

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

**Wait 30 s before stopping the emulator after disabling or enabling an app.** `adb emu kill`
doesn't shut Android down, and Android saves such a change only a while after it's made, so the
next cold boot starts with the app as it was before. Measured on these emulators:

- Android writes an app's enabled state 10 s after the first unsaved change (10.35 s measured; on
  an emulator starved of CPU, 10 s after the `pm` command returned, which itself took 3–4 s
  there). A setting (`settings put`) it writes about 0.2 s after the change (0.34 s at most when
  starved). API 22 writes both at once.
- Then the file needs up to 5 s more: Android keeps the old one as a backup until the new one is
  complete, and until the filesystem's journal has recorded that (every 5 s), a boot reads the
  backup.

So a change is safe 15 s after it at the latest, and 30 s leaves twice that. Stopped earlier, a
change survived only by chance. The alternative is to stop the emulator with
android-tv-wsl-dev-tools'
[`stop-emulator.sh`](https://github.com/CVasilakis/android-tv-wsl-dev-tools/blob/main/bin/README.md#stop-emulatorsh),
which has Android save its pending changes before it stops it, so no wait is needed.

API 22 (Android 5.1) is the exception: its stock launcher's HOME filter has no priority, so with
Luncher installed, Home asks which home app to use. Pick Luncher there ("Always"); disabling the
stock launcher isn't needed.

Don't uninstall Luncher while the stock launcher is disabled, or Home has nowhere to go. Re-enable
the stock launcher first, or wipe the emulator's data (android-tv-wsl-dev-tools'
`start-emulator.sh -wipe-data`, or "Wipe Data" in Android Studio's Device Manager). While the stock
launcher is enabled, Luncher appears in its app row (with its banner) and can be opened like any
app. Home seems to do nothing while Luncher is already in front, because Luncher is the home screen.

Re-enabled, the stock launcher starts cold at the next Home. The API 36 one (`tvlauncher`) can then
open a promotion over itself a few seconds later, "Buy and rent movies on your TV"
(`.dialog.ShowDialogsActivity`, at most once per boot in the runs seen), which stays until
dismissed. An app opened in those seconds ends up behind it, which is why the instrumented tests
start from a settled home screen
([`docs/TESTING.md`](../docs/TESTING.md#instrumented-tests-espresso-ui-automator)).

On API 23 and 29, the first boot of a new emulator with an SD card opens "USB drive connected"
(`com.android.tv.settings/.device.storage.NewStorageActivity`) in front of the home app, and it
stays until Back; later boots don't. It's Android TV's Settings announcing the SD card, which
Android mounts as a removable drive from API 23 on. android-tv-wsl-dev-tools' `create-avd.sh`
gives every emulator an SD card, so each of them, CI's included, shows the screen once.
`avdmanager create avd` without `--sdcard` writes an SD card size into the emulator's
`config.ini` but creates no SD card image, so its emulators have no SD card and don't show it.
Why the other API levels don't show it isn't known. The instrumented tests press Back on it, and
so does `start-emulator.sh --wait-for-home`.

On the API 26 and 27 (Android 8.0 and 8.1) Android TV emulators, Home does nothing at all, whoever
the home app is: Android ignores it until the TV setup wizard has set `tv_user_setup_complete`, and
these images never run that wizard (logcat: "Not starting activity because user setup is in
progress"). android-tv-wsl-dev-tools' `start-emulator.sh` sets it after the boot, and `HomeKeyTest`
for its own run. On an emulator started another way, set it by hand:

```bash
adb shell settings put secure tv_user_setup_complete 1
```
