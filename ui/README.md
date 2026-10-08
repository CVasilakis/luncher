# ui/

What every screen shares, UI only, `:ui`: the app-wide theme and colors, and helpers and views two
features both use. It depends on no other module, and holds no ports and nothing of a single
feature: a feature's own resources and views stay in its module. Why it exists:
[`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md#where-things-go).

| | |
|---|---|
| Package | `com.luncher.launcher.ui` |
| Depends on | nothing |

## Layout

```
ui/
├── build.gradle.kts              luncher.android.library, with test fixtures
└── src/
    ├── main/
    │   ├── java/com/luncher/launcher/ui/
    │   │   └── Colors.kt         color(id): a color resource on every API level
    │   └── res/values/
    │       ├── colors.xml        background, accent, primary and secondary text
    │       └── themes.xml        Theme.Luncher, the app's and the home screen's theme
    └── testFixtures/java/com/luncher/launcher/testing/
        ├── TvDevice.kt           the TV screens the JVM tests run on (TV_1080P, TV_SCREENS)
        └── LayoutChecks.kt       the layout tests' checks: bounds, inside, apart, whole text
```

Resources here have no prefix (`accent`, `Theme.Luncher`); a feature's are named after it
(`home_tile_gap`). Code refers to them through this module's R class, imported as
`com.luncher.launcher.ui.R as UiR` next to the module's own `R`; layouts and themes by name, as
any resource.

The test fixtures are what the screens' JVM tests share, in every module
(`testImplementation(testFixtures(project(":ui")))`): how they're used,
[`../docs/TESTING.md`](../docs/TESTING.md#layouts-on-other-screens).

## The theme

`Theme.Luncher` is the application's theme and the home screen's; a feature whose screens look
different has its own (the settings panels' dialog theme,
[`feature/settings/`](../feature/settings/README.md#the-settings-panel)). They're all platform
themes (`Theme.DeviceDefault…`), not AppCompat, like every activity's `android.app.Activity`
([`ARCHITECTURE.md`](../docs/ARCHITECTURE.md#rules), rule 5).

**Never a white screen:** every background Android may show for a screen before it draws
(`windowBackground`, `colorBackground`, `windowSplashScreenBackground`) is set, dark, in the
themes (this one, a feature's own), rather than left to `Theme.DeviceDefault`, which device makers
may restyle. `ThemesTest`, in `:app`, checks every activity of the merged manifest, drawing their
backgrounds over black.

### While Luncher starts

While a cold-started app's process starts, Android may show a starting window until the app has
drawn. The home screen has none on any Android version: the previous screen (an app, the stock
launcher, the boot animation) stays until Luncher has drawn, and the home screen then appears
whole.

- **Up to Android 11 (API 30)**, the starting window would be a window with the `windowBackground`
  of the theme the manifest gives the activity. The emulators of API 22 to 30 show one when
  Luncher's process was killed and Home brings it back (what a TV short of memory does), when Home
  starts it while another home app is in front, and when it's opened as an app.
  `Theme.Luncher` sets `windowDisablePreview`, for which Android adds none (AOSP
  `ActivityRecord.addStartingWindow` returns before making one).
- **From Android 12 (API 31) on**, an activity of type home never gets a splash screen, whatever
  its theme (AOSP `ActivityRecord.getStartingWindowType`).
- **At boot**, on every version, the first home activity gets no starting window: the boot
  animation stays until Luncher draws.
- **Opened as an app** rather than as the home screen, on Android 12 and 13 it still gets a
  splash screen: from 12 on, Android ignores `windowDisablePreview` for an activity started from
  the launcher, System UI or the system (`launchedFromSystemSurface`). On TV, Android's window
  manager overrides every app's splash screen (AOSP `TvStartingWindowTypeAlgorithm`): only its
  background on 12, a solid colour on 13, which `windowSplashScreenBackground` keeps the home
  screen's, none from 14 on. `windowSplashScreenAnimatedIcon` and the app icon are never shown.
- **A task snapshot**, the app's own last frame shown when returning to its task, is decided
  before the theme is read, so `windowDisablePreview` doesn't stop that.

A launch screen, a drawing as the starting window up to Android 11, was removed: Android fades it
out over the first frame, which on a slow TV looks like the two screens flickering
([`docs/archive/`](../docs/archive/README.md#the-launch-screen-removed)).
