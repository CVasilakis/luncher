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
├── src/test/                         JVM tests of the whole app: ThemesTest (../ui/README.md)
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

## Builds

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

Builds have the version set in `build.gradle.kts`, the last release's between releases
([`docs/RELEASING.md`](../docs/RELEASING.md#versions)). The release APK is unsigned and can't be
installed as is: the release workflow signs it ([`docs/RELEASING.md`](../docs/RELEASING.md)).

## Icon and banner

The logo is four Android desserts, from the Android 14–17 "platform logo" in AOSP
(`frameworks/base`, `packages/SystemUI/res/drawable/android14_patch_monochrome.xml` to
`android17_patch_monochrome.xml`), served as a 2×2 grid of launcher tiles that double as plates
on the screen of a TV. The adaptive icon puts it on sky blue; before Android 8.0, where no launcher
masks the icon, it's a sky-blue rounded square. The TV banner is the TV and the name "Luncher"
side by side on screen blue, in Roboto Black, kept as outlines so no font is needed to draw it.
The debug build adds an amber DEBUG stripe. The store listing's images are the same drawings
([`fastlane/`](../fastlane/README.md#icon-banner-and-feature-graphic)).

They're vector drawables, which Android draws natively on every supported version, so no
per-density PNGs are needed. They're flat: each path has its coordinates and stroke width already
moved and scaled, with no `<group>` and no `<clip-path>`. Vector drawables before Android 7.0
(API 24) don't scale a stroke's width with its group's scale, and keep a group's clip path for
everything drawn after the group; on API 22 the first drew every line several times too thick and
the second left half the drawing out. Edit them the same way.

### License

The icon and banner drawables (`src/main/res/drawable/`, `src/debug/res/drawable/`) and the store
listing's images are derived from AOSP artwork and are under the Apache License 2.0
([`LICENSE-APACHE-2.0`](LICENSE-APACHE-2.0)), not Luncher's MIT license. What that license asks,
and where it's met:

- **A copy of the license:** `LICENSE-APACHE-2.0`, the full text.
- **The original notices:** [`NOTICE`](NOTICE) lists the derived files and carries the Android
  attribution from AOSP's `frameworks/base` NOTICE; each drawable keeps the AOSP copyright header.
- **Changes stated in modified files:** each drawable says what was changed. Keep the header and
  that statement when editing or copying one, and list a new derived file in `NOTICE`.

What was changed, per dessert:

| Dessert | Change to the AOSP drawing |
|---|---|
| 14 cake | Robot head removed from the top of the cake; sprinkles added where it was, on the same 4/3 grid and in the same two sizes as the existing ones. |
| 15 ice cream | Robot ears, rocket, trail and stars removed; the scoop is cut where AOSP's canvas ended, gets a waffle cone and is scaled into the band the other desserts use. |
| 16 baklava | Robot, its trail and the stars removed. |
| 17 cinnamon roll | Robot ears removed. |

The lettering "Luncher" is Roboto, under the Apache License 2.0 too.
