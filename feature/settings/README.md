# feature/settings/

The settings panel, `:feature:settings`, and the screens it opens: Hide apps, the appearance
editor and the picture library (Pictures, choosing the wallpaper in it, Add from folder); and the
way in for a picture another app shares. What a feature module holds and may use: [`../README.md`](../README.md); the rules it follows:
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
    │   │   ├── PicturesActivity.kt     Pictures: the grid, a picture's actions, Delete unused; adds a picture it's given
    │   │   ├── ChooseWallpaperActivity.kt  Pictures choosing the wallpaper, for the appearance editor
    │   │   ├── AddFromFolderActivity.kt  the drop folders' pictures, a preview of the selected one; OK returns it
    │   │   ├── SharePictureActivity.kt   where another app's share arrives: passes the picture to Pictures, no screen
    │   │   ├── PicturesDialog.kt the dialogs over Pictures: a picture's actions, a question, a message
    │   │   ├── PreviewColumn.kt  Add from folder's preview and note, the preview smaller on a short screen
    │   │   └── PictureTexts.kt   what the picture screens call things: sizes, names, why a picture wasn't added
    │   └── res/                  the themes (Theme.Luncher.Settings, .Panel, .FullScreen, .Pictures, .Pictures.Dialog,
    │                             .Pictures.NoDisplay), layouts, the picture tiles' frames, settings_* dimensions,
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
| The panels and the editor: `exported="false"`, `launchMode="singleTop"` | Only Luncher opens them; a repeated OK or Menu press doesn't stack a second panel. |
| `HideAppsActivity`: `Theme.Luncher.Settings.Panel` | Opens in the settings panel's place, which already dims the home screen ([below](#hidden-apps)). |
| `AppearanceActivity`: `Theme.Luncher.Settings.FullScreen` | Full-screen and opaque: it covers the home screen whole, so the home screen is stopped meanwhile and applies the changes once, when it comes back ([`ARCHITECTURE.md`](../../docs/ARCHITECTURE.md#appearance), point 5). |
| No `screenOrientation` | Android 8.0 (API 26) refuses one on a floating activity; it shows over the landscape home screen anyway, as the editor does. |
| `PicturesActivity`, `ChooseWallpaperActivity`, `AddFromFolderActivity`: `Theme.Luncher.Settings.Pictures` | Full-screen and opaque, on the home screen's background, like the appearance editor. |
| `SharePictureActivity`: `exported="true"` | Other apps' shares arrive there. |
| `SharePictureActivity`: `Theme.Luncher.Settings.Pictures.NoDisplay`, `noHistory`, `excludeFromRecents` | It shows nothing, and finishes in `onCreate`, before `onResume`, as a `windowNoDisplay` activity must; its backgrounds are still the home screen's dark ones, in case a device shows one ([`ui/README.md`](../../ui/README.md#the-theme)). |
| `supportsRtl="true"` | As the app declares it ([why](../../app/README.md#manifest-why-each-part-is-there)), so the JVM tests mirror the screens in a right-to-left language as the app does. |

The intent filters that make `SharePictureActivity` a target of other apps' shares are the app's
to declare ([`app/README.md`](../../app/README.md#manifest-why-each-part-is-there)); the module
declares how the activities behave, and the build merges the two.

## The settings panel

Its entries are Hide apps ([below](#hidden-apps)), Appearance ([below](#appearance)), Pictures
(the picture library, [below](#pictures), full-screen like the editor)
and the device's own settings. On a screen too short for them all (720p at the 1080p density),
the entries scroll, as many showing as leave the panel a margin from the screen's edges, ending
on half an entry, as Hide apps' list does.
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
side with the time and the date hidden; their order with either hidden; the background's options
without a picture, its position with the picture stretched) is dimmed, and the focus passes it by.
The Background group starts with its picture, which isn't an option: its row shows the picture's
name, or None, and OK on it opens the picture library to choose one
([`ChooseWallpaperActivity`](#pictures)), which stores
the choice; back in the editor (`onResume`), the appearance is read again. The last entry, Reset
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
| `AppearanceActivity` | the groups' entries, opening one at a time; the options' rows, what each option and value is called (exhaustive `when`s, so a new option doesn't compile until it has its labels), the keys, the background's picture, and the reset entry |
| `Pictures`, `PictureImages` (`:domain`) | the wallpaper's name, and its picture drawn at the preview's size |
| `AppearancePreviewView` | draws the preview: an illustration, not the real home screen (below) |

**The preview** draws outlines of tiles on the home screen's background (the wallpaper's picture,
drawn at the sample screen's size in the background's style, or the plain color), the first one
framed and zoomed as focused, and plain blocks for the time, the date and the settings gear: no
names, images or text. It's a made-up sample, not the user's apps or the TV's shape: a 16:9 screen of 960 by 540
dp with seven tiles, a full row and a short one, so that every option's values look different. It
lays them out with `TileLayouts` and `topBarLayout` at its own density (its pixels per sample dp),
so every dp value scales with it and it can't disagree with the home screen. It isn't focusable.

To add an option: [`ARCHITECTURE.md`](../../docs/ARCHITECTURE.md#appearance), "Adding an aspect";
its labels go in `label` and `valueLabel`, in every translation.

What keeps it light: the preview draws from shapes it keeps, made anew only when the part of the
appearance they come from changes (compared with `==`), so a new corner radius lays out nothing,
and drawing allocates nothing. The wallpaper's picture is drawn in the background, the latest
request winning, read at once if it was drawn at the preview's size before, and dropped when the
editor stops. Only the options' rows are views; the preview is one view. Nothing of the home
screen runs meanwhile.

### Pictures

Pictures (`PicturesActivity`) is Luncher's own picture library, opened from the settings panel's
Pictures entry: a grid of thumbnails, the newest first, as many a row as fit the width, ending in
an Add from folder tile, with Delete unused below; each picture says what it's used for (the
wallpaper), or that nothing uses it. The whole screen scrolls as the focus moves, so a short screen
shows as much as it can. OK on a picture opens its actions: Use as wallpaper (unless it is), which
keeps how the wallpaper is drawn, and Delete. Deleting asks first, Cancel focused, so a repeated OK
deletes nothing, and names what the picture is used for, which goes back to its default (the
wallpaper to none, `withoutPictures`). Delete unused asks too, saying how much room the pictures
take, and keeps those in use. While the library is empty, the screen says how to add a picture: a
share from another app, or a drop folder ([`platform/README.md`](../../platform/README.md#the-drop-folders)),
with its path. Back closes it, back to the settings panel. In a right-to-left language it mirrors
like the settings panels; the pictures don't.

| Part | Job |
|---|---|
| `Picture`, `PictureLibrary` (`:domain`) | a picture of the library; the library in the screen's order, the newest first, the room it takes, the pictures nothing uses |
| `Pictures`, `PictureImport` (`:domain`) / `FilesPictures` (`:platform`) | the library, which the screen follows while it shows, and adding to it |
| `PictureImages` (`:domain`) / `CachedPictureImages` (`:platform`) | the thumbnails, drawn at the tile's size in pixels |
| `pictureUses`, `withoutPictures` (`:domain`), `AppearanceSettings` | what each picture is used for, from the appearance, which the screen follows too; the uses a deletion resets |
| `PicturesActivity` | the grid's tiles, the focus, a picture's actions and the questions; adds the picture it's opened with, or the one Add from folder returns, and selects it, or says why it wasn't added (`ImportResult`, an exhaustive `when` in `whyNotAdded`) |

**Choosing the wallpaper** (`ChooseWallpaperActivity`, Pictures opened from the appearance editor's
Background group): the title says so, a first tile, No picture, chooses none, and OK on a picture
makes it the wallpaper; either closes the screen. Delete unused isn't offered there.

All thumbnails are read at once when the screen shows, from what `CachedPictureImages` kept, on the
main thread, so the grid shows them with its first frame; one not kept (a picture just added, a
screen of another size) is drawn in the background, a `LatestRequest` each, and shows when it's
done. The screen keeps each picture's tile while the picture is in the library, so its thumbnail is
read or drawn once, and drops the requests in `onStop`.

### Adding a picture

**From another app** (`SharePictureActivity`): a file manager's or a gallery's Share, or "Set as",
of a picture (`SEND` or `ATTACH_DATA`, `image/*`). It takes the picture's Uri out of the intent and
passes it, with the read access the other app gave, to Pictures, and finishes at once; Pictures
adds it, saying so below the title meanwhile, and selects it, or says why it couldn't. A picture
the library has already (the same content) is selected, and nothing is said. An intent with no
picture it can pass on (none in it, or one the other app gave no access to, which Android won't
let Luncher pass on) becomes the message that Luncher can't read it, never a crash. What the Uri
points to is the library's to judge ([`platform/README.md`](../../platform/README.md#pictures)): a
`file://` one, as Android up to 9 shares files, gets the message that says to use a drop folder
instead, with its path.

**From a drop folder** (`AddFromFolderActivity`): the pictures a file manager or a computer put in
`Android/media/<applicationId>`, on the TV or a USB drive, listed the TV's first, then the drives',
each by name (`inListOrder`), with a preview of the selected one beside them, drawn in the
background. OK returns the picture to Pictures, which adds it as a shared one. A file in a format
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

- **Nothing runs while it's hidden.** The library and the appearance are followed from `onStart`
  to `onStop`, and the thumbnails and previews being drawn are dropped in `onStop`.
- **Pictures are decoded at the size shown**, one at a time on the background thread, and freed
  once drawn ([`platform/README.md`](../../platform/README.md#pictures)): a 12 MP photo is never
  held whole.
- **A thumbnail is drawn once per screen size**, then read from its file.
