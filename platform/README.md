# platform/

The adapters, `:platform`: the domain's ports implemented on Android APIs (PackageManager,
SharedPreferences, the system clock and its broadcasts, files), and the images the screens show. Only
`:app` depends on this module; `AppGraph` there creates the adapters, and the features see the
ports only, so a screen can't call an Android data API or create an adapter itself. Why, and the
rules adapters follow: [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md).

| | |
|---|---|
| Namespace | `com.luncher.launcher.platform` (its R class); the code is in a package per `:domain` topic |
| Depends on | `:domain`, `:ui` (the colors an image is drawn in) |

## Layout

```
platform/
├── build.gradle.kts              luncher.android.library; its dependencies
└── src/
    ├── main/
    │   ├── AndroidManifest.xml   package visibility (below)
    │   ├── java/com/luncher/launcher/
    │   │   ├── appearance/
    │   │   │   └── PreferencesAppearanceSettings.kt AppearanceSettings on SharedPreferences: a key per option, and its stored form; the wallpaper's picture
    │   │   ├── apps/
    │   │   │   ├── PackageManagerInstalledApps.kt   InstalledApps on PackageManager, and LauncherApps for when apps change
    │   │   │   ├── PreferencesAppArrangements.kt    AppArrangements on SharedPreferences: the file and its format
    │   │   │   ├── BannerImages.kt                  AppImages<Bitmap>: draws an app's Banner into a bitmap of the tile's size
    │   │   │   └── CachedBannerImages.kt            AppImages<Bitmap> in front of BannerImages: each tile's image kept in a file for the next start
    │   │   ├── clock/
    │   │   │   └── AndroidClock.kt                  Clock on the system time, settings and time broadcasts
    │   │   ├── images/
    │   │   │   └── PixelFiles.kt                    the image caches' file format: a header, then the pixels, compressed
    │   │   ├── pictures/
    │   │   │   ├── FilesPictures.kt                 Pictures and PictureImport<Uri> on files: the library's pictures and their index
    │   │   │   ├── MediaFolderPictures.kt           FolderPictures<Uri, Bitmap> in the drop folders, Android/media/<applicationId>
    │   │   │   ├── FilePictureImages.kt             PictureImages<Bitmap>: draws a picture of the library, framed, at a size
    │   │   │   ├── CachedPictureImages.kt           PictureImages<Bitmap> in front of FilePictureImages: what it drew kept in files
    │   │   │   ├── PictureFiles.kt                  reading a picture file: its size, upright, its format; drawing it framed in ARGB_8888
    │   │   │   └── JpegOrientation.kt               a JPEG's orientation tag, read without ExifInterface
    │   │   └── jobs/
    │   │       └── BackgroundThreadJobs.kt          Jobs on one thread at background priority, with a priority queue
    │   └── res/values/colors.xml the card drawn for an app without a banner
    └── test/java/…/              JVM tests (Robolectric), same packages as the code
```

Packages follow `:domain`'s topics (`appearance/`, `apps/`, `clock/`, `jobs/`, `pictures/`, and later e.g. `network/`),
so the adapter of `com.luncher.domain.clock.Clock` is in `clock/`. What two topics' adapters share
is in a package of its own (`images/`: the caches' file format).

## Writing adapters

- **One port each,** named after how it provides it (`PreferencesAppArrangements`), where the
  port is named after what it provides (`AppArrangements`). An image a screen shows is a port too,
  generic in the image type (`AppImages<Bitmap>`), since `:domain` can't name an Android type.
- **Storage formats stay here:** file names, keys and encodings (rule 6 of
  [`ARCHITECTURE.md`](../docs/ARCHITECTURE.md#rules)). A test of the adapter writes the stored
  format out, so a change that breaks what a TV already stored fails
  (`PreferencesAppArrangementsTest`). A value stored by another version that the adapter doesn't
  know reads as a default rather than failing (`PreferencesAppearanceSettings`: that option's
  default, the others as stored).
- **Device state that changes** (the time, later the network) is watched only while a listener is
  registered ([`ARCHITECTURE.md`](../docs/ARCHITECTURE.md#background-work-and-changes-while-shown)):
  `AndroidClock` registers its broadcast receiver with the first listener and unregisters it with
  the last.
- **Another app's resources can fail** (uninstalled a moment ago, broken): an adapter returns what
  it can (`BannerImages` draws a card) rather than crashing the home screen.
- **An image file is decoded by the adapter, and freed once drawn.** The bitmap in a `Drawable`
  from Android may be shared through Android's drawable cache, so it can't be freed, and from API
  33 on it stays in memory until a garbage collection, which an idle home screen doesn't get: with
  200 apps on API 36, 260 MB instead of 78 MB. `BannerImages` decodes a banner file at the file's
  own size, draws it scaled to the tile and recycles it; only a banner that isn't an image file (a
  vector, a color) or is a nine-patch goes through a `Drawable`.
- **A cache's format can change freely.** `CachedBannerImages` keeps each tile's image in
  `cacheDir`, one file per app, so a cold start reads it rather than drawing it, which takes a
  large part of a start with many apps. A file that doesn't match (another format version, size,
  install of the app or of Luncher, label) is drawn and written again, so unlike a stored setting
  it needs no test of its format and no migration, and Android may delete it. Writing waits until
  the main thread is idle, so it doesn't slow the start that draws, and is then a job per image.
  `CachedPictureImages` keeps the pictures' images the same way, in the same file format
  (`PixelFiles`), but draws them in jobs, so it writes each in the job that drew it.
- **Public,** since `AppGraph` in `:app` creates them; no other module depends on this one.

## Pictures

The picture library ([`feature/pictures/README.md`](../feature/pictures/README.md)) is three
adapters on files, and a cache:

| Adapter | Port | What it keeps, and how |
|---|---|---|
| `FilesPictures` | `Pictures`, `PictureImport<Uri>` | each picture's original file, unchanged, in `filesDir/pictures/`, named after a hash of its content, so the same picture added twice is stored once; and `index.json`, the pictures' names, sizes as shown and when they were added, written whole to a new file that then replaces the old one. Its format is kept across updates, so `FilesPicturesTest` writes it out. |
| `MediaFolderPictures` | `FolderPictures<Uri, Bitmap>` | nothing: it lists the drop folders (below) |
| `FilePictureImages` | `PictureImages<Bitmap>` | nothing: it draws a picture of the library at a size, framed by the domain's `frame`, blurred and dimmed as its `PictureStyle` says |
| `CachedPictureImages` | `PictureImages<Bitmap>` | what `FilePictureImages` drew, in `cacheDir/pictures/<picture>/`, one file per size, its style in the header: the thumbnails at the grid's tile size in pixels, the wallpaper at the screen's, the editor's preview at its own |

- **Adding a picture is a job,** of `JobPriority.PICTURE_LIBRARY`, as is everything that changes
  the library's files (moving a new picture's file into place, deleting, writing the index), so
  they run in the order they came, and a picture's file is in place before the library names it.
  Adding streams the source into a new file, stopping past the size limit, hashing it on the way;
  decodes its size and then all of it, small (`inSampleSize`), since a size alone can decode where
  the picture can't (AVIF on API 31); and then, on the main thread, either finds it in the index
  already or has it moved into place and named. Once per process, a job deletes the files and
  drawn images the index doesn't name, which a killed process can leave; an index that can't be
  read keeps every file.
- **What another app gives is read, never trusted.** A source is opened through `ContentResolver`
  as a stream, its name kept as a label only, never as a path. A `file://` Uri is refused
  (`Unreadable`): only an app with storage permission could read it, as older Android versions
  share files, and Luncher asks for none ([`app/README.md`](../app/README.md#manifest-why-each-part-is-there)).
  The one exception is a file of Luncher's own drop folders, which Add from folder passes.
- **A picture is drawn into ARGB_8888, at the size it's shown, upright.** `PictureFiles.draw`
  decodes it at no more than the size needed (`inSampleSize`), into sRGB from API 26 on (an iPhone's
  photo is in Display P3), turns it as its orientation tag says, which BitmapFactory doesn't, and
  draws the framed part into a bitmap of exactly that size, over the home screen's background
  color (for a transparent picture); then frees the decoded one. A decoder may give another config
  (16-bit PNG: RGBA_F16 from API 26 on; GIF: none up to 26), which the drawing turns into ARGB_8888.
- **Blur and dim** are drawn into the picture's bitmap, after its framing: blurred by drawing it at
  an eighth of its size, blurring that (a box blur, twice each way), and drawing it back up with
  filtering, so it costs little at any screen size; dimmed by a black fill of the dim's share.
- **The orientation tag** of a JPEG is read by `jpegOrientation`, not `android.media.ExifInterface`,
  whose native code up to Android 6 has known security bugs, while any app can share a crafted
  file. A HEIF photo's is read by `ExifInterface`, which reads HEIF from API 28 on, the first
  version that decodes it, and is written in Java from API 24 on.
- **A format a TV can't decode is named,** from the file's header (`pictureFormatOf`, in
  `:domain`), never its name: HEIF before API 28, AVIF before API 33. Anything else that doesn't
  decode is `NotAPicture`.

### The drop folders

`Android/media/<applicationId>` on every mounted volume, the TV's storage and each USB drive or SD
card (`Context.getExternalMediaDirs()`, which creates them): a file manager on the TV, or a computer
writing a USB drive, puts pictures there, and Luncher reads them without any permission. Not
`Android/data/<applicationId>`, which file managers can't write from Android 11 on. From Android
16 on, a file another app put there can't be read; `MediaFolderPictures` lists only what it can
read, so there a picture comes in through a share.

## Manifest

| Element | Reason (don't remove without replacing it) |
|---|---|
| `<queries>` for `MAIN`+`LEANBACK_LAUNCHER` | Package visibility (targetSdk 30+): without it `PackageManagerInstalledApps` can't see other apps. Only TV apps: Luncher lists and draws nothing else, and starting an app needs no visibility, so a query for phone apps (`MAIN`+`LAUNCHER`) would only widen what it can see. |
