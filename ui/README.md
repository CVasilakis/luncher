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
    │   │   ├── ListEntry.kt      an entry: inverse while focused (a step away while OK is held), tinted while chosen, outlined while open; a field zoomed;
    │   │   │                     in a list, as tall as the list makes its rows, its focused look drawn by the list as it slides
    │   │   ├── EntryText.kt      an entry of one line, its icon before it: a picture's actions (Rename, Delete)
    │   │   ├── OkHold.kt         OK held on a view that opens something on a long press: Luncher's own hold, 350 ms, a
    │   │   │                     tile's menu, a picture's menu
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
    │   │   │                     texts and what each button does. PanelDialog, their base and WorkingDialog's window:
    │   │   │                     how a floating panel comes and goes
    │   │   ├── ScreenDim.kt      the dim under a screen's dialogs, a window of the screen's that fades with it
    │   │   ├── WindowEntrance.kt a floating window's entrance played in the window, where Android plays none
    │   │   │                     (Android 5.1 and 6.0): the dialogs', a tile's menu's
    │   │   ├── NameField.kt      a new name's field, outlined in the accent while focused, in the error color once refused
    │   │   ├── Badge.kt          a dialog's mark beside its title: done, danger or info
    │   │   ├── EntriesRow.kt     buttons (or fields) side by side, or one below the other when one doesn't fit
    │   │   ├── Windows.kt        a panel's window and a screen's own, styled
    │   │   ├── ScreenSize.kt     screenSize(): the screen's size in px, what the wallpaper is drawn at and
    │   │   │                     the previews' shape
    │   │   ├── HomeTiles.kt      homeTileSize(): the home screen's tiles' size in px, what their pictures are prepared
    │   │   │                     at, by the home screen and the tile banner picker alike; FOCUSED_TILE_ZOOM, the
    │   │   │                     focused tile's zoom, the home screen's and the picker's preview's
    │   │   ├── ShortestShowing.kt  how long a wait has shown, so it shows half a second at least: WorkingDialog's,
    │   │   │                     and the share window's wheel
    │   │   ├── TileName.kt       the app name below a tile: its paint, its gap, the room it takes
    │   │   │                     below a tile; the home screen's, and the room the appearance preview leaves
    │   │   ├── TileShapes.kt     a see-through picture's shape on its tile, made as the tile is prepared: a band
    │   │   │                     around its outline and its soft shadow, at half the tile's scale
    │   │   ├── TilePainter.kt    draws a home tile from its parts (TileParts): its image, a see-through picture's look
    │   │   │                     (backdrop, a focus that follows the tile or the picture's shape), the Frosted tiles'
    │   │   │                     frost (TileFrost), the focus and held frames, a lifted tile's outline; the home
    │   │   │                     screen's tiles, and the tile banner picker's preview of one at rest and focused
    │   │   ├── WallpaperOutline.kt  the dark edge around what's drawn on the wallpaper, on the home screen
    │   │   │                     and in the previews of it
    │   │   └── WorkingDialog.kt  a floating panel in the theme's colors, a spinning wheel and a message, taking every key,
    │   │                         shown half a second at least: for work the user waits for (the wallpaper drawn,
    │   │                         a picture added); Cancel too, and Back, for work that can be stopped
    │   └── res/
    │       ├── anim/             panel_enter, panel_exit: a floating panel's fade in and out; screen_hold_open, screen_hold_close:
    │       │                     the screen under a full-screen one, held still
    │       ├── animator/         entry_zoom, button_zoom: a field's, and a button's, zoom and lift (and their dip while OK is held)
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
    │       │   │                 shape's band, shadow and its drop; the tile's frame and its lift (tile_lift)
    │       │   ├── ids.xml       the tag that keeps a screen's dim on its window
    │       │   └── themes.xml    Theme.Luncher, the app's and the home screen's theme; Theme.Luncher.Dialog, the
    │       │                     dialogs', and Theme.Luncher.Working; Widget.Luncher.Button, a button's
    │       └── values-h450dp/    a screen's title, and the gap below a panel's, on a screen 450 dp tall or more
    │                             (smaller on 720p at 1080p density)
    ├── test/java/com/luncher/launcher/ui/
    │   ├── WholeRowsTest.kt      the lists' rules, on the JVM alone
    │   ├── PressedLooksTest.kt   what a press looks like (an entry's fill, a button's and a field's dip) and the hold of a row (Robolectric)
    │   ├── LookAheadScrollViewTest.kt  rows of a little different heights made one, a taller one kept (Robolectric)
    │   └── TilePainterTest.kt    each look drawn as the focus comes, in as few passes as it needs, the frost where
    │                             the tile is, the lifted outline (Robolectric)
    └── testFixtures/java/com/luncher/launcher/testing/
        ├── TvDevice.kt           the TV screens the JVM tests run on (TV_1080P, TV_SCREENS)
        ├── LayoutChecks.kt       the layout tests' checks: bounds, inside, apart, whole text, a zoom not cut
        ├── DialogFits.kt         a dialog's check: a margin from the screen's edges, its texts whole
        └── Captures.kt           the screenshots' captures, the window in front given the focus first
```

Resources here have no prefix (`accent`, `Theme.Luncher`, `icon_check`); a feature's are named after it
(`home_tile_focus`). Code refers to them through this module's R class, imported as
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

A Frosted tile draws its part of the screen's frost (`TileFrost`, the wallpaper's frosted copy and
the space it covers, set on the painter by the screen), then the picture: two passes at rest, as a
shader can't compose two bitmaps below API 28. The frost's tint is composed into its shader, as
the glass is into the picture's. The tile's place on the screen and its zoom move and scale the
canvas, not the shader, whose matrix stays as made: changed at each draw, it makes Android build
the shader again (from API 26 on), which made scrolling 2 to 3 times slower on API 36.

A lifted tile's shadow is Android's, cast from its view's outline; the painter gives that outline
(`TilePainter.outline`), its shape and its strength, so every screen that lifts a tile casts the
same shadow: the tile's rounded shape a pixel inside its edge (`SHADOW_INSET`), as strong as the
tile's parts say ([`../feature/home/README.md`](../feature/home/README.md#appearance)).

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
- **A press shows at once, as OK goes down.** Without it, nothing showed until the release, when the action
  ran. A view's pressed state is an item in the state list or the state animator it already has, so a
  press only changes a state: nothing is made for a press or a frame, and **nothing is added to a focus
  move or a list's slide** (the lists draw the focused look themselves while they slide, and a row they
  hold at rest looks at rest, pressed or not). One rule: *what zooms when focused dips when pressed;
  what doesn't gets a darker fill.*
  - A **row or an entry** (`ListEntry`, `EntryText`: the menus' entries, the panel's, a list's rows,
    an option's values, the pictures' rows) changes its fill while OK is held on it focused, or on a
    list's selected row, to `inverseSurfacePressed`: a step away from the focused one that shows in
    both modes (a little darker where the focused fill is light, a little lighter where it's dark,
    checked in `ColorRolesTest`). It lasts while OK is down.
  - A **button** (`PillButton`, every kind) dips under its resting size, to 0.98, its shadow set
    down, in 80 ms (`button_zoom.xml`), and zooms back in the focus's time as OK is let go; a danger
    button, which has an edge and no fill, shows it by its size.
  - A **field** (`zooms="true"`) dips to 0.97 the same way (`entry_zoom.xml`), keeping its fill.
  - The **gear** on the home screen and the **tiles** dip too
    ([`feature/home/`](../feature/home/README.md#appearance)).
  Even a quick press shows its look, at the key's going down; a fill is on screen only while OK is
  down, a dip also as it comes back.
- **A hold of OK opens a menu after 350 ms, wherever Luncher has one.** A tile's menu and a picture's
  menu in the editors open after Luncher's own hold (`OkHold`, `menuHold` in `:domain`), sooner than
  Android's long press (500 ms, 400 from Android 11), where the user hasn't lengthened that in the
  accessibility settings. A view calls `OkHold` from its key handlers; `ListEntry` does for a row
  that has a long-click listener, so the pictures' rows have it with no code of their own. The view
  stays pressed meanwhile, which shows as above.
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
- **A floating panel comes and goes quickly.** A dialog, the working window, the settings panel
  and the panels opened in its place, the window after a share and an option's list fade in in
  100 ms (`decelerate_cubic`) and out in 80 ms (`anim/panel_enter`, `panel_exit`, in the style
  `Animation.Luncher.Panel`), as a tile's menu does, which grows out of its tile too
  ([`feature/home/`](../feature/home/README.md#how-the-menu-comes-and-goes)). Android's own
  animations, which the dialog themes would leave them, are slower and slide on Android 16, where a
  dialog-themed activity (the settings panel) gets none at all and pops in and out. A dialog's or a
  list's window plays the style's window items, an activity's window its activity items, so the
  style has both. The panels whose window covers the screen have `Animation.Luncher.PanelActivity`,
  the activity's items alone: on Android 7.0 such a window played its window exit too, two frames
  late, so the panel faded twice over and jumped back as the first fade ended. How a panel closes is
  read from the window under it, the top full-screen one of Android's transition, not from the panel
  (seen on Android 7.0 to 13, not on 16), and that window's own animation is four times as long:
  the home screen's window carries the panel's while the settings panel is over it
  ([`feature/home/`](../feature/home/README.md#the-top-bar)), and the window after a share, which can come
  over any app, names its fade as it finishes. Android 5.1 and 6.0 play no app dialog's window entrance: there `PanelDialog`
  (every dialog of `Dialogs.kt`, and `WorkingDialog`'s window) plays it in the window
  (`WindowEntrance`), started at its first draw, as on a slow TV that can come after the animation
  would have ended; the window keeps its exit, and has no entrance of its own. An activity's and a list's entrances play there. Each follows Android's animation
  settings: a dialog's and a list's the window animation scale, an activity's the transition
  animation scale, the entrance in the window the animator duration scale; with animations off,
  none. Nothing waits for an exit: what closed the panel acts as it starts fading. A window
  coming from another app (the window after a share) comes with Android's task animation, the
  home screen with it; while a screen of Luncher's is in front (`Foreground`), the share window
  has `panel_enter` instead, as Android's task switch played nothing of its own there (it popped in
  on Android 16).
- **A full-screen screen comes and goes as quickly.** The appearance editor, the background editor,
  Add from folder and the tile banner picker fade in in 100 ms and out in 80 ms too, in place of
  Android's cross-fade of about 350 ms, and the screen under holds still meanwhile
  (`Animation.Luncher.Screen`: `panel_enter` and `panel_exit` for the screen, `screen_hold_open` and
  `screen_hold_close` for the one under it, which would otherwise fade or slide with Android's own).
  Set in those screens' themes, not in `Theme.Luncher`, which is also the home screen's: how it
  opens an app and comes back from one stays Android's.
- **A dialog's dim goes with what it dims.** The dialogs don't use Android's dim: it's a layer of its
  own, outside the window it belongs to, so a screen's exit doesn't fade it, and once a screen has
  called `finish()` Android no longer changes it. `ScreenDim` is a window of the screen's that
  covers it and only draws the dim, 0.6 of black, below the screen's dialogs and taking no key and no focus, so
  their windows work as they would alone. Being the screen's, it fades with the screen, and being one per screen, it
  isn't touched as one dialog hands over to the next. Every `PanelDialog` holds it while it shows; it
  comes and goes with the same fade as they do, with the last one. The panels that are activities (the settings
  panel, the window after a share) draw it in their own window: a window an activity adds goes above
  the activity's, so `ScreenDim` can't go under them. Their window covers the screen with `@color/dim` as
  its background, and the panel is a view in it (`View.stylePanel`), with the shadow a floating window
  has (`panel_elevation`). Android's dim is used nowhere. Hide apps, over the settings panel, dims
  nothing, and a tile's menu neither.
- **One window handing over to the next leaves the dim as it is.** A dialog's button or entry that
  opens another window, the working window closing into its message, a tile's menu into the rename
  dialog: `Handover.from(old) { open next }` shows the next first; `old` stays, with no animation and
  no key, until the next has drawn its first frame, then closes at once, so the dim never goes between
  them and the first dialog of a process, which is slow to make, doesn't leave the screen bare ("drawn" is the
  first draw from Android 11, and from Android 7.0 before it the first frame rendered, as the first
  window of a process shows some frames after the main thread has drawn it; the old one goes after one
  second at the latest). If the action opens no window, `old` closes as it would alone, with its fade, unless the action finishes its
  screen: then it stays, and goes with the screen's own exit, dim and all, as closed first it would fade away alone and
  leave the screen under it bright until Android starts that exit. If the action starts a screen
  (`Handover.toScreen`, inside `from`: a tile's menu opening the tile banner picker or App info), `old`
  stays, taking no key but Back, as closed at once it would leave the screen under it bare for as long as
  the next one takes to start (another app's, longer): until its screen is stopped, covered whole, when it
  closes unseen, with no animation; or comes back without having been, from a screen over part of it
  (Android TV's side panels), when it's there as the user left it. A screen that couldn't start closes
  it as usual; one that isn't on its way (its screen not paused) within a second, with its fade.
  Once closed, a window handed over has its keys and animations back: a tile's menu and the working
  window are shown again later.
  Cancel on the working window closes it at once, before what it does.
- **A window shown is never left deaf (Android 9).** There a window that gets a key in the few ms
  after Android adds it (OK released just as a tile's menu opens, a key that came with the one
  opening a dialog) may never get its window focus on the app's side: Android sends it every key and
  its view root drops them all, Back too, so only Home leaves it. A dialog's window (`PanelDialog`) and
  a tile's menu call `WindowFocus.checkAfterShow` once they show: a short while later a window still
  without the window focus is made unfocusable and, at the main thread's next turn, focusable again,
  which has Android give it the focus (the two in one turn, or its attributes set again, don't); a few
  tries at most. A window that has the focus, or that another app's window in front rightly has it
  over, is left as it is. Only from Android 8.0 to 10 (`WindowFocus.NEEDED`): seen on 9; not on 7.0,
  11 or 16.
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
not drawn into bitmaps, from API 23 on: bitmaps sent to the GPU made a slide's frames about 6
times slower on tv_api28. With animations off, the list moves at once.

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
