# app/

The Luncher application module: the UI, the adapters that implement `:domain`'s ports on Android
APIs, and the composition root. How they fit together, and the rules they follow:
[`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md).

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
    │   │   ├── HomeActivity.kt       the home screen: reads the apps, shows them, opens them
    │   │   ├── AppTilesView.kt       places the tiles where the domain's TileLayout says; scrolls
    │   │   ├── AppTileView.kt        one app: its image, focus frame and zoom
    │   │   └── BannerImages.kt       adapter: draws an app's Banner into a bitmap of the tile's size
    │   └── apps/
    │       └── PackageManagerInstalledApps.kt   InstalledApps port on PackageManager
    └── res/
        ├── animator/home_tile_focus.xml   zoom of the focused tile
        ├── drawable/
        │   ├── banner.xml            TV banner, 320×180 dp (plate between a fork and a knife)
        │   └── ic_launcher.xml       app icon (plate with a play button)
        ├── layout/home_activity.xml
        └── values/                   colors, dimensions, strings, theme
```

## Current state

The home screen shows the installed TV apps (activities with `MAIN` + `LEANBACK_LAUNCHER`) as a
grid of banners, sorted by name, five per row; OK opens the focused app. Hiding, reordering,
custom banners, wallpapers and settings don't exist yet.

## The home screen

Each part does one job, so a new arrangement or image source changes one of them:

| Part | Job |
|---|---|
| `homeApps` (`:domain`) | which apps show, in which order |
| `TileLayout` (`:domain`) | where each tile goes and how big it is; `TileGrid` is the only one so far |
| `AppTilesView` | lays tiles out where the `TileLayout` says, and scrolls to the focused one. `createLayout` is the only place that picks the arrangement. |
| `bannerFor` (`:domain`) | which image a tile shows |
| `BannerImages` | draws that image into a bitmap of the tile's size |
| `AppTileView` | draws that bitmap, the focus frame and zoom |
| `HomeActivity` | reads the apps in `onResume`; when they changed, creates tiles for new apps and drops those of removed ones |

What keeps it light:

- **One bitmap per tile, at the tile's size.** A banner resource is usually 640×360 px or more;
  only the scaled copy is kept, and drawing a tile copies it once.
- **Only new apps cost a bitmap.** Coming back to the home screen reads the app list again, but
  keeps the tiles, their bitmaps and the focus when it's the same; when an app was installed,
  only its tile is new.
- **Nothing allocated per key press.** Android's own focus search moves between tiles; the zoom
  is a state animator created with each tile; scrolling reuses one `Scroller`.
- **Layout passes only when the tiles change.** Focus, zoom and scrolling redraw without
  measuring or laying out again.

## Platform choices

- **Plain platform classes:** activities extend `android.app.Activity` and use the platform theme
  `Theme.DeviceDefault.NoActionBar`, not AppCompat.
- **Graphics are vectors** (`VectorDrawable` renders natively on API 21+), so no per-density PNGs
  are needed.

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

A home screen must not close on Back; how `HomeActivity` ignores it on every Android version is
explained in its comments.

## Build outputs

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk (~0.9 MB, no shrinking)
./gradlew installDebug       # install on the running emulator/device
./gradlew assembleRelease    # app/build/outputs/apk/release/app-release-unsigned.apk (R8 minified)
```

Release builds have no signing config yet, so the release APK is unsigned and can't be installed as is.

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

**Wait 30 s before stopping the emulator after disabling or enabling an app.** Android saves that
change seconds later, not at once, and `adb emu kill` stops Android without saving: the next cold
boot starts with the app as it was before
([android-tv-wsl-dev-tools](https://github.com/CVasilakis/android-tv-wsl-dev-tools/blob/main/bin/README.md#start-emulatorsh)).

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
progress"). android-tv-wsl-dev-tools' `start-emulator.sh` sets it after the boot, and `HomeKeyTest`
for its own run. On an emulator started another way, set it by hand:

```bash
adb shell settings put secure tv_user_setup_complete 1
```
