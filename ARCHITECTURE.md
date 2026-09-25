# Architecture

Luncher is a minimal Android TV home screen that has to run well on very weak devices (little RAM,
slow CPU and storage). Two goals shape the structure:

1. **Minimal runtime cost.** Platform Views and plain Kotlin only, so every layer boundary must
   cost nothing at runtime: interfaces and constructors, no frameworks.
2. **Targeted, isolated changes.** A feature or fix touches one area, with rules the compiler
   enforces where possible, and tests that cover each area separately ([`TESTING.md`](TESTING.md)).

## Modules

```
:app  ──depends on──▶  :domain
Android: UI, adapters,        Pure Kotlin: models, rules, ports.
composition root              No Android, no libraries.
```

| Module | Contains | May use |
|---|---|---|
| [`domain/`](domain/README.md) | **Models** (immutable data), **rules** (what the launcher decides: which apps show, in which order, with which banner) and **ports** (interfaces for what the rules need from the device) | Kotlin standard library only. Android isn't on its classpath, so an `android.*` import doesn't compile. |
| [`app/`](app/README.md) | **UI** (activities, views, layouts), **adapters** (port implementations on Android APIs: PackageManager, SharedPreferences, WallpaperManager, files) and the **composition root** | Android platform APIs, `:domain` |

`:domain` never depends on `:app`. A decision made in `:domain` is testable in milliseconds on the
JVM, and survives a rewrite of the UI.

## Inside `:app`: packages by feature

```
com.luncher.launcher
├── LuncherApplication.kt   holds the AppGraph (tests may replace it); `Activity.graph` accessor
├── AppGraph.kt             composition root: creates adapters, lazily; open so tests can override ports
├── home/                   the home screen (HomeActivity, its views)
└── apps/                   adapters about installed apps (PackageManagerInstalledApps)
```

A feature package holds everything of one feature on the Android side: its screens, views and
adapters. Features don't import each other. What two features share is either domain (a model or
port in `:domain`), or UI-only (a `ui/` package for shared views and styling, created when first
needed).

Resources follow the same split, because Android puts all of a module's resources in one
namespace. Names start with their feature (`home_activity.xml`, `home_status`,
`home_status_apps_found`). Only app-wide resources have no prefix: `app_name`, theme, colors.

## Rules

1. **No Android in `:domain`**, and no libraries either. The build enforces this.
2. **Activities and views render and forward input; they don't decide.** Filtering, ordering,
   hiding, choosing a banner: rules in `:domain`, called by the UI.
3. **Android data APIs live only in adapters behind a port.** The UI never calls PackageManager,
   SharedPreferences or files directly. It gets ports from `graph`.
4. **Only `AppGraph` creates adapters.** It's the one place that knows which implementation backs a
   port. Tests override single ports in a subclass and install it as `LuncherApplication.graph`
   ([`TESTING.md`](TESTING.md#organizing-tests)); production code never assigns `graph`.
5. **Minimal visibility.** `private` by default. `internal` in `:domain` for helpers the app
   doesn't need. Public only what another package uses.
6. **Pay only for what's used.** Adapters are created lazily in `AppGraph`. No reflection,
   annotation processing or DI framework. No runtime libraries without the user's agreement (that
   includes kotlinx-coroutines; plain `java.util.concurrent` and `Handler` cover background work).
   Don't allocate in drawing or D-pad handling code. Scale bitmaps down to their display size.
7. **Storage formats belong to adapters.** `:domain` sees typed values (sets of hidden apps, an
   order), not preference keys or file layouts, so a storage change stays in one adapter.
8. **Everything works with a D-pad** (see [`AGENTS.md`](AGENTS.md)).

## Where things go

| Change | Where |
|---|---|
| A launcher decision (which apps show, sort order, banner choice, validation of a setting) | a rule in `:domain`, plus unit tests next to it |
| New data the rules need from the device (settings, wallpaper, images) | a port (interface) in `:domain` with a fake in its test fixtures, an adapter in `:app/<feature>/`, one line in `AppGraph` |
| A new screen (e.g. settings) | a new feature package in `:app` with its activity, layouts prefixed with the feature name, manifest entry |
| A shared view or style | `:app` `ui/` package, `values/` without a prefix |
| Build or version changes | [`gradle/`](gradle/README.md) |
| Emulator and developer tooling | not here: the separate android-cli-dev-tools repository (see [`README.md`](README.md#requirements)) |

## Adding a feature, step by step

1. Model the decision in `:domain`: models, a rule, and a port if device data is needed. Write its
   unit tests first; they're the cheapest.
2. Implement the port in an adapter in the feature's `:app` package; add it to `AppGraph`.
3. Build the UI in the feature package: it gets ports from `graph`, calls the rule, and renders.
4. Add the tests each layer needs ([`TESTING.md`](TESTING.md)), and check D-pad use on the emulator.
5. Update the READMEs that describe what changed.
