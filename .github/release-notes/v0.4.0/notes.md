## What's new

### A new look for the menus and settings

Every menu and settings screen was redesigned, based on the guidelines of Google's Material 3 for TV, without importing any external libraries. Everything stays just plain Kotlin with custom views, and care is taken to ensure that performance and memory usage stay the same by efficiently implementing effects or avoiding the more expensive ones altogether. The result is a modern, consistent look and feel across all screens while the instant startup and existing functions remain unchanged. Here is a before-and-after comparison of the various screens:

### Before and after

| Screen | Before (v0.3.0) | After (v0.4.0) |
|---|---|---|
| Home screen | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/before/home.png) | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/after/home.png) |
| Hold OK on an app | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/before/move.png) | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/after/tile-menu.png) |
| Moving an app | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/before/move.png) | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/after/move.png) |
| Settings | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/before/settings.png) | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/after/settings.png) |
| Hide apps | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/before/hide-apps.png) | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/after/hide-apps.png) |
| Appearance: Tiles | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/before/appearance-tiles.png) | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/after/appearance-tiles.png) |
| Appearance: Time and date | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/before/appearance-time.png) | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/after/appearance-time.png) |
| Appearance: Theme | New | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/after/appearance-theme.png) |
| Light mode | New | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/after/appearance-light.png) |
| App names, lifted tile | New | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/after/home-names-lift.png) |
| Reset to defaults | Reset at once | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/after/reset-question.png) |
| Background | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/before/background.png) | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/after/background.png) |
| A background option | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/before/option-list.png) | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/after/option-list.png) |
| Hold OK on a picture | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/before/picture-actions.png) | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/after/picture-actions.png) |
| Rename a picture | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/before/rename-keyboard.png) | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/after/rename-keyboard.png) |
| Delete a picture | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/before/delete-question.png) | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/after/delete-question.png) |
| Delete all | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/before/delete-all.png) | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/after/delete-all.png) |
| Back with unsaved changes | Discarded at once | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/after/discard-question.png) |
| Add from folder | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/before/add-from-folder.png) | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/after/add-from-folder.png) |
| A shared picture added | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/before/share-saved.png) | ![](https://github.com/CVasilakis/luncher/raw/v0.4.0/.github/release-notes/v0.4.0/after/share-saved.png) |

### Other additions

- **Theme:** an accent color (8 to choose from) and Dark or Light theme for menus and settings.
- **App menu:** hold OK on an app to move or hide it, replacing arrange mode.
- **Focus frame:** turn it off to lift the selected app with a soft shadow instead.
- **App name:** show the app names below the selected tile or every tile.

## Changes

- Tiles have 16 dp corners by default (was 12 dp).
- Damaged or cut-short pictures are refused, and marked "Damaged" in Add from folder.

## Fixes

- The background editor keeps its focus when you come back to it.
- The editors' previews show the saved background at once.
- A shared folder, or a share without a picture, now says so.

## Thanks

Thanks to [@VadymMelnychuk](https://github.com/VadymMelnychuk) for the first outside contribution ([#2](https://github.com/CVasilakis/luncher/pull/2)): the lifted tile and the app names.
