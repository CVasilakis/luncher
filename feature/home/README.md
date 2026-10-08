# feature/home/

The home screen, `:feature:home`: the top bar with the time and the settings entry, the app tiles,
and arrange mode. What a feature module holds and may use: [`../README.md`](../README.md); the
rules it follows: [`../../docs/ARCHITECTURE.md`](../../docs/ARCHITECTURE.md).

| | |
|---|---|
| Package | `com.luncher.launcher.home` |
| Depends on | `:domain`; `:ui`; `:feature:settings`, for `SettingsActivity`'s class, which it starts |

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
    │   │   ├── ClockView.kt      the top bar's time and date, following the Clock port
    │   │   ├── AppTilesView.kt   places the tiles where the domain's TileLayout says; scrolls
    │   │   ├── AppTileView.kt    one app: its image, focus frame and zoom; held or hidden while arranging
    │   │   └── ArrangeMode.kt    arrange mode: keys to the domain's ArrangeSession, the shelf, the top bar's title and hint
    │   └── res/                  the focused tile's zoom (animator/), the settings gear and its focus
    │                             disc, home_activity.xml, home_* dimensions and strings
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
their banners, which scroll: by name until the user reorders them, as many per row as fit at about
`home_tile_width` (154 dp: five on a 16:9 TV, more on a screen wider in dp, e.g. 1080p at
160 dpi). OK opens the focused app. Each part does
one job, so a new arrangement, image source or top bar item changes one of them:

| Part | Job |
|---|---|
| `homeApps` (`:domain`) | which apps show, in which order, and which are hidden: the installed apps matched to the stored `AppArrangement` ([Hidden apps](../settings/README.md#hidden-apps)) |
| `TileLayout` (`:domain`) | where each tile goes and how big it is; `TileGrid` is the only one so far, with as many columns as fit tiles of about `home_tile_width` (`TileGrid.columnsFor`), so tiles keep their size next to the top bar's text on any screen |
| `AppTilesView` | lays tiles out where the `TileLayout` says, and scrolls to the focused one. `grid` is the only place that picks the arrangement. |
| `bannerFor` (`:domain`) | which image a tile shows |
| `AppImages` (`:domain`), `BannerImages` (`:platform`) | draws that image into a bitmap of the tile's size |
| `AppTileView` | draws that bitmap, the focus frame and zoom; in arrange mode, a white frame and a bigger zoom when held, dimmed when hidden |
| `Clock` (`:domain`) | what time it is, in which time zone and hour format, and when that changes |
| `AndroidClock` (`:platform`) | reads those from Android, and watches the time broadcasts only while something listens |
| `ClockView` | formats a reading in the device's language, at the start of the top bar |
| `HomeActivity` | reads the apps and their arrangement in `onResume`; when the shown apps changed, creates tiles for new ones and drops the others. Without tiles, says why: nothing installed, or everything hidden. Starts the clock in `onStart` and stops it in `onStop`. Opens the [settings panel](../settings/README.md#the-settings-panel) on OK on the gear or on the Menu key. A long press of OK on a tile starts [arrange mode](#arrange-mode), which gets every key first while it's on. |

### The top bar

`home_top_bar` in `home_activity.xml` holds the clock at its start and the settings gear at its
end, the left and the right in every language
([why](../../app/README.md#manifest-why-each-part-is-there)); later items (e.g. status
indicators) go at the end too. Each item that shows device state
that changes (the time, later e.g. the network) gets it from a port you can watch
([`ARCHITECTURE.md`](../../docs/ARCHITECTURE.md#background-work-and-changes-while-shown)), with a
fake in `:domain`'s test fixtures that the test moves
([`FakeClock`](../../domain/src/testFixtures/kotlin/com/luncher/domain/clock/FakeClock.kt)), and is
a view with `start(port)` and `stop()`, called from `HomeActivity`'s `onStart` and `onStop`.

The bar itself isn't focusable. An item that should be reachable with the D-pad (the settings
gear) is a focusable view in it, and Up from the first row of tiles moves there through
Android's own focus search. Focus in the bar stays there when the apps change on a return to the
home screen; otherwise it stays on the same app.

What keeps it light:

- **One bitmap per tile, at the tile's size.** A banner resource is usually 640×360 px or more;
  only the scaled copy is kept, and drawing a tile copies it once.
- **Only new apps cost a bitmap.** Coming back to the home screen reads the app list again, but
  keeps the tiles, their bitmaps and the focus when it's the same; when an app was installed,
  only its tile is new.
- **Nothing allocated per key press in Luncher's code.** Android's own focus search moves between
  tiles; the zoom is a state animator created with each tile; scrolling reuses one `Scroller`.
  Android 5 still allocates inside its animators each time they start, about 2 KB a focus move.
- **Layout passes only when the tiles change.** Focus, zoom and scrolling redraw without
  measuring or laying out again. The clock's text changes once a minute, which lays out the top
  bar only, not the tiles.
- **Nothing runs while the home screen is hidden.** The clock's broadcast receiver exists only
  from `onStart` to `onStop`.

## Arrange mode

A long press of OK on an app's tile starts it, holding that app. The top bar's clock and gear give
way to the title "Arrange apps" and a hint of what the keys do, and a shelf of the hidden apps
(dimmed) appears below the others, under a "Hidden" label; with none hidden, a dashed empty slot
shows where hiding is.

| While… | Arrows | OK | Back |
|---|---|---|---|
| holding an app (white frame, bigger zoom) | move it | put it down | put it down, end the mode |
| not holding one | move the focus | pick up the focused app, shown or hidden | end the mode |

Left and Right move the held app one place along the order, wrapping rows; Up and Down one row.
Down out of the last row puts it onto the shelf, in its column, which hides it; Up out of the
shelf's first row brings it back into the last row. Left and Right never cross between the two.
Home, or anything that stops the home screen (an app starting, the screen going off), ends the
mode too, with a held app put down where it is.

| Part | Job |
|---|---|
| `ArrangeSession` (`:domain`) | the held app, where each arrow takes it, and the apps as arranged; it keeps state, the one rule that does ([`domain/README.md`](../../domain/README.md#writing-models-rules-and-ports)) |
| `TileMoves` (`:domain`), in `TileGrid` | where a moved tile goes in the grid, and where one coming in from above or below lands |
| `ShelfLayout` (`:domain`) | the positions: the shown tiles, the label, the shelf |
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
  keeps it while the width, the number of tiles and `shownCount` stay the same, which a move
  doesn't change. Only an app crossing between the shown and the hidden ones creates new ones: the
  view's `ShelfLayout` and its two `TileGrid`s, and `ArrangeSession`'s `TileMoves`.
- **Nothing is read while arranging.** The apps are read again when the home screen comes back
  after the mode, not while the user's changes are on screen.
