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
│   │   ├── InstalledApp.kt      model: a launchable app with its label and whether it has a banner
│   │   ├── InstalledApps.kt     port: the TV apps installed on the device
│   │   ├── AppImages.kt         port: an app's image drawn at a size, in the UI's image type
│   │   ├── AppArrangement.kt    model: the user's order of the shown apps, and the hidden apps, as stored
│   │   ├── AppArrangements.kt   port: where the arrangement is kept
│   │   ├── HomeApps.kt          rule: which apps the home screen shows, in which order, and which are hidden
│   │   ├── ArrangedApps.kt      model and rules: the shown and hidden apps; hiding or showing one, and what to store
│   │   └── Banner.kt            model and rule: which image a tile shows (the app's banner or its icon)
│   ├── clock/
│   │   ├── ClockReading.kt      model: the time, time zone and hour format at one moment
│   │   └── Clock.kt             port: the device's clock, and when what it shows changes
│   ├── settings/
│   │   ├── SettingsEntry.kt     model: the kinds of entry the settings panel lists, one type each
│   │   └── SettingsMenu.kt      models and rule: which entries the panel lists, in tabs and groups
│   ├── arrange/
│   │   └── ArrangeSession.kt    rule: the user arranging apps, holding one and moving it among and between shown and hidden
│   └── layout/
│       ├── TileLayout.kt        how an arrangement of tiles reports sizes and positions
│       ├── TileMoves.kt         how it says where a tile the user moves goes
│       ├── Direction.kt         model: a D-pad arrow
│       ├── TileGrid.kt          rule: the grid (rows of tiles, centered; as many columns as fit a width), and moves in it
│       └── ShelfLayout.kt       rule: the shown apps' tiles above a shelf of the hidden ones, while arranging
├── test/kotlin/…/               unit tests (docs/TESTING.md), same packages as the code
└── testFixtures/kotlin/…/       fakes of the ports, used by the tests of every module
    ├── apps/FakeInstalledApps.kt   fake of the InstalledApps port
    ├── apps/FakeAppArrangements.kt fake of the AppArrangements port: the arrangement in memory
    └── clock/FakeClock.kt          fake of the Clock port: a fixed time the test moves
```

Packages are by topic (`apps/`, `arrange/`, `clock/`, `layout/`, `settings/`, and later e.g. `wallpaper/`), each holding the
models, rules and ports of that topic.

## Writing models, rules and ports

- Models are immutable (`data class` with `val`s).
- Rules are plain functions or classes that take models and return models. They have no side effects,
  so they're testable without fakes. The one exception is a rule that follows the user's key
  presses, `ArrangeSession`: it keeps state and changes it in place, so a key press allocates
  nothing ([`ARCHITECTURE.md`](../docs/ARCHITECTURE.md#rules), rule 5). It's still pure Kotlin,
  tested the same way.
- Ports are interfaces named after what they provide (`InstalledApps`), not how
  (`PackageManagerApps`); adapters in `:platform` implement them. A port whose result is a UI type
  this module can't name is generic in it (`AppImages<Image>`).
- A port for device state that changes while it's shown (`Clock`) has a read function, and
  `addListener`/`removeListener` that tell when to read again, so nothing polls.
