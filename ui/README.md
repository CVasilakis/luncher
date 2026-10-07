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
    │       └── themes.xml        Theme.Luncher, every screen's theme or its parent
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
