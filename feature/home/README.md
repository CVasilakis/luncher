# feature/home/

The home screen, `:feature:home`: the top bar with the time and the settings entry, the app tiles,
and arrange mode. What a feature module holds and may use: [`../README.md`](../README.md); the
rules it follows: [`../../docs/ARCHITECTURE.md`](../../docs/ARCHITECTURE.md).

| | |
|---|---|
| Package | `com.luncher.launcher.home` |
| Depends on | `:domain`; `:ui`; `:feature:settings`, for `SettingsActivity`'s class, which it starts, and the settings panel's names, which the message that every app is hidden refers to |

## Layout

```
feature/home/
├── build.gradle.kts              luncher.android.library and Roborazzi; its dependencies
├── lint.xml                      lint's exceptions for this module's files
└── src/
    ├── main/
    │   ├── AndroidManifest.xml   HomeActivity, and how it behaves (below)
    │   ├── java/com/luncher/launcher/home/
    │   │   ├── HomeGraph.kt      the ports the home screen needs; Activity.graph
    │   │   ├── HomeActivity.kt   the home screen: reads the apps, shows those not hidden, opens them
    │   │   ├── TopBarView.kt     the top bar: the clock and the settings entry where the appearance puts them
    │   │   ├── ClockView.kt      the top bar's time and date, following the Clock port
    │   │   ├── AppTilesView.kt   places the tiles where the domain's TileLayout says; scrolls
    │   │   ├── AppTileView.kt    one app: its image, focus frame and zoom; held or hidden while arranging
    │   │   ├── TilePaints.kt     what every tile draws with: frames, dimming, the corners
│   │   ├── HomeWallpaper.kt  the wallpaper, the window's background: read at once if drawn before, else drawn in a job
    │   │   └── ArrangeMode.kt    arrange mode: keys to the domain's ArrangeSession, the shelf, the top bar's title and hint
    │   └── res/                  the focused tile's zoom (animator/), the settings gear and its focus
    │                             disc, home_activity.xml, home_* dimensions and strings, and their
    │                             translations in values-el/ and values-ar/ (docs/TRANSLATIONS.md)
    └── test/
        ├── java/…/home/          JVM tests; TestHomeGraph.kt: the ports a test gives the screen, and
        │                         the tests' Application (named in resources/robolectric.properties)
        └── screenshots/home/     reference images, committed
```

Everything but `HomeActivity` and `HomeGraph` is `internal`. The JVM tests' `TestHomeGraph` has
fakes of the ports but the real `BannerImages`, so the screenshots show tiles as the app draws
them.

## Manifest

The module declares `HomeActivity` and how it behaves; the intent filters that make it the home
screen and list it among the TV's apps are the app's ([`app/README.md`](../../app/README.md#manifest-why-each-part-is-there)).
The build merges the two.

| Element | Reason (don't remove without replacing it) |
|---|---|
| `launchMode="singleTask"` | Pressing Home returns to the same instance instead of stacking new ones. |
| `stateNotNeeded`, `clearTaskOnLaunch`, `excludeFromRecents` | Standard for home activities: always starts clean, never in Recents. |
| `screenOrientation="landscape"` | TVs are landscape. |
| `exported="true"` | Android starts it for Home and from the TV's app list. |
| `theme="@style/Theme.Luncher"` | Set on the activity rather than only on the app, so the JVM tests, which have no app manifest, show it as the app does. |
| `supportsRtl="true"` | As the app declares it ([why](../../app/README.md#manifest-why-each-part-is-there)), so the JVM tests follow a right-to-left language as the app does: there the home screen still stays left to right. |

A home screen must not close on Back; how `HomeActivity` ignores it on every Android version is
explained in its comments.

## The home screen

The screen is a top bar that stays in place, with the time and date in the device's language and
hour format, and below it the TV apps (activities with `MAIN` + `LEANBACK_LAUNCHER`) as tiles of
their banners, which scroll: by name, as the device's language sorts words, until the user
reorders them, as many per row as fit at about
`TileGeometry`'s tile width (154 dp: five on a 16:9 TV, more on a screen wider in dp, e.g. 1080p
at 160 dpi). OK opens the focused app. Each part does
one job, so a new arrangement, image source or top bar item changes one of them:

| Part | Job |
|---|---|
| `homeApps` (`:domain`) | which apps show, in which order, and which are hidden: the installed apps matched to the stored `AppArrangement` ([Hidden apps](../settings/README.md#hidden-apps)) |
| `Appearance` (`:domain`) | how the home screen looks, as the user set it in the [appearance editor](../settings/README.md#appearance), in parts: `TileGeometry` (the tiles' width, the gap between them, their vertical and horizontal alignment), `TileStyle` (their corners: a radius, 12 dp by default, or round ends), `TopBarAppearance` (whether the time and the date show, the clock's side, the time's side of the date), `Background` (the wallpaper: a picture of the picture library, or none, its fit, position, dim and blur). [Below](#appearance): how each part is applied. |
| `AppearanceSettings` (`:domain`), `PreferencesAppearanceSettings` (`:platform`) | the stored appearance, and when it changes |
| `TileLayout`, `TileLayouts` (`:domain`) | where each tile goes and how big it is; `TileGrid` is the only one so far, with as many columns as fit tiles of about the geometry's width (`TileGrid.columnsFor`), so tiles keep their size next to the top bar's text on any screen. `TileLayouts` is the only place that picks the arrangement, for the tiles' places and for moves in arrange mode alike. |
| `TileFocus` (`:domain`) | where each arrow takes the focus among the tiles ([below](#focus)) |
| `AppTilesView` | lays tiles out where the `TileLayout` says, moves the focus where the `TileFocus` says, and scrolls to the focused one; holds the `TilePaints` its tiles share |
| `bannerFor` (`:domain`) | which image a tile shows |
| `AppImages` (`:domain`), `CachedBannerImages` and `BannerImages` (`:platform`) | draws that image into a bitmap of the tile's size, or reads the one an earlier start drew |
| `AppTileView` | draws that bitmap, the focus frame and zoom, with the corners `TilePaints` gives; in arrange mode, a white frame and a bigger zoom when held, dimmed when hidden |
| `Clock` (`:domain`) | what time it is, in which time zone and hour format, and when that changes |
| `AndroidClock` (`:platform`) | reads those from Android, and watches the time broadcasts only while something listens |
| `ClockView` | formats a reading in the device's language; the time and the date side by side, in the order the top bar gives, either or both hidden |
| `topBarLayout` (`:domain`), `TopBarView` | which edge of the top bar the clock and the settings entry are at, the time's side of the date, and whether each of the two shows; the view moves its items there |
| `HomeActivity` | reads the apps and their arrangement in `onResume`; when the shown apps changed, creates tiles for new ones (an updated app is new: its banner may have changed) and drops the others. Without tiles, says why: nothing installed, or everything hidden. Starts the clock and follows the appearance, the arrangement and the installed apps in `onStart`, and stops all four in `onStop`: an app hidden or shown in the settings shows at once, behind the panel, and so does an app installed, updated or removed; arrange mode's own saves don't read the apps again, and an app changed while arranging shows when the mode ends. Opens the [settings panel](../settings/README.md#the-settings-panel) on OK on the gear or on the Menu key. A long press of OK on a tile starts [arrange mode](#arrange-mode), which gets every key first while it's on. |

### The top bar

`home_top_bar` in `home_activity.xml`, a `TopBarView`, holds the clock at one edge and the settings
gear at the other: by default the clock at the left, the time before the date, and the gear at the
right; the appearance can swap them, and hide the time, the date or both. With both hidden, the
gear is at the right, wherever the clock's side says. The domain's `topBarLayout` decides where
each goes, so the editor's preview shows them alike. A hidden time or date is invisible, not gone:
the bar keeps its height, so the tiles below it never move. Left and right are the screen's in every language
([why](../../app/README.md#manifest-why-each-part-is-there)); later items (e.g. status
indicators) go with the settings gear. The time always gets its room first; a date too long for
what's left ends in "…". Each item that shows device state
that changes (the time, later e.g. the network) gets it from a port you can watch
([`ARCHITECTURE.md`](../../docs/ARCHITECTURE.md#background-work-and-changes-while-shown)), with a
fake in `:domain`'s test fixtures that the test moves
([`FakeClock`](../../domain/src/testFixtures/kotlin/com/luncher/domain/clock/FakeClock.kt)), and is
a view with `start(port)` and `stop()`, called from `HomeActivity`'s `onStart` and `onStop`.

The bar itself isn't focusable. An item that should be reachable with the D-pad (the settings
gear) is a focusable view in it, and Up from the first row of tiles moves there through
Android's own focus search ([Focus](#focus)). In the bar, Left and Right move only among its
items, in the order they're laid out, and do nothing at its ends, where Android's search would go
on to a tile below; Down goes back to the tiles. Focus in the bar stays there when the apps change
on a return to the home screen; otherwise it stays on the same app.

What keeps it light:

- **One bitmap per tile, at the tile's size.** A banner resource is usually 640×360 px or more;
  only the scaled copy is kept, and drawing a tile copies it once.
- **Only new apps cost a bitmap.** Coming back to the home screen, or an app changing while it's
  shown, reads the app list again, but keeps the tiles, their bitmaps and the focus when it's the
  same; when an app was installed or updated, only its tile is new. Changes are heard from
  Android, not looked for: nothing runs between them, and a burst of them (an app store's
  updates) reads the list once.
- **Nothing allocated per key press in Luncher's code.** `TileFocus` keeps its last move in place;
  the zoom is a state animator created with each tile; scrolling reuses one `Scroller`.
  Android 5 still allocates inside its animators each time they start, about 2 KB a focus move.
- **Layout passes only when the tiles change.** Focus, zoom and scrolling redraw without
  measuring or laying out again. The clock's text changes once a minute, which lays out the top
  bar only, not the tiles.
- **Nothing runs while the home screen is hidden.** The clock's broadcast receiver exists only
  from `onStart` to `onStop`, and the appearance and the arrangement are followed only then.

### Appearance

The home screen reads the user's `Appearance` in `onStart`, then again whenever the port says it
changed, until `onStop`, and hands each part to the one view that draws it. Each view compares its
part with `==` against the one it has, so an unchanged part costs nothing: every return to the home
screen reads an equal appearance. What a change costs depends on the part:

| Part | Goes to | A change costs |
|---|---|---|
| `TileGeometry` | `AppTilesView.tileGeometry` | a new layout from `TileLayouts` (it's an input of the layout, with the width, the height, the number of tiles and `shownCount`), and a layout pass; new banner bitmaps only for tiles whose size changes, so a new placement draws none |
| `TileStyle` | `AppTilesView.tileStyle` | the radius in the `TilePaints` all tiles share, and a redraw: no layout pass, no new bitmaps |
| `TopBarAppearance` | `TopBarView.appearance` | the bar's items moved (focus on the gear stays), and a layout pass of the bar only |
| `Background` | `HomeWallpaper.show` | the window's background: the picture drawn at the screen's size, read from what an earlier start drew or drawn in the background ([below](#the-wallpaper)); nothing else is laid out or drawn anew |

The appearance editor covers the home screen whole, so the home screen is stopped while the user
changes it, and applies the result once, when it comes back
([`ARCHITECTURE.md`](../../docs/ARCHITECTURE.md#appearance)).

Where the tiles go is the layout's decision (`TileLayouts`, from the geometry's alignments): rows
keep the apps' order and are as wide as the space, and a row that isn't full goes at the left,
in the middle or at the right. In the middle, it keeps the other rows' gaps, so it's half a column
off them when the tiles it lacks are an odd number. The rows go at the top of the space below the
top bar, in its middle (with three rows, the second in the middle; with two, the gap between them)
or at its bottom, if they fit it; otherwise they start at the top and scroll. Centered or at the
bottom, a full bottom row puts the short row first rather than last.

With rounded corners (the default), each tile fills a rounded rectangle with its bitmap through a
shader made with the bitmap, when its size changes; the focus and held frames, and the shelf's
empty slot, follow the same corners. The radius in pixels is the domain's (`Corners`): at most
half the tile's height, which is what round ends are, so they're half circles on any screen. A new
radius draws the same bitmaps, so no banner is drawn again. Drawing allocates nothing, with or
without rounded corners.

### The wallpaper

The `Background` part's picture is the window's background (`HomeWallpaper`), behind the top bar
and the tiles, drawn at the screen's size in the background's style by `PictureImages`
([`platform/README.md`](../../platform/README.md#pictures)): filling the screen at the position the
user chose, or stretched, dimmed and blurred as set. A cold start reads it on the main thread from
what an earlier start drew, as the tiles' images, so the home screen's first frame has it. One not
drawn yet (a new picture or style, another screen size) is drawn in the background, the first job
in line (`JobPriority.WALLPAPER`), the plain background showing meanwhile, the latest request
winning; one still being drawn when the home screen stops is drawn again when it comes back. A
picture that's gone (deleted in Pictures, which sets the wallpaper to none, or its file lost) is no
wallpaper. The picture's drawing is kept at the screen's size in a file, about 8 MB in memory at
1080p while shown.

### Focus

Left and Right go to the next tile along the row, and do nothing at its ends. Up and Down go to the
next row's tile nearest across, which in full rows is the one right above or below; of two as
near, which a centered row half a column off puts on either side, the left one. Right after a move
Up or Down, the opposite key goes back to the tile it came from, even where another is as near or
nearer, so Down then Up is always a round trip; a move Left or Right forgets the way back. Up out
of the first row is the only way to the top bar; at every other edge the focus stays.

`TileFocus` (`:domain`) decides all of this; `AppTilesView` asks it in `focusSearch`, and forgets
its last move whenever a tile moves. Android's own search would go to the nearest view that way
anywhere on the screen: past a row's end to a tile of another row, and to the settings gear, which
on a 1080p TV ends a pixel right of the last tile, as the tiles' width rounds down. It only takes
Up out of the tiles, where the gear is the one thing above.

## Arrange mode

A long press of OK on an app's tile starts it, holding that app. The top bar's clock and gear give
way to the title "Arrange apps" and a hint of what the keys do, and a shelf of the hidden apps
(dimmed) appears below the others, under a "Hidden" label; with none hidden, a dashed empty slot
shows where hiding is.

| While… | Arrows | OK | Back |
|---|---|---|---|
| holding an app (white frame, bigger zoom) | move it | put it down | put it down, end the mode |
| not holding one | move the focus | pick up the focused app, shown or hidden | end the mode |

Left and Right move the held app one place along the order, wrapping rows; Up and Down one row, to
the place nearest across, as the [focus](#focus) goes, and back the way it came. Down out of the
last row puts it onto the shelf, in its column, which hides it; Up out of the shelf's first row
brings it back into the last row. Left and Right never cross between the two.
Home, or anything that stops the home screen (an app starting, the screen going off), ends the
mode too, with a held app put down where it is.

| Part | Job |
|---|---|
| `ArrangeSession` (`:domain`) | the held app, where each arrow takes it, and the apps as arranged; it keeps state, the one rule that does ([`domain/README.md`](../../domain/README.md#writing-models-rules-and-ports)) |
| `TileMoves` (`:domain`), in `TileGrid` | where a moved tile goes in the grid, and where one coming in from above or below lands |
| `ShelfLayout` (`:domain`) | the positions: the shown tiles, the label, the shelf; the three placed together in the height as the tiles' vertical alignment says, and the label over the shelf as its rows that aren't full are, at the left, centered or at the right |
| `ArrangeMode` | starts and ends the mode; turns keys into the session's moves and moves the tiles to match; stores the arrangement each time an app that moved is put down |
| `AppTilesView` | with `shownCount` set, lays the tiles out with a `ShelfLayout` and draws the label and the empty slot; `moveTile` moves one without it losing focus |

The first move fixes the order: from then on the shown apps keep the user's order instead of the
one by name, and apps installed later come after them.

A long press that starts the mode ends with OK's release, which the mode ignores: it only reacts
to presses that started while it was on. The mode needs no Menu key, and the Menu key does
nothing meanwhile. On Android 16 (API 36), Back reaches the app only through
`OnBackInvokedCallback`, not as a key, so `HomeActivity` passes Back to the mode from there as well
as from `onBackPressed`.

To try it on an emulator, a long press of OK has to hold the key down, which `adb shell input
keyevent --longpress` doesn't before API 30 ([`EMULATORS.md`](../../docs/EMULATORS.md#keys)).

What keeps it light:

- **The hidden apps' tiles exist only during the mode.** Their bitmaps are drawn when it starts,
  and dropped with the tiles when it ends.
- **A move allocates nothing.** The tile is moved in place (detached and attached again, which
  keeps its focus), and the tiles are laid out again with the layout they had: `AppTilesView`
  keeps it while the width and height, the number of tiles, `shownCount` and the `TileGeometry`
  stay the same, each compared with what the layout was made from, and a move changes none of them. Only an app crossing between the shown and the hidden ones creates new ones: the
  view's `ShelfLayout` and its two `TileGrid`s, and `ArrangeSession`'s `TileMoves`.
- **Nothing is read while arranging.** The apps are read again when the mode ends if one changed
  meanwhile, and when the home screen comes back, not while the user's changes are on screen.
