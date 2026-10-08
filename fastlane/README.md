# fastlane/

Luncher's store listing: its name, descriptions, the changes of each version, and its images.
F-Droid shows it on Luncher's page, reading it from the repository at each release's tag; Google
Play takes the same files, uploaded by hand or with fastlane's `supply`. The root
[`README.md`](../README.md) shows the feature graphic and the first four screenshots too. Nothing
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

## Screenshots

Taken on the `tv_api36` emulator in English, of a release build, which carries Luncher's own name
(a debug build is labeled "Luncher (debug)"), with the debug build disabled so that it doesn't
appear among the apps. The apps shown are the emulator's own; their banners belong to their
owners.

1. The home screen.
2. Arrange mode, holding an app.
3. Arrange mode, the app moved onto the hidden shelf.
4. The settings panel.
5. Hide apps, with that app hidden.

`screencap` saves PNGs with an alpha channel, which `convert` (ImageMagick) removes:

```bash
adb exec-out screencap -p > shot.png
convert shot.png -alpha off -strip -define png:color-type=2 1.png
```
