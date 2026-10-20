# domain/

The launcher's models, rules and ports, in pure Kotlin. What each of them is, what this module may
use, and how it fits with the other modules: [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md).

| | |
|---|---|
| Package | `com.luncher.domain` |
| Build | [`build.gradle.kts`](build.gradle.kts): Kotlin/JVM (`org.jetbrains.kotlin.jvm`), bytecode level 17 like the Android modules |

## Layout

```
domain/src/
├── main/kotlin/com/luncher/domain/
│   ├── apps/
│   │   ├── LaunchableApp.kt     model: an app's TV launcher activity (package + activity), its identity
│   │   ├── InstalledApp.kt      model: a launchable app with its label, whether it has a banner, and when it was installed or updated
│   │   ├── InstalledApps.kt     port: the TV apps installed on the device, and when apps change
│   │   ├── AppImages.kt         port: an app's image drawn at a size, in the UI's image type
│   │   ├── AppArrangement.kt    model: the user's order of the shown apps, and the hidden apps, as stored
│   │   ├── AppArrangements.kt   port: where the arrangement is kept, and when it changes
│   │   ├── HomeApps.kt          rule: which apps the home screen shows, in which order, and which are hidden
│   │   ├── ArrangedApps.kt      model and rules: the shown and hidden apps; hiding or showing one, and what to store
│   │   └── Banner.kt            model and rule: which image a tile shows (the app's banner or its icon)
│   ├── appearance/
│   │   ├── Appearance.kt        model: how the home screen looks, as far as the user can change it, in parts (docs/ARCHITECTURE.md#appearance)
│   │   ├── TileGeometry.kt      model: where tiles go and how big they are, in dp; their alignment
│   │   ├── VerticalAlignment.kt model: the rows at the top, centered or at the bottom of the height
│   │   ├── HorizontalAlignment.kt model: a row that isn't full at the left, centered or at the right
│   │   ├── TileStyle.kt         model: how each tile is drawn (its corners)
│   │   ├── Corners.kt           model and rule: a corner radius or round ends, in pixels for a tile's height
│   │   ├── TopBarAppearance.kt  model: whether the time and the date show, the clock's side of the top bar, the time's side of the date
│   │   ├── Background.kt        model and rule: the wallpaper, a picture of the library or none, and how it's drawn; only values its context offers
│   │   ├── Side.kt              model: left or right, the screen's, in every language
│   │   ├── AppearanceOption.kt  models and rules: each option the user can change, its values, those a context offers (OptionContext), stepping through them, around
│   │   ├── AppearanceOptions.kt models and rule: which options the editor lists, in groups; a group edited apart (the background's)
│   │   └── AppearanceSettings.kt port: the stored appearance, and when it changes
│   ├── jobs/
│   │   ├── Jobs.kt              port: the background thread; cancelling (Cancellable) a queued job; every job's priority (JobPriority)
│   │   └── LatestRequest.kt     helper: one thing a screen loads in the background, only the latest request counting
│   ├── pictures/
│   │   ├── Picture.kt           model: a picture of the library (PictureId, from its content), its name, size as shown, bytes, when added
│   │   ├── Pictures.kt          port: the library's pictures, renaming and deleting them, and when they change
│   │   ├── PictureImport.kt     port: adding a picture from a source, slow, cancellable, when its copying starts, and how that went (ImportResult)
│   │   ├── PictureLibrary.kt    rule: the library as the background editor lists it, the newest first, and what each one is used for
│   │   ├── PictureLimits.kt     rule: the largest picture the library takes (bytes, megapixels), and the most pictures one share brings
│   │   ├── PictureNames.kt      rules: the name a picture keeps (pictureName), a name the library has already (nameClash: the same file, or another), the name a picture renamed keeps or why it can't (renaming), and what a drop folder's listing already tells can't be added (whyRefused)
│   │   ├── ReceivedPictures.kt  helper: the pictures other apps shared, added one at a time in sessions the window that took them last holds; how far it got, then how it went (Outcome), stopped on Cancel; also Add all's, an instance of its own
│   │   ├── PictureFormat.kt     model and rule: the formats a TV may not show (HEIF, AVIF), recognized by a file's header
│   │   ├── Framing.kt           model and rules: how a picture fills a space of another shape (Fit, PicturePosition), which part of it shows, and what filling cuts from it (PictureCut: nothing under 1%, its sides, or its top and bottom)
│   │   ├── PictureStyle.kt      model and rule: a picture's framing, dim and blur (Blur: off, then three levels of equal steps), as it's drawn
│   │   ├── PictureUse.kt        model and rule: what a picture is used for (the wallpaper), the uses a received picture offers (pictureUseMenu), and an appearance without deleted pictures
│   │   ├── PictureImages.kt     port: a picture drawn at a size in a style, in the UI's image type; kept or drawn anew, a screen's drawing stopped when it's dropped
│   │   ├── PreparedPictures.kt  port: each picture prepared once for the screens (its working copy), and whether it is for the screen as it is now
│   │   └── FolderPictures.kt    port, model and rule: the pictures in the drop folders, their preview, and the order they're listed in
│   ├── wallpaper/
│   │   ├── WallpaperDrawing.kt  model and rule: what a wallpaper is drawn from (a picture, its style, the screen's size), and why the one kept won't do (DrawingCause)
│   │   ├── StoredWallpaper.kt   port: the one wallpaper kept, drawn at the screen's size, so a start shows it at once
│   │   └── Wallpaper.kt         helper: the wallpaper as it stands (none, ready, or being drawn and why), one per process, which a screen watches; saving a background (drawn, kept, then stored), and forgetting a deleted picture
│   ├── clock/
│   │   ├── ClockReading.kt      model: the time, time zone and hour format at one moment
│   │   └── Clock.kt             port: the device's clock, and when what it shows changes
│   ├── settings/
│   │   ├── SettingsEntry.kt     model: the kinds of entry the settings panel lists, one type each
│   │   └── SettingsMenu.kt      rule: which entries the panel lists, in order
│   ├── arrange/
│   │   └── ArrangeSession.kt    rule: the user arranging apps, holding one and moving it among and between shown and hidden
│   └── layout/
│       ├── TileLayout.kt        how an arrangement of tiles reports sizes and positions
│       ├── TileMoves.kt         how it says where a tile the user moves goes, and which tile is nearest across
│       ├── Direction.kt         model: a D-pad arrow
│       ├── TileGrid.kt          rule: the grid (rows of tiles as wide as a width; the short row first or last, at the left, centered or at the right; at the top, centered or at the bottom of a height), and moves in it
│       ├── TileFocus.kt         rule: where each arrow takes the focus among the tiles: along the row, the nearest tile up or down, and back the way it came
│       ├── TileLayouts.kt       rule: the layouts for a TileGeometry, in pixels at a density; picks the arrangement and placement
│       ├── ShelfLayout.kt       rule: the shown apps' tiles above a shelf of the hidden ones, while arranging
│       └── TopBarLayout.kt      rule: which edge of the top bar each of its items is at, for a TopBarAppearance
├── test/kotlin/…/               unit tests (docs/TESTING.md), same packages as the code
└── testFixtures/kotlin/…/       fakes of the ports, used by the tests of every module
    ├── apps/FakeInstalledApps.kt   fake of the InstalledApps port
    ├── apps/FakeAppArrangements.kt fake of the AppArrangements port: the arrangement in memory
    ├── appearance/FakeAppearanceSettings.kt  fake of the AppearanceSettings port: the appearance in memory
    ├── appearance/AppearanceChanges.kt       each option at each value but its default, in a context that offers it: what the catalog-wide tests draw (SAMPLE_WALLPAPER for the background's, wide or tall)
    ├── clock/FakeClock.kt          fake of the Clock port: a fixed time the test moves
    ├── jobs/ManualJobs.kt          fake of the Jobs port: the queued jobs run when the test says, all or the next one, in the real order
    ├── pictures/FakePictures.kt    fake of the Pictures and PictureImport ports: the library in memory, each source's answer set by the test
    ├── pictures/FakePictureImages.kt  fake of the PictureImages port: draws what the test says, keeps what it drew
    ├── pictures/FakePreparedPictures.kt fake of the PreparedPictures port: every picture prepared but those the test says
    ├── wallpaper/FakeStoredWallpaper.kt fake of the StoredWallpaper port: the wallpaper kept in memory
    └── pictures/FakeFolderPictures.kt fake of the FolderPictures port: the drop folders' pictures in memory
```

Packages are by topic (`appearance/`, `apps/`, `arrange/`, `clock/`, `jobs/`, `layout/`,
`pictures/`, `settings/`, `wallpaper/`), each holding the models, rules and ports of that topic.

## Writing models, rules and ports

- Models are immutable (`data class` with `val`s).
- Rules are plain functions or classes that take models and return models. They have no side effects,
  so they're testable without fakes. The exceptions are the rules that follow the user's key
  presses, `ArrangeSession` and `TileFocus`: they keep state (the held app, the last move) and
  change it in place, so a key press allocates nothing
  ([`ARCHITECTURE.md`](../docs/ARCHITECTURE.md#rules), rule 5). It's still pure Kotlin, tested the
  same way.
- A helper the screens share that isn't a rule lives with its topic too: `LatestRequest`, in
  `jobs/`, the "latest request wins" of
  [`ARCHITECTURE.md`](../docs/ARCHITECTURE.md#background-work-and-changes-while-shown), built on the
  `Jobs` port and tested with `ManualJobs`; `Wallpaper`, in `wallpaper/`, state that outlives a
  screen, one per process, built on `StoredWallpaper`, `PictureImages`, `AppearanceSettings` and
  `Jobs`, and tested with their fakes; and `ReceivedPictures`, in `pictures/`, the shared pictures
  being added, built on `PictureImport` and tested with `FakePictures`.
- Ports are interfaces named after what they provide (`InstalledApps`), not how
  (`PackageManagerApps`); adapters in `:platform` implement them. A port whose result is a UI type
  this module can't name is generic in it (`AppImages<Image>`), and so is one that takes an
  Android type (`PictureImport<Source>`, a Uri in the app). A slow port answers later, on the main
  thread, through a function it's given (`PictureImport.add`), with a sealed result each screen
  handles in an exhaustive `when` (`ImportResult`). One a screen may stop waiting for returns a
  `Cancellable`, whose KDoc says what cancelling stops (`PictureImport.add`: no answer, nothing
  added).
- A port for state that changes while it's shown (`Clock`, `AppearanceSettings`, `AppArrangements`,
  `InstalledApps`, `Pictures`) is a port you can watch
  ([`ARCHITECTURE.md`](../docs/ARCHITECTURE.md#background-work-and-changes-while-shown)).
