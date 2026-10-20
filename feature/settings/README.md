# feature/settings/

The settings panel, `:feature:settings`, and the screens it opens: Hide apps, the appearance
editor, and from it the background editor, where the wallpaper is chosen from Luncher's picture
library, with Add from folder; and the way in for the pictures another app shares. What a feature
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
    │   │   ├── SettingsActivity.kt   the settings panel: lists the domain's settingsMenu, opens entries
    │   │   ├── HideAppsActivity.kt   the Hide apps list: every app by name; OK hides or shows one
    │   │   ├── PanelHeight.kt    a panel's list as tall as the screen leaves room for, ending on half a row
    │   │   ├── AppearanceActivity.kt the appearance editor: the options, Left and Right step them
    │   │   ├── AppearancePreviewView.kt  the editor's preview: an illustration of the home screen
    │   │   ├── AppearanceEditorLayout.kt the editor's columns: the options level with the title
    │   │   ├── OptionRow.kt      an option's row: both editors'; Left and Right step it, or OK opens its list
    │   │   ├── OptionList.kt     an option's values in a list over its row: the background editor's
    │   │   ├── OptionLabels.kt   what the options, their values and groups are called
    │   │   ├── BackgroundActivity.kt the background editor: the pictures, the draft, Save; renames and deletes pictures, prepares them
    │   │   ├── BackgroundStartColumn.kt  its preview, options, Save and Delete all, the preview as large as the room left
    │   │   ├── EntriesRow.kt     entries side by side, or one below the other when one doesn't fit: Save and Delete all
    │   │   ├── AddFromFolderActivity.kt  the drop folders' pictures, a preview of the selected one; OK adds it, then returns it; Add all
    │   │   ├── SharePictureActivity.kt   where another app's share arrives: opens the home screen and the window, no screen
    │   │   ├── PictureReceivedActivity.kt the window over the home screen after a share: shows the pictures being added, then their uses, or how many were saved and why the others weren't
    │   │   ├── PicturesDialog.kt the dialogs over the picture screens: a picture's actions, a question, a message; renaming one
    │   │   ├── PreviewColumn.kt  Add from folder's preview, Add all and note, the preview smaller on a short screen
    │   │   └── PictureTexts.kt   what the picture screens call things: sizes, names, why a picture wasn't added
    │   └── res/                  the themes (Theme.Luncher.Settings, .Panel, .FullScreen, .Pictures, .Pictures.Dialog,
    │                             .Pictures.NoDisplay), layouts, the thumbnails' frames, the warning mark, settings_* dimensions,
    │                             colors and strings (the window and the focused entry are :ui's), their
    │                             translations in values-el/ and values-ar/ (docs/TRANSLATIONS.md)
    └── test/
        ├── java/…/settings/      JVM tests; TestSettingsGraph.kt: the ports a test gives the screens,
        │                         and the tests' Application (named in resources/robolectric.properties);
        │                         TestPicturesGraph.kt: the same for the picture screens, an empty library
        └── screenshots/          reference images, committed: settings/, pictures/
```

Everything but the activities and `SettingsGraph` is `internal`.

## Manifest

| Element | Reason (don't remove without replacing it) |
|---|---|
| Every activity but `SharePictureActivity`: `exported="false"` | Only Luncher opens them. |
| The panels and the two editors: `launchMode="singleTop"` | A repeated OK or Menu press doesn't stack a second one. |
| `HideAppsActivity`: `Theme.Luncher.Settings.Panel` | Opens in the settings panel's place, which already dims the home screen ([below](#hidden-apps)). |
| `AppearanceActivity`: `Theme.Luncher.Settings.FullScreen` | Full-screen and opaque: it covers the home screen whole, so the home screen is stopped meanwhile and applies the changes once, when it comes back ([`ARCHITECTURE.md`](../../docs/ARCHITECTURE.md#appearance), point 5). |
| No `screenOrientation` | Android 8.0 (API 26) refuses one on a floating activity; it shows over the landscape home screen anyway, as the editor does. |
| `BackgroundActivity`: `Theme.Luncher.Settings.FullScreen` | Full-screen and opaque, as the appearance editor, which opens it. |
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

## The settings panel

Its entries are Hide apps ([below](#hidden-apps)), Appearance ([below](#appearance)) and the
device's own settings. On a screen too short for them all, the entries scroll, as many showing as
leave the panel a margin from the screen's edges, ending on half an entry, as Hide apps' list
does; the three of them fit on every TV screen the layout tests check.
`SettingsActivity` is a floating window over the dimmed home screen (its theme,
`Theme.Luncher.Settings`, is a platform dialog theme). Back closes it, as any activity, and so does
Home: the home screen is `singleTask`, and Android closes what's above it in its task. The home
screen's focus is where it was.

| Part | Job |
|---|---|
| `settingsMenu` (`:domain`) | which entries the panel lists, in order |
| `SettingsEntry` (`:domain`) | the kinds of entry, one type each |
| `SettingsActivity` | shows the entries one below the other, all equally far apart; each entry's label (`label`) and what OK on it does (`open`) |

An entry with settings of its own
opens them in a panel of its own ([below](#hidden-apps)) rather than in more of this one. To add
an entry:

1. A `SettingsEntry` type in `:domain`, placed by `settingsMenu`, with its unit test.
2. Its label and action in `SettingsActivity`: `label` and `open` are exhaustive `when`s, so this
   module doesn't compile until both handle the new type.
3. A value the entry changes (e.g. whether the date shows) is read and stored through a port with
   an adapter, like any data from the device ([`ARCHITECTURE.md`](../../docs/ARCHITECTURE.md#where-things-go)).

What keeps it light: its code runs, and its window exists, only while it's open. Behind it the home
screen stays started (the clock keeps running) and, as after any other activity, reads the apps
again when the panel closes, keeping its tiles when nothing changed.

### Hidden apps

Hide apps (`HideAppsActivity`) is a panel of its own, opened in the settings panel's place: it
lists every app by name; OK on one hides it or shows it again, and Back returns to the settings
panel. The home screen behind shows each change at once: it follows the stored arrangement while
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
| `homeApps` (`:domain`) | matches the stored apps to the installed ones: an update that renamed an app's activity keeps its place and hidden state, and apps that aren't installed right now keep theirs for when they come back |
| `ArrangedApps` (`:domain`) | the shown and hidden apps: hiding one puts it first among the hidden apps, showing one puts it last in the user's order (or in its place by name while there's none), and `byLabel` is the list's content |
| `HideAppsActivity` | shows that list, stores each change; reads the apps again when they change while it shows, and when it comes back |

The list is a platform `ListView`: it creates views only for the rows on screen, and reuses them
while scrolling. It shows at most six and a half rows, so the half row says there's more; on a
small screen (e.g. 720p at the 1080p density, 360 dp tall) as many as leave the panel a margin
from the screen's edges, still ending on half a row.

### Appearance

Appearance (`AppearanceActivity`) is the editor of the home screen's
[appearance](../../docs/ARCHITECTURE.md#appearance): full-screen, with its large title right above a
preview of the home screen at the left, the two centered in the height, and the options at the
right, starting level with the title. The options are in groups
(Tiles, Time and date, Background), each an entry of its own, closed at first: OK on one shows its options
below it and closes the group that was open, so the list stays short; OK again closes it. Each
option shows its value between arrows; Left and Right step it through its values, around from the
last to the first, and each change is stored at once, the preview following. An option that
changes nothing with the others as they are (a full bottom row with the rows at the top; the
side with the time and the date hidden; their order with either hidden), or with what else
decides it (the background's picture's shape, in the background editor:
[below](#background)), is dimmed, and the focus passes it by.
The Background group is edited apart (`editedApart`): it's one entry, the picture's name, or
None, beside it, and OK on it opens the background editor ([below](#background)), where the
picture and its options are saved together; back in the editor (`onResume`), the appearance and
its picture's name (renamed there, say) are read again. The last entry, Reset
to defaults, puts every option back, and the wallpaper to none. Back returns to the settings
panel; the home screen shows the result when it comes back.

It's started with `openOwnPanel`, like Hide apps, but covers the settings panel and the home screen
whole (its theme). In a right-to-left language it mirrors like the other panels: the options at
the left, each value at the left of its name, and Left steps toward the arrow on that side, the
next value. The preview's content doesn't: it shows the home screen, which stays left to right
([why](../../app/README.md#manifest-why-each-part-is-there)). The options take at most
`settings_appearance_options_width`, and half of a narrow screen, so the preview keeps the rest;
they scroll when they're taller than the screen.

| Part | Job |
|---|---|
| `appearanceOptions`, `AppearanceOption` (`:domain`) | which options there are, in which groups; each one's values, in order, the part of `Appearance` it reads and changes, whether it applies, and what a step does, with the values an `OptionContext` offers (all of them here) |
| `AppearanceSettings` (`:domain`) / `PreferencesAppearanceSettings` (`:platform`) | port and adapter: the stored `Appearance`, read once per process; each write is stored at once, in the background, and told to the listeners (the home screen, while it's shown) |
| `TileLayouts`, `topBarLayout` (`:domain`) | where the preview's tiles and top bar items go, by the home screen's own rules |
| `AppearanceActivity` | the groups' entries, opening one at a time; a group edited apart as one entry; the options' rows (`OptionRow`, `OptionLabels.kt`'s exhaustive `when`s, so a new option doesn't compile until it has its labels) and the reset entry |
| `Pictures`, `PictureImages` (`:domain`) | the wallpaper's name, and its picture drawn at the preview's size, from its working copy |
| `AppearancePreviewView` | draws the preview: an illustration, not the real home screen (below) |

**The preview** draws outlined tiles filled with white at 20 % (`settings_preview_tile`, in place of
the banners) on the home screen's background (the wallpaper's picture, drawn at the sample screen's
size in the background's style, or the plain color), the first one framed and zoomed as focused, and
plain blocks for the time, the date and the settings gear: no names, images or text. It's a made-up
sample, not the user's apps: a screen 960 dp wide, of the TV's shape (`screenSize`, which the
wallpaper is drawn at: 540 dp tall on a 16:9 TV), so a picture is cut as on the home screen, with
seven tiles, a full row and a short one, so that every option's values look different. The width
stays on every shape, so a row holds as many tiles. It lays them out with `TileLayouts` and
`topBarLayout` at its own density (its pixels per sample dp), so every dp value scales with it and
it can't disagree with the home screen. What's on the wallpaper has the home screen's edge
(`WallpaperOutline`, [`feature/home/README.md`](../home/README.md#the-top-bar)), so the illustration
reads on a white picture too: the blocks and the tiles' outlines, at the sample's scale but a pixel
at least (there the tiles' fill doesn't show, their outlines do); not the focus frame, as on the
home screen. A new shape gets it too: `AppearancePreviewViewTest` fails for a light shape without
it. It isn't focusable.

To add an option: [`ARCHITECTURE.md`](../../docs/ARCHITECTURE.md#appearance), "Adding an aspect";
its labels go in `optionLabel` and `optionValue` (`OptionLabels.kt`), in every translation.

What keeps it light: the preview draws from shapes it keeps, made anew only when the part of the
appearance they come from changes (compared with `==`), so a new corner radius lays out nothing,
and drawing allocates nothing. The wallpaper's picture is drawn in the background from its
working copy ([below](#background)), in milliseconds, the latest request winning, and dropped
when the editor stops. Only the options' rows are views; the preview is one view. Nothing of the home
screen runs meanwhile.

### Background

The background editor (`BackgroundActivity`) is where the wallpaper is chosen and saved, opened
from the appearance editor's Background entry, full-screen like it, or from the window after a
share, with that picture chosen ([below](#adding-a-picture)). At the end, Luncher's picture
library as a list: No picture, the pictures, the newest first, each with its thumbnail and name,
then Add from folder ([below](#adding-a-picture)); a hint above them says a long press of OK
renames or deletes a picture, as arrange mode's says what its keys do. While the library is empty, how to add
a picture follows: a share from another app, or the drop folder
([`platform/README.md`](../../platform/README.md#the-drop-folders)), with its path. At the start, a
preview of the home screen with the chosen background, the background's options (Fit, Position,
Dim, Blur, rows like the appearance editor's), and Save with Delete all beside it, in red, half
the row each, or Delete all below Save when either doesn't fit its half (`EntriesRow`: a long
translation on a narrow screen). There's no title: the preview gets that room.
The preview is as large as the room the others leave (`BackgroundStartColumn`), at most as tall
as its screen at the column's width, at least a quarter of that, so Save and Delete all show without scrolling on a 1080p screen;
on the smallest screens the column scrolls. In a right-to-left language it mirrors like the other
settings screens; the preview's content and the pictures don't.

What the user changes is a draft, the one exception to the appearance editor's rule of storing each
change at once ([`ARCHITECTURE.md`](../../docs/ARCHITECTURE.md#appearance)): OK on a picture
chooses it (its name in the accent color, a check beside it), and the preview follows; nothing is
stored. An option's row shows its value with a mark pointing down, not between arrows: OK on it
opens its values in a list (`OptionList`, a `PopupWindow`) over the row's value, the value it has
focused and lined up with the row, in the accent color; the list's end at the row's end, moved
up or down only to stay inside the overscan margin. The preview shows each value the focus comes
to; OK keeps it, and Back closes the list (not the editor) with the value from before. Left and
Right on the rows don't change
anything: they move the focus, as on the other entries, so Right goes to the pictures beside the
options (the row level with the option). The appearance editor keeps its arrows: nothing is beside
its options. The options offer only what moves the chosen picture on this screen, by how filling
the screen cuts it (`pictureCut`, from the picture's size and `screenSize`, the wallpaper's): the
position's list holds Center, Left and Right for a picture wider than the screen, Center, Top and
Bottom for a taller one. All four are dimmed without a
picture; the position also with the picture stretched; the fit and the position also with a
picture of the screen's shape, or so near it that filling would cut less than 1% (a 16:9
picture on a 1366 × 768 TV), where every choice looks the same. A stretch stored for such a
picture is kept, dimmed. The draft never holds a position its picture doesn't offer: opening
the editor (the stored background, a picture just received, a draft kept), choosing another
picture, or deleting the draft's makes it Center, so the next picture starts there too. Back, or Home, leaves the background as it was. **Save** stores the draft,
through the process's `Wallpaper` ([`feature/home/README.md`](../home/README.md#the-wallpaper)):
- unchanged, the editor just closes: a stored position the draft shows as Center counts as
  unchanged, as it draws the same;
- without a picture, it's stored at once, the kept wallpaper deleted, and the editor closes;
- with one, a window (`WorkingDialog`, from `:ui`) says "Processing background…" at once, taking every
  key, Back too, while the picture is drawn at the screen's size and kept; only then is the
  background stored, and the window closes, after half a second at least, and the editor with
  it. If the user goes home meanwhile, the drawing goes on, and the home screen says so instead.

A **long press of OK** on a picture offers Rename and Delete, under its name and size.

**Rename** asks for the new name in a field holding the old one, the TV's keyboard up, the cursor
at the end (where the keyboard puts it anyway). Rename below it, or the keyboard's Done, keeps the
name as a picture's is kept (`pictureName`: no control characters, trimmed, cut in the middle past
255 units); a name another picture has, or nothing typed, is refused below the field in the accent
color, the dialog staying open (`renaming`), as two pictures of one name would be told apart by
nothing. A name is the library's index only: the picture's file, working copy and thumbnail, the
stored wallpaper and the background setting go by its id, from its content, so nothing is copied,
decoded or drawn again, and the row keeps its place (the list goes by when a picture was added)
and its thumbnail. The appearance editor shows the new name beside Background when it comes back.

**Delete** asks first, Cancel focused, so a repeated OK deletes nothing, saying what deleting it
does, in one text for any picture and another for the background's: the picture goes from
Luncher's pictures only, and the background's picture leaves the home screen with no picture.
That happens at once (`Wallpaper.forget`): the background stored without a picture, then the kept
wallpaper deleted, then the picture. A draft's picture deleted is no picture in the draft.

**Delete all**, beside Save, deletes every picture of the library the same way, after the same
kind of question, Cancel focused, which says how many, and, when one is the background's, that the
home screen will have no picture. The editor stays, the focus on Save. While the library is empty
it's dimmed, and the focus passes it by.

| Part | Job |
|---|---|
| `Picture`, `PictureLibrary` (`:domain`) | a picture of the library; the library in the list's order, the newest first, and what each one is used for |
| `Pictures`, `PictureImport` (`:domain`) / `FilesPictures` (`:platform`) | the library, which the screen follows while it shows, renaming and deleting, and adding to it |
| `renaming` (`:domain`) | the name a picture renamed keeps, or why it can't: none typed, or another picture's |
| `PreparedPictures`, `PictureImages` (`:domain`) / `WorkingCopyImages` (`:platform`) | each picture's working copy, made once; the preview's wallpaper drawn from it at each value tried |
| `PictureImages` (`:domain`) / `CachedPictureImages` (`:platform`) | the thumbnails, drawn from the working copies at the row's size in pixels, and kept |
| `pictureUses`, `withoutPictures` (`:domain`) | what each picture is used for, which deleting it says, and the background without it |
| `Wallpaper` (`:domain`) | Save (`save`: drawn, kept, then stored) and deleting the wallpaper's picture (`forget`) |
| `pictureCut`, `OptionContext`, `Background.offeredIn` (`:domain`) | what filling the screen cuts from the draft's picture, the options' values that offers, and the draft without a position it doesn't |
| `OptionRow`, `OptionList` | the options' rows, OK opening a list of the values the draft's picture offers, which the preview follows as the focus moves; Back in it puts the value from before back |
| `PicturesDialog`, `RenameDialog` | the dialogs over the list: a picture's Rename and Delete, the question before deleting; the field for a new name, and why it's refused |
| `BackgroundActivity` | the list, the draft, Save and its window, Delete all, the questions; chooses and focuses the picture Add from folder added (`EXTRA_ADDED`), or focuses the first in the list of those Add all added, choosing none (`EXTRA_ADDED_ALL`); starts with a picture just received chosen (`EXTRA_PICTURE`) |

**Prepared once, then at once.** Each picture of the library has a working copy
([`platform/README.md`](../../platform/README.md#pictures)): the whole picture decoded once, as
it's added, covering half the screen, from which its thumbnail and the preview at every value of
an option the user tries are drawn in milliseconds, where the photo itself takes a decode each
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
read or drawn once, and drops the requests in `onStop`. A draft survives a configuration change
(a TV switching resolution), kept in the instance state.

### Adding a picture

**From another app** (`SharePictureActivity`): a file manager's or a gallery's Share of one
picture or several, or "Set as" of one (`SEND`, `SEND_MULTIPLE` or `ATTACH_DATA`, `image/*`). It
takes the pictures' Uris out of the intent (a Share's stream, one or a list, else every item of
its clip, as an app may send them only there; each once), then opens, in one `startActivities`,
Luncher's home screen and over it a window (`PictureReceivedActivity`) with the Uris and the read
access the other app gave, and finishes at once. Back from the window is the home screen.

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

The window, floating like the settings panel, shows the pictures being added, one at a time: the
process's `ReceivedPictures`, a session that the window owns. While they're added, it says "Adding
the picture…", or "Adding picture 2 of 5…", on one line, and Cancel, focused; after a spinning
wheel once a picture is copied (`ReceivedPictures.copying`, which `PictureImport` reports as the
copying starts): the real work, which then shows half a second at least (`ShortestShowing`, from
`:ui`), so it never flashes, and stays until the end, not on and off between pictures. A picture
refused for what its app says of its name or size, before anything is copied, shows no wheel: why
is said at once. Then, for one picture, "Picture added", its name and an entry for each use a picture can
be put to (`pictureUseMenu`, today Background, which opens the background editor with the picture
chosen, not saved, and closes the window); or, with OK, "Picture not added" and why
(`whyNotAdded`). A picture the library has already, by its content or as the same file (the same
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

The read access passed to the window is only the access Luncher was given (a grant, which goes
in the intent's clip): any other Uri goes without one, for the library to judge, as an exported
provider's may be read anyway, so passing it on never fails. An intent with no picture in it
becomes the window's message that Luncher can't read it, never a crash; so does one the other app
gave no access to, once the library has tried it. What the Uri points to is the library's to
judge ([`platform/README.md`](../../platform/README.md#pictures)): a `file://` one, as Android up
to 9 shares files, gets the message that says to use a drop folder instead, with its path.

| Part | Job |
|---|---|
| `SharePictureActivity` | takes the pictures out of the share, refuses more than the limit, passes the Uris and the access Luncher holds to the window; the window alone while one shows, else a new one that takes an open session over |
| `PictureReceivedActivity` | shows the session: the count while the pictures are added, then one picture's result and uses, or the counts; ends the session when it closes |
| `ReceivedPictures` (`:domain`) | the session, one per process (Add all, below, has one of its own): the pictures added one at a time through `PictureImport`, each once, a share joining it, the window that holds it and may end it, whether one is being copied (`copying`), adding stopped with the session still open (`stop`), and how it went (`Outcome`: each picture's answer, and how many were cancelled) |
| `PictureLimits.MAX_SHARED` (`:domain`) | the most pictures one share brings |
| `PictureImport` (`:domain`) / `FilesPictures` (`:platform`) | adding one picture to the library, cancellable, when its copying starts, and how it went (`ImportResult`) |
| `pictureUseMenu` (`:domain`) | the uses a picture received alone offers |

**From a drop folder** (`AddFromFolderActivity`): the pictures a file manager or a computer put in
`Android/media/<applicationId>`, on the TV or a USB drive, listed the TV's first, then the drives',
each by name (`inListOrder`), with a preview of the selected one beside them, of the TV's shape
as the wallpaper fills it, drawn in the background, a spinning wheel in its place meanwhile;
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
after its size, why in the accent color so it stands out, and OK on it says why at once, reading
nothing: a file in a format this TV can't show ([which](../../platform/README.md#pictures)), one
over 50 MB or over 64 megapixels (`PictureLimits`), which have no preview either, and one named as
a picture of the library, the same file (the same name and size: "Already in Luncher's pictures")
or another. The listing goes by names: a file whose picture was renamed in the library isn't
marked, and OK finds it there by its content, without decoding it. In place of a preview that isn't drawn, or that can't be (a damaged picture, once its
decode fails), a warning mark shows, so nobody waits for one. Anything else that isn't a picture isn't listed. The folders are read again each time the screen
shows, so a USB drive plugged in meanwhile counts. On a screen too short for the preview, Add all
and the note, the preview gets smaller (`PreviewColumn`), down to a quarter of its height; on the
smallest screens the column then scrolls, to its end while Add all has the focus, so the note
shows too.

**Add all**, below the preview, adds every picture of the list that isn't marked, in the list's
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

To try it on an emulator, put a picture in the debug build's drop folder, up to API 34 (from
Android 16 on Luncher can't read a file adb put there either,
[`platform/README.md`](../../platform/README.md#the-drop-folders); share one instead):

```bash
adb push holiday.jpg /sdcard/Android/media/com.luncher.launcher.debug/
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
