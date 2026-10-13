# platform/

The adapters, `:platform`: the domain's ports implemented on Android APIs (PackageManager,
SharedPreferences, the system clock and its broadcasts), and the images the screens show. Only
`:app` depends on this module; `AppGraph` there creates the adapters, and the features see the
ports only, so a screen can't call an Android data API or create an adapter itself. Why, and the
rules adapters follow: [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md).

| | |
|---|---|
| Namespace | `com.luncher.launcher.platform` (its R class); the code is in a package per `:domain` topic |
| Depends on | `:domain`, `:ui` (the colors an image is drawn in) |

## Layout

```
platform/
├── build.gradle.kts              luncher.android.library; its dependencies
└── src/
    ├── main/
    │   ├── AndroidManifest.xml   package visibility (below)
    │   ├── java/com/luncher/launcher/
    │   │   ├── appearance/
    │   │   │   └── PreferencesAppearanceSettings.kt AppearanceSettings on SharedPreferences: a key per option, and its stored form
    │   │   ├── apps/
    │   │   │   ├── PackageManagerInstalledApps.kt   InstalledApps on PackageManager
    │   │   │   ├── PreferencesAppArrangements.kt    AppArrangements on SharedPreferences: the file and its format
    │   │   │   └── BannerImages.kt                  AppImages<Bitmap>: draws an app's Banner into a bitmap of the tile's size
    │   │   └── clock/
    │   │       └── AndroidClock.kt                  Clock on the system time, settings and time broadcasts
    │   └── res/values/colors.xml the card drawn for an app without a banner
    └── test/java/…/              JVM tests (Robolectric), same packages as the code
```

Packages follow `:domain`'s topics (`appearance/`, `apps/`, `clock/`, and later e.g. `wallpaper/`, `network/`),
so the adapter of `com.luncher.domain.clock.Clock` is in `clock/`.

## Writing adapters

- **One port each,** named after how it provides it (`PreferencesAppArrangements`), where the
  port is named after what it provides (`AppArrangements`). An image a screen shows is a port too,
  generic in the image type (`AppImages<Bitmap>`), since `:domain` can't name an Android type.
- **Storage formats stay here:** file names, keys and encodings (rule 6 of
  [`ARCHITECTURE.md`](../docs/ARCHITECTURE.md#rules)). A test of the adapter writes the stored
  format out, so a change that breaks what a TV already stored fails
  (`PreferencesAppArrangementsTest`). A value stored by another version that the adapter doesn't
  know reads as a default rather than failing (`PreferencesAppearanceSettings`: that option's
  default, the others as stored).
- **Device state that changes** (the time, later the network) is watched only while a listener is
  registered ([`ARCHITECTURE.md`](../docs/ARCHITECTURE.md#background-work-and-changes-while-shown)):
  `AndroidClock` registers its broadcast receiver with the first listener and unregisters it with
  the last.
- **Another app's resources can fail** (uninstalled a moment ago, broken): an adapter returns what
  it can (`BannerImages` draws a card) rather than crashing the home screen.
- **An image file is decoded by the adapter, and freed once drawn.** The bitmap in a `Drawable`
  from Android may be shared through Android's drawable cache, so it can't be freed, and from API
  33 on it stays in memory until a garbage collection, which an idle home screen doesn't get: with
  200 apps on API 36, 260 MB instead of 78 MB. `BannerImages` decodes a banner file at the file's
  own size, draws it scaled to the tile and recycles it; only a banner that isn't an image file (a
  vector, a color) or is a nine-patch goes through a `Drawable`.
- **Public,** since `AppGraph` in `:app` creates them; no other module depends on this one.

## Manifest

| Element | Reason (don't remove without replacing it) |
|---|---|
| `<queries>` for `MAIN`+`LEANBACK_LAUNCHER` | Package visibility (targetSdk 30+): without it `PackageManagerInstalledApps` can't see other apps. Only TV apps: Luncher lists and draws nothing else, and starting an app needs no visibility, so a query for phone apps (`MAIN`+`LAUNCHER`) would only widen what it can see. |
