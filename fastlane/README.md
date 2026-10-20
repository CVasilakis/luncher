# fastlane/

Luncher's store listing: its name, descriptions, the changes of each version, and its images.
F-Droid shows it on Luncher's page, reading it from the repository at each release's tag; Google
Play takes the same files, uploaded by hand or with fastlane's `supply`. The root
[`README.md`](../README.md) shows the same screenshots too, under a wider cut of the feature
graphic ([below](#icon-banner-and-feature-graphic)). Nothing
here is part of the build.

```
metadata/android/en-US/
  title.txt                     the app's name
  short_description.txt         one line, at most 80 characters
  full_description.txt          at most 4000 characters
  changelogs/<versionCode>.txt  what's new in that version, at most 500 characters
  images/icon.png               the app's icon, 512 × 512
  images/tvBanner.png           the TV banner, 1280 × 720
  images/featureGraphic.png     the header of the store page, 1024 × 500
  images/tvScreenshots/         1920 × 1080 screenshots, shown in the order of their names
```

## Rules

- **The path is fixed:** `fastlane/metadata/android/<language>/` at the root of the repository.
  F-Droid looks for it in three places only: the root, the folder its build starts from (`app/`,
  kept for the app itself), and a build flavor's source folder; fastlane's `supply` looks at the
  root by default. Other languages go next to `en-US/`, e.g. `el-GR/`.
- **One listing for every store,** so its text doesn't name a store. Text that one store needs and
  the others don't would go outside `metadata/android/`.
- **A changelog for each release,** named after its `versionCode` and written in the commit that
  raises the version ([`docs/RELEASING.md`](../docs/RELEASING.md#making-a-release)): F-Droid takes
  it from the release's tag.
- **The descriptions and screenshots show the latest release:** checked, and updated where the
  release changes what they say, before each release
  ([`docs/RELEASING.md`](../docs/RELEASING.md#making-a-release)), for the same reason.
- **Plain text,** one paragraph per line: both stores keep the line breaks, and Play shows no
  HTML beyond a few tags.
- **Images without transparency:** Play asks for the feature graphic, the TV banner and the
  screenshots without an alpha channel, and for the icon with one, fully opaque.

## Icon, banner and feature graphic

The same drawings as the app's own: the icon is the adaptive app icon's visible part, square
(stores round the corners themselves), the TV banner is the app's banner at 4×, and the feature
graphic is the banner's TV and name on a wider ground
([`app/README.md`](../app/README.md#icon-and-banner)). Their desserts come from AOSP artwork, so
they are under the Apache License 2.0, not Luncher's MIT license, like the app's icon and banner,
and listed in its `NOTICE` ([`app/README.md`](../app/README.md#license)).

Rendered from SVGs of the same drawings with librsvg
(`gdk-pixbuf-thumbnailer -s <width> in.svg out.png`), then without the alpha channel like the
screenshots below, except the icon, whose alpha channel is made opaque instead:

```bash
convert icon.png -alpha opaque -strip -define png:color-type=6 icon.png
```

The root README's banner, [`docs/images/readme-banner.png`](../docs/images/readme-banner.png), is
the feature graphic's drawing cut to 4:1, so it doesn't take up the page: the SVG's view box
narrowed to 1280 × 320 around the TV and the name, the blue ground widened to fill it, rendered
at 2560 × 640 for sharp high-density screens. It's derived from AOSP artwork too, and listed in
the `NOTICE`.

## Screenshots

Taken on the `gtv_api34` emulator (Google TV) in English, of a release build
(`assembleRelease`, signed with the debug key to install it), which carries Luncher's own name (a
debug build is labeled "Luncher (debug)"), with the debug build uninstalled so that it doesn't
appear among the apps. Not on `gtv_api36`: from Android 16 on, Luncher can't read a picture adb
copied into its drop folder, so Add from folder would show none. Luncher's appearance settings are
at their defaults, but for the background: a calm gradient made for the screenshots, a PNG with an
ordered dither, so it shows no bands and the screenshots stay small (random grain made them 2 MB
each). The emulator's own apps are turned off (`pm disable-user`), and nine others installed
so that they fill the default layout (five in the bottom row, four above), ordered so that light
and dark banners alternate: ERTFLIX, Jellyfin, Kodi, Launcher Manager, Material Files, NewPipe,
Next Player, RadioDroid and Tailscale. Their banners belong to their owners. The library's photos
are the author's own.

1. The home screen.
2. Arrange mode, holding an app.
3. The settings panel.
4. Appearance, with Tiles open.
5. Background, the gradient chosen, the photos listed below it.
6. Add from folder.

`screencap` saves PNGs with an alpha channel, which `convert` (ImageMagick) removes:

```bash
adb exec-out screencap -p > shot.png
convert shot.png -alpha off -strip -define png:color-type=2 1.png
```
