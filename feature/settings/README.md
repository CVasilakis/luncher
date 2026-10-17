# feature/settings/

The settings panel, `:feature:settings`, and the screens it opens: Hide apps, the appearance
editor, and from it the background editor, where the wallpaper is chosen from Luncher's picture
library, with Add from folder; and the way in for a picture another app shares. What a feature
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
    │   ├── AndroidManifest.xml   the screens' activities, and how they behave (below)
    │   ├── java/com/luncher/launcher/settings/
    │   │   ├── SettingsGraph.kt  the ports the settings screens need; Activity.graph
    │   │   ├── SettingsActivity.kt   the settings panel: lists the domain's settingsMenu, opens entries
    │   │   ├── HideAppsActivity.kt   the Hide apps list: every app by name; OK hides or shows one
    │   │   ├── PanelHeight.kt    a panel's list as tall as the screen leaves room for, ending on half a row
    │   │   ├── AppearanceActivity.kt the appearance editor: the options, Left and Right step them
    │   │   ├── AppearancePreviewView.kt  the editor's preview: an illustration of the home screen
    │   │   ├── AppearanceEditorLayout.kt the editor's columns: the options level with the title
    │   │   ├── OptionRow.kt      an option's row, Left and Right stepping it: both editors'
    │   │   ├── OptionLabels.kt   what the options, their values and groups are called
    │   │   ├── BackgroundActivity.kt the background editor: the pictures, the draft, Save; deletes and adds pictures
    │   │   ├── BackgroundStartColumn.kt  its preview, options and Save, the preview as large as the room left
    │   │   ├── AddFromFolderActivity.kt  the drop folders' pictures, a preview of the selected one; OK returns it
    │   │   ├── SharePictureActivity.kt   where another app's share arrives: opens the home screen and the window, no screen
    │   │   ├── PictureReceivedActivity.kt the window over the home screen after a share: adds the picture, offers its uses
    │   │   ├── PicturesDialog.kt the dialogs over the picture screens: a question, a message
    │   │   ├── PreviewColumn.kt  Add from folder's preview and note, the preview smaller on a short screen
    │   │   └── PictureTexts.kt   what the picture screens call things: sizes, names, why a picture wasn't added
    │   └── res/                  the themes (Theme.Luncher.Settings, .Panel, .FullScreen, .Pictures, .Pictures.Dialog,
    │                             .Pictures.NoDisplay), layouts, the thumbnails' frames, settings_* dimensions,
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
| `PictureReceivedActivity`: `Theme.Luncher.Settings` | A floating window over the dimmed home screen, like the settings panel. |
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
(Tiles, Status bar, Background), each an entry of its own, closed at first: OK on one shows its options
below it and closes the group that was open, so the list stays short; OK again closes it. Each
option shows its value between arrows; Left and Right step it through its values, around from the
last to the first, and each change is stored at once, the preview following. An option that
changes nothing with the others as they are (a full bottom row with the rows at the top; the
side with the time and the date hidden; their order with either hidden) is dimmed, and the focus
passes it by.
The Background group is edited apart (`editedApart`): it's one entry, the picture's name, or
None, beside it, and OK on it opens the background editor ([below](#background)), where the
picture and its options are saved together; back in the editor (`onResume`), the appearance is
read again. The last entry, Reset
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
| `appearanceOptions`, `AppearanceOption` (`:domain`) | which options there are, in which groups; each one's values, in order, the part of `Appearance` it reads and changes, whether it applies, and what a step does |
| `AppearanceSettings` (`:domain`) / `PreferencesAppearanceSettings` (`:platform`) | port and adapter: the stored `Appearance`, read once per process; each write is stored at once, in the background, and told to the listeners (the home screen, while it's shown) |
| `TileLayouts`, `topBarLayout` (`:domain`) | where the preview's tiles and top bar items go, by the home screen's own rules |
| `AppearanceActivity` | the groups' entries, opening one at a time; a group edited apart as one entry; the options' rows (`OptionRow`, `OptionLabels.kt`'s exhaustive `when`s, so a new option doesn't compile until it has its labels) and the reset entry |
| `Pictures`, `PictureImages` (`:domain`) | the wallpaper's name, and its picture drawn at the preview's size |
| `AppearancePreviewView` | draws the preview: an illustration, not the real home screen (below) |

**The preview** draws outlines of tiles on the home screen's background (the wallpaper's picture,
drawn at the sample screen's size in the background's style, or the plain color), the first one
framed and zoomed as focused, and plain blocks for the time, the date and the settings gear: no
names, images or text. It's a made-up sample, not the user's apps: a screen 960 dp wide, of the
TV's shape (`screenSize`, which the wallpaper is drawn at: 540 dp tall on a 16:9 TV), so a picture
is cut as on the home screen, with seven tiles, a full row and a short one, so that every option's
values look different. The width stays on every shape, so a row holds as many tiles. It
lays them out with `TileLayouts` and `topBarLayout` at its own density (its pixels per sample dp),
so every dp value scales with it and it can't disagree with the home screen. It isn't focusable.

To add an option: [`ARCHITECTURE.md`](../../docs/ARCHITECTURE.md#appearance), "Adding an aspect";
its labels go in `optionLabel` and `optionValue` (`OptionLabels.kt`), in every translation.

What keeps it light: the preview draws from shapes it keeps, made anew only when the part of the
appearance they come from changes (compared with `==`), so a new corner radius lays out nothing,
and drawing allocates nothing. The wallpaper's picture is drawn in the background, the latest
request winning, read at once if it was drawn at the preview's size before, and dropped when the
editor stops. Only the options' rows are views; the preview is one view. Nothing of the home
screen runs meanwhile.

### Background

The background editor (`BackgroundActivity`) is where the wallpaper is chosen and saved, opened
from the appearance editor's Background entry, full-screen like it, or from the window after a
share, with that picture chosen ([below](#adding-a-picture)). At the end, Luncher's picture
library as a list: No picture, the pictures, the newest first, each with its thumbnail and name,
then Add from folder ([below](#adding-a-picture)); a hint above them says a long press of OK
deletes a picture, as arrange mode's says what its keys do, and "Adding a picture…" while Add
from folder's picture is added. While the library is empty, how to add
a picture follows: a share from another app, or the drop folder
([`platform/README.md`](../../platform/README.md#the-drop-folders)), with its path. At the start, a
preview of the home screen with the chosen background, the background's options (Fit, Position,
Dim, Blur, as the appearance editor's rows) and Save. There's no title: the preview gets that room.
The preview is as large as the room the others leave (`BackgroundStartColumn`), at most as tall
as its screen at the column's width, at least a quarter of that, so Save shows without scrolling on a 1080p screen;
on the smallest screens the column scrolls. In a right-to-left language it mirrors like the other
settings screens; the preview's content and the pictures don't.

What the user changes is a draft, the one exception to the appearance editor's rule of storing each
change at once ([`ARCHITECTURE.md`](../../docs/ARCHITECTURE.md#appearance)): OK on a picture
chooses it (its name in the accent color, a check beside it), Left and Right step the options,
dimmed without a picture (the position also with the picture stretched), and the preview follows;
nothing is stored. Back, or Home, leaves the background as it was. **Save** stores the draft,
through the process's `Wallpaper` ([`feature/home/README.md`](../home/README.md#the-wallpaper)):
- unchanged, the editor just closes;
- without a picture, it's stored at once, the kept wallpaper deleted, and the editor closes;
- with one, a window (`WorkingDialog`, from `:ui`) says "Applying effects…" at once, taking every
  key, Back too, while the picture is drawn at the screen's size and kept; only then is the
  background stored, and the window closes, after half a second at least, and the editor with
  it. If the user goes home meanwhile, the drawing goes on, and the home screen says so instead.

A **long press of OK** on a picture asks to delete it, Cancel focused, so a repeated OK deletes
nothing, saying what deleting it does: Luncher's copy goes, the file it came from stays, and the
wallpaper's picture leaves the home screen with its plain background. That happens at once
(`Wallpaper.forget`): the background stored without a picture, then the kept wallpaper deleted,
then the picture. A draft's picture deleted is no picture in the draft.

| Part | Job |
|---|---|
| `Picture`, `PictureLibrary` (`:domain`) | a picture of the library; the library in the list's order, the newest first, and what each one is used for |
| `Pictures`, `PictureImport` (`:domain`) / `FilesPictures` (`:platform`) | the library, which the screen follows while it shows, and adding to it |
| `PictureImages` (`:domain`) / `CachedPictureImages` (`:platform`) | the thumbnails, drawn at the row's size in pixels, and the preview's wallpaper |
| `pictureUses`, `withoutPictures` (`:domain`) | what each picture is used for, which deleting it says, and the background without it |
| `Wallpaper` (`:domain`) | Save (`save`: drawn, kept, then stored) and deleting the wallpaper's picture (`forget`) |
| `BackgroundActivity` | the list, the draft, Save and its window, the questions; adds the picture Add from folder returns, and chooses it, or says why it wasn't added (`ImportResult`, an exhaustive `when` in `whyNotAdded`); starts with a picture just received chosen (`EXTRA_PICTURE`) |

All thumbnails are read at once when the screen shows, from what `CachedPictureImages` kept, on the
main thread, so the list shows them with its first frame; one not kept (a picture just added, a
screen of another size) is drawn in the background, a `LatestRequest` each, and shows when it's
done. The screen keeps each picture's row while the picture is in the library, so its thumbnail is
read or drawn once, and drops the requests in `onStop`. A draft survives a configuration change
(a TV switching resolution), kept in the instance state.

### Adding a picture

**From another app** (`SharePictureActivity`): a file manager's or a gallery's Share, or "Set as",
of a picture (`SEND` or `ATTACH_DATA`, `image/*`). It takes the picture's Uri out of the intent,
then opens, in one `startActivities`, Luncher's home screen and over it a window
(`PictureReceivedActivity`) with the Uri and the read access the other app gave, and finishes at
once. The home screen is opened as the TV's app list opens Luncher, with the intent Android gives
for its `LEANBACK_LAUNCHER` entry (`getLeanbackLaunchIntentForPackage`), so it's Luncher's
whether or not it's the device's home app, and this feature needs nothing of `:feature:home`'s;
both open in the home screen's task, so Back from the window is the home screen. Not a `HOME`
intent, which makes the task a home screen's: once closed, Android shows the device's home app,
and up to Android 5.1, with no default, asks which one.

The window, floating like the settings panel, says "Adding the picture…" after a spinning wheel,
on one line, and Cancel, focused, while the library adds it, then "Picture saved", the picture's name and an
entry for each use a picture can be put to (`pictureUseMenu` in `:domain`, today Background, which
opens the background editor with the picture chosen, not saved, and closes the window); or why it
wasn't added, and OK. Back closes it, the picture in the library. While it's being added, Cancel
and Back close the window and stop the adding, the picture not added; closing the window any
way stops it (`onDestroy`), so no answer comes to a window that's gone. An app that stops sending
the picture gets the message that Luncher can't read it, after the library's time limit
([`platform/README.md`](../../platform/README.md#another-apps-answers)). A picture the library has already (the
same content) is saved as well. An intent with no picture it can pass on (none in it, or one the
other app gave no access to, which Android won't let Luncher pass on) becomes the window's
message that Luncher can't read it, never a crash. What the Uri
points to is the library's to judge ([`platform/README.md`](../../platform/README.md#pictures)): a
`file://` one, as Android up to 9 shares files, gets the message that says to use a drop folder
instead, with its path.

**From a drop folder** (`AddFromFolderActivity`): the pictures a file manager or a computer put in
`Android/media/<applicationId>`, on the TV or a USB drive, listed the TV's first, then the drives',
each by name (`inListOrder`), with a preview of the selected one beside them, of the TV's shape
as the wallpaper fills it, drawn in the background. OK returns the picture to the background editor, which adds it and chooses it. A file in a format
this TV can't show ([which](../../platform/README.md#pictures)) is listed, marked, and OK on it says so;
anything else that isn't a picture isn't listed. The folders are read again each time the screen
shows, so a USB drive plugged in meanwhile counts. On a screen too short for the preview and its
note, the preview gets smaller (`PreviewColumn`).

To try it on an emulator, put a picture in the debug build's drop folder, up to API 34 (from
Android 16 on Luncher can't read a file adb put there either,
[`platform/README.md`](../../platform/README.md#the-drop-folders); share one instead):

```bash
adb push holiday.jpg /sdcard/Android/media/com.luncher.launcher.debug/
```

### What keeps the picture library light

- **Nothing runs while it's hidden.** The library is followed from `onStart` to `onStop`, and the
  thumbnails and previews being drawn are dropped in `onStop`.
- **Pictures are decoded at the size shown**, one at a time on the background thread, and freed
  once drawn ([`platform/README.md`](../../platform/README.md#pictures)): a 12 MP photo is never
  held whole.
- **A thumbnail is drawn once per screen size**, then read from its file.
