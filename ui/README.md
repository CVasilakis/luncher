# ui/

What every screen shares, UI only, `:ui`: the app-wide theme, the themed views the menus and
settings are made of (the design system, [below](#the-design-system)), and helpers and views two
features both use. It holds no ports and nothing of a single feature: a feature's own resources
and views stay in its module. Why it exists:
[`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md#where-things-go).

| | |
|---|---|
| Package | `com.luncher.launcher.ui` |
| Depends on | `:domain`, for the theme's colors (`ColorRoles`), which its views take |

## Layout

```
ui/
├── build.gradle.kts              luncher.android.library, with test fixtures and JVM tests of its own
└── src/
    ├── main/
    │   ├── java/com/luncher/launcher/ui/
    │   │   ├── Colors.kt         color(id): a color resource on every API level
    │   │   ├── Themed.kt         the design system's base: a view styled from ColorRoles (Themed), styleAll,
    │   │   │                     the state colors and backgrounds its views share
    │   │   ├── ThemedText.kt     a text in a role of the theme, inverse on a focused entry, its icon too
    │   │   ├── ThemedIcon.kt     an icon (icon_…) in a role of the theme
    │   │   ├── ListEntry.kt      an entry: inverse while focused, tinted while chosen, outlined while open; a field zoomed;
    │   │   │                     in a list, as tall as the list makes its rows, its focused look drawn by the list as it slides
    │   │   ├── EntryText.kt      an entry of one line, its icon before it: a picture's actions (Rename, Delete)
    │   │   ├── PillButton.kt     a button: filled, tonal or danger, its icon beside its text, giving way
    │   │   ├── ScrollFrame.kt    a scrolling list's frame: cut along its rounded corners, fading out where there's more
    │   │   ├── LookAheadListView.kt, LookAheadScrollView.kt  lists that scroll a row ahead of the focus, their
    │   │   │                     window whole rows, sliding under a focus that stays put
    │   │   ├── WholeRows.kt      the lists' rules: how many rows a window shows (fitRows), when an arrow slides
    │   │   │                     the list (slideFor), the rows' usual height; FittedRows, what a row asks its list
    │   │   ├── FocusSlide.kt     a list's slide under the focus: the bar it draws where the focus stays
    │   │   ├── ThemedSpinner.kt  a wait's spinning wheel, in the accent
    │   │   ├── Dialogs.kt        the dialogs over the menus, the settings and the home screen, floating panels: a
    │   │   │                     question or a message (MessageDialog), a menu of actions on one thing (MenuDialog), a
    │   │   │                     new name (NameDialog: a note below the field, a third button, dimmed where it can't be
    │   │   │                     chosen, a length limit; above the keyboard while it shows); the features give their
    │   │   │                     texts and what each button does
    │   │   ├── NameField.kt      a new name's field, outlined in the accent while focused, in the error color once refused
    │   │   ├── Badge.kt          a dialog's mark beside its title: done, danger or info
    │   │   ├── EntriesRow.kt     buttons (or fields) side by side, or one below the other when one doesn't fit
    │   │   ├── Windows.kt        a panel's window and a screen's own, styled
    │   │   ├── ScreenSize.kt     screenSize(): the screen's size in px, what the wallpaper is drawn at and
    │   │   │                     the previews' shape
    │   │   ├── HomeTiles.kt      homeTileSize(): the home screen's tiles' size in px, what their pictures are prepared
    │   │   │                     at, by the home screen and the tile banner picker alike
    │   │   ├── ShortestShowing.kt  how long a wait has shown, so it shows half a second at least: WorkingDialog's,
    │   │   │                     and the share window's wheel
    │   │   ├── TileName.kt       the app name below a tile: its paint, its gap, the room it takes
    │   │   │                     below a tile; the home screen's, and the room the appearance preview leaves
    │   │   ├── TileShapes.kt     a see-through picture's shape on its tile, made as the tile is prepared: a band
    │   │   │                     around its outline and its soft shadow, at half the tile's scale
    │   │   ├── TilePainter.kt    draws a home tile from its parts (TileParts): its image, a see-through picture's look
    │   │   │                     (backdrop, a focus that follows the tile or the picture's shape), the focus and held
    │   │   │                     frames; the home screen's tiles, and the tile banner picker's preview of one
    │   │   ├── WallpaperOutline.kt  the dark edge around what's drawn on the wallpaper, on the home screen
    │   │   │                     and in the previews of it
    │   │   └── WorkingDialog.kt  a floating panel in the theme's colors, a spinning wheel and a message, taking every key,
    │   │                         shown half a second at least: for work the user waits for (the wallpaper drawn,
    │   │                         a picture added); Cancel too, and Back, for work that can be stopped
    │   └── res/
    │       ├── animator/         entry_zoom, button_zoom: a focused field's zoom, a button's zoom and lift
    │       ├── drawable/         icon_…, the line icons (white, tinted where they're used); panel_background,
    │       │                     a floating panel's window before it's styled
    │       ├── layout/           working_dialog: WorkingDialog's wheel and message, and its Cancel; dialog: the
    │       │                     dialogs' panel, with dialog_button and dialog_entry, a button and a menu's entry
    │       ├── values/
    │       │   ├── attrs.xml     the themed views' attributes: colorRole, fill, zooms, entryCorner, kind
    │       │   ├── colors.xml    the home screen's: background, accent, primary and secondary text (opaque over
    │       │   │                 a picture), the wallpaper outline; a panel, as a window is before it's styled
    │       │   ├── dimens.xml    the design system's type, corners, zoom, buttons, icons, entries and fade; a panel's
    │       │   │                 corners, the dialogs' and WorkingDialog's sizes, the wallpaper outline's width, the
    │       │   │                 tile name's text size and gap; the home screen's padding at the sides
    │       │   │                 (screen_padding_horizontal), which the home tile's size comes from; the tile
    │       │   │                 shape's band, shadow and its drop; the tile's frame
    │       │   └── themes.xml    Theme.Luncher, the app's and the home screen's theme; Theme.Luncher.Dialog, the
    │       │                     dialogs', and Theme.Luncher.Working; Widget.Luncher.Button, a button's
    │       └── values-h450dp/    a screen's title, and the gap below a panel's, on a screen 450 dp tall or more
    │                             (smaller on 720p at 1080p density)
    ├── test/java/com/luncher/launcher/ui/
    │   ├── WholeRowsTest.kt      the lists' rules, on the JVM alone
    │   └── LookAheadScrollViewTest.kt  rows of a little different heights made one, a taller one kept (Robolectric)
    └── testFixtures/java/com/luncher/launcher/testing/
        ├── TvDevice.kt           the TV screens the JVM tests run on (TV_1080P, TV_SCREENS)
        ├── LayoutChecks.kt       the layout tests' checks: bounds, inside, apart, whole text, a zoom not cut
        ├── DialogFits.kt         a dialog's check: a margin from the screen's edges, its texts whole
        └── Captures.kt           the screenshots' captures, the window in front given the focus first
```

Resources here have no prefix (`accent`, `Theme.Luncher`, `icon_check`); a feature's are named after it
(`home_tile_lift`). Code refers to them through this module's R class, imported as
`com.luncher.launcher.ui.R as UiR` next to the module's own `R`; layouts and themes by name, as
any resource.

The test fixtures are what the screens' JVM tests share, in every module
(`testImplementation(testFixtures(project(":ui")))`): how they're used,
[`../docs/TESTING.md`](../docs/TESTING.md#layouts-on-other-screens).

A home tile's drawing is here (`TilePainter`), not in `:feature:home`, so the tile banner picker's
preview draws a tile as the home screen does: one painter per screen, its paints made once (the
image's, the glass's, the masks', the focus and held frames), and for each tile its `TileParts`,
made with each image: the shader that rounds it, its shape's masks placed at the tile's scale, and
the look as it applies (none for an opaque image). Drawing allocates nothing. What each look
draws: [`../feature/home/README.md`](../feature/home/README.md#appearance).

A see-through tile with glass at rest is drawn in one pass, as an opaque one is: its parts compose
the glass at its resting strength under the picture, in one shader (a gradient of one color, which
the painter shares between its tiles, under the picture's bitmap shader). Drawn on its own, the
glass would fill the whole tile a second time at every frame, for every see-through tile on the
screen. Below API 28 Android composes a bitmap's shader only with one of another kind, hence the
gradient rather than a bitmap of the color. The focused tile, and the one the focus leaves, draw the
glass on its own: its strength follows the focus, and the band or the shadow of the picture's shape
goes between it and the picture. From API 33 the picture's shader is told to filter itself: on
API 36 a bitmap inside a composed shader isn't filtered by the paint.

## The design system

The menus and the settings (a tile's menu, the settings panels, the editors, the picture screens,
the dialogs) are made of a few themed views, Material 3 for TV's: what can be pressed looks like it
can, the focused thing stands out from afar, and every color comes from the user's theme.

- **Colors are roles, from the theme.** A view never names a color: it takes one of the domain's
  `ColorRoles` (`colorRoles`, [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md#appearance)) by
  what it's for. Each view is `Themed`: `style(roles)` builds its drawables and color lists from
  the roles, in code (a color state list can't name a theme's attribute before API 23, and the
  theme changes while a screen shows), and keeps the roles it was styled with, so styling it again
  with equal ones makes nothing anew. A screen styles all of its views with `styleAll` (or
  `stylePanel` and `styleScreen`, which style its window too) as it's made, and again, in place,
  when the theme changes. A themed text's or icon's role is its `colorRole` attribute:
  `onSurface`, `onSurfaceVariant`, `primary`, `error`.
- **Focus: the inverse color, and a little zoom off lists.** A focused entry (`ListEntry`,
  `EntryText`) is filled with `inverseSurface`, light on a dark theme and dark on a light one,
  its texts and icons the inverse too (they take its state, `duplicateParentState`). A row of a
  list or a menu keeps its size: the inverse color says enough, and zooming rows played an
  animation at every key, the one scrolling cost measured in Add from folder's list. A field (the
  background editor's options, `zooms="true"`) is zoomed by `entry_zoom` (1.03), with no shadow:
  fields sit close, and one would fall on the next; a focused button (`PillButton`) turns to the
  inverse color (a danger one to the error color), zoomed by `button_zoom` and lifted by
  `button_lift`, so the GPU casts its shadow. The zoom and the lift are state animators made
  with each view, so a key press only starts them. A focused
  view looks focused only while its window has the focus: a dialog or a list over a screen takes
  the keys, and what had the focus under it looks as it is otherwise. A list's selected row
  (`ListView`, which moves a selection, not the focus) looks focused while the list has the focus.
- **What can be pressed looks like it.** Buttons have round ends: `filled` with the accent for the
  action of a screen or dialog (Save, OK), `tonal` for the others (Cancel, Add all), `danger`, an
  edge of the error color, for what deletes or resets. A button's icon sits beside its text, the
  two centered together; where they don't fit, the icon gives way, so the text shows whole. A
  field (the background editor's options) is filled with `surfaceHigh`, its value under its name, a
  mark that it opens a list; outlined in the accent while its list shows (`activated`). The entry
  chosen (the background's picture) is tinted with the accent (`activated`).
- **Corners nest.** An entry next to its container's corners (a list in its container, an
  option's values, a tile's menu) has corners concentric with the container's: the container's
  radius less the padding between them (`corner_entry_in_container`, 10 dp in a 20 dp container),
  set with `entryCorner`, so the two curves run side by side. A tile's menu follows the tiles'
  corners, and its entries with it (`ListEntry.corner`, `ScrollFrame.corner`,
  [`feature/home/`](../feature/home/README.md#a-tiles-menu)). An entry far from its container's
  corners (a side sheet's, a dialog's) keeps `corner_entry` (12 dp).
- **The keyboard covers no dialog's field, and moves no screen.** A dialog with a field (`NameDialog`) is
  centered, and near the top of the screen, `dialog_margin` from its edge, while the keyboard
  shows. The screen that opens one declares `windowSoftInputMode="adjustNothing"`: by default
  Android slides a window up so its own focused view (a low tile, a low picture) clears the
  keyboard, a black band below it. From API 30 on, Android says whether the keyboard shows, not
  reliably how tall it is (0 on some versions), so the dialog moves on whether it shows, changing
  its window only when that changes (changed before the keyboard is up, the window would keep it
  down); before API 30, Android moves the dialog above the keyboard and back by itself. From API
  30 on, the dialog's window doesn't shrink to the room above the keyboard either (its
  `fitInsetsTypes` leave the keyboard out): on a screen too short for both (720p at 320 dpi) the
  keyboard covers the buttons, whose Rename its Done stands in for, never the field.
- **Room for the zoom, inside what clips.** A parent that clips cuts each child to the child's own
  bounds, so a zoomed field or button needs its room inside the view that holds it: padding, with
  `clipToPadding` off, and `clipChildren` off on what's between it and what clips. The layout
  tests check it (`assertZoomShown`). A list's padding stays as a margin around its rows.
- **A list that scrolls keeps its corners, fades out where there's more, and scrolls ahead of the
  focus.** In a `ScrollFrame`, what scrolls under the frame's edges is cut along its rounded
  corners (its outline, clipped by the GPU, no layer, no bitmap); while there's more below, or
  above, the list fades into the frame's color at that edge, one gradient drawn over it, shown and
  hidden as the list scrolls, never while that end of it shows. The list in it is a
  `LookAheadListView` or a `LookAheadScrollView`: while there's more beyond an edge, the focused
  row stays a row away from it, so the next row shows whole before the focus gets there, and the
  focus is never on a row under a fade; the first and the last rows go to the edge, the last one
  whole and not faded.
- **A scrolling list's window is whole rows, and one thing moves at a time.** While its rows
  don't all fit, a list gives them one height, a little taller or shorter than the usual row, so
  a whole number of them fill its room: as many as fit stretched, or one more squeezed, whichever
  changes the rows less as a ratio (`fitRows`, `WholeRows.kt`); a squeezed row gives up its
  padding, never its content (`ListEntry` doesn't clip to its padding); a row whose content needs
  more (a name on two lines) is that much taller, and the window is whole rows as long as it
  doesn't show. The fade covers the row at the edge, so that row looks dimmed, not cut. Up and Down move
  the focus while the row beyond the next one shows whole; else the focus stays where it is and
  the list slides a row behind it, in 150 ms (`slideFor`, `FocusSlide`). For that time the list
  draws the focus itself where it stays, the focused fill and inside it the passing rows' texts
  and icons as they look focused, drawn once as the slide starts, apart from the rows: recorded
  (a `Picture`) and played by the GPU as it draws the rows, or before API 23 into two bitmaps
  (the GPU keeps one drawing of a view a frame, its look at rest, which the rows keep meanwhile); at the end the
  row looks focused itself, where the bar was. A key during a slide ends it at once. A focus moved
  any other way (a screen giving a row the focus) shows the row a row ahead at once, the list
  scrolled by whole rows.
- **Sizes.** Android TV's 960 x 540 dp canvas, the 48 dp margin at the sides and 27 to 32 dp above
  and below, an 8 dp grid; Material for TV's type, a little larger for the distance (an entry's 18
  sp, a line below it 14 sp, a screen's title 34 sp, smaller below 450 dp tall). Icons are line
  drawings of this module (`icon_…`), white, tinted where they're used; the ones that point the
  way the text reads (a chevron, opening another app) mirror in a right-to-left language.

What keeps it light: a view's drawables are made when it's styled (a screen's start, a new
theme), never while drawing or for a key press; a theme the screen has already is compared and
skipped. A shadow is the GPU's, cast from a view's outline. A fade is one gradient over the list.
A list's slide makes nothing for a key press or a frame: its animator and the two focused looks
are kept for the next slides, and dropped as the list leaves its window. The looks are recorded,
not drawn into bitmaps, from API 23 on: bitmaps sent to the GPU took a slide's frames from about
15 to about 90 ms on tv_api28. With animations off, the list moves at once.

## The theme

`Theme.Luncher` is the application's theme and the home screen's; `Theme.Luncher.Dialog` is the
dialogs' (`Dialogs.kt`), and `Theme.Luncher.Working`, the same, `WorkingDialog`'s: floating panels
like the settings panel, over the dimmed screen, which any feature can show. A feature whose screens
look different has its own (the settings panels' dialog theme,
[`feature/settings/`](../feature/settings/README.md#the-settings-panel), with the share entry point
without a window, [its manifest](../feature/settings/README.md#manifest)). They're all platform
themes (`Theme.DeviceDefault…`), not AppCompat, like every activity's `android.app.Activity`
([`ARCHITECTURE.md`](../docs/ARCHITECTURE.md#rules), rule 5).

**Never a white screen:** every background Android may show for a screen before it draws
(`windowBackground`, `colorBackground`, `windowSplashScreenBackground`) is set, dark, in the
themes (this one, a feature's own), rather than left to `Theme.DeviceDefault`, which device makers
may restyle. `ThemesTest`, in `:app`, checks every activity of the merged manifest, drawing their
backgrounds over black. The user's theme, light ones too, is applied in code as a screen is made
(`styleScreen`, `stylePanel`), before its first frame: Android shows no starting window for a
screen opened from another of Luncher's (`windowDisablePreview`), nor for a floating one.

**Texts at their view's start:** every theme (this one, a feature's own) sets `textAlignment` to
`viewStart`, so in a right-to-left language each text lines up with the screen's direction rather
than its own: English and Arabic app names alike at the right of a mirrored list, and at the left
of the home screen, which stays left to right
([why](../app/README.md#manifest-why-each-part-is-there)).

### While Luncher starts

While a cold-started app's process starts, Android may show a starting window until the app has
drawn. The home screen has none on any Android version: the previous screen (an app, the stock
launcher, the boot animation) stays until Luncher has drawn, and the home screen then appears
whole.

- **Up to Android 11 (API 30)**, the starting window would be a window with the `windowBackground`
  of the theme the manifest gives the activity. The emulators of API 22 to 30 show one when
  Luncher's process was killed and Home brings it back (what a TV short of memory does), when Home
  starts it while another home app is in front, and when it's opened as an app.
  `Theme.Luncher` sets `windowDisablePreview`, for which Android adds none (AOSP
  `ActivityRecord.addStartingWindow` returns before making one).
- **From Android 12 (API 31) on**, an activity of type home never gets a splash screen, whatever
  its theme (AOSP `ActivityRecord.getStartingWindowType`).
- **At boot**, on every version, the first home activity gets no starting window: the boot
  animation stays until Luncher draws.
- **Opened as an app** rather than as the home screen (from the TV's app list, or by a shared
  picture, [`feature/settings/`](../feature/settings/README.md#adding-a-picture)), on Android 12 and 13 it still gets a
  splash screen: from 12 on, Android ignores `windowDisablePreview` for an activity started from
  the launcher, System UI or the system (`launchedFromSystemSurface`). On TV, Android's window
  manager overrides every app's splash screen (AOSP `TvStartingWindowTypeAlgorithm`): only its
  background on 12, a solid colour on 13, which `windowSplashScreenBackground` keeps the home
  screen's, none from 14 on. `windowSplashScreenAnimatedIcon` and the app icon are never shown.
- **A task snapshot**, the app's own last frame shown when returning to its task, is decided
  before the theme is read, so `windowDisablePreview` doesn't stop that.

A launch screen, a drawing as the starting window up to Android 11, was removed: Android fades it
out over the first frame, which on a slow TV looks like the two screens flickering
([`docs/archive/`](../docs/archive/README.md#the-launch-screen-removed)).
