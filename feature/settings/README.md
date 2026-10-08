# feature/settings/

The settings panel, `:feature:settings`, and the panels it opens: Hide apps. What a feature module
holds and may use: [`../README.md`](../README.md); the rules it follows:
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
    │   │   └── HideAppsActivity.kt   the Hide apps list: every app by name; OK hides or shows one
    │   └── res/                  the panels' themes (Theme.Luncher.Settings, .Panel), window and
    │                             focused entry, layouts, settings_* colors, dimensions and strings
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
| No `screenOrientation` | Android 8.0 (API 26) refuses one on a floating activity; it shows over the landscape home screen anyway. |

## The settings panel

Its entries are Hide apps ([below](#hidden-apps)) and the device's own settings.
`SettingsActivity` is a floating window over the dimmed home screen (its theme,
`Theme.Luncher.Settings`, is a platform dialog theme). Back closes it, as any activity, and so does
Home: the home screen is `singleTask`, and Android closes what's above it in its task. The home
screen's focus is where it was.

| Part | Job |
|---|---|
| `settingsMenu` (`:domain`) | which entries the panel lists: tabs of groups of entries, in order |
| `SettingsEntry` (`:domain`) | the kinds of entry, one type each |
| `SettingsActivity` | shows a tab: its groups one below the other, with a gap between them; each entry's label (`label`) and what OK on it does (`open`) |

The panel shows the first tab. The tab strip to pick another one, and group titles, are built
with the first tab or group that needs them. To add an entry:

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
panel. The home screen shows the change when it comes back, since it reads the apps then anyway.

A panel of its own is another activity in this module, started from the settings panel with
`openOwnPanel`. The settings panel's window stays behind it, invisible (its alpha is 0 until it
resumes), since a smaller panel would show it around its edges; the dimming of the home screen is
that window's too, which is why the other panel's theme, `Theme.Luncher.Settings.Panel`, dims
nothing itself.

| Part | Job |
|---|---|
| `AppArrangement` (`:domain`) | what's stored: the order of the shown apps (none until the user reorders, which means by name) and the hidden apps in their order |
| `AppArrangements` (`:domain`) / `PreferencesAppArrangements` (`:platform`) | port and adapter: reads the arrangement once per process, and saves each change at once, in the background |
| `homeApps` (`:domain`) | matches the stored apps to the installed ones: an update that renamed an app's activity keeps its place and hidden state, and apps that aren't installed right now keep theirs for when they come back |
| `ArrangedApps` (`:domain`) | the shown and hidden apps: hiding one puts it first among the hidden apps, showing one puts it last in the user's order (or in its place by name while there's none), and `byLabel` is the list's content |
| `HideAppsActivity` | shows that list, stores each change |

The list is a platform `ListView`: it creates views only for the rows on screen, and reuses them
while scrolling. It shows at most six and a half rows, so the half row says there's more; on a
small screen (e.g. 720p at the 1080p density, 360 dp tall) as many as leave the panel a margin
from the screen's edges, still ending on half a row.
