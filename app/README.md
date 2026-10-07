# app/

The Luncher application module: the UI, the adapters that implement `:domain`'s ports on Android
APIs, and the composition root. How they fit together, and the rules they follow:
[`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md).

## Layout

```
app/
├── build.gradle.kts                  module build: SDK levels, version, debug app ID, R8, :domain, test setup
├── LICENSE-APACHE-2.0, NOTICE        license of the icon, banner and launch screen artwork (Apache 2.0, from AOSP)
├── proguard-rules.pro                app-specific R8 rules (none yet)
├── src/test/                         JVM tests: Robolectric, Roborazzi screenshots (docs/TESTING.md)
│   ├── java/com/luncher/launcher/    same packages as the code; TvDevice.kt: TV screen config
│   └── screenshots/<feature>/        reference images, committed
├── src/androidTest/                  instrumented tests on the emulator
│   └── java/com/luncher/launcher/
│       ├── <feature>/                Espresso: one screen, real key events
│       └── system/                   UI Automator: Home key, other apps
├── src/debug/res/
│   ├── values/strings.xml            the debug build's name, "Luncher (debug)"
│   └── drawable/                     its banner and icon: the release's with an amber DEBUG stripe
└── src/main/
    ├── AndroidManifest.xml           launcher registration, TV features, package visibility
    ├── java/com/luncher/launcher/
    │   ├── LuncherApplication.kt     holds the AppGraph (tests may replace it); `Activity.graph`
    │   ├── AppGraph.kt               composition root: creates adapters (lazily); open for test fakes
    │   ├── home/
    │   │   ├── HomeActivity.kt       the home screen: reads the apps, shows those not hidden, opens them
    │   │   ├── ClockView.kt          the top bar's time and date, following the Clock port
    │   │   ├── AppTilesView.kt       places the tiles where the domain's TileLayout says; scrolls
    │   │   ├── AppTileView.kt        one app: its image, focus frame and zoom; held or hidden while arranging
    │   │   ├── ArrangeMode.kt        arrange mode: keys to the domain's ArrangeSession, the shelf, the top bar's title and hint
    │   │   └── BannerImages.kt       adapter: draws an app's Banner into a bitmap of the tile's size
    │   ├── settings/
    │   │   ├── SettingsActivity.kt   the settings panel: lists the domain's settingsMenu, opens entries
    │   │   └── HideAppsActivity.kt   the Hide apps list: every app by name; OK hides or shows one
    │   ├── apps/
    │   │   ├── PackageManagerInstalledApps.kt   InstalledApps port on PackageManager
    │   │   └── PreferencesAppArrangements.kt    AppArrangements port on SharedPreferences
    │   └── clock/
    │       └── AndroidClock.kt       Clock port on the system time, settings and time broadcasts
    └── res/
        ├── animator/home_tile_focus.xml   zoom of the focused tile
        ├── drawable/
        │   ├── banner.xml            TV banner, 320×180 dp: the logo's TV and the name
        │   ├── ic_launcher.xml       app icon before Android 8.0: the TV on a sky-blue square
        │   ├── ic_launcher_foreground.xml   the adaptive icon's foreground: the TV
        │   ├── launch_screen.xml     the launch screen on API 22: the drawing, stretched over the window
        │   ├── launch_screen_art.xml the launch screen's drawing, 960×540 dp: the TV and the name in a glow
        │   ├── home_settings*.xml    the top bar's settings gear, and its focus disc
        │   └── settings_*.xml        the settings panel's window and focused entry
        ├── drawable-v23/launch_screen.xml   the launch screen from API 23 on: the drawing centred
        ├── drawable-anydpi-v26/ic_launcher.xml   app icon from 8.0 on: adaptive, the TV on sky blue
        ├── layout/                   home_activity.xml; settings_activity.xml, settings_entry.xml,
        │                             settings_hide_apps_activity.xml, settings_app_row.xml
        └── values/                   colors, dimensions, strings, themes
```

## Current state

The home screen shows the time and date in a top bar, in the device's language and hour format,
and below it the installed TV apps (activities with `MAIN` + `LEANBACK_LAUNCHER`) as a grid of
banners, sorted by name, five per row; OK opens the focused app. A gear at the end of the top bar,
or the Menu key, opens the settings panel. Its entries are Hide apps, a list of every app where OK
hides one from the home screen or shows it again, and the device's own settings. When every app is
hidden, the home screen says where to show them again. A long press of OK on an app starts
[arrange mode](#arrange-mode), where the user moves apps and hides them on a shelf. Custom
banners, wallpapers and Luncher's other settings don't exist yet.

## The home screen

The screen is a top bar that stays in place, and below it the tiles, which scroll. Each part does
one job, so a new arrangement, image source or top bar item changes one of them:

| Part | Job |
|---|---|
| `homeApps` (`:domain`) | which apps show, in which order, and which are hidden: the installed apps matched to the stored `AppArrangement` ([below](#hidden-apps)) |
| `TileLayout` (`:domain`) | where each tile goes and how big it is; `TileGrid` is the only one so far |
| `AppTilesView` | lays tiles out where the `TileLayout` says, and scrolls to the focused one. `grid` is the only place that picks the arrangement. |
| `bannerFor` (`:domain`) | which image a tile shows |
| `BannerImages` | draws that image into a bitmap of the tile's size |
| `AppTileView` | draws that bitmap, the focus frame and zoom; in arrange mode, a white frame and a bigger zoom when held, dimmed when hidden |
| `Clock` (`:domain`) | what time it is, in which time zone and hour format, and when that changes |
| `AndroidClock` | reads those from Android, and watches the time broadcasts only while something listens |
| `ClockView` | formats a reading in the device's language, at the start of the top bar |
| `HomeActivity` | reads the apps and their arrangement in `onResume`; when the shown apps changed, creates tiles for new ones and drops the others. Without tiles, says why: nothing installed, or everything hidden. Starts the clock in `onStart` and stops it in `onStop`. Opens the [settings panel](#the-settings-panel) on OK on the gear or on the Menu key. A long press of OK on a tile starts [arrange mode](#arrange-mode), which gets every key first while it's on. |

### The top bar

`home_top_bar` in `home_activity.xml` holds the clock at its start and the settings gear at its
end; later items (e.g. status indicators) go at the end too. Each item that shows device state
that changes (the time, later e.g. the network) is built the same way:

- **A port in `:domain`** that reads the state and tells listeners when it changes, with a fake in
  the test fixtures that the test moves ([`FakeClock`](../domain/src/testFixtures/kotlin/com/luncher/domain/clock/FakeClock.kt)).
- **An adapter in a topic package** (`clock/`, later e.g. `network/`), created in `AppGraph`. It
  registers with Android (a broadcast receiver, a callback) only while it has listeners.
- **A view in `home/`** with `start(port)` and `stop()`, called from `HomeActivity`'s `onStart` and
  `onStop`: a hidden home screen listens to nothing, and reads everything again when it comes back.

The bar itself isn't focusable. An item that should be reachable with the D-pad (the settings
gear) is a focusable view in it, and Up from the first row of tiles moves there through
Android's own focus search. Focus in the bar stays there when the apps change on a return to the
home screen; otherwise it stays on the same app.

What keeps it light:

- **One bitmap per tile, at the tile's size.** A banner resource is usually 640×360 px or more;
  only the scaled copy is kept, and drawing a tile copies it once.
- **Only new apps cost a bitmap.** Coming back to the home screen reads the app list again, but
  keeps the tiles, their bitmaps and the focus when it's the same; when an app was installed,
  only its tile is new.
- **Nothing allocated per key press.** Android's own focus search moves between tiles; the zoom
  is a state animator created with each tile; scrolling reuses one `Scroller`.
- **Layout passes only when the tiles change.** Focus, zoom and scrolling redraw without
  measuring or laying out again. The clock's text changes once a minute, which lays out the top
  bar only, not the tiles.
- **Nothing runs while the home screen is hidden.** The clock's broadcast receiver exists only
  from `onStart` to `onStop`.

## Arrange mode

A long press of OK on an app's tile starts it, holding that app. The top bar's clock and gear give
way to the title "Arrange apps" and a hint of what the keys do, and a shelf of the hidden apps
(dimmed) appears below the others, under a "Hidden" label; with none hidden, a dashed empty slot
shows where hiding is.

| While… | Arrows | OK | Back |
|---|---|---|---|
| holding an app (white frame, bigger zoom) | move it | put it down | put it down, end the mode |
| not holding one | move the focus | pick up the focused app, shown or hidden | end the mode |

Left and Right move the held app one place along the order, wrapping rows; Up and Down one row.
Down out of the last row puts it onto the shelf, in its column, which hides it; Up out of the
shelf's first row brings it back into the last row. Left and Right never cross between the two.
Home, or anything that stops the home screen (an app starting, the screen going off), ends the
mode too, with a held app put down where it is.

| Part | Job |
|---|---|
| `ArrangeSession` (`:domain`) | the held app, where each arrow takes it, and the apps as arranged; it keeps state, the one rule that does ([`domain/README.md`](../domain/README.md#writing-models-rules-and-ports)) |
| `TileMoves` (`:domain`), in `TileGrid` | where a moved tile goes in the grid, and where one coming in from above or below lands |
| `ShelfLayout` (`:domain`) | the positions: the shown tiles, the label, the shelf |
| `ArrangeMode` | starts and ends the mode; turns keys into the session's moves and moves the tiles to match; stores the arrangement each time an app that moved is put down |
| `AppTilesView` | with `shownCount` set, lays the tiles out with a `ShelfLayout` and draws the label and the empty slot; `moveTile` moves one without it losing focus |

The first move fixes the order: from then on the shown apps keep the user's order instead of the
one by name, and apps installed later come after them.

A long press that starts the mode ends with OK's release, which the mode ignores: it only reacts
to presses that started while it was on. The mode needs no Menu key, and the Menu key does
nothing meanwhile. On Android 16 (API 36), Back reaches the app only through
`OnBackInvokedCallback`, not as a key, so `HomeActivity` passes Back to the mode from there as well
as from `onBackPressed`.

To try it on an emulator, a long press of OK has to hold the key:
`remote.sh --long-press DPAD_CENTER` does on every API level (android-tv-wsl-dev-tools v1.4.0 on;
in the interactive `remote.sh`, `l` then Enter), and so does holding Enter in the emulator window.
`adb shell input keyevent --longpress` doesn't before API 30
([`README.md`](../README.md#emulators-and-the-android-tv-wsl-dev-tools-scripts)): there it's a
short press, which opens the app. The instrumented tests hold it with real key events
(`longPressOk`, in `src/androidTest/…/Keys.kt`), which works on every version.

What keeps it light:

- **The hidden apps' tiles exist only during the mode.** Their bitmaps are drawn when it starts,
  and dropped with the tiles when it ends.
- **A move allocates nothing,** except when an app crosses between the shown and the hidden
  ones. The tile is moved in place (detached and attached again, which keeps its focus); only
  the tiles' layout runs again.
- **Nothing is read while arranging.** The apps are read again when the home screen comes back
  after the mode, not while the user's changes are on screen.

## The settings panel

`SettingsActivity` is a floating window over the dimmed home screen (its theme,
`Theme.Luncher.Settings`, is a platform dialog theme). Back closes it, as any activity, and so does
Home: the home screen is `singleTask`, and Android closes what's above it in its task. The home
screen's focus is where it was.

| Part | Job |
|---|---|
| `settingsMenu` (`:domain`) | which entries the panel lists: tabs of groups of entries, in order |
| `SettingsEntry` (`:domain`) | the kinds of entry, one type each |
| `SettingsActivity` | shows a tab: its groups one below the other, with a gap between them; each entry's label (`label`) and what OK on it does (`open`) |

The panel shows the first tab. The tab strip to pick another one, and group titles, are built
with the first tab or group that needs them. To add an entry:

1. A `SettingsEntry` type in `:domain`, placed by `settingsMenu`, with its unit test.
2. Its label and action in `SettingsActivity`: `label` and `open` are exhaustive `when`s, so the
   app doesn't compile until both handle the new type.
3. A value the entry changes (e.g. whether the date shows) is read and stored through a port with
   an adapter, like any data from the device ([`ARCHITECTURE.md`](../docs/ARCHITECTURE.md#where-things-go)).

What keeps it light: its code runs, and its window exists, only while it's open. Behind it the home
screen stays started (the clock keeps running) and, as after any other activity, reads the apps
again when the panel closes, keeping its tiles when nothing changed.

### Hidden apps

Hide apps (`HideAppsActivity`) is a panel of its own, opened in the settings panel's place: it
lists every app by name; OK on one hides it or shows it again, and Back returns to the settings
panel. The home screen shows the change when it comes back, since it reads the apps then anyway.

A panel of its own is another activity in `settings/`, started from the settings panel with
`openOwnPanel`. The settings panel's window stays behind it, invisible (its alpha is 0 until it
resumes), since a smaller panel would show it around its edges; the dimming of the home screen is
that window's too, which is why the other panel's theme, `Theme.Luncher.Settings.Panel`, dims
nothing itself.

| Part | Job |
|---|---|
| `AppArrangement` (`:domain`) | what's stored: the order of the shown apps (none until the user reorders, which means by name) and the hidden apps in their order |
| `AppArrangements` (`:domain`) / `PreferencesAppArrangements` | port and adapter: reads the arrangement once per process, and saves each change at once, in the background |
| `homeApps` (`:domain`) | matches the stored apps to the installed ones: an update that renamed an app's activity keeps its place and hidden state, and apps that aren't installed right now keep theirs for when they come back |
| `ArrangedApps` (`:domain`) | the shown and hidden apps: hiding one puts it first among the hidden apps, showing one puts it last in the user's order (or in its place by name while there's none), and `byLabel` is the list's content |
| `HideAppsActivity` | shows that list, stores each change |

The list is a platform `ListView`: it creates views only for the rows on screen, and reuses them
while scrolling. It shows at most six and a half rows, so the half row says there's more; on a
small screen (e.g. 720p at the 1080p density, 360 dp tall) as many as leave the panel a margin
from the screen's edges, still ending on half a row.

## The launch screen

While a cold-started app's process starts, Android shows a starting window until the app draws.
Up to Android 11 (API 30) that window is the activity theme's `windowBackground`, which for the
home screen used to be its plain dark colour: a blank screen for half a second or more. Luncher's
home screen has `Theme.Luncher.Launch` in the manifest, whose `windowBackground` is
`launch_screen.xml`, the TV and the name in a glow; `HomeActivity.onCreate` switches to
`Theme.Luncher` before anything else, or the drawing would stay as the window's background,
drawn behind the tiles on every frame. The emulators of API 22 to 30 show it when Luncher's
process was killed and Home brings it back (what a TV short of memory does), when Home starts it
while another home app is in front, and when it's opened as an app. At boot, by AOSP's code, the
first home activity gets no starting window: the boot animation stays until Luncher draws.

From Android 12 (API 31) on there is no launch screen to draw, whatever the theme says:

- An activity of type home never gets a splash screen (AOSP `ActivityRecord.getStartingWindowType`):
  the previous screen stays until Luncher draws.
- On TV, Android's window manager overrides every app's splash screen (AOSP
  `TvStartingWindowTypeAlgorithm`): only its background on Android 12, a solid colour on 13, none
  from 14 on. `windowSplashScreenAnimatedIcon` and the app icon are never shown. For Luncher
  opened as an app on 12 and 13, `windowSplashScreenBackground` keeps that colour the home
  screen's.

The drawing, `launch_screen_art.xml`, is a 16:9 TV's whole screen, 960×540 dp, which is what a TV
usually is at 720p, 1080p and 4K. From API 23 on, `drawable-v23/launch_screen.xml` puts it at that
size in the middle of the home screen's background, so it keeps its shape on any screen: on 4:3,
16:10 or 21:9 it's cut at the edges or surrounded by the background, into which its glow fades, and
at a density that makes a 16:9 screen larger than 960×540 dp it's smaller than the screen. A
layer-list places an item by gravity only from API 23 on, so on API 22 `drawable/launch_screen.xml`
stretches the drawing over the window, distorted on a screen that isn't 16:9. A vector drawable is
drawn into a bitmap of the size it's shown at (lint's `VectorRaster` warning), about the screen's,
but only while the starting window shows. Its glow is opaque discs, each in its colour already
blended: vector drawables have gradients only from API 24 on, and before that they're drawn in
8-bit steps one shape at a time, so stacked faint transparent discs would add up to nothing on
API 22.

On the API 22 emulator, with the display density changed at runtime (`wm density`, as Developer
options do) to differ from the device's own, Android's starting window draws the drawing zoomed
in and shifted; with that density set from boot, it's drawn right. It's Android 5.1's: Luncher's
own code draws the same drawable right at that density.

## Platform choices

- **Plain platform classes:** activities extend `android.app.Activity` and use the platform theme
  `Theme.DeviceDefault.NoActionBar`, not AppCompat.
- **Graphics are vectors** (`VectorDrawable` renders natively on API 21+), so no per-density PNGs
  are needed.
- **Never a white screen:** every background Android may show for a screen before it draws
  (`windowBackground`, `colorBackground`, `windowSplashScreenBackground`) is set, dark, in
  `themes.xml`, rather than left to `Theme.DeviceDefault`, which device makers may restyle.
  `ThemesTest` checks every activity in the manifest.

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
| `SettingsActivity`, `HideAppsActivity`: `exported="false"`, `launchMode="singleTop"` | Only Luncher opens them; a repeated OK or Menu press doesn't stack a second panel. |
| `HomeActivity`: `Theme.Luncher.Launch` | Shows the launch screen while Luncher starts ([below](#the-launch-screen)). |
| `HideAppsActivity`: `Theme.Luncher.Settings.Panel` | Opens in the settings panel's place, which already dims the home screen ([above](#hidden-apps)). |
| `SettingsActivity`, `HideAppsActivity`: no `screenOrientation` | Android 8.0 (API 26) refuses one on a floating activity; it shows over the landscape home screen anyway. |

A home screen must not close on Back; how `HomeActivity` ignores it on every Android version is
explained in its comments.

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

The icon, banner and launch screen drawables are flat: each path has its coordinates and stroke width already
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
