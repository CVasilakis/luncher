# domain/

The launcher's models, rules and ports, in pure Kotlin. What each of them is, what this module may
use, and how it fits with `:app`: [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md).

| | |
|---|---|
| Package | `com.luncher.domain` |
| Build | [`build.gradle.kts`](build.gradle.kts): Kotlin/JVM (`org.jetbrains.kotlin.jvm`), bytecode level 17 like `:app` |

## Layout

```
domain/src/
├── main/kotlin/com/luncher/domain/
│   ├── apps/
│   │   ├── LaunchableApp.kt     model: an app's TV launcher activity (package + activity), its identity
│   │   ├── InstalledApp.kt      model: a launchable app with its label and whether it has a banner
│   │   ├── InstalledApps.kt     port: the TV apps installed on the device
│   │   ├── HomeApps.kt          rule: which apps the home screen shows, in which order
│   │   └── Banner.kt            model and rule: which image a tile shows (the app's banner or its icon)
│   ├── clock/
│   │   ├── ClockReading.kt      model: the time, time zone and hour format at one moment
│   │   └── Clock.kt             port: the device's clock, and when what it shows changes
│   └── layout/
│       ├── TileLayout.kt        how an arrangement of tiles reports sizes and positions
│       └── TileGrid.kt          rule: the grid (rows of a fixed number of columns, centered)
├── test/kotlin/…/               unit tests (docs/TESTING.md), same packages as the code
└── testFixtures/kotlin/…/       fakes of the ports, used by the tests of every module
    ├── apps/FakeInstalledApps.kt   fake of the InstalledApps port
    └── clock/FakeClock.kt          fake of the Clock port: a fixed time the test moves
```

Packages are by topic (`apps/`, `clock/`, `layout/`, and later e.g. `settings/`, `wallpaper/`), each holding the
models, rules and ports of that topic.

## Writing models, rules and ports

- Models are immutable (`data class` with `val`s).
- Rules are plain functions or classes that take models and return models. They have no side effects,
  so they're testable without fakes.
- Ports are interfaces named after what they provide (`InstalledApps`), not how
  (`PackageManagerApps`); adapters in `:app` implement them.
- A port for device state that changes while it's shown (`Clock`) has a read function, and
  `addListener`/`removeListener` that tell when to read again, so nothing polls.
