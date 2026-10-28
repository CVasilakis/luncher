# feature/settings/

The settings panel, `:feature:settings`, and the screens it opens: Hide apps, the appearance
editor, and from it the background editor, where the wallpaper is chosen from Luncher's picture
library, with Add from folder; the tile banner picker, which the home screen's tile menu opens,
where a picture of the library is chosen for an app's tile; and the way in for the pictures
another app shares. What a feature
module holds and may use: [`../README.md`](../README.md); the rules it follows:
[`../../docs/ARCHITECTURE.md`](../../docs/ARCHITECTURE.md).

| | |
|---|---|
| Package | `com.luncher.launcher.settings` |
| Depends on | `:domain`, `:ui` |

## Layout

```
feature/settings/
├── build.gradle.kts              luncher.android.library and Roborazzi; its dependencies
└── src/
    ├── main/
    │   ├── AndroidManifest.xml   the screens' activities, how they behave, the home apps it sees (below)
    │   ├── java/com/luncher/launcher/settings/
    │   │   ├── SettingsGraph.kt  the ports the settings screens need; Activity.graph
    │   │   ├── ScreenTheme.kt    a screen's theme colors, and its restyling in place when the theme changes
    │   │   ├── Sheet.kt          placeAsSheet: a panel's window as a side sheet on the home screen's gear's side
    │   │   ├── SettingsActivity.kt   the settings panel: lists the domain's settingsMenu, opens entries
    │   │   ├── HideAppsActivity.kt   the Hide apps list: every app by name; OK hides or shows one
    │   │   ├── Chip.kt           Chip (an app's "Hidden"), ChipFrame (a stepped value between its arrows)
    │   │   ├── AppearanceActivity.kt the appearance editor: the options, Left and Right step them
    │   │   ├── AppearancePreviewView.kt  the editor's preview: an illustration of the home screen
    │   │   ├── AppearanceEditorLayout.kt the editor's columns: the options' container level with the title
    │   │   ├── EditorStartColumn.kt  its title, preview and buttons, the preview as large as the room left
    │   │   ├── StepperRow.kt     an option's name, and its value's chip beside it or below it
    │   │   ├── OptionRow.kt      an option's row, of any catalog: both editors'; Left and Right step it, or OK opens its list
    │   │   ├── OptionList.kt     an option's values in a list beside its field, of any catalog: the background editor's
    │   │   ├── OptionLabels.kt   what a catalog's options and their values are called (OptionLabels), the appearance's (AppearanceLabels) with the accents' swatches; the groups' names and icons
    │   │   ├── BackgroundActivity.kt the background editor: the picture editors' parts below, the preview of the home screen, Save
    │   │   ├── LibraryList.kt    a picture editor's list: the first row it's given, the pictures, Add from folder; the thumbnails, the focus kept
    │   │   ├── PictureThumbnail.kt   a row's thumbnail, and over it a see-through picture's hairline in the row's state
    │   │   ├── PictureActions.kt the library read with each picture's uses, and Rename, Delete, Delete all with their questions
    │   │   ├── PicturePreparing.kt   the pictures not prepared for the screen, prepared at its start behind a window
    │   │   ├── PictureDraft.kt   a picture editor's draft: its values offered, changed as Save counts it, Back's question, the instance state
    │   │   ├── OptionGrid.kt     a picture editor's options, of any catalog, as a grid of fields, two a row
    │   │   ├── PictureStartColumn.kt  a picture editor's start: the preview it's given, options, Save and Delete all, the preview as large as the room left, or gone below a least height if given
    │   │   ├── TileBannerActivity.kt  the tile banner picker: the picture editors' parts, a preview of the app's tile, Save through the per-app port
    │   │   ├── TilePreviewView.kt    its preview: the app's tile at rest and focused, as the home screen draws it, at its place there
    │   │   ├── AddFromFolderActivity.kt  the drop folders' pictures, a preview of the selected one; OK adds it, then returns it; Add all
    │   │   ├── SharePictureActivity.kt   where another app's share arrives: opens the home screen and the window, no screen
    │   │   ├── PictureReceivedActivity.kt the window over the home screen after a share: shows the pictures being added, then their uses, or how many were saved and why the others weren't
    │   │   ├── PreviewColumn.kt  Add from folder's preview, Add all and note, the preview smaller on a short screen, gone below a row's height
    │   │   ├── ListFrame.kt      a list's frame (a :ui ScrollFrame): Down past its last row stays there, not on the column beside it
    │   │   └── PictureTexts.kt   what the picture screens call things: sizes, names, why a picture wasn't added
    │   └── res/                  the themes (Theme.Luncher.Settings, .Panel, .FullScreen, .Pictures,
    │                             .Pictures.NoDisplay) and the label style, layouts, the thumbnails' frame,
    │                             settings_* dimensions (compact ones in values/, the rest in values-h450dp/), colors
    │                             and strings (the components, dialogs, icons and colors of the theme are :ui's), their
    │                             translations in values-el/ and values-ar/ (docs/TRANSLATIONS.md)
    └── test/
        ├── java/…/settings/      JVM tests; TestSettingsGraph.kt: the ports a test gives the screens,
        │                         and the tests' Application (named in resources/robolectric.properties);
        │                         TestPicturesGraph.kt: the same for the picture screens, an empty library
        └── screenshots/          reference images, committed: settings/, pictures/
```

Everything but the activities and `SettingsGraph` is `internal`, and `TileBannerActivity.intent`, the one way another
feature opens the picker ([`ARCHITECTURE.md`](../../docs/ARCHITECTURE.md#features)).

## Manifest

| Element | Reason (don't remove without replacing it) |
|---|---|
| Every activity but `SharePictureActivity`: `exported="false"` | Only Luncher opens them. |
| The panels, the two editors and the tile banner picker: `launchMode="singleTop"` | A repeated OK or Menu press doesn't stack a second one. |
| `HideAppsActivity`: `Theme.Luncher.Settings.Panel` | Opens in the settings panel's place, which already dims the home screen ([below](#hidden-apps)). |
| `AppearanceActivity`: `Theme.Luncher.Settings.FullScreen` | Full-screen and opaque: it covers the home screen whole, so the home screen is stopped meanwhile and applies the changes once, when it comes back ([`ARCHITECTURE.md`](../../docs/ARCHITECTURE.md#appearance), point 5). |
| No `screenOrientation` | Android 8.0 (API 26) refuses one on a floating activity; it shows over the landscape home screen anyway, as the editor does. |
| `BackgroundActivity`: `Theme.Luncher.Settings.FullScreen` | Full-screen and opaque, as the appearance editor, which opens it. |
| `BackgroundActivity`: `windowSoftInputMode="adjustNothing"` | The keyboard a picture's Rename opens doesn't slide the editor up behind the dialog to keep a low picture in sight, with a black band below; the dialog moves instead (`:ui`'s `NameDialog`, [`ui/README.md`](../../ui/README.md#the-design-system)). |
| `TileBannerActivity`: `Theme.Luncher.Settings.FullScreen`, `windowSoftInputMode="adjustNothing"` | As the background editor, whose layout and parts it shares: full-screen and opaque over the home screen, which is stopped meanwhile and shows the tile as stored when it comes back; a picture's Rename doesn't slide it up. |
| `AddFromFolderActivity`: `Theme.Luncher.Settings.Pictures` | Full-screen and opaque, on the home screen's background. |
| `SharePictureActivity`: `exported="true"` | Other apps' shares arrive there. |
| `<queries>` for `MAIN`+`HOME` | Package visibility (targetSdk 30+): `SharePictureActivity` tells whether Luncher is the device's home app by what a `HOME` intent resolves to ([below](#adding-a-picture)), which needs every home app visible. Without it, Android hides the stock launcher, and Luncher, the only home app left, seems the default: a share then opened a second home screen beside the stock launcher's (measured on API 30). |
| `PictureReceivedActivity`: `Theme.Luncher.Settings` | A floating window over the dimmed home screen, like the settings panel. |
| `PictureReceivedActivity`: `launchMode="singleTop"` | A share that arrives while it shows comes to it (`onNewIntent`), and its pictures join those being added ([below](#adding-a-picture)). A new window would replace it, as the home screen, `singleTask`, clears what's above it, and the pictures it was adding would stop. |
| `SharePictureActivity`: `taskAffinity=""` | A share sent to a task of its own (`NEW_TASK`, as an app sends one from a notification or a service) opens in a task of no app, gone as it finishes, and the home screen and the window in Luncher's task, both sent there by its affinity (the window too: else it would join the share's task, where the home screen's, brought back, could come over it on API 28). With Luncher's affinity, the task the share made would be rooted at that share; Android would then only bring it back for the next share alike (a `SEND` of the same type, sent the same way, which it matches without the picture), and that picture would never arrive. Measured on API 22 to 36, where it happened whenever that task was in front, and on API 24 also with Luncher as the home app. Android also advises an empty affinity on an exported activity, so another app can't take over its task. |
| `SharePictureActivity`: `Theme.Luncher.Settings.Pictures.NoDisplay`, `noHistory`, `excludeFromRecents` | It shows nothing, and finishes in `onCreate`, before `onResume`, as a `windowNoDisplay` activity must; its backgrounds are still the home screen's dark ones, in case a device shows one ([`ui/README.md`](../../ui/README.md#the-theme)). |
| `supportsRtl="true"` | As the app declares it ([why](../../app/README.md#manifest-why-each-part-is-there)), so the JVM tests mirror the screens in a right-to-left language as the app does. |

The intent filters that make `SharePictureActivity` a target of other apps' shares are the app's
to declare ([`app/README.md`](../../app/README.md#manifest-why-each-part-is-there)); the module
declares how the activities behave, and the build merges the two.

## The look

Every screen here is built from `:ui`'s themed components ([`ui/README.md`](../../ui/README.md#the-design-system)):
entries that turn inverse while focused, pill buttons and fields that also zoom, lists in rounded
containers that fade out where there's more and scroll a row ahead of the focus. The texts, icons and buttons take their colors from the
theme the appearance holds (`ThemeAppearance`: an accent and dark or light,
[`ARCHITECTURE.md`](../../docs/ARCHITECTURE.md#appearance)), through `ScreenTheme`: a screen styles
its views and its window with its `roles` as it's made, and while it's started, a change of the
theme restyles it in place, with no new activity (the appearance editor's Theme group, [below](#appearance)).
The screens under the editor find the change as they come back (`onStart`). Another option
changed restyles nothing: only the theme part is compared. The manifest's themes stay dark, so a
light screen is light from its first frame but a window Android shows before it is never white
([`ui/README.md`](../../ui/README.md#the-theme)).

A screen of 450 dp or more tall (1080p and 720p at their usual densities) gets the normal
spacing (`values-h450dp/`); a shorter one (720p at the 1080p density, 360 dp tall) the compact
spacing in `values/`, so the same layouts fit it.

## The settings panel

Its entries are Hide apps ([below](#hidden-apps)), Appearance ([below](#appearance)) and the
device's own settings. `SettingsActivity` is a side sheet over the dimmed home screen, on the side of the
screen where the home screen's gear is, so it opens beside it (`placeAsSheet`: at most
`settings_sheet_width` wide, as tall as the screen less its margin; at the right by default, at
the left once the clock is moved to the right; the screen's side in any language, as the home
screen doesn't mirror, though the panel's content does), its theme, `Theme.Luncher.Settings`, a platform
dialog theme. Each entry is one line: its icon, its name, centered in the entry's height, and at
its end where it goes: a chevron to a panel of Luncher's, an arrow out of it to another app. On a screen too short for them all, the entries
would scroll a row ahead of the focus in whole rows, fading out where there's more (a
`LookAheadScrollView` in a `ScrollFrame`); the three of them fit on every TV screen the layout
tests check. Back closes it, as any activity, and so does Home: the home
screen is `singleTask`, and Android closes what's above it in its task. The home screen's focus is
where it was.

| Part | Job |
|---|---|
| `settingsMenu` (`:domain`) | which entries the panel lists, in order |
| `SettingsEntry` (`:domain`) | the kinds of entry, one type each |
| `SettingsActivity` | shows the entries one below the other, all equally far apart; each entry's name (`label`), icon (`icon`), end mark (`end`) and what OK on it does (`open`) |

An entry with settings of its own
opens them in a panel of its own ([below](#hidden-apps)) rather than in more of this one. To add
an entry:

1. A `SettingsEntry` type in `:domain`, placed by `settingsMenu`, with its unit test.
2. Its name, icon, end mark and action in `SettingsActivity`: `label`, `icon`, `end` and `open`
   are exhaustive `when`s, so this module doesn't compile until all handle the new type.
3. A value the entry changes (e.g. whether the date shows) is read and stored through a port with
   an adapter, like any data from the device ([`ARCHITECTURE.md`](../../docs/ARCHITECTURE.md#where-things-go)).

What keeps it light: its code runs, and its window exists, only while it's open. Behind it the home
screen stays started (the clock keeps running) and, as after any other activity, reads the apps
again when the panel closes, keeping its tiles when nothing changed.

### Hidden apps

Hide apps (`HideAppsActivity`) is a panel of its own, a side sheet in the settings panel's place:
below its title, a hint that OK hides an app or shows it again, then every app by name, with no
icon, its name dimmed and a "Hidden" chip at its end while it's hidden. Each app is listed by its
real name, the device's, and in that order, so it's found where it's expected whatever it was
called on the home screen; an app the user renamed there has "Renamed: " and that name below it,
in smaller text. Every row is as tall as two names with a margin above and below, at least a
list's usual row, renamed or not, as the list gives its rows one height; the two names are
centered in it together, the chip at their middle. OK on one hides it or shows
it again, and Back returns to the settings panel. It's the one place a hidden app is shown again; an app is also hidden from its tile's menu
on the home screen ([`../home/README.md`](../home/README.md#a-tiles-menu)). The home screen behind shows each change at once: it follows the stored arrangement while
it's started, as it stays behind the floating panels. The list itself follows the installed apps
while it shows: an app installed or removed meanwhile appears in it, or leaves it, at once.

A panel of its own is another activity in this module, started from the settings panel with
`openOwnPanel`. The settings panel's window stays behind it, invisible (its alpha is 0 until it
resumes), since a smaller panel would show it around its edges; the dimming of the home screen is
that window's too, which is why the other panel's theme, `Theme.Luncher.Settings.Panel`, dims
nothing itself.

| Part | Job |
|---|---|
| `AppArrangement` (`:domain`) | what's stored: the order of the shown apps (none until the user reorders, which means by name) and the hidden apps in their order |
| `AppArrangements` (`:domain`) / `PreferencesAppArrangements` (`:platform`) | port and adapter: reads the arrangement once per process, and saves each change at once, in the background, telling the listeners (the home screen behind) |
| `AppCustomizations` (`:domain`) / `PreferencesAppCustomizations` (`:platform`) | what the user set for each app on the home screen: the name shown below the real one |
| `homeApps` (`:domain`) | matches the stored apps to the installed ones: an update that renamed an app's activity keeps its place, hidden state and name, and apps that aren't installed right now keep theirs for when they come back |
| `ArrangedApps` (`:domain`) | the shown and hidden apps: hiding one puts it first among the hidden apps, showing one puts it last in the user's order (or in its place by the home screen's name while there's none), and `byLabel` is the list's content, by the device's label |
| `HideAppsActivity` | shows that list, stores each change; reads the apps again when they change while it shows, and when it comes back (the names too, which only the home screen changes, so it doesn't follow them while it shows) |

The list is a platform `ListView`: it creates views only for the rows on screen, and reuses them
while scrolling. It fills the sheet below the title and the hint, as the settings panel's entries
do (`layout_weight`), down to the sheet's padding, or a few pixels above it: when the apps don't
all fit, the window ends on a whole row, the one at the edge dimmed by the fade, at the top too
once it has scrolled (a `ScrollFrame`, no scroll bar). Its window is whole rows, and it scrolls a row ahead of the selection, sliding under it
(a `LookAheadListView`, [`ui/`](../../ui/README.md#the-design-system)): the selected app stays a
row away from an edge with more beyond it, so the next app shows whole before the selection gets
there, and the last app comes up whole, no longer faded, before it's selected.

### Appearance

Appearance (`AppearanceActivity`) is the editor of the home screen's
[appearance](../../docs/ARCHITECTURE.md#appearance) and of Luncher's theme: full-screen, with its
large title right above a preview of the home screen at the left, and below the preview two
buttons, Background and Reset to defaults, the three centered in the height (`EditorStartColumn`:
the preview as large as the room the others leave, at least a quarter of it); the options at the
right, in their container, starting level with the title and reaching down to the bottom margin
from the start, whether a group is open or not, as the side sheets' lists do, measured for the room below it as the
columns are measured, where the title's place follows from the start column's measures
(`AppearanceEditorLayout`): measured for more room first, the list would fit its rows to that room
([`ui/`](../../ui/README.md#the-design-system)) and lose its scroll at each layout. The options
are in groups (Tiles, Time and date, Theme), each an entry of its own with its icon and a mark
pointing down, or up while it's open, closed at first: OK on one shows its options below it and
closes the group that was open, so the list stays short; OK again closes it. Each option shows its
value in a chip between arrows, beside its name or, where that leaves the name too little room,
below it (`StepperRow`); Left and Right step it through its values, around from the last to the
first, and each change is stored at once, the preview following.

The Theme group holds the accent color (Orange, the default, Pink, Purple, Blue, Teal, Green,
Amber, Gray; each value with its swatch) and the mode, Dark or Light. They change the menus and
settings, and the accent the home screen's focus frame and settings gear too, while the mode
leaves the home screen dark on its wallpaper. A change restyles the editor in place
([above](#the-look)); the preview's frame takes the accent too. An option that
changes nothing with the others as they are (a full bottom row with the rows at the top; the
side with the time and the date hidden; their order with either hidden), or with what else
decides it (the background's picture's shape, in the background editor:
[below](#background)), is dimmed, and the focus passes it by.
The background's group is edited apart (`editedApart`): it's the Background button, which opens
the background editor ([below](#background)), where the picture and its options are saved
together; back in the editor (`onResume`), the appearance is read again. Reset to defaults, in the
error color like Delete all, asks first (Cancel focused, as Delete), then puts every option back,
the theme too, and the wallpaper to none; with every option at its default already, it does
nothing. Back returns to the settings
panel; the home screen shows the result when it comes back.

It's started with `openOwnPanel`, like Hide apps, but covers the settings panel and the home screen
whole (its theme). In a right-to-left language it mirrors like the other panels: the options at
the left, each value's chip at the left of its name, and Left steps toward the arrow on that side, the
next value. The preview's content doesn't: it shows the home screen, which stays left to right
([why](../../app/README.md#manifest-why-each-part-is-there)). The options take at most
`settings_appearance_options_width`, and half of a narrow screen, so the preview keeps the rest;
they scroll when they're taller than the screen, a row ahead of the focus.

| Part | Job |
|---|---|
| `appearanceOptions`, `AppearanceOption` (`:domain`) | which options there are, in which groups; each one's values, in order, the part of `Appearance` it reads and changes, whether it applies, and what a step does, with the values an `AppearanceContext` offers (all of them here) |
| `AppearanceSettings` (`:domain`) / `PreferencesAppearanceSettings` (`:platform`) | port and adapter: the stored `Appearance`, read once per process; each write is stored at once, in the background, and told to the listeners (the home screen, while it's shown) |
| `TileLayouts`, `topBarLayout` (`:domain`) | where the preview's tiles and top bar items go, by the home screen's own rules |
| `AppearanceActivity` | the groups' entries, opening one at a time; a group edited apart as a button; the options' rows (`OptionRow` with `AppearanceLabels`, whose exhaustive `when`s, as `topicIcon`'s, don't compile until a new option has its labels, and a group its icon) and the reset button; its restyling when the theme changes (`ScreenTheme`) |
| `ThemeAppearance`, `colorRoles` (`:domain`) | the theme the Theme group changes, and the colors every screen here takes from it |
| `PictureImages`, `Wallpaper` (`:domain`) | the wallpaper's picture: the home screen's own, shrunk, when it's of the background; else drawn at the preview's size, from its working copy |
| `AppearancePreviewView` | draws the preview: an illustration, not the real home screen (below) |

**The preview**, its corners rounded and, on the plain background, edged in the theme's outline color, draws outlined tiles filled with white at 20 % (`settings_preview_tile`, in place of
the banners) on the home screen's background (the wallpaper's picture, drawn at the sample screen's
size in the background's style, or the plain color), the first one zoomed as focused, as much
framed as lifted (with the focus frame on, the frame in the accent's vivid tone inside its edge, as
on the home screen; off, outlined as the other tiles, with a dozen faint rounded rectangles, each a
little wider and lower, for the shadow), and plain blocks for the time,
the date, the settings gear and, while the app name shows, the focused tile's name, or every tile's: no images or
text. It's a made-up sample, not the user's apps: a screen 960 dp wide, of the TV's shape
(`screenSize`, which the wallpaper is drawn at: 540 dp tall on a 16:9 TV), so a picture is cut as on
the home screen, with seven tiles, a full row and a short one, so that every option's values look
different. The width stays on every shape, so a row holds as many tiles. It lays them out with
`TileLayouts` and `topBarLayout` at its own density (its pixels per sample dp), so every dp value
scales with it and it can't disagree with the home screen. The room the rows leave the focused
tile's name is measured as the home screen's (`:ui`'s `TileName`, at the largest zoom), so it
follows the device's font size and the language's font there too. What's on the wallpaper has the
home screen's edge (`WallpaperOutline`, [`feature/home/README.md`](../home/README.md#the-top-bar)),
so the illustration reads on a white picture too: the blocks and the tiles' outlines, at the
sample's scale but a pixel at least (there the tiles' fill doesn't show, their outlines do); not the
focus frame, as on the home screen. A new shape gets it too: `AppearancePreviewViewTest` fails for a
light shape without it. It isn't focusable.

To add an option: [`ARCHITECTURE.md`](../../docs/ARCHITECTURE.md#appearance), "Adding an aspect";
its labels go in `AppearanceLabels`' `label` and `value` (`OptionLabels.kt`), in every translation.

What keeps it light: the preview draws from shapes it keeps, made anew only when the part of the
appearance they come from changes (compared with `==`), so a new corner radius lays out nothing,
and drawing allocates nothing. The wallpaper's picture is the home screen's own when it's of the
background (`Wallpaper.readyFor`): the home screen read it as it started, so it's in memory, even
the first time after the TV started, and the preview has it in its first frame, shrunk to its
size on the main thread, in milliseconds: the first frame takes less than with the whole image,
which has over 4 times the pixels to upload. Halved first, each half averaging 2 x 2 pixels,
then scaled the rest of the way, as drawing it at once would turn thin lines to moiré
(`AppearancePreviewViewTest`); as a working copy covers half the screen, the two look alike.
Another background (the background editor's draft, a style tried) is drawn in the background
from its working copy ([below](#background)), in milliseconds, the latest request winning, and
dropped when the editor stops. A picture is drawn again only when what it's drawn from (the
background, the preview's size) changes: back in an editor, the one it still shows stays, and back
from saving a new background, the one replaced isn't drawn again before the new one. Only the
options' rows are views; the preview is one view. Nothing of the home screen runs meanwhile.

### Background

The background editor (`BackgroundActivity`) is where the wallpaper is chosen and saved, opened
from the appearance editor's Background button, full-screen like it. At the end, below their
label (Pictures), Luncher's picture library as a list in its container (a `ListFrame`: cut along
its rounded corners, fading out where there's more, scrolling a row ahead of the focus): No picture, the pictures by
name, as the device's language sorts words, each with its thumbnail, its name on one line, cut with "…" where it doesn't fit, and below it its
size in px and its file's size (as in Add from folder, without where: it's in Luncher's storage), then Add from folder
([below](#adding-a-picture)); a hint at the label's end says a long press of OK renames or
deletes a picture, as the home screen's says what the keys do while moving an app. While the library is empty, how to add
a picture follows: a share from another app, or the drop folder
([`platform/README.md`](../../platform/README.md#the-drop-folders)), with its path. At the start,
with no label, a preview of the home screen with the chosen background, of the
TV's shape with rounded corners, the background's options (Blur and Position, then Dim and Fit) as a 2 × 2
grid of fields, each its name above its value, and Save (filled) with Delete all (outlined, in
the error color) beside it, half the row each; two fields, or the buttons, go one below the other
when either doesn't fit its half (`:ui`'s `EntriesRow`: a long translation on a narrow screen). A
field is as wide as its option's widest value, whichever it shows (`OptionRow`), so whether two
share a row doesn't depend on the values: the fields don't move with the draft, nor between two
openings of the screen. There's no
title, nor a label above the preview: the preview gets that room.
The preview is as large as the room the others leave (`PictureStartColumn`), at most as tall
as its screen at the column's width, at least a quarter of that, so Save and Delete all show without scrolling on a 1080p screen;
on the smallest screens the column scrolls. In a right-to-left language it mirrors like the other
settings screens; the preview's content and the pictures don't.

The editor opens with the focus on the chosen picture, or No picture. Down past the list's last
entry leaves the focus there (`ListFrame`): Android would move it to the nearest entry below
anywhere on the screen, an option, Save or Delete all; Left still goes to them. Coming back to it
(from Add from folder, another app, the TV's sleep), the focus is where it was, the list scrolled
as it was: the list is made again only if pictures were added, deleted or moved meanwhile, and then
the focus stays on what had it, unless that's a picture that's gone: then it goes to the one that
took its place, or the one before it. Whether what had the focus is still there is told by its
parents, not by whether it shows: Android shows the editor's window again only after `onStart`,
where the editor reads the library again, so until then none of its views counts as shown.

What the user changes is a draft, the one exception to the appearance editor's rule of storing each
change at once ([`ARCHITECTURE.md`](../../docs/ARCHITECTURE.md#appearance)): OK on a picture
chooses it (tinted with the accent, a check beside it), and the preview follows; nothing is
stored. An option's field shows its value with a mark pointing toward its list, not between
arrows: OK on it opens its values in a list (`OptionList`, a `PopupWindow`) beside the field, at
its end edge, so neither the field nor the preview is covered: a field of the second column has
it over the pictures, the value it has level with the field; one of the first (Blur, Dim) reaches
the grid's end edge, its first value level with the field whatever the value, so it covers what's
beside and below the field whole (the other column's fields, Save and Delete all), never a part
of them, nor the preview above. Either is moved up or down only to stay inside the overscan
margin. The value it has focused and checked; the field outlined in the accent meanwhile
(activated). In a right-to-left language it opens on the other side. The preview shows each value
the focus comes to; OK keeps it, and so does Left, the arrow back toward the field (Right in a
right-to-left language), while Back closes the list (not the editor) with the value from before. Left and Right on the fields don't
change anything: they move the focus, as on the other entries, so Right goes from the second
column to the pictures beside the options. The appearance editor keeps its arrows: nothing is beside
its options. The options offer only what moves the chosen picture on this screen, by how filling
the screen cuts it (`pictureCut`, from the picture's size and `screenSize`, the wallpaper's): the
position's list holds Center, Left and Right for a picture wider than the screen, Center, Top and
Bottom for a taller one. All four are dimmed without a
picture; the position also with the picture stretched; the fit and the position also with a
picture of the screen's shape, or so near it that filling would cut less than 1% (a 16:9
picture on a 1366 × 768 TV), where every choice looks the same. A stretch stored for such a
picture is kept, dimmed. Another picture chosen (`choosing`), as well as one Add
from folder added, or No picture, starts as it is: Center, Dim and Blur Off,
not the options set for the picture before, so the preview shows the picture itself and its
options are set for it (a bright picture needs more dimming than a dark one). The fit is kept:
it's about the picture's shape, not its look. The stored picture chosen again brings back its
stored options, as the home screen shows it, and Save then just closes. The draft never holds a
position its picture doesn't offer either: opening the editor with the stored background, or a
draft kept, makes such a position Center. Back, or Home, leaves the background as it was; Back
with the draft changed (counted as Save counts it, below) asks first, "Discard changes?", its badge
beside the title, Cancel focused, so a repeated Back loses nothing: Cancel stays in the editor, the
draft kept, and Discard closes it. **Save** stores the draft,
through the process's `Wallpaper` ([`feature/home/README.md`](../home/README.md#the-wallpaper)):
- unchanged, the editor just closes: a stored position the draft shows as Center counts as
  unchanged, as it draws the same;
- without a picture, it's stored at once, the kept wallpaper deleted, and the editor closes;
- with one, a window (`WorkingDialog`, from `:ui`) says "Processing background…" at once, taking every
  key, Back too, while the picture is drawn at the screen's size and kept; only then is the
  background stored, and the window closes, after half a second at least, and the editor with
  it. If the user goes home meanwhile, the drawing goes on, and the home screen says so instead.

A **long press of OK** on a picture offers Rename and Delete (in the error color), each with its
icon, under its name and size (`:ui`'s `MenuDialog`).

**Rename** asks for the new name in a field holding the old one, the TV's keyboard up, the cursor
at the end (where the keyboard puts it anyway; `:ui`'s `NameDialog` and `NameField`, outlined in the accent while it has
the focus), the dialog above the keyboard while it shows and the editor behind it still. Cancel and Rename below it, or the keyboard's Done (or Enter), keeps the
name as a picture's is kept (`pictureName`: no control characters, trimmed, cut in the middle past
255 units); a name another picture has, or nothing typed, is refused below the field in the error
color, the field outlined in it, the dialog staying open (`renaming`), as two pictures of one name would be told apart by
nothing. A name is the library's index only: the picture's file, working copy and thumbnail, the
stored wallpaper and the background setting go by its id, from its content, so nothing is copied,
decoded or drawn again; the row keeps its thumbnail, and moves to its new place by name, the
focus with it.

**Delete** asks first, its badge beside the title (`:ui`'s `Badge`, as every dialog's mark), Cancel focused, so a repeated OK deletes nothing, saying what deleting it
does, a sentence for each of its uses, then that the picture goes from Luncher's pictures only:
the background's picture leaves the home screen with no picture, and the tiles that show it
show their app's own image again, with how many (counted among the installed apps, shown or
hidden, the tiles the user can find). That happens at once, the uses first, so nothing names a
picture that's gone: the tiles' pictures stored as none (those of apps not installed now too),
the background stored without a picture (`Wallpaper.forget`), then the kept wallpaper deleted,
then the picture. A draft's picture deleted is no picture in the draft.

**Delete all**, beside Save, deletes every picture of the library the same way, after the same
kind of question, Cancel focused, which says how many, and what that does to their uses, as
Delete says it of one, the tiles counted for all of them. The editor stays, the focus on Save. While the library is empty
it's dimmed, and the focus passes it by.

The editor is put together from parts every screen that chooses a picture of the library and
frames it shares (the tile banner picker too, [below](#tile-banner)), each one concern behind a
narrow surface, its state its own, none of them knowing which screen holds it; the layouts of the list's column (`settings_pictures_column`) and of what's below
the preview (`settings_picture_editor_controls`) are theirs, included by the screen's.

| Part | Job |
|---|---|
| `Picture`, `PictureLibrary` (`:domain`) | a picture of the library; the library in the list's order, by name, and what each one is used for |
| `Pictures`, `PictureImport` (`:domain`) / `FilesPictures` (`:platform`) | the library, which the screen follows while it shows, renaming and deleting, and adding to it |
| `renaming` (`:domain`) | the name a picture renamed keeps, or why it can't: none typed, or another picture's |
| `PreparedPictures`, `PictureImages` (`:domain`) / `WorkingCopyImages` (`:platform`) | each picture's working copy, made once; the preview's wallpaper drawn from it at each value tried, but the saved background's, the home screen's own ([above](#appearance)) |
| `PictureImages` (`:domain`) / `CachedPictureImages` (`:platform`) | the thumbnails, drawn from the working copies at the row's size in pixels, a see-through picture's left see-through, and kept |
| `pictureUses`, `withoutPictures` (`:domain`) | what each picture is used for, which deleting it says, and the background without it |
| `Wallpaper` (`:domain`) | Save (`save`: drawn, kept, then stored), deleting the wallpaper's picture (`forget`), and the saved background's image, which the preview shows while the draft is that background (`readyFor`) |
| `pictureCut`, `AppearanceContext`, `Background.offeredIn` (`:domain`) | what filling the screen cuts from the draft's picture, the options' values that offers, and the draft without a position it doesn't |
| `choosing` (`:domain`, `PictureChoice`'s rule) | the draft with another picture chosen: as it is, or as stored if it's the stored one; a value of the thing itself (a tile's look) kept either way |
| `OptionGrid`, `OptionRow`, `OptionList`, `AppearanceLabels` | the options' fields, two a row, OK opening a list of the values the draft's picture offers, which the preview follows as the focus moves; Back in it puts the value from before back |
| `MessageDialog`, `MenuDialog`, `NameDialog` (`:ui`) | the dialogs over the list, a badge beside the title and the text below starting under it: the question before deleting; a picture's Rename and Delete; the field for a new name, and why it's refused; this screen gives their texts and what each button does |
| `LibraryList` | the list: the first row the screen names (No picture here), the pictures with their thumbnails, Add from folder and the empty library's help, the chosen mark; the focus kept as the library changes; opening Add from folder, then focusing the picture it added (`EXTRA_ADDED`), which the screen chooses, or the first in the list of those Add all added, choosing none (`EXTRA_ADDED_ALL`) |
| `PictureActions` | the library read with each picture's uses, the wallpaper and the installed apps' tiles; a picture's Rename and Delete, Delete all, their questions, and deleting: the uses forgotten first (the tiles' through `AppCustomizations`, the wallpaper's through `Wallpaper.forget`), then the pictures |
| `PicturePreparing` | the pictures not prepared for the screen, prepared at its start behind the working window, stopped with the screen and gone on with as it comes back |
| `PictureDraft` | the draft: only the values its picture offers on the screen, whether it changed as Save counts it (each side as the screen's `counted` says: the background's as offered, a tile's without a look that changes nothing), Back's question, and the draft kept in the instance state (`DraftState`: the background's dim and blur, a tile's look, beside the picture and framing every draft has) |
| `PictureStartColumn` | the preview the screen gives, at the shape it gives (here the TV's), as large as the room the options, Save and Delete all leave |
| `BackgroundActivity` | what's the wallpaper's alone: the background's options, the preview of the home screen, Save and its window; the parts above put together |

**Prepared once, then at once.** Each picture of the library has a working copy
([`platform/README.md`](../../platform/README.md#pictures)): the whole picture decoded once, as
it's added, covering half the screen, from which its thumbnail and the preview at every value of
an option the user tries are drawn in milliseconds (but the background saved, whose preview is
the home screen's own, [above](#appearance)), where the photo itself takes a decode each
time (seconds, on a weak TV, for a large one). A picture not prepared for the screen as it is now (the TV switched
resolution, an update draws pictures otherwise, its copy was lost) is prepared as the editor opens,
before the user can do anything: a window (`WorkingDialog`) says "Preparing the picture…", or
"Preparing picture 2 of 5…", a job each, half a second at least, and Cancel, or Back, closes the
editor, nothing chosen yet. Hidden meanwhile, the editor stops, and prepares what's left when it
comes back. Save draws the wallpaper from the original, as it's stored for good.

All thumbnails are read at once when the screen shows, from what `CachedPictureImages` kept, on the
main thread, so the list shows them with its first frame; one not kept (a picture just added, a
screen of another size) is drawn in the background from its working copy, a `LatestRequest` each,
and shows when it's done. The screen keeps each picture's row while the picture is in the library, so its thumbnail is
read or drawn once, and drops the requests in `onStop`. A see-through picture's thumbnail is see-through (its
working copy drawn over nothing, `WorkingCopyImages.keepingSeeThrough`), over the row's own color
without the card behind the others, with a hairline around it, drawn over the picture
(`PictureThumbnail`, which takes its row's state, as the row's texts do), so the column keeps the
thumbnails' shape and a dark logo shows over a focused row: the theme's `outlineVariant` at rest
and on the focused row, its `outline` on the chosen row's accent tint, where the faint one all but
vanished in the dark theme (`LibraryListTest` checks it reads there at least as well as at rest,
for every accent in both modes). Only for a picture known to be see-through
(`Picture.seeThrough`), which its copy tells: a row made before (while the editor prepares the
pictures) gets the line as the library shows again; the other rows, opaque pictures' included,
are as they were. A draft survives a configuration change
(a TV switching resolution), kept in the instance state.

### Tile banner

The tile banner picker (`TileBannerActivity`) is where a picture of the library is chosen for one
app's tile, in place of its own banner, and framed; Tile banner in the tile's menu on the home
screen opens it ([`../home/README.md`](../home/README.md#a-tiles-menu)), for that app. (The code calls
what it chooses the tile's image: `TileImage`, and its options `TileImageOption`.) It's the
background editor's screen with a tile in place of the home screen, put together from the same
parts ([above](#background)), so everything not said here is as there: the pictures' list, its
focus and its hint, the long press's Rename and Delete, Delete all (the whole library, after the
same question), Add from folder, preparing the pictures at its start, the options' fields and
their lists, the draft and Back's question, the instance state, mirroring in a right-to-left
language but for the preview's content and the pictures.

At the end, the list's first row is "Original banner": no picture, the tile showing
its app's own image (its banner, or for an app without one its icon card), its thumbnail that
image. At the start, a preview of the app's tile with the draft (`TilePreviewView`), what the
home screen will show: the tile twice, side by side, each in a panel with the design system's
corners, at rest and focused (zoomed by `:ui`'s `FOCUSED_TILE_ZOOM`, framed or lifted as the
appearance's Focus frame says); below it the app's name as the home screen shows it, centered,
so the screen says which app's tile it is with no title; then the tile image's options, two
rows of two fields: Fit and Position, then the tile's look, Backdrop and Focus; then Save and
Delete all. The panels line up with the fields' two columns, the gap between them the fields'.

Each panel shows the tile at the home tile's size (`homeTileSize`), over the part of the wallpaper
around the tile's place on the home screen, at the screen's size: so it's the home screen there,
pixel for pixel, but for a lifted tile's shadow, which Android casts from a light above the
screen's top middle, so it falls a little differently at the panel's place. The place is the
home screen's to tell, as it opens the picker (`TileBannerActivity.intent`'s `TilePlace`,
`:domain`): only it knows where its tiles are, as its
top bar is as tall as its text and its scroll is where the focus went; it gives the tile's place
in its window, scrolled as it shows, before its zoom (`AppTilesView.placeOf`). Without one, or
with one given on a screen of another size (`TilePlace.on`), the tile is in the screen's middle.
Near the screen's edge a panel shows the wallpaper up to it, the tile off its middle, where it is
against the wallpaper. Without a wallpaper, or while it's being drawn, the panels show the home
screen's plain color, edged in the theme's outline color; the wallpaper is the home screen's own
(`Wallpaper.readyFor`), followed while the picker shows. The preview's shape
(`TilePreviewView.shape`) is two panels each the zoomed tile with room around it
(`settings_tile_preview_room`); `PictureStartColumn` gives it the height the others leave, and
where that's less than the tile needs, both panels show the tile and the wallpaper smaller alike.
Shorter than a row of the list (`settings_pictures_preview_least`, as Add from folder's preview),
they'd show nothing to judge a look by, and they go, the others getting their room; the app's name
stays (on a 640 x 360 dp screen, 720p at 320 dpi, in every language; on 4:3 in Greek).
Left to right in every language, the resting tile at the left, as the home screen doesn't mirror.

The options are the tile image's catalog (`TileImageOption`, with its own context,
`TileImageContext`): what Fit and Position offer follows what filling **a tile** cuts from the
picture, not the screen: Left and Right for a picture wider than a tile, Top and Bottom for a
taller one, both dimmed for a picture of a tile's shape (a 1920 x 1080 one), and without a
picture; the position also while stretched. Their names and values are the background's
(`TileImageLabels`). The look (`TileLook`) is for a picture with see-through parts (a logo cut out
of its background): **Backdrop**, what's behind it in the tile (None, Light glass, the default,
Dark glass, or Frosted), and **Focus**, what the focus frame, or the lifted tile's shadow, follows
(the Tile's shape, the default, or the picture's own Shape); the focus style itself stays the
appearance's Focus frame, for every tile. Both are dimmed without a picture, and for a picture
that isn't see-through, or isn't known to be yet (`Picture.seeThrough`, learned as its working
copy is made, which the picker's start does where it's missing: the options follow as the library
tells). The home screen draws the look ([`../home/README.md`](../home/README.md#appearance)), and
the preview draws it as the home screen does, with `:ui`'s `TilePainter`: the backdrop (Frosted,
the wallpaper's frosted copy behind the tile's place), the frame or the band around the picture's
shape, the lifted tile's shadow (Android's, from the outline the painter gives the home screen's
tiles, `TilePainter.outline`; the picture's own soft shadow for Focus Shape). The draft's tile is
made as the home screen's is prepared, at the tile's size, but kept nowhere
(`PreparedTiles.make`, in a job, the latest request winning, the tile shown before staying
meanwhile): the file is Save's. A new picture, framing or Focus makes it again, Focus Shape with
the picture's shape (which then serves Tile too); a new backdrop is drawn from the tile made. The
app's own image, for Original banner, is drawn at once at the tile's size; it also stands in for a
picture whose tile can't be made (its working copy not prepared yet, or gone), as on the tile,
which is made at the next change, the library telling once its copy is made.

The picker opens on what's stored for the tile, the focus on its picture, or on Original banner.
The look is the tile's, not a picture's (`PictureChoice`'s values of the thing itself): another
picture chosen keeps the draft's, and so does the stored picture chosen again, which brings back
its stored framing only. Where Backdrop and Focus are dimmed, a look changed is no change
(`TileImage.counted`): Back doesn't ask and Save just closes, though the draft keeps the look for
the next picture, and a picture saved stores it.
**Save** stores the choice, through `AppCustomizations` (`ArrangedApps.withImage`: what else is
set for the app kept), then prepares the tile's picture for the home screen, which only reads it
([`../home/README.md`](../home/README.md#a-tiles-picture)): drawn from the picture's working copy
at the home screen's tiles' size (`:ui`'s `homeTileSize`, from the screen's width less the home
screen's padding at the sides, which `:ui` holds, so the two agree to the pixel), in a job, while
a window says "Processing tile banner…" (`WorkingDialog`, half a second at least, taking every
key); the picker closes once it's done. It prepares through the process's
`PreparedTileImages`, the one the home screen follows, so a return to the home screen meanwhile
(Home) shows the home screen's window saying the same, until it's done, and nothing is prepared
twice. Where nothing is to prepare, it closes at once, with no window
([`ARCHITECTURE.md`](../../docs/ARCHITECTURE.md#background-work-and-changes-while-shown), point
9): a tile prepared already for what was saved (another Backdrop, drawn live; Focus on an opaque
picture, whose shape is the tile's), and Original banner, which takes the picture off the tile and
deletes what was prepared of it. A tile that can't be prepared (a full disk) closes the window all
the same, the choice stored: the home screen tries once more behind its own window, then shows the
app's own image. Unchanged, Save just closes. A picture another tile, or the wallpaper, shows can be
chosen too. Deleting the draft's picture makes Original banner the draft; deleting the tile's
stored picture takes it off the tile at once, as deleting does everywhere. The app is found among
the installed apps, shown or hidden, by the package and activity the home screen passed
(`TileBannerActivity.intent`); gone (uninstalled meanwhile), the picker closes, storing nothing.

| Part | Job |
|---|---|
| `TileImage`, `TileLook`, `TileImageOption`, `TileImageContext` (`:domain`) | the draft and what's stored: a picture or none, its framing and the tile's look; its options, what each offers for a picture in a tile's shape, and whether it applies (the look only to a see-through picture); what Save counts as changed (`counted`) |
| `AppCustomizations` (`:domain`), `ArrangedApps.withImage` | where the choice is stored, with what else the user set for the app |
| `PreparedTileImages` (`:domain`), `SettingsGraph.preparedTileImages`; `homeTileSize` (`:ui`) | the tile saved prepared for the home screen, at its tiles' size, through the process's one, which the home screen follows; whether there's anything to wait for |
| `AppImages` (`:domain`), `SettingsGraph.appBanners` | the app's own image, drawn at the tile's and the row's size, kept nowhere: the home screen's kept tiles (`appImages` there) are its own, a file per app, and drawing through them would replace the tile's |
| `PreparedTiles` (`:domain`), `SettingsGraph.preparedTiles` | the draft's tile made as a tile is prepared, at the tile's size, kept nowhere (`make`), in the background, the latest request winning |
| `TilePlace` (`:domain`) | where the home screen showed the tile, which it passes with the intent; the screen's middle without one |
| `Wallpaper` (`:domain`), `TilePainter`, `TileFrost`, `FOCUSED_TILE_ZOOM`, `tile_lift` (`:ui`) | the home screen's wallpaper and its frost; the tile drawn, zoomed and lifted as the home screen's |
| `TilePreviewView` | the preview: two panels, the wallpaper's part around the tile's place, the tile at rest and focused; the app's own image at once, a picture's tile once made (the app's own where it can't be made), made again only when the picture, framing, Focus or the tile's size changes |
| `TileBannerActivity` | what's the tile banner picker's alone: the app it's for, its preview and name, Save through `AppCustomizations` and `PreparedTileImages`, behind its window; the picture editors' parts ([above](#background)) put together |

### Adding a picture

**From another app** (`SharePictureActivity`): a file manager's or a gallery's Share of one
picture or several, or "Set as" of one (`SEND`, `SEND_MULTIPLE` or `ATTACH_DATA`, `image/*`). It
takes the pictures' Uris out of the intent (a Share's stream, one or a list, else every item of
its clip, as an app may send them only there; each once), then opens, in one `startActivities`,
Luncher's home screen and over it a window (`PictureReceivedActivity`) with the Uris and the read
access the other app gave, and finishes at once. Back from the window, or OK, is the home screen;
the pictures are then in the background editor's list.

While Luncher is the device's home app (a `HOME` intent resolves to it), the home screen is opened
as Home opens it, with a `HOME` intent to Luncher's package that names no component. On API 24 to
27 a home screen opened any other way, even by a `HOME` intent naming its component, is a second
one, in a task of its own beside the home app's, which stays until the process ends. There Android
also keeps every other activity out of the home screen's task, so the window has a task of its
own, over it. Otherwise the home screen is opened as the TV's app list opens Luncher, with the
intent Android gives for its `LEANBACK_LAUNCHER` entry (`getLeanbackLaunchIntentForPackage`), so
it's Luncher's whether or not it's the device's home app. Not a `HOME` intent then, which makes the
task a home screen's: once closed, Android shows the device's home app, and up to Android 5.1, with
no default, asks which one. Either way this feature needs nothing of `:feature:home`'s. Which
home app is the default can be told from API 30 on only with every home app visible
([Manifest](#manifest)).

`SharePictureActivity` itself has no task affinity, and it sends the window, as the home screen,
to Luncher's task by its affinity: a share sent to a task of its own opens in one that's gone as
it finishes, so it never leaves a task the next share would be taken for ([Manifest](#manifest)).

A share of more than 100 pictures (`PictureLimits.MAX_SHARED`) passes none: the window says
Luncher adds up to 100 at a time, so the user shares again the ones they want. The total is known
before anything is read. Android itself can't pass a much larger share: one of 1000 never arrives
on API 22 to 28, as the intent is too large to start the activity
(`TransactionTooLargeException`), and nothing tells Luncher.

The window, floating like a dialog, its mark beside the title (a `Badge`), shows the pictures
being added, one at a time: the process's `ReceivedPictures`, a session that the window owns.
While they're added, it says "Adding the picture…", or "Adding picture 2 of 5…", on one line, and
Cancel, focused; a spinning wheel in the mark's place once a picture is copied (`ReceivedPictures.copying`, which `PictureImport` reports as the
copying starts): the real work, which then shows half a second at least (`ShortestShowing`, from
`:ui`), so it never flashes, and stays until the end, not on and off between pictures. A picture
refused for what its app says of its name or size, before anything is copied, shows no wheel: why
is said at once. Then, for one picture, "Picture added" and its name, with OK; or "Picture not
added" and why (`whyNotAdded`), with OK. A picture the library has already, by its content or as the same file (the same
name and size), isn't an error: "Already in Luncher's pictures", its name, and OK
(`ImportResult.wasInLibrary`). For several, how many were added now ("3 pictures added", or
"Pictures not added"), then a line for each reason some weren't, with how many, in a fixed order,
a reason none has left out (`notAddedLines`): those the library had, not pictures or damaged, in a
format this TV can't show, over 50 MB, over 64 megapixels (`ImportResult.TooLarge` says which),
named as another picture of the library, unreadable, no room; then those Cancel stopped, and a
share of too many; and OK. One named as another picture of the library isn't added: the user
renames or deletes that one in the background editor, or renames the file
([`platform/README.md`](../../platform/README.md#pictures)).

A share that arrives while the window shows joins it: `SharePictureActivity` then sends the window
alone, without the home screen, which would close it (`singleTask`), and the window, `singleTop`,
gets it in `onNewIntent`; its pictures are added after the others, and the end says how all of
them went. Back closes the window, the pictures added in the library. While they're added, Cancel
and Back stop the adding, those still to add not added (`ReceivedPictures.stop`): once a picture
was saved, the window then says how it went, those cancelled counted, so the user knows what was
kept; before that, it closes. Home closes it, and stops the adding too. Closing it any way ends
the session (`finish`), and so does Android closing it (`onDestroy`): while Luncher is the home
app, Home clears the home screen's task, the window with it (measured on API 23 and 28 to 36).
Where Android leaves the window alive behind the home screen instead, on API 24 to 27 as the home
app (the window has a task of its own) and whenever another app is the home app, the window
closes itself once it's out of sight after the user chose to leave it (`onUserLeaveHint`, then
`onStop`). Not on the hint alone: Android gives it too as a share arrives over the window, which
stays in sight, as the share screen shows nothing, and gets that share. Out of sight for another
reason, as when the TV sleeps, the window goes on. So no picture is added for a window the user
left, nor read without the access that went with it: Android ends the access another app gave
with the activity that received it. A configuration change doesn't end the session: the window
made anew finds it where it was, nothing added twice. An app that stops sending a picture gets
the message that Luncher can't read it, after the library's time limit ([`platform/README.md`](../../platform/README.md#another-apps-answers)).

Only the window that took the session last ends it (its hold, `ReceivedPictures.holder`). On
API 29, while Luncher isn't the home app, Android closes Luncher's task, the home screen and the
window, as soon as another app's task opens (the share's own, or the sending app's): an activity
excluded from Recents, as the home screen is, keeps its task there only while it's the latest. The
window is destroyed, and its session ended, only later, so a share arriving meanwhile still finds
the session open. `SharePictureActivity` therefore sends the window alone only when one of
Luncher's tasks has it on top (`ActivityManager.getAppTasks`); otherwise it opens the home screen
and a new window, as for a first share, which takes the session over, and holds the session itself
meanwhile, so the old window, no longer holding it, ends nothing. Either way it passes on the
access to the pictures still to add as well: a new window needs it, as the old window's goes with
it.

The read access passed to the window is only the access Luncher was given (a grant, which goes in
the intent's clip): any other Uri goes without one, for the library to judge, as an exported
provider's may be read anyway, so passing it on never fails. An intent with no picture in it (no
Uri at all, a text only, extras that don't unparcel) becomes the window's message that the app
shared no picture ("Nothing to add"), never a crash, not the message that a file can't be read,
which says to copy a file that never came; one the other app gave no access to gets that message,
once the library has tried it. A folder shared with the pictures, as a file manager does, is left
out of the session once the library finds it (`ImportResult.Folder`): neither added nor counted as
not added, and a share of folders only says there's nothing to add too. What the Uri points to is
the library's to judge ([`platform/README.md`](../../platform/README.md#pictures)): a `file://`
one, as Android up to 9 shares files, gets the message that says to use a drop folder instead, with
its path.

| Part | Job |
|---|---|
| `SharePictureActivity` | takes the pictures out of the share, refuses more than the limit, passes the Uris and the access Luncher holds to the window; the window alone while one shows, else a new one that takes an open session over |
| `PictureReceivedActivity` | shows the session: the count while the pictures are added, then one picture's result, or the counts; ends the session when it closes |
| `ReceivedPictures` (`:domain`) | the session, one per process (Add all, below, has one of its own): the pictures added one at a time through `PictureImport`, each once, a share joining it, a folder left out, the window that holds it and may end it, whether one is being copied (`copying`), adding stopped with the session still open (`stop`), and how it went (`Outcome`: each picture's answer, and how many were cancelled; or nothing to add, only folders shared) |
| `PictureLimits.MAX_SHARED` (`:domain`) | the most pictures one share brings |
| `PictureImport` (`:domain`) / `FilesPictures` (`:platform`) | adding one picture to the library, cancellable, when its copying starts, and how it went (`ImportResult`) |

**From a drop folder** (`AddFromFolderActivity`): the pictures a file manager or a computer put in
`Android/media/<applicationId>`, on the TV or a USB drive, which a line below the title says, on
one line, the folder last, so where it doesn't fit (the smallest screens) only its start is cut;
listed together by name, as the
device's language sorts words (`inListOrder`), below their label and how many there are, in a
container like the editor's pictures; each row with an icon of where it is (a TV or a USB drive),
its name, then where it is ("Local" or "USB drive") and its size. Beside them, below its label, a
preview of the selected one, rounded, of the TV's shape as the wallpaper fills it, drawn in the
background, a spinning wheel in its place meanwhile; no name below it, as the list's selected row shows it. The
column takes 41 % of the width (`shareTheWidth`, set in code, as nested weights would measure the
list twice);
moving on stops the one being drawn at once
([`ARCHITECTURE.md`](../../docs/ARCHITECTURE.md#background-work-and-changes-while-shown), point 4).
Once it's drawn, the others' are drawn ahead, one at a time, the nearest first (after it, then
before it), at a priority below anything the user waits for (`JobPriority.PICTURE_PREFETCH`), and
stopped as soon as the selection moves, so the picture selected never waits for one drawn ahead.
Each preview is kept while the screen shows (`FolderPictures.cachedPreview`, a file each in
`cacheDir`), so a picture selected again, or drawn ahead, shows at once; they're deleted when the
screen closes, and when it opens anew. The screen doesn't wait for them all at its start: a
folder, or a USB drive, can hold many pictures, most of which the user never looks at.
OK adds the picture to the library while a window (`WorkingDialog`) says "Adding the picture…"
over the list, the preview's decode stopped first so the adding needn't wait for it, and Cancel,
focused, or Back stops the adding and closes the window at once. Added, or found in the library
already by its content, the picture goes back to the background editor, which chooses and
focuses it: the user waits in one place, and nothing moves under them. Refused, the screen says
why (`ImportResult`, an exhaustive `when` in `whyNotAdded`) and stays, for another choice.
Closing it while it adds (Home) stops the adding. What the listing
and the library already tell can't be added (`whyRefused`, in `:domain`) is marked in the list,
after where it is, why in the error color so it stands out, and OK on it says why at once, reading
nothing: a file in a format this TV can't show ([which](../../platform/README.md#pictures)), one
over 50 MB or over 64 megapixels (`PictureLimits`), which have no preview either, and one named as
a picture of the library, the same file (the same name and size: "Already in Luncher's pictures")
or another. The listing goes by names: a file whose picture was renamed in the library isn't
marked, and OK finds it there by its content, without decoding it. A picture cut short or
damaged can't be told from the listing, which reads only a file's start: its preview finds it,
once it's tried (the selected picture's, or one drawn ahead), as does adding it
([`platform/README.md`](../../platform/README.md#pictures)). It's then marked the same way,
"Damaged", for as long as the screen shows (a file changed since is tried again), and OK on it
says at once that the file isn't a picture or is damaged. Add all passes the marked ones by,
counted among those not added; one not tried yet is refused as it's added, and counted the same.
In place of a preview that isn't drawn, or can't be, a warning mark shows, so nobody waits for
one. Anything else that isn't a picture isn't listed. The folders are read again each time the screen
shows, so a USB drive plugged in meanwhile counts. On a screen too short for the preview, Add all
and the note, the preview gets smaller (`PreviewColumn`); where it would be shorter than a row of
the list (64 dp: the smallest screens, large text, a long translation), the preview goes, with
its label, and none is drawn: Add all and the note are the column. Where
even they don't fit, the column scrolls, to its end while Add all has the focus, so the note
shows too. The 64 dp were chosen from the preview's heights measured on screens 360 to 540 dp
tall in each language, with normal and large text: the room left to it depends as much on the
text as on the screen.

The list scrolls a row ahead of the selection, as the editor's does. Down past its last picture
leaves the focus there, as in the editor's list (`ListFrame`), not on Add all; Right goes to it.

**Add all**, below the preview, a tonal button, adds every picture of the list that isn't marked, in the list's
order, one at a time: a session of `ReceivedPictures` of the screen's own, not the process's,
held only while it adds, since the screen stops the adding when it closes anyway. A window
(`WorkingDialog`) says "Adding picture 2 of 5…", with Cancel, focused, which Back does too. Then a
message says how many were added ("3 pictures added", or "Pictures not added"), and a line for each
reason some weren't, as after a share (`notAddedLines`), the marked ones counted too, with OK. OK,
or Back, returns the pictures added to the background editor, which focuses the first of them in
its list and chooses none: the user chooses one there. With none added, the screen stays. Cancel
before any was added closes the window, and the screen stays; after, the message says how it went,
those cancelled counted. Closing the screen while it adds (Home) stops the adding. While no picture
of the list can be added, Add all is dimmed, and the focus passes it by.

To try it on an emulator, put a picture in the debug build's drop folder (from Android 16 on
Luncher can't read a file adb put there,
[`platform/README.md`](../../platform/README.md#the-drop-folders), until it's made the file's
owner, below; or copy it there with a file manager on the emulator, or share one):

```bash
adb push holiday.jpg /sdcard/Android/media/com.luncher.launcher.debug/
```

From Android 16 on, the media store can make Luncher the owner of what adb put there:

```bash
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
  -d file:///sdcard/Android/media/com.luncher.launcher.debug/holiday.jpg
adb shell "content update --uri content://media/external/file \
  --bind owner_package_name:s:com.luncher.launcher.debug \
  --where \"_data LIKE '%/Android/media/com.luncher.launcher.debug/%'\""
```

To try a share, install the instrumented tests' APK too (`./gradlew installDebug
installDebugAndroidTest`): its providers serve the sample pictures, and a picture that never
comes ([`INSTRUMENTED-TESTS.md`](../../docs/INSTRUMENTED-TESTS.md#pictures-from-another-app-and-the-tests-own-folders)).
`-f 0x10000000` sends it to a task of its own, as an app does from a notification:

```bash
adb shell am start -a android.intent.action.SEND -t image/jpeg --grant-read-uri-permission -f 0x10000000 \
  --eu android.intent.extra.STREAM content://com.luncher.launcher.debug.test.samples/progressive.jpg \
  -n com.luncher.launcher.debug/com.luncher.launcher.settings.SharePictureActivity
# a picture that never comes (Cancel; or "can't read it" after 15 s): …debug.test.stalled/silent/cloud.jpg
# a folder, as a file manager shares one ("Nothing to add"): -t '*/*', …debug.test.samples/Holidays
# nothing shared ("Nothing to add"): the same without --eu
```

`am start` can't send a list of Uris, so several pictures are shared through the test APK's
`ShareSender`, which sends them as a gallery does, with the access to each; `…test.granted/` serves
the same samples, readable only through that access:

```bash
adb shell am start -n com.luncher.launcher.debug.test/com.luncher.launcher.testing.ShareSender \
  --esa com.luncher.launcher.testing.PICTURES content://com.luncher.launcher.debug.test.granted/progressive.jpg,content://com.luncher.launcher.debug.test.granted/baseline.jpg \
  --ecn com.luncher.launcher.testing.TARGET com.luncher.launcher.debug/com.luncher.launcher.settings.SharePictureActivity
```

### What keeps the picture library light

- **Nothing runs while it's hidden.** The library is followed from `onStart` to `onStop`, and the
  thumbnails and previews being drawn are dropped in `onStop`.
- **Pictures are decoded at the size shown**, one at a time on the background thread, and freed
  once drawn ([`platform/README.md`](../../platform/README.md#pictures)): a 12 MP photo is never
  held whole.
- **A picture is decoded once for the screens**, into its working copy, as it's added; thumbnails
  and previews are drawn from that, and only Save decodes the original again. A file the library
  has already, under any name, isn't decoded at all, and renaming a picture decodes nothing.
- **A thumbnail is drawn once per screen size**, then read from its file, one per picture.
- **A decode nobody waits for stops**, the preview of a picture no longer selected, or of a
  screen that closed, at its next read, so the next job doesn't wait for it.
- **Add from folder draws ahead only while it's idle**, below everything else, and stops at once
  for the picture the user selects; its previews are files of the screen's life, deleted as it
  closes.
- **A share's pictures are added one at a time**, each read as a stream into its file: the session
  keeps only their Uris and answers, at most 100, and stops when its window closes.
- **Add all adds one at a time too**, the previews stopped meanwhile so the adding has the jobs'
  thread, and stops when its screen closes.
