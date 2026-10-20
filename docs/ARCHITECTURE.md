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
| [`platform/`](../platform/README.md) | The **adapters**: port implementations on Android APIs (PackageManager, SharedPreferences, the clock, files) | Android platform APIs, `:domain`, `:ui` |
| [`app/`](../app/README.md) | The **composition root** (`AppGraph`), which creates the adapters and gives each feature its ports; the application's manifest, icon, version and release build | every module |

Dependencies only point down: `:domain` depends on nothing, no module depends on `:app`, and only
`:app` depends on `:platform` (and `:feature:home`'s JVM tests, which draw tiles and a wallpaper
with the real `BannerImages` and `FilePictureImages`). A decision made in `:domain` is testable in milliseconds on the JVM,
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
the theme, colors, and the look of a panel and its focused entry.

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
   ([below](#background-work-and-changes-while-shown)). Don't allocate in drawing, or for a key
   that moves something (the focus, a held app); a key that changes a stored value (putting a
   moved app down, a setting's next value) may, as storing it does anyway. Scale bitmaps down to
   their display size.
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
the main thread (decoding a picture). Neither may delay a key press. Points 7 to 9 say how heavy
work is kept from coming back, and how a wait shows.

1. **All state lives on the main thread.** Activities, views and port listeners run there only;
   nothing else reads or writes them, so they need no lock. What passes between threads does so
   in two places only, each behind its own: the jobs' queue and its results, with
   `LatestRequest`'s word to a job that its request was dropped (points 3 and 4), and
   `outside/`'s waits on another app (point 6).
2. **State that can change while it's shown comes from a port you can watch:** a read function, and
   `addListener`/`removeListener` that tell when to read again, on the main thread (`Clock` is
   one), so nothing polls. Its adapter tells its listeners when the state changes, also when
   another screen of Luncher changed it, and registers with Android (a broadcast receiver, a
   callback) only while it has listeners, so a hidden screen costs nothing. A screen listens from
   `onStart` to `onStop`: the home screen stays started behind the floating settings panel, so a
   setting changed there shows on it at once, and a hidden screen does nothing. A change that's
   quick to apply (another `TileLayout`) is applied right there, on the main thread.
   State that outlives the screens, because work for one of them goes on when it closes (the
   wallpaper being drawn) or a screen recreated must find it where it was (the shared pictures
   being added), is a helper in `:domain` built on ports, one per process, which `AppGraph` makes
   and every screen that needs it watches the same way (`Wallpaper`, `ReceivedPictures`).
3. **Slow work is a job:** a computation from immutable inputs to an immutable result (a file and
   a size to a bitmap), which touches no view and no state, or an adapter's writing of a file only
   it reads, replaced in one step. Jobs run one at a time on a single background thread, at
   Android's background priority, through the `Jobs` port, which `AppGraph` creates on first use:
   the highest priority first, in the order they came among equals, every priority listed in
   `JobPriority`. A running job isn't interrupted, so a job is small: one image, not all of them.
   Two exceptions end a job early: one that waits on another app gives up by itself (point 6),
   and a decode whose request was dropped stops at its next read (point 4), so the jobs behind
   it, the one the user just asked for among them, don't wait for a picture nobody will see.
   A job posts its result to the main thread with a `Handler`. One at a time also bounds memory:
   only one large image is decoded at once.
4. **The latest request wins.** Each thing a screen loads (a picture's thumbnail, a preview) is
   requested through a small helper, `LatestRequest` in `:domain`'s `jobs/`, that drops any
   earlier request: one not started yet is cancelled, and the result
   of one that finishes late is ignored; one that's running is told (the `stopped` its work is
   given), and a decode stops at its next read: `:platform`'s `PictureFiles` reads a picture
   through a stream that fails from then on, which ends the decoder within milliseconds on every
   API level. The screen stops it in `onStop`, which drops everything.
   What a screen ends up showing depends only on the last request, never on which job finished
   first.
5. **Tests decide when jobs run.** The background thread is a port, `Jobs`; tests give
   `ManualJobs`, which runs the queued jobs when the test says, so a loading state, a finished
   load and two loads finishing out of order are each tested on the JVM, without waiting
   ([`TESTING.md`](TESTING.md)).
6. **A job never waits on another app.** What another app answers (its content provider's query,
   its open, the data it sends) may never come: a cloud app on a dead network, a broken one. A
   job that waited for it would hold up every job behind it, which the screens wait for. So an
   adapter asks another app only through `:platform`'s `outside/`
   ([`platform/README.md`](../platform/README.md#another-apps-answers)): its job gives up once the
   caller cancels it, or once the app has answered nothing for a time limit, and a call that can't
   be stopped once made is left on a short-lived thread of its own, which ends when the app
   answers or its process dies. `OutsideCalls` there is the only place that starts such a thread,
   and bounds how many are alive; nothing else starts a thread, as Luncher's own slow work is a
   job. Android's own services (PackageManager, LauncherApps) aren't another app: Android answers
   them. Tests give `outside/` a clock and a wait for data of their own, so the time another app
   takes passes when the test says.
7. **Heavy work is done once, and kept with what it depends on.** Work that costs more than a
   frame (decoding a picture) is done once per input. Its result is kept by the adapter that made
   it, with everything that shaped it in its header (the screen's size in px, the drawing's
   version); when any of that differs, it's made again in place. It's made as early as its input
   exists (a picture's working copy during its import, `PreparedPictures`). A screen that finds
   one missing makes all of them at its start, behind a working window, before the user can act,
   so nothing lags afterwards (the background editor's "Preparing picture 2 of 5…"). Options and
   styles are applied to the kept result, never to the original again; only what's stored for
   good (the wallpaper, on Save) is drawn from the original.
8. **Nothing is left behind.** One kept file per thing, never one per size or variant, replaced
   in one step. It's deleted with what it was made from, and each store is swept once per
   process of what nothing names any more (`FilesPictures`' cleanup of the pictures' files,
   working copies and thumbnails); what's kept only while a screen shows is deleted as it closes,
   and as it opens anew (Add from folder's previews).
9. **A wait shows, a decision doesn't.** What can be decided from data at hand (limits, formats,
   names in the library) is decided on the main thread and answered at once, without a working
   window (`whyRefused`). Real work the user waits for shows a working window at once, which stays
   at least half a second (`:ui`'s `ShortestShowing`, which `WorkingDialog` and the share window's
   wheel use), so it never flashes; while it shows, keys go to it only, and Back or Cancel stops
   the work where it can be stopped. A screen that can't tell real work from a decision before it
   starts shows the wait only once the adapter says the real work began (`PictureImport.add`'s
   `copying`).

Every kind of job has its priority in `JobPriority`, which lists them all; what each one does is
in the README of the module that runs it ([`platform/`](../platform/README.md), the features). What
a screen needs at once (the app list, the tiles' images, a wallpaper drawn before) is read on the
main thread, as it's quick enough.

## Appearance

What the user can change about how the home screen looks (where the tiles go, their corner
radius, a color, which side the clock is on, …) follows one design, so that another aspect is a
fixed list of steps that touches nothing else, and each step forgotten fails to compile or fails
a test.

1. **`Appearance`, in `:domain`'s `appearance/`, is one immutable model, made of parts.** Each
   part goes to the one part of the home screen that draws it, and holds values that cost the
   same to apply: `TileGeometry` (where tiles go and how big they are: a new layout, and new
   images when their size changes) is one; how a tile is drawn (a redraw only) would be another.
   Its defaults are the home screen as it looks without settings. Values are dp or choices, never
   pixels, and in the screen's own directions: left and right never mirror in a right-to-left
   language ([`app/README.md`](../app/README.md#manifest-why-each-part-is-there)). A color is a
   choice from a palette defined in `:domain`, never a free value.
2. **Each consumer applies only its part, and compares it with `==`.** A view keeps what it built
   from the part it was given last, and builds anew only when the new part is different, so an
   unchanged part costs nothing, and a value added to a part takes effect without code that
   remembers to drop what was kept. `TileLayouts`, in `:domain`'s `layout/`, is the one place
   that turns a `TileGeometry` into the layouts and moves of the tiles, in pixels at a density.
3. **What the user can change is a catalog of options in `:domain`:** each option is a type of
   its own, with an ordered list of values and the part of `Appearance` it reads and changes. A
   number is a list of steps too, so every option is changed the same way. Every layer that
   handles options does so in an exhaustive `when`, so a new option doesn't compile until each
   one handles it. Which values change anything can depend on facts that aren't settings, so
   aren't in `Appearance`: an `OptionContext`, which a screen builds and passes with the
   appearance (today what filling the screen cuts from the background's picture, `pictureCut`:
   left and right move only a wide picture, top and bottom only a tall one, and neither the
   position nor the fit one of the screen's shape). An option offers only the values that move
   something there, and doesn't apply where none would; a fact nobody knows (null) offers every
   value, as do the options a fact doesn't concern, so a fact added later changes no other
   option, nor a screen that doesn't know it. The full list of values stays what's stored: a
   stored value reads back whatever the context, and a screen keeps one the context doesn't
   offer out of its draft (`Background.offeredIn`), so it never shows a value that does
   nothing.
4. **One port you can watch stores the whole `Appearance`**
   ([above](#background-work-and-changes-while-shown)). Its adapter in `:platform` keeps one value
   per option, under a key it assigns in an exhaustive `when`; a value it doesn't know (stored by
   an older or newer version) becomes that option's default, and nothing else changes. A value
   that isn't an option (the wallpaper's picture, below) has a key of its own.
5. **The home screen applies a change once, not at every step.** A screen where the user changes
   the appearance covers the home screen whole, so the home screen is stopped meanwhile, doesn't
   listen, and applies the result when it comes back (a tile size's steps would otherwise redraw
   every banner each time).
6. **Whatever else shows an appearance (a preview) lays it out with the same `:domain` rules** as
   the home screen, at its own scale, never with rules of its own, so it can't disagree with the
   home screen.

Adding an aspect, e.g. the focus frame's color:

| Step | Where | What catches it when it's missing |
|---|---|---|
| The value in the part of `Appearance` it belongs to, its default the current look; a palette or limits it needs | `:domain` | unit tests |
| Its option in the catalog | `:domain` | unit tests |
| Its storage key | `:platform` | the exhaustive `when` doesn't compile; a round trip of every option in the catalog |
| Its label and its values' labels, in every translation | `:feature:settings` | the exhaustive `when` doesn't compile; lint ([`TRANSLATIONS.md`](TRANSLATIONS.md)) |
| Its effect on the home screen, and on the preview where it shows | `:feature:home`, `:feature:settings` | a test that sets each option of the catalog to a value other than its default, and fails if the home screen, or the preview, draws the same |

Today `Appearance` has four parts: `TileGeometry` (the tiles' size, spacing and alignment),
`TileStyle` (their corners), `TopBarAppearance` (whether the time and the
date show, the clock's side, and the time's side of the date) and `Background` (the wallpaper: a
picture of the picture library, or none, and how it's drawn). The catalog's options, the vertical
and the horizontal alignment, a full bottom row, the corner radius, the time and the date shown or
hidden, the clock's side and the time's side, are changed in the settings' appearance editor
([`feature/settings/README.md`](../feature/settings/README.md#appearance)); the wallpaper's fit,
position, dim and blur in the background editor it opens (below). The wallpaper's picture
isn't an option, since its values are the library's pictures, not a list known in `:domain`.

**The one exception to storing each change at once: the background.** Its group is edited apart
(`editedApart`), in the background editor
([`feature/settings/README.md`](../feature/settings/README.md#background)), where the user
chooses the picture and its options in a draft, which only Save stores: applying a
background decodes a photo and draws the wallpaper at the screen's size, a wait the user sees on
a TV, too slow for each value the user tries. Save goes through `Wallpaper` in `:domain`'s `wallpaper/`, which draws and
keeps the picture first, then stores the background, so the setting never names a wallpaper that
isn't drawn and the home screen always has one to show
([`feature/home/README.md`](../feature/home/README.md#the-wallpaper)).

## Where things go

| Change | Where |
|---|---|
| A launcher decision (which apps show, sort order, banner choice, validation of a setting) | a rule in `:domain`, plus unit tests next to it |
| New data the rules or screens need from the device (settings, pictures, installed apps) | a port (interface) in `:domain` with a fake in its test fixtures; an adapter in `:platform`, in the package of the port's topic; one line in `AppGraph`; the port in the graph interface of each feature that uses it |
| An image the UI shows (banners, a picture's thumbnail) | which image: a model and rule in `:domain` (like `Banner`, or a picture's `Framing`); drawing it: an adapter in `:platform` behind a port generic in the image type (`AppImages<Bitmap>`, `PictureImages<Bitmap>`), since `:domain` can't name `Bitmap`. It draws at the size shown ([rule 5](#rules)), and is slow work if it's slow ([above](#background-work-and-changes-while-shown)). Kept for the next start, it's a cache adapter in front of the one that draws (`CachedBannerImages`, `CachedPictureImages`). Drawn from a picture the user brought in, it's drawn from the picture's working copy, decoded once, never from the original again, except what's stored for good (the wallpaper) ([point 7](#background-work-and-changes-while-shown)). |
| Asking another app for something, or reading what it sends (a shared file, its provider) | an adapter in `:platform`, which asks through `outside/` ([point 6](#background-work-and-changes-while-shown)) with the one `OutsideCalls` `AppGraph` gives it; if a screen waits for the answer, its port returns a `Cancellable` (`PictureImport.add`) |
| A picture the user brings in, and what it's used for | the picture library: `:domain`'s `pictures/` (the models, the rules of framing, limits and formats, and the ports), its adapters in `:platform`'s `pictures/`, its screens in `:feature:settings` ([`feature/settings/README.md`](../feature/settings/README.md#background)); the pictures other apps share, added one at a time: `ReceivedPictures` in `:domain`'s `pictures/`, the share screen and its window in `:feature:settings` ([`feature/settings/README.md`](../feature/settings/README.md#adding-a-picture)); the wallpaper drawn from one and kept: `:domain`'s `wallpaper/` and `FileStoredWallpaper` ([`feature/home/README.md`](../feature/home/README.md#the-wallpaper)) |
| How the home screen arranges apps (grid, apps per row, alignment, a carousel) | a `TileLayout` in `:domain`'s `layout/` that computes sizes and positions, and `TileMoves` for where a tile the user moves goes, with unit tests; `TileLayouts` picks it, so everything that shows tiles lays them out alike; the view that shows the tiles only places them where the layout says ([`feature/home/README.md`](../feature/home/README.md#the-home-screen)) |
| What the user can do while arranging apps (moving, hiding, which key does what to the held app) | `ArrangeSession` in `:domain`'s `arrange/`, with unit tests; `ArrangeMode` in `:feature:home` turns keys into its moves ([`feature/home/README.md`](../feature/home/README.md#arrange-mode)) |
| Something the home screen's top bar shows (the clock, the settings entry; later status indicators) | a view in `:feature:home`, placed in the bar, with the wallpaper outline (`:ui`'s `WallpaperOutline`) so it reads on any picture; device state it shows (the time, the network) comes from a port you can watch ([above](#background-work-and-changes-while-shown)). Details: [`feature/home/README.md`](../feature/home/README.md#the-top-bar) |
| Something the user can change about how the home screen looks (the tiles' alignment, corner radius, a color) | a value in `Appearance` and an option in its catalog, in `:domain`; the steps and what checks each: [Appearance](#appearance) |
| A new setting | its entry: `settingsMenu` in `:domain`'s `settings/`, and its label and action in `:feature:settings`; a value it stores comes through a port you can watch, so the screens it affects update while it changes; a screen of its own (a list, like Hide apps) is another activity in `:feature:settings`. Steps: [`feature/settings/README.md`](../feature/settings/README.md#the-settings-panel) |
| A new screen of its own, which no feature's screens open or share anything with | a new feature module (below) |
| A shared view, style or UI helper | `:ui`, resources without a prefix |
| Text the user sees | a string in the `strings.xml` of the module that shows it, with a comment for translators, and in every translation ([`TRANSLATIONS.md`](TRANSLATIONS.md)) |
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
