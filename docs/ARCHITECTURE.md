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
| [`domain/`](../domain/README.md) | **Models** (data), **rules** (what the launcher decides: which apps show, in which order, with which banner) and **ports** (interfaces for what the rules need from the device) | Kotlin standard library only. Android isn't on its classpath, so an `android.*` import doesn't compile. |
| [`app/`](../app/README.md) | **UI** (activities, views, layouts), **adapters** (port implementations on Android APIs: PackageManager, SharedPreferences, WallpaperManager, files) and the **composition root** | Android platform APIs, `:domain` |

`:domain` never depends on `:app`. A decision made in `:domain` is testable in milliseconds on the
JVM, and survives a rewrite of the UI.

## Inside `:app`: packages by feature

The root package `com.luncher.launcher` holds only `LuncherApplication`, which keeps the process's
`AppGraph`, and `AppGraph`, the composition root. Everything else is in a feature package
(`home/`, `apps/`, …; the current files are listed in [`app/README.md`](../app/README.md#layout)).

A feature package holds everything of one feature on the Android side: its screens, views and
adapters. Features don't import each other. What two features share is either domain (a model or
port in `:domain`), or UI-only (a `ui/` package for shared views and styling, created when first
needed).

Resources follow the same split, because Android puts all of a module's resources in one
namespace. Names start with their feature (`home_activity.xml`, `home_apps`, `home_no_apps`).
Only app-wide resources have no prefix: `app_name`, theme, colors.

## Rules

1. **Activities and views render and forward input; they don't decide.** Filtering, ordering,
   hiding, choosing a banner: rules in `:domain`, called by the UI.
2. **Android data APIs live only in adapters behind a port.** The UI never calls PackageManager,
   SharedPreferences or files directly. It gets ports from `graph`.
3. **Only `AppGraph` creates adapters.** It's the one place that knows which implementation backs a
   port. Production code never assigns `LuncherApplication.graph`; tests replace ports through it
   ([`TESTING.md`](TESTING.md#organizing-tests)).
4. **Minimal visibility.** `private` by default. `internal` in `:domain` for helpers the app
   doesn't need. Public only what another package uses.
5. **Pay only for what's used.** Adapters are created lazily in `AppGraph`. No reflection,
   annotation processing or DI framework. No runtime libraries (AndroidX, AppCompat, Leanback,
   Compose, image loaders, kotlinx-coroutines, …): adding one is a deliberate decision, never a
   default; plain `java.util.concurrent` and `Handler` cover background work. Don't allocate in
   drawing or D-pad handling code. Scale bitmaps down to their display size.
6. **Storage formats belong to adapters.** `:domain` sees typed values (sets of hidden apps, an
   order), not preference keys or file layouts, so a storage change stays in one adapter.
7. **Everything runs on API 22.** Guard newer APIs with `Build.VERSION.SDK_INT` checks.
8. **Everything works with a D-pad:** arrows, OK, Back and Home. There's no touchscreen. Many
   remotes have no Menu button, so nothing may be reachable only through Menu (long-press OK is
   the common alternative).

## Where things go

| Change | Where |
|---|---|
| A launcher decision (which apps show, sort order, banner choice, validation of a setting) | a rule in `:domain`, plus unit tests next to it |
| New data the rules need from the device (settings, wallpaper, installed apps) | a port (interface) in `:domain` with a fake in its test fixtures, an adapter in `:app/<feature>/`, one line in `AppGraph` |
| An image the UI shows (banners, later the wallpaper) | which image: a model and rule in `:domain` (like `Banner`); drawing it: an adapter in `:app/<feature>/`, created in `AppGraph`. The adapter has no port, because its result (`Bitmap`, `Drawable`) is an Android type `:domain` can't name. It draws at the size shown ([rule 5](#rules)). |
| How the home screen arranges apps (grid, apps per row, alignment, a carousel) | a `TileLayout` in `:domain`'s `layout/` that computes sizes and positions, with unit tests; the view that shows the tiles only places them where the layout says ([`app/README.md`](../app/README.md#the-home-screen)) |
| A new screen (e.g. settings) | a new feature package in `:app` with its activity, layouts prefixed with the feature name, manifest entry |
| A shared view or style | `:app` `ui/` package, `values/` without a prefix |
| Build or version changes | [`gradle/`](../gradle/README.md) |
| A guide that covers the whole project | [`docs/`](README.md) |
| Emulator and developer tooling | not here: the separate android-tv-wsl-dev-tools repository (see [`README.md`](../README.md#emulators-and-the-android-tv-wsl-dev-tools-scripts)) |

## Adding a feature, step by step

1. Model the decision in `:domain`: models, a rule, and a port if device data is needed. Write its
   unit tests first; they're the cheapest.
2. Implement the port in an adapter in the feature's `:app` package; add it to `AppGraph`.
3. Build the UI in the feature package: it gets ports from `graph`, calls the rule, and renders.
4. Add the tests each layer needs ([`TESTING.md`](TESTING.md)), and check D-pad use on the emulator.
5. Update the docs that describe what changed ([`README.md`](README.md#writing-the-docs)).
