# fastlane/

Luncher's store listing: its name, descriptions, the changes of each version, and its images.
F-Droid shows it on Luncher's page, reading it from the repository at each release's tag; Google
Play takes the same files, uploaded by hand or with fastlane's `supply`. Nothing here is part of
the build.

```
metadata/android/en-US/
  title.txt                     the app's name
  short_description.txt         one line, at most 80 characters
  full_description.txt          at most 4000 characters
  changelogs/<versionCode>.txt  what's new in that version, at most 500 characters
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
- **Images without transparency:** Play rejects PNGs with an alpha channel.

The icon (`images/icon.png`, 512 × 512), feature graphic (`images/featureGraphic.png`,
1024 × 500) and TV banner (`images/tvBanner.png`, 1280 × 720) come with the chosen logo.

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
