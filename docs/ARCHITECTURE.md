# Architecture

Luncher is a minimal Android TV home screen that has to run well on very weak devices (little RAM,
slow CPU and storage). Two goals shape the structure:

1. **Minimal runtime cost.** Platform Views and plain Kotlin only, so every layer boundary must
   cost nothing at runtime: interfaces and constructors, no frameworks.
2. **Targeted, isolated changes.** A feature or fix touches one area, with rules the compiler
   enforces where possible, and tests that cover each area separately ([`TESTING.md`](TESTING.md)).

## Modules

```
:app               ──▶  every module below; the composition root
:feature:home      ──▶  :domain, :ui; :feature:settings, for its activity's class
:feature:settings  ──▶  :domain, :ui
:platform          ──▶  :domain, :ui
:ui                     depends on nothing
:domain                 depends on nothing; pure Kotlin
```

| Module | Contains | May use |
|---|---|---|
| [`domain/`](../domain/README.md) | **Models** (data), **rules** (what the launcher decides: which apps show, in which order, with which banner) and **ports** (interfaces for what the rules and screens need from the device) | Kotlin standard library only. Android isn't on its classpath, so an `android.*` import doesn't compile. |
| [`feature/<name>/`](../feature/README.md) | One feature's **UI**: its activities, views, layouts and resources | `:domain`, `:ui`; another feature only to start its activity by class |
| [`ui/`](../ui/README.md) | What every screen shares, UI only: the app-wide theme and colors, shared views and helpers | Android platform APIs |
| [`platform/`](../platform/README.md) | The **adapters**: port implementations on Android APIs (PackageManager, SharedPreferences, the clock, later WallpaperManager and files) | Android platform APIs, `:domain`, `:ui` |
| [`app/`](../app/README.md) | The **composition root** (`AppGraph`), which creates the adapters and gives each feature its ports; the application's manifest, icon, version and release build | every module |

Dependencies only point down: `:domain` depends on nothing, no module depends on `:app`, and only
`:app` depends on `:platform` (and `:feature:home`'s JVM tests, which draw tiles with the real
`BannerImages`). A decision made in `:domain` is testable in milliseconds on the JVM,
and survives a rewrite of the UI. The build files set every boundary, so the compiler enforces
them: a feature can't name an adapter or another feature's views, since it doesn't depend on
them or they're `internal` there. The settings every Android module's build shares are in
[`build-logic/`](../build-logic/README.md).

## Features

A feature module holds everything of one feature on the Android side: its screens, views, layouts,
resources and manifest entries. It declares the ports it needs in an interface of its own
(`HomeGraph`), which `AppGraph` implements and the Application hands out; its activities get them
through `graph`. Everything in it is `internal` except its activities and that interface.

Features don't depend on each other, with one exception: a feature may start another feature's
activity by its class (`Intent(this, SettingsActivity::class.java)`), and use nothing else of it,
which the other's `internal` enforces. The compiler then checks the link, which an intent action
string wouldn't. Such links go one way; one that would close a cycle goes through an interface
`:app` implements instead. What two features share is either domain (a model or port in
`:domain`), or UI-only (`:ui`).

Resources are named after their feature (`home_activity.xml`, `home_apps`, `home_no_apps`), since
the app's resources all end up in one namespace. Only `:ui`'s app-wide resources have no prefix:
theme, colors.

## Rules

1. **Activities and views render and forward input; they don't decide.** Filtering, ordering,
   hiding, choosing a banner: rules in `:domain`, called by the UI.
2. **Android data APIs live only in adapters behind a port.** The UI never calls PackageManager,
   SharedPreferences or files directly: it gets ports from `graph`, and `:platform` isn't on a
   feature's classpath.
3. **Only `AppGraph` creates adapters.** It's the one place that knows which implementation backs a
   port. Production code never assigns `LuncherApplication.graph`; tests replace ports through it
   or through their feature's test graph ([`TESTING.md`](TESTING.md#organizing-tests)).
4. **Minimal visibility.** `private` by default; `internal` for what a module shares only within
   itself (in a feature, everything but its activities and graph interface). Public only what
   another module uses.
5. **Pay only for what's used.** Adapters are created lazily in `AppGraph`. No reflection,
   annotation processing or DI framework. No runtime libraries (AndroidX, AppCompat, Leanback,
   Compose, image loaders, kotlinx-coroutines, …): adding one is a deliberate decision, never a
   default. Activities extend `android.app.Activity`, with platform themes
   ([`ui/README.md`](../ui/README.md#the-theme)); plain `java.util.concurrent` and `Handler` cover
   background work
   ([below](#background-work-and-changes-while-shown)). Don't allocate in drawing or D-pad
   handling code. Scale bitmaps down to their display size.
6. **Storage formats belong to adapters.** `:domain` sees typed values (sets of hidden apps, an
   order), not file names, preference keys or file layouts, so a storage change stays in one
   adapter.
7. **Everything runs on API 22.** Guard newer APIs with `Build.VERSION.SDK_INT` checks.
8. **Everything works with a D-pad:** arrows, OK, Back and Home. There's no touchscreen. Many
   remotes have no Menu button, so nothing may be reachable only through Menu (long-press OK is
   the common alternative).

## Background work and changes while shown

Two needs, with one pattern each: a screen that must update while it's shown, because something
changed (a setting changed in the settings panel, over the home screen), and work too slow for
the main thread (decoding a wallpaper). Neither may delay a key press.

1. **All state lives on the main thread.** Activities, views and port listeners run there only;
   nothing else reads or writes them, so nothing needs a lock.
2. **State that can change while it's shown comes from a port you can watch:** a read function, and
   `addListener`/`removeListener` that tell when to read again, on the main thread (`Clock` is
   one), so nothing polls. Its adapter tells its listeners when the state changes, also when
   another screen of Luncher changed it, and registers with Android (a broadcast receiver, a
   callback) only while it has listeners, so a hidden screen costs nothing. A screen listens from
   `onStart` to `onStop`: the home screen stays started behind the floating settings panel, so a
   setting changed there shows on it at once, and a hidden screen does nothing. A change that's
   quick to apply (another `TileLayout`) is applied right there, on the main thread.
3. **Slow work is a job:** a computation from immutable inputs to an immutable result (a file and
   a size to a bitmap), which touches no view and no state. Jobs run one at a time on a single
   background thread that `AppGraph` creates on first use, and post their result to the main
   thread with a `Handler`. One at a time also bounds memory: only one large image is decoded at
   once.
4. **The latest request wins.** Each thing a screen loads (its wallpaper) is requested through a
   small helper that drops any earlier request: one not started yet is cancelled, and the result
   of one that finishes late is ignored. The screen stops it in `onStop`, which drops everything.
   What a screen ends up showing depends only on the last request, never on which job finished
   first.
5. **Tests decide when jobs run.** The background thread is replaceable like a port; tests give an
   executor that runs the queued jobs when the test says, so a loading state, a finished load and
   two loads finishing out of order are each tested on the JVM, without waiting
   ([`TESTING.md`](TESTING.md)).

No job runs today: the app list and the banners are read on the main thread, as they're quick
enough so far. The first slow job, the wallpaper, brings the background thread and the helper,
and moves to them whatever measures slow.

## Where things go

| Change | Where |
|---|---|
| A launcher decision (which apps show, sort order, banner choice, validation of a setting) | a rule in `:domain`, plus unit tests next to it |
| New data the rules or screens need from the device (settings, wallpaper, installed apps) | a port (interface) in `:domain` with a fake in its test fixtures; an adapter in `:platform`, in the package of the port's topic; one line in `AppGraph`; the port in the graph interface of each feature that uses it |
| An image the UI shows (banners, later the wallpaper) | which image: a model and rule in `:domain` (like `Banner`); drawing it: an adapter in `:platform` behind a port generic in the image type (`AppImages<Bitmap>`), since `:domain` can't name `Bitmap`. It draws at the size shown ([rule 5](#rules)), and is slow work if it's slow ([above](#background-work-and-changes-while-shown)). |
| How the home screen arranges apps (grid, apps per row, alignment, a carousel) | a `TileLayout` in `:domain`'s `layout/` that computes sizes and positions, and `TileMoves` for where a tile the user moves goes, with unit tests; the view that shows the tiles only places them where the layout says ([`feature/home/README.md`](../feature/home/README.md#the-home-screen)) |
| What the user can do while arranging apps (moving, hiding, which key does what to the held app) | `ArrangeSession` in `:domain`'s `arrange/`, with unit tests; `ArrangeMode` in `:feature:home` turns keys into its moves ([`feature/home/README.md`](../feature/home/README.md#arrange-mode)) |
| Something the home screen's top bar shows (the clock, the settings entry; later status indicators) | a view in `:feature:home`, placed in the bar; device state it shows (the time, the network) comes from a port you can watch ([above](#background-work-and-changes-while-shown)). Details: [`feature/home/README.md`](../feature/home/README.md#the-top-bar) |
| A new setting | its entry: `settingsMenu` in `:domain`'s `settings/`, and its label and action in `:feature:settings`; a value it stores comes through a port you can watch, so the screens it affects update while it changes; a screen of its own (a list, like Hide apps) is another activity in `:feature:settings`. Steps: [`feature/settings/README.md`](../feature/settings/README.md#the-settings-panel) |
| A new screen of its own (e.g. a wallpaper picker) | a new feature module (below) |
| A shared view, style or UI helper | `:ui`, resources without a prefix |
| Build or version changes | [`gradle/`](../gradle/README.md); settings every Android module shares: [`build-logic/`](../build-logic/README.md) |
| A guide that covers the whole project, or a topic too narrow for the document above it | [`docs/`](README.md) |
| Emulator and developer tooling | not here: the separate android-tv-wsl-dev-tools repository ([`EMULATORS.md`](EMULATORS.md)) |

## Adding a feature, step by step

1. Model the decision in `:domain`: models, a rule, and a port if device data is needed. Write its
   unit tests first; they're the cheapest.
2. Implement the port in an adapter in `:platform`; add it to `AppGraph`.
3. Create the feature module: `feature/<name>/` with a `build.gradle.kts` applying
   `luncher.android.library`, its namespace `com.luncher.launcher.<name>`, and the dependencies on
   `:domain` and `:ui`; `include` it in `settings.gradle.kts`, and add it to `:app`'s dependencies.
   ([`feature/README.md`](../feature/README.md) shows the parts.)
4. Declare the ports it needs in its `<Name>Graph` interface; `AppGraph` implements it, and
   `LuncherApplication` implements its `Owner`.
5. Build the UI in the module: it gets ports from `graph`, calls the rule, and renders. Its
   activities go in its manifest, with their theme.
6. Add the tests each layer needs ([`TESTING.md`](TESTING.md)), and check D-pad use on the emulator.
7. Update the docs that describe what changed ([`README.md`](README.md#writing-the-docs)).
