# domain/

The launcher's models, rules and ports, in pure Kotlin. No Android and no libraries: the
compiler rejects both, since neither is on this module's classpath. See
[`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md) for how it fits with `:app`.

| | |
|---|---|
| Package | `com.luncher.domain` |
| Build | [`build.gradle.kts`](build.gradle.kts): Kotlin/JVM (`org.jetbrains.kotlin.jvm`), bytecode level 17 like `:app` |
| Tests | `src/test/kotlin/`, JUnit 4: `./gradlew :domain:test` (see [`../docs/TESTING.md`](../docs/TESTING.md)) |
| Test fixtures | `src/testFixtures/kotlin/`: fakes of the ports, used by the tests of every module |

## Layout

```
domain/src/
├── main/kotlin/com/luncher/domain/apps/
│   ├── LaunchableApp.kt         model: an app's TV launcher activity (package + activity)
│   └── InstalledApps.kt         port: the TV apps installed on the device
├── test/kotlin/…/apps/          unit tests, same packages as the code
└── testFixtures/kotlin/…/apps/
    └── FakeInstalledApps.kt     fake of the InstalledApps port
```

Packages are by topic (`apps/`, and later e.g. `settings/`, `wallpaper/`), each holding the models, rules
and ports of that topic.

## Rules

- Models are immutable (`data class` with `val`s).
- Rules are plain functions or classes that take models and return models. They have no side effects,
  so they're testable without fakes.
- Ports are interfaces named after what they provide (`InstalledApps`), not how
  (`PackageManagerApps`); adapters in `:app` implement them.
- `internal` for anything `:app` doesn't need.
- Every port has a fake in `src/testFixtures/`, changed together with the port.
