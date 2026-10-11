# feature/settings/

The settings panel, `:feature:settings`, and the panels it opens: Hide apps and the appearance
editor. What a feature module holds and may use: [`../README.md`](../README.md); the rules it follows:
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
    │   ├── AndroidManifest.xml   the panels' activities (below)
    │   ├── java/com/luncher/launcher/settings/
    │   │   ├── SettingsGraph.kt  the ports the settings screens need; Activity.graph
    │   │   ├── SettingsActivity.kt   the settings panel: lists the domain's settingsMenu, opens entries
    │   │   ├── HideAppsActivity.kt   the Hide apps list: every app by name; OK hides or shows one
    │   │   ├── AppearanceActivity.kt the appearance editor: the options, Left and Right step them
    │   │   └── AppearancePreviewView.kt  the editor's preview: an illustration of the home screen
    │   └── res/                  the panels' themes (Theme.Luncher.Settings, .Panel, .FullScreen), window and
    │                             focused entry, layouts, settings_* colors, dimensions and strings,
    │                             their translations in values-el/ and values-ar/ (docs/TRANSLATIONS.md)
    └── test/
        ├── java/…/settings/      JVM tests; TestSettingsGraph.kt: the ports a test gives the screens,
        │                         and the tests' Application (named in resources/robolectric.properties)
        └── screenshots/settings/ reference images, committed
```

Everything but the activities and `SettingsGraph` is `internal`.

## Manifest

| Element | Reason (don't remove without replacing it) |
|---|---|
| `exported="false"`, `launchMode="singleTop"` | Only Luncher opens them; a repeated OK or Menu press doesn't stack a second panel. |
| `HideAppsActivity`: `Theme.Luncher.Settings.Panel` | Opens in the settings panel's place, which already dims the home screen ([below](#hidden-apps)). |
| `AppearanceActivity`: `Theme.Luncher.Settings.FullScreen` | Full-screen and opaque: it covers the home screen whole, so the home screen is stopped meanwhile and applies the changes once, when it comes back ([`ARCHITECTURE.md`](../../docs/ARCHITECTURE.md#appearance), point 5). |
| No `screenOrientation` | Android 8.0 (API 26) refuses one on a floating activity; it shows over the landscape home screen anyway, as the editor does. |
| `supportsRtl="true"` | As the app declares it ([why](../../app/README.md#manifest-why-each-part-is-there)), so the JVM tests mirror the panels in a right-to-left language as the app does. |

## The settings panel

Its entries are Hide apps ([below](#hidden-apps)), Appearance ([below](#appearance)) and the
device's own settings.
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
it's started, as it stays behind the floating panels.

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
| `HideAppsActivity` | shows that list, stores each change |

The list is a platform `ListView`: it creates views only for the rows on screen, and reuses them
while scrolling. It shows at most six and a half rows, so the half row says there's more; on a
small screen (e.g. 720p at the 1080p density, 360 dp tall) as many as leave the panel a margin
from the screen's edges, still ending on half a row.

### Appearance

Appearance (`AppearanceActivity`) is the editor of the home screen's
[appearance](../../docs/ARCHITECTURE.md#appearance): full-screen, with its title right above a
preview of the home screen at the left, and the options at the right. The options are in groups
(the tiles', the top bar's), each an entry of its own, closed at first: OK on one shows its options
below it and closes the group that was open, so the list stays short; OK again closes it. Each
option shows its value between arrows; Left and Right step it through its values, around from the
last to the first, and each change is stored at once, the preview following. An option that
changes nothing with the others as they are (a full bottom row with the tiles at the top) is
dimmed, and the focus passes it by. The last entry, Reset to defaults, puts every option back.
Back returns to the settings panel; the home screen shows the result when it comes back.

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
| `AppearanceActivity` | the groups' entries, opening one at a time; the options' rows, what each option and value is called (exhaustive `when`s, so a new option doesn't compile until it has its labels), the keys, and the reset entry |
| `AppearancePreviewView` | draws the preview: an illustration, not the real home screen (below) |

**The preview** draws outlines of tiles on the home screen's background, the first one framed and
zoomed as focused, and plain blocks for the time, the date and the settings gear: no names, images
or text. It's a made-up sample, not the user's apps or the TV's shape: a 16:9 screen of 960 by 540
dp with seven tiles, a full row and a short one, so that every option's values look different. It
lays them out with `TileLayouts` and `topBarLayout` at its own density (its pixels per sample dp),
so every dp value scales with it and it can't disagree with the home screen. It isn't focusable.

To add an option: [`ARCHITECTURE.md`](../../docs/ARCHITECTURE.md#appearance), "Adding an aspect";
its labels go in `label` and `valueLabel`, in every translation.

What keeps it light: the preview draws from shapes it keeps, made anew only when the part of the
appearance they come from changes (compared with `==`), so a new corner radius lays out nothing,
and drawing allocates nothing. Only the options' rows are views; the preview is one view. Nothing
of the home screen runs meanwhile.
