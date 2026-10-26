# feature/home/

The home screen, `:feature:home`: the top bar with the time and the settings entry, the app tiles,
their menu and moving an app, and the wallpaper behind them. What a feature module holds and may use: [`../README.md`](../README.md); the
rules it follows: [`../../docs/ARCHITECTURE.md`](../../docs/ARCHITECTURE.md).

| | |
|---|---|
| Package | `com.luncher.launcher.home` |
| Depends on | `:domain`; `:ui`; `:feature:settings`, for `SettingsActivity`'s class and `TileBannerActivity.intent`, the screens it starts, and the settings panel's names, which the message that every app is hidden refers to |

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
    │   │   ├── OutlinedTextView.kt  a text on the wallpaper, with :ui's WallpaperOutline around it
    │   │   ├── AppTilesView.kt   places the tiles where the domain's TileLayout says; scrolls
    │   │   ├── AppTileView.kt    one app: its image, or its picture as prepared, read; focus zoom with a frame or a lift and
    │   │   │                     shadow; its name; held while moved; drawn again once its picture is prepared
    │   │   ├── TilePaints.kt     what every tile draws with: frames, dimming, the name, the corners, the focus frame on or off, the shadow's outline
    │   │   ├── HomeWallpaper.kt  the wallpaper, the window's background, as the process's Wallpaper says
    │   │   ├── HomeWorking.kt    the one window while the wallpaper is drawn again or the tiles' pictures prepared, saying why
    │   │   ├── TileMenu.kt       a tile's menu: the domain's AppActions for its app, those the device can't do
    │   │   │                     dimmed, in a window above, below or beside it, its corners following the tiles'
    │   │   └── MoveMode.kt       moving an app: keys to the domain's MoveSession, the top bar's title and hint
    │   └── res/                  the focused tile's zoom, or zoom and lift (animator/), the settings gear (its focus
    │                             disc is set in code, in the accent), home_activity.xml, the tile menu's layouts and theme
    │                             (Theme.Luncher.Home.Menu), home_* dimensions and strings, and their
    │                             translations in values-el/ and values-ar/ (docs/TRANSLATIONS.md)
    └── test/
        ├── java/…/home/          JVM tests; TestHomeGraph.kt: the ports a test gives the screen, and
        │                         the tests' Application (named in resources/robolectric.properties)
        └── screenshots/home/     reference images, committed
```

Everything but `HomeActivity` and `HomeGraph` is `internal`. The JVM tests' `TestHomeGraph` has
fakes of the ports but the real `BannerImages`, so the screenshots show tiles as the app draws
them, and a real `Wallpaper` over a kept wallpaper in memory (`FakeStoredWallpaper`); the
screenshots of a wallpaper draw it with the real `FilePictureImages` too.

## Manifest

The module declares `HomeActivity` and how it behaves; the intent filters that make it the home
screen and list it among the TV's apps are the app's ([`app/README.md`](../../app/README.md#manifest-why-each-part-is-there)).
The build merges the two.

| Element | Reason (don't remove without replacing it) |
|---|---|
| `launchMode="singleTask"` | Pressing Home returns to the same instance instead of stacking new ones. |
| `stateNotNeeded`, `clearTaskOnLaunch`, `excludeFromRecents` | Standard for home activities: always starts clean, never in Recents. |
| `screenOrientation="landscape"` | TVs are landscape. |
| `exported="true"` | Android starts it for Home and from the TV's app list; a shared picture opens it one of those two ways ([`settings/README.md`](../settings/README.md#adding-a-picture)). |
| `theme="@style/Theme.Luncher"` | Set on the activity rather than only on the app, so the JVM tests, which have no app manifest, show it as the app does. |
| `windowSoftInputMode="adjustNothing"` | The keyboard the [rename dialog](#renaming-an-app) opens doesn't slide the home screen up behind it to keep a low tile in sight (wallpaper, clock and all, a black band below); the dialog moves instead. |
| `supportsRtl="true"` | As the app declares it ([why](../../app/README.md#manifest-why-each-part-is-there)), so the JVM tests follow a right-to-left language as the app does: there the home screen still stays left to right. |

A home screen must not close on Back; how `HomeActivity` ignores it on every Android version is
explained in its comments.

## The home screen

The screen is a top bar that stays in place, with the time and date in the device's language and
hour format, and below it the TV apps (activities with `MAIN` + `LEANBACK_LAUNCHER`) as tiles of
their banners, which scroll: by name, as the device's language sorts words, until the user
reorders them, as many per row as fit at about
`TileGeometry`'s tile width (154 dp: five on a 16:9 TV, more on a screen wider in dp, e.g. 1080p
at 160 dpi). An app's name is the one the user gave it, where they did, else its label: below
its tile, on the card drawn for an app without a banner, in the order by name, and for a screen
reader (the tile's description, its menu's pane title). OK opens the focused app. Each part does
one job, so a new arrangement, image source or top bar item changes one of them:

| Part | Job |
|---|---|
| `homeApps` (`:domain`) | which apps show, in which order, and which are hidden: the installed apps matched to the stored `AppArrangement` ([Hidden apps](../settings/README.md#hidden-apps)), each a `HomeApp` with what the user set for it, matched the same way (a reinstalled app, or one whose update renamed its activity, keeps its name) |
| `AppCustomizations` (`:domain`), `PreferencesAppCustomizations` (`:platform`) | what the user set for each app (its name, its tile's picture), and when it changes; kept apart from the arrangement |
| `Appearance` (`:domain`) | how the home screen looks, as the user set it in the [appearance editor](../settings/README.md#appearance), in parts: `TileGeometry` (the tiles' width, the gap between them, their vertical and horizontal alignment, whether the app names show: below the focused tile, below every tile, or not at all), `TileStyle` (their corners: a radius, 16 dp by default, or round ends; the focus frame: on by default, else the focused tile lifted), `TopBarAppearance` (whether the time and the date show, the clock's side, the time's side of the date), `Background` (the wallpaper: a picture of the picture library, or none, its fit, position, dim and blur). [Below](#appearance): how each part is applied. |
| `AppearanceSettings` (`:domain`), `PreferencesAppearanceSettings` (`:platform`) | the stored appearance, and when it changes |
| `TileLayout`, `TileLayouts` (`:domain`) | where each tile goes and how big it is; `TileGrid` is the only one so far, with as many columns as fit tiles of about the geometry's width (`TileGrid.columnsFor`), so tiles keep their size next to the top bar's text on any screen. `TileLayouts` is the only place that picks the arrangement, for the tiles' places and for moving an app alike. |
| `TileFocus` (`:domain`) | where each arrow takes the focus among the tiles ([below](#focus)) |
| `AppTilesView` | lays tiles out where the `TileLayout` says, moves the focus where the `TileFocus` says, and scrolls to the focused one; holds the `TilePaints` its tiles share |
| `bannerFor` (`:domain`) | which image a tile shows: the app's banner, the card of its icon and name for an app without one, or a picture of the library the user chose for it ([below](#a-tiles-picture)) |
| `AppImages` (`:domain`), `CachedBannerImages` and `BannerImages` (`:platform`) | draws the app's own image into a bitmap of the tile's size, or reads the one an earlier start drew |
| `PreparedTileImages`, `PreparedTiles` (`:domain`), `FilePreparedTiles` (`:platform`) | a tile's picture, prepared ahead in jobs and kept, read as the tile is drawn ([below](#a-tiles-picture)) |
| `AppTileView` | draws that bitmap, the focus zoom with the frame or the lift ([below](#appearance)), with the corners `TilePaints` gives, and its app's name below it if the geometry says (while focused, or always); while its app is [moved](#moving-an-app), a white frame |
| `Clock` (`:domain`) | what time it is, in which time zone and hour format, and when that changes |
| `AndroidClock` (`:platform`) | reads those from Android, and watches the time broadcasts only while something listens |
| `ClockView` | formats a reading in the device's language; the time and the date side by side, in the order the top bar gives, either or both hidden |
| `topBarLayout` (`:domain`), `TopBarView` | which edge of the top bar the clock and the settings entry are at, the time's side of the date, and whether each of the two shows; the view moves its items there |
| `HomeActivity` | reads the apps, their arrangement and what the user set for them in `onStart`, so a change made while it was hidden shows as soon as it's visible again, even behind the settings panel; when the shown apps changed, creates tiles for new ones (an updated app is new: its banner may have changed; so is a renamed one, or one whose tile's picture changed, whose tile alone is drawn again) and drops the others. Without tiles, says why: nothing installed, or everything hidden. Starts the clock and follows the wallpaper, the tiles' pictures being prepared, the appearance, the arrangement, the names and the installed apps in `onStart`, and stops all seven in `onStop`: an app hidden or shown in the settings, or hidden from its tile's menu, shows at once, and so does an app renamed, given a picture, installed, updated or removed; moving an app saves without reading the apps again, and an app changed while moving one shows when that ends. When the focused app is gone (hidden, removed), the focus goes to the tile now in its place, or the last one; without tiles, to the settings entry. Opens the [settings panel](../settings/README.md#the-settings-panel) on OK on the gear or on the Menu key. A long press of OK on a tile opens [its menu](#a-tiles-menu); [moving an app](#moving-an-app) gets every key first while it's on. |

### A tile's picture

A tile shows its app's banner, or, for an app without one, a card of its icon and name; or a
picture of the library the user chose for it (`AppCustomization.image`, a `TileImage`: the
picture, filled from its middle or a side, or stretched, and the tile's look), which `bannerFor`
gives as `Banner.Picture`, with the app's own image to show where the picture isn't prepared. The
picture is **prepared ahead, and only read here**: drawn in a job from the picture's working copy,
decoded at about the tile's size, never from the original, a photo whose decode would hold up
the start, and kept for good (`FilePreparedTiles`, in the app's private files), so the home
screen's main thread never decodes a picture. It's prepared at Save in the tile banner picker
([`settings/README.md`](../settings/README.md#tile-banner)), and by the home screen behind its
window where it isn't for the tile as it is ([below](#preparing-the-tiles-pictures)). What a
tile's picture is prepared for is its drawing (`TileDrawing`): the picture, its framing, what the
tile's focus follows, and the tile's size (`:ui`'s `homeTileSize`, the size `TileLayouts` gives
in the screen's width less the home screen's padding at the sides, which `:ui` holds, so the
picker prepares the same size; `HomeLayoutTest` checks the tiles laid out are that size). Its
backdrop isn't: it's drawn live. Kept with the image: whether it's see-through, and, where the
focus follows a see-through picture's shape, that shape's masks at half scale, which a later
drawing of the look uses. For now a see-through picture is drawn over the background's color,
as every picture was.

A tile whose picture isn't prepared for its drawing (none yet, the TV switched resolution, an
update draws pictures otherwise) shows its app's own image, kept as that, so the picture shows as
soon as it's prepared. One that doesn't read (damaged) shows its app's own image, and is prepared
again, once. A tile that no longer shows a picture (the app uninstalled, its picture deleted from
the library, [`settings/README.md`](../settings/README.md#background), or its own banner chosen
again) has what was prepared of it deleted.

### Preparing the tiles' pictures

The home screen prepares the missing tiles' pictures, behind its window. At each read of the apps,
and when the appearance gives the tiles another size, it tells `PreparedTileImages` (`:domain`,
one per process, made by `AppGraph`, the same the tile banner picker saves through) what every
installed app's tile showing a picture is drawn from, hidden ones' too, so showing an app again
never waits (`ArrangedApps.tileDrawings`). Those not prepared for it are prepared in jobs
(`JobPriority.PICTURE_SCREENS`), the picture's working copy first where it isn't made for the
screen, then the tile, while the window says why: "Fitting the tile banners to this screen…" when
each one is for a tile of another size, "Processing tile banner…" while a tile saved in the
picker isn't done yet (the user went home meanwhile), else "Updating the tile banners…". It's the
wallpaper's window, which says the wallpaper's cause first when both are due
([below](#the-wallpaper)), and takes every key. It asks only when what the tiles are drawn from,
or their size, changed since, so a return to the home screen costs nothing.

A working copy found damaged as a tile is prepared is deleted and made again at once, then the
tile; found damaged again, the tile is given up. So is a tile that doesn't read. A tile that can't
be prepared (its picture's original unreadable, a full disk) is given up until Luncher starts
again, keeping its app's image, so nothing is tried again and again; saving it again in the
picker tries once more. As each tile is prepared, it's drawn again
(`AppTileView.followPreparing`), nothing else laid out. The preparing goes on when the home screen
stops, and its tiles show their pictures when it's back. What's kept is what the tiles show: a
tile that leaves them has its file deleted, in a job, and the first preparing of a process deletes
every file no tile names.

### The top bar

`home_top_bar` in `home_activity.xml`, a `TopBarView`, holds the clock at one edge and the settings
gear at the other: by default the clock at the left, the time before the date, and the gear at the
right; the appearance can swap them, and hide the time, the date or both. With both hidden, the
gear is at the right, wherever the clock's side says. The domain's `topBarLayout` decides where
each goes, so the editor's preview shows them alike, and the settings panel opens on the gear's
side ([`settings/README.md`](../settings/README.md#the-settings-panel)). A hidden time or date is invisible, not gone:
the bar keeps its height, so the tiles below it never move. Left and right are the screen's in every language
([why](../../app/README.md#manifest-why-each-part-is-there)); later items (e.g. status
indicators) go with the settings gear. The time always gets its room first; a date too long for
what's left ends in "…". Each item that shows device state
that changes (the time, later e.g. the network) gets it from a port you can watch
([`ARCHITECTURE.md`](../../docs/ARCHITECTURE.md#background-work-and-changes-while-shown)), with a
fake in `:domain`'s test fixtures that the test moves
([`FakeClock`](../../domain/src/testFixtures/kotlin/com/luncher/domain/clock/FakeClock.kt)), and is
a view with `start(port)` and `stop()`, called from `HomeActivity`'s `onStart` and `onStop`.

Everything the home screen draws straight on the wallpaper (the bar's items, the title and hint
while moving an app, the message without apps) has a thin dark edge (`:ui`'s `WallpaperOutline`, which
the editors' preview of the home screen draws too), so it reads on any picture, a white one too:
0.75 dp past the shape, in the plain background's color at 80 %, so on the plain background it
doesn't show. It's drawn under the shape, so it never thins it, and only when the shape is drawn,
so it costs no memory and no time between redraws. An outlined text's padding has room for the
edge, so its view never cuts it. A new item in the bar brings its own: a text is an
`OutlinedTextView`; an icon, a vector, draws the same path stroked in `wallpaper_outline` first,
as the gear does (`home_settings.xml`). `HomeOutlineTest` draws each item of the bar, and the
message without apps, alone on white, and fails for one without it. The secondary texts (the date,
the hint, the message without apps) are 70 % white on the plain background, and an opaque light
grey (`text_secondary_on_picture`) over a picture (`HomeWallpaper` says when one shows):
translucent, they took a light picture's colors, muddy inside their dark edge. The editors'
preview draws the date's block alike.

The bar itself isn't focusable. An item that should be reachable with the D-pad (the settings
gear) is a focusable view in it, and Up from the first row of tiles moves there through
Android's own focus search ([Focus](#focus)). In the bar, Left and Right move only among its
items, in the order they're laid out, and do nothing at its ends, where Android's search would go
on to a tile below; Down goes back to the tiles. Focus in the bar stays there when the apps change
on a return to the home screen; otherwise it stays on the same app.

What keeps it light:

- **One bitmap per tile, at the tile's size.** A banner resource is usually 640×360 px or more;
  only the scaled copy is kept, and drawing a tile copies it once. A see-through picture whose
  focus follows its shape adds its two masks, at half the tile's scale, one byte a pixel.
- **Only new apps cost a bitmap.** Coming back to the home screen, or an app changing while it's
  shown, reads the app list again, but keeps the tiles, their bitmaps and the focus when it's the
  same; when an app was installed or updated, only its tile is new. Changes are heard from
  Android, not looked for: nothing runs between them, and a burst of them (an app store's
  updates) reads the list once.
- **Nothing allocated per key press in Luncher's code.** `TileFocus` keeps its last move in place;
  the zoom (and the lift) is a state animator created with each tile, as is the name's fade;
  scrolling reuses one `Scroller`.
  Android 5 still allocates inside its animators each time they start, about 2 KB a focus move.
- **Layout passes only when the tiles change.** Focus, zoom and scrolling redraw without
  measuring or laying out again. The clock's text changes once a minute, which lays out the top
  bar only, not the tiles.
- **Nothing runs while the home screen is hidden.** The clock's broadcast receiver exists only
  from `onStart` to `onStop`, and the wallpaper, the appearance and the arrangement are followed
  only then; a wallpaper being drawn goes on, as it's the process's
  ([below](#the-wallpaper)).

### Appearance

The home screen reads the user's `Appearance` in `onStart`, then again whenever the port says it
changed, until `onStop`, and hands each part to the one view that draws it. Each view compares its
part with `==` against the one it has, so an unchanged part costs nothing: every return to the home
screen reads an equal appearance. What a change costs depends on the part:

| Part | Goes to | A change costs |
|---|---|---|
| `TileGeometry` | `AppTilesView.tileGeometry` | a new layout from `TileLayouts` (it's an input of the layout, with the width, the height and the number of tiles), and a layout pass; new banner bitmaps only for tiles whose size changes, so a new placement, or the name shown, draws none |
| `TileStyle` | `AppTilesView.tileStyle` | the radius and the focus frame in the `TilePaints` all tiles share, and a redraw; the frame turned on or off also gives each tile the state animator that lifts it or not: no layout pass, no new bitmaps |
| `TopBarAppearance` | `TopBarView.appearance` | the bar's items moved (focus on the gear stays), and a layout pass of the bar only |
| `Background` | `HomeWallpaper.show` | the window's background: the picture kept at the screen's size, or drawn again in the background while a window says so ([below](#the-wallpaper)); nothing else is laid out or drawn anew |

The appearance editor, and the background editor it opens, cover the home screen whole, so the
home screen is stopped while the user changes it, and applies the result once, when it comes back
([`ARCHITECTURE.md`](../../docs/ARCHITECTURE.md#appearance)); a background saved there is drawn
before it's stored, so it's ready by then.

Where the tiles go is the layout's decision (`TileLayouts`, from the geometry's alignments): rows
keep the apps' order and are as wide as the space, and a row that isn't full goes at the left,
in the middle or at the right. In the middle, it keeps the other rows' gaps, so it's half a column
off them when the tiles it lacks are an odd number. The rows go at the top of the space below the
top bar, in its middle (with three rows, the second in the middle; with two, the gap between them)
or at its bottom, if they fit it; otherwise they start at the top and scroll. Centered or at the
bottom, a full bottom row puts the short row first rather than last.

With rounded corners (the default), each tile fills a rounded rectangle with its bitmap through a
shader made with the bitmap, when its size changes; the focus and held frames follow the same
corners. The radius in pixels is the domain's (`Corners`): at most
half the tile's height, which is what round ends are, so they're half circles on any screen. A new
radius draws the same bitmaps, so no banner is drawn again. Drawing allocates nothing, with or
without rounded corners.

The focused tile zooms by 1.15 (`home_tile_focus.xml`). With the focus frame on
(`TileStyle.focusFrame`, the default), it has a frame in the theme's accent, its vivid tone
(`ColorRoles.homeAccent`, Orange by default), drawn inside its edge, so it's as big framed as not.
With the frame off, it rises toward the viewer instead, at the same zoom (`translationZ`,
`home_tile_lift`, in `home_tile_lift.xml`), so
Android's renderer casts its shadow from its outline, a rounded rectangle with the tiles' corners
(`TilePaints.tileOutline`). The shadow is the GPU's, as for any raised view since Android 5: no
blurred bitmap, no software layer, nothing drawn per frame in Luncher's code; `AppTilesView` doesn't
clip its tiles, so it shows past the tile. A held tile has a white frame, with the frame on or off,
and the same zoom: the white frame tells it apart from one only focused.

With the app name shown (`TileGeometry.appName`, Off by default), a tile draws its app's name
centered below it, in the primary text color, cut with "…" to the tile's width, outlined as
everything on the wallpaper is ([The top bar](#the-top-bar)): When focused, the focused tile only,
the name fading in and out with the focus in the zoom's time; Always, every tile, at all times,
nothing fading. It's drawn
with the tile, past its bottom edge, so it zooms and lifts with it; its text is cut once per tile
size, and the fade is an animator made with the tile, so a focus move only starts it. The rows leave
it room, the same either way: `TilePaints.nameHeight`, the gap and a line of text at the largest zoom a tile gets,
measured by `:ui`'s `TileName` (which the [appearance editor's
preview](../settings/README.md#appearance) measures with too), is the room `TileLayouts` keeps below
each row, the last one too, so a name never covers the next row, and the rows at the bottom end that
much higher. Scrolling to a focused tile shows its name too. The room doesn't change where the focus
or a moved app goes.

### The wallpaper

The `Background` part's picture is the window's background (`HomeWallpaper`), behind the top bar
and the tiles, at the screen's size, filling the screen at the position the user chose, or
stretched, dimmed and blurred as set. It's drawn once and kept, apart from the image caches, as
one file in the app's private storage (`StoredWallpaper`,
[`platform/README.md`](../../platform/README.md#pictures)), so a start reads it on the main thread
and the home screen's first frame has it, as the tiles' images. The editors' previews draw it too,
shrunk, while it's of their background (`Wallpaper.readyFor`), so it's in theirs at once as well
([`feature/settings/README.md`](../settings/README.md#appearance)).

`Wallpaper`, in `:domain`, says what the wallpaper is: none, ready, or being drawn and why. One
per process, it's the same for every screen, and the home screen follows it from `onStart` to
`onStop`. When the one kept won't do, `Wallpaper` draws it again in the background, the first job
in line (`JobPriority.WALLPAPER`), the latest request winning, and replaces the kept one when it's
done. Meanwhile the home screen shows its plain color, and a window over it (`WorkingDialog`, from
`:ui`) says why, taking every key, Back too, so the wait reads as one, not as a screen that's
stuck: "Fitting the background to this screen…" for a screen of another size (the TV switched to
4K, say), "Updating the background…" when the one kept is of something else, was drawn by another
version, or is lost. The window shows for half a second at least, so it never just flashes. It's
the home screen's one window for a wait (`HomeWorking`): while the [tiles'
pictures](#preparing-the-tiles-pictures) are prepared too, it says so once the wallpaper is drawn,
its message changed, never closed and opened again in between. A
drawing goes on when the home screen stops, and shows when it's back. A wallpaper saved in the
background editor is drawn the same way; if the user goes home before it's done, the window says
"Processing background…", as the editor's did.

A picture that's gone (deleted in a picture editor, which sets the wallpaper to none, or its
file lost) is no wallpaper, and without one, none is kept: asked for none, `Wallpaper` deletes the
kept file. The wallpaper takes about 8 MB of memory at 1080p while shown.

| Part | Job |
|---|---|
| `Background` (`:domain`) | the picture, or none, and how it's drawn: fit, position, dim, blur |
| `WallpaperDrawing`, `whyDraw` (`:domain`) | what a wallpaper is drawn from (the picture, its style, the screen's size), and why the one kept won't do |
| `Wallpaper` (`:domain`) | the wallpaper as it stands, one per process, made by `AppGraph`: reads the one kept, draws it again when it won't do, saves a new background (drawn, then stored), forgets a deleted picture |
| `StoredWallpaper` (`:domain`) / `FileStoredWallpaper` (`:platform`) | the one wallpaper kept, a file replaced in one step |
| `PictureImages` (`:domain`) / `FilePictureImages` (`:platform`) | draws the picture at the screen's size, keeping nothing |
| `HomeWallpaper` | the window's background as `Wallpaper` says |
| `HomeWorking` | the window while the wallpaper is drawn or the tiles' pictures prepared (`PreparedTileImages`, a tile saved in the picker among them), as both say: the wallpaper's cause first |
| `WorkingDialog` (`:ui`) | that window: a spinning wheel and why, taking every key, half a second at least |

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

## A tile's menu

A long press of OK on an app's tile opens its menu (`TileMenu`), a small panel above the tile, or
below it where there's no room above, centered on it inside the TV's overscan margin and below
the top bar, so the clock, the date and the settings entry always show; where centered it would
leave that margin (a row's first or last tile), its edge lines up with the tile's edge on that
side. Where it fits neither above nor below (a longer menu, a short screen such as 720p at 320
dpi), it goes beside the tile, on the side with more room, level with it. Where it fits whole
nowhere (a middle column's tile on that screen, or more entries than the screen is tall), it
takes the most room there is beside, above or below the tile, and its entries scroll in it as the
settings panel's do (`LookAheadScrollView` in a `ScrollFrame`, [`ui/README.md`](../../ui/README.md#the-design-system)):
whole rows, a row ahead of the focus, the list sliding under a focus that stays put, fading out
where there's more. So it never covers its tile, but where not even two rows have room anywhere
(a row's middle tile in the middle of that screen): there two rows, scrolling, on the roomier
side, over the tile's edge by as little as they can. It lists
what the user can do to that app, with no title, the first entry focused; OK on one closes the menu and does it,
Back closes it. An entry the device can't do is listed all the same, dimmed as the settings'
unavailable entries are, so the menu reads the same on every TV: the focus passes it by, and OK
can't reach it. The tile stays focused and zoomed next to it, so it's clear which app it's for
without a title; the app's name is for screen readers only, the menu's pane title (from API 28
on), not the window's title, which would rename the window Android names after its activity.
Its corners are as round as the tiles' (the appearance's corner radius), so it looks of a piece
with them, up to a panel's 28 dp: a pill, or 32 dp, gets 28 dp, as rounder would make a small
menu look like a pill; its entries' corners are concentric with its own where that's round
enough, else a third of the menu's, at least 2 dp: 2 dp for tiles of 4 and 8 dp, 4 dp for 12,
concentric from 16 dp; square in a square menu (`menuCorner`, `entryCorner`). No other
panel follows the tiles.

| Entry | Does |
|---|---|
| Move | [moves the app](#moving-an-app); not offered while it's the only app shown |
| Hide | hides the app at once, first among the hidden apps; the [Hide apps](../settings/README.md#hidden-apps) list shows it again |
| Rename | asks for a name of the user's own for the app, which the home screen shows wherever it names the app ([below](#renaming-an-app)) |
| Tile banner | opens the tile banner picker ([`settings/README.md`](../settings/README.md#tile-banner)), full-screen over the home screen, where a picture of the library is chosen for the tile and framed; the home screen, stopped meanwhile, shows the tile as stored once it's back ([above](#a-tiles-picture)) |
| App info | opens the app's page in the device's settings (its storage, permissions, force stop, uninstall), in a task of its own, as another app; Back returns to the home screen, which shows what changed there (an app uninstalled or disabled) as any change of the apps. Dimmed where the device has no such page, as on the Android TV 5.1 (API 22) emulator, whose settings have only the list of all apps. A settings app gone since the menu opened gets a message instead |

| Part | Job |
|---|---|
| `AppAction`, `appActions` (`:domain`) | which actions there are, and which the menu of an app lists, in order, each with whether it can be chosen (`AppMenuEntry.available`), from facts of the device it's given |
| `appName`, `ArrangedApps.renamed` (`:domain`) | the name a renamed app keeps, or none; what to store of what the user set for the apps with it |
| `AppInfoScreen` (`:domain`), `PackageManagerAppInfoScreen` (`:platform`) | whether the device has the page App info opens, asked as each menu opens, since a settings app can be disabled meanwhile |
| `tileMenuPlacement` (`:domain`) | where the menu goes: above the tile as it's drawn (zoomed, its name below it), below where there's no room, across centered, or in line with the tile's edge where centered would leave the room; beside it, level with it, where it fits neither above nor below; `tileMenuHeight`: how tall it may be, the most room there is where it fits whole nowhere |
| `AppTileView.focusedBounds` | where the tile is drawn on the screen while it has focus, for that rule |
| `TileMenu` | the menu's window: an entry for each action with its icon and label from exhaustive `when`s over `AppAction`, dimmed and out of the focus's way where it isn't available, placed where the rule says; in the theme's colors, its corners following the tiles' (`style`, `menuCorner`) |
| `HomeActivity` | opens the menu on a long press, and does what an entry says, in an exhaustive `when` too: Move starts `MoveMode`, Hide stores the arrangement with the app hidden, which shows as any change of it does, Rename asks for the name (`:ui`'s `NameDialog`) and stores it, Tile banner starts the picker for the app (`TileBannerActivity.intent`), App info starts the device's page for the app |

A new action is a type of `AppAction`, listed by
`appActions`, which then doesn't compile in `:feature:home` until the menu has its label and icon,
and `HomeActivity` what it does. One the device may not be able to do is listed with
`available` false rather than left out, from a fact `appActions` takes as a parameter, which
`HomeActivity` reads from a port as the menu opens (App info: `AppInfoScreen`); one that only
changes nothing in some state (Move, with one app shown) is left out.

The menu is styled like the settings ([`ui/README.md`](../../ui/README.md#the-design-system)):
its entries (`ListEntry`, rows of a list) turn inverse while focused, keeping their size as a list's rows do,
and it takes the theme's colors,
light or dark, as the settings panel does. The home screen applies the theme as it follows the
appearance (`showTheme`, only when the theme part changed): the accent on the tiles' focus frame
(`AppTilesView.accent`) and on the gear's focus disc, the colors on the menu and on the window
that says the wallpaper is being drawn or the tiles' pictures prepared (`HomeWorking.roles`). Light or dark leaves the home
screen itself dark, on its wallpaper.

The menu is a dialog, a window of its own, so Android keeps the keys and the focus in it while it
shows, and the tile under it keeps its focus. The home screen behind it isn't paused, so it still
follows the apps and their arrangement: a change of the shown apps (an app installed meanwhile)
closes the menu, whose tile may be gone. Home, and anything that stops the home screen, closes it
too. The long press's release goes to the menu's window, which ignores a release without its
press.

The menu is made the first time it opens, and kept for the next ones, which only add its entries
again: nothing is made before the user asks for it.

### Renaming an app

Rename asks for the app's new name in a dialog in the middle of the screen, over the home screen
dimmed, as the picture library's dialogs are: `:ui`'s `NameDialog`, the same one the background
editor renames a picture with ([`settings/README.md`](../settings/README.md#background)). Its
title is "Rename app", its field holds the name the app shows now, the TV's keyboard up and the
cursor at the end; for an app the user renamed, its original name, the device's label, is below
the field ("Original name: YouTube"), so it's clear what Reset gives back. Below them, Cancel,
Reset and Rename. While the keyboard shows, the dialog is above it, and the home screen behind
stays where it is (`adjustNothing`, [Manifest](#manifest)). Rename, the keyboard's Done, or Enter,
keeps what's typed as `appName` says: no control
characters, trimmed, at most 50 UTF-16 units (`MAX_APP_NAME`: a tile shows about 20 characters,
Hide apps about 40), which is also all the field takes. An empty field, or the original name,
puts the app's own name back, and so does Reset, dimmed and passed by the focus while the app has
its own name. Nothing is refused: two apps may have one name. Cancel, or Back, changes nothing.
Like the settings' dialogs, it mirrors in a right-to-left language, while the home screen under it
doesn't.

The name is stored at once through `AppCustomizations` (`ArrangedApps.renamed`: what was set for
every app, the apps not installed now too, with this one's name; an app left with nothing set
isn't stored), which the home screen follows as any change of the names: the renamed app's tile
alone is made and drawn again, in its place by name while there's no order of the user's, the
focus on it. Like the menu, the dialog closes when the home screen stops (Home, another app, the
screen off) and when the shown apps change while it shows, as its app may be gone.

## Moving an app

Move in a tile's menu holds its app. The top bar's clock and gear give way to the title "Move app"
and a hint of what the keys do. The hidden apps don't show meanwhile, so every other tile stays where
it is.

| Key | Does |
|---|---|
| Arrows | move the held app (white frame, the focused tile's zoom) |
| OK | puts it down where it is, and ends the mode |
| Back | the same |

Left and Right move the held app one place along the order, wrapping rows; Up and Down one row, to
the place nearest across, as the [focus](#focus) goes, and back the way it came. At the edges it
stays. Home, or anything that stops the home screen (an app starting, the screen going off), puts it
down too. The new order is stored once, as the mode ends, if the app moved.

| Part | Job |
|---|---|
| `MoveSession` (`:domain`) | the held app, where each arrow takes it among the shown apps, and the apps as arranged; it keeps state, as `TileFocus` does ([`domain/README.md`](../../domain/README.md#writing-models-rules-and-ports)) |
| `TileMoves` (`:domain`), in `TileGrid` | where a moved tile goes in the grid |
| `MoveMode` | starts and ends the mode; turns keys into the session's moves and moves the tiles to match; stores the arrangement as it ends |
| `AppTilesView` | `moveTile` moves one without it losing focus |

The first move fixes the order: from then on the shown apps keep the user's order instead of the
one by name, and apps installed later come after them. Hiding an app doesn't fix it.

The mode only reacts to an OK pressed while it's on: the release of the OK that chose Move went to
the menu's window. It needs no Menu key, and the Menu key does nothing meanwhile. On Android 16
(API 36), Back reaches the app only through `OnBackInvokedCallback`, not as a key, so
`HomeActivity` passes Back to the mode from there as well as from `onBackPressed`.

To try it on an emulator, a long press of OK has to hold the key down, which `adb shell input
keyevent --longpress` doesn't before API 30 ([`EMULATORS.md`](../../docs/EMULATORS.md#keys)).

What keeps it light:

- **A move allocates nothing.** The tile is moved in place (detached and attached again, which
  keeps its focus), and the tiles are laid out again with the layout they had: `AppTilesView`
  keeps it while the width and height, the number of tiles and the `TileGeometry` stay the same,
  each compared with what the layout was made from, and a move changes none of them.
- **Nothing is read while moving.** The apps are read again when the mode ends if one changed
  meanwhile, and when the home screen comes back, not while the user's change is on screen.
