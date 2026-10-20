# platform/

The adapters, `:platform`: the domain's ports implemented on Android APIs (PackageManager,
SharedPreferences, the system clock and its broadcasts, files), and the images the screens show.
Only `:app` depends on this module; `AppGraph` there creates the adapters, and the features see the
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
    │   │   │   └── PixelFiles.kt                    the image caches' and the kept wallpaper's file format: a header, then the pixels, compressed
    │   │   ├── pictures/
    │   │   │   ├── FilesPictures.kt                 Pictures and PictureImport<Uri> on files: the library's pictures and their index
    │   │   │   ├── MediaFolderPictures.kt           FolderPictures<Uri, Bitmap> in the drop folders, Android/media/<applicationId>
    │   │   │   ├── FilePictureImages.kt             PictureImages<Bitmap>: draws a picture of the library from its original at a size, framed, dimmed, blurred (the wallpaper)
    │   │   │   ├── WorkingCopies.kt                 the working copies: each picture decoded once, covering half the screen, kept as a JPEG
    │   │   │   ├── WorkingCopyImages.kt             PictureImages<Bitmap> and PreparedPictures on the working copies: what the screens show
    │   │   │   ├── CachedPictureImages.kt           PictureImages<Bitmap> in front of WorkingCopyImages: each picture's thumbnail kept in a file
    │   │   │   ├── PictureFiles.kt                  reading a picture file: its size, upright, its format; drawing it framed in ARGB_8888
    │   │   │   └── JpegOrientation.kt               a JPEG's orientation tag, read without ExifInterface
    │   │   ├── jobs/
    │   │   │   └── BackgroundThreadJobs.kt          Jobs on one thread at background priority, with a priority queue
    │   │   ├── outside/                             asking another app, which may never answer (below)
    │   │   │   ├── OutsideCalls.kt                  the calls that can't be stopped, each on a short-lived thread, at most 10 alive; the clock and the wait for data
    │   │   │   ├── OutsideReader.kt                 asks and reads a Uri another app answers for, giving up when stopped or after 15 s without an answer
    │   │   │   ├── Stop.kt                          tells a job that waits on another app to give up
    │   │   │   └── OutsideCallEnded.kt              the IOException of a wait that gave up, and why
    │   │   └── wallpaper/
    │   │       └── FileStoredWallpaper.kt           StoredWallpaper<Bitmap>: the one wallpaper kept, a file in the app's private files
    │   └── res/values/colors.xml the card drawn for an app without a banner
    └── test/java/…/              JVM tests (Robolectric), same packages as the code
```

Packages follow `:domain`'s topics (`appearance/`, `apps/`, `clock/`, `jobs/`, `pictures/`,
`wallpaper/`, and later e.g. `network/`), so the adapter of `com.luncher.domain.clock.Clock` is in `clock/`. What two topics' adapters share
is in a package of its own (`images/`: the caches' file format; `outside/`: asking another app).

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
  `CachedPictureImages` keeps the pictures' thumbnails the same way, one file per picture, in the
  same file format (`PixelFiles`), but draws them in jobs, so it writes each in the job that drew it.
- **Public,** since `AppGraph` in `:app` creates them; no other module depends on this one.

## Another app's answers

What another app answers may never come: a cloud app on a dead network, a broken one. So an
adapter that asks another app for something (today `FilesPictures`, for a shared picture) asks
through `outside/`, and its job never waits on that app for good
([`ARCHITECTURE.md`](../docs/ARCHITECTURE.md#background-work-and-changes-while-shown), point 6):

- **`OutsideReader` asks and reads, for one job,** and gives up once its `Stop` is set (the user
  cancelled) or the app has answered nothing for 15 s (`LIMIT_MS`). Each answer, the query's, the
  open's or a piece of data, starts that time again, so a slow transfer that goes on isn't cut.
  Giving up throws `OutsideCallEnded`, an `IOException`, so an adapter that handles a failed read
  handles it too.
- **Data is read only once `Os.poll` says some came,** waiting 250 ms at a time, and the job looks
  in between whether to give up. Closing the stream from another thread would be simpler, but it
  doesn't end a read that waits on API 22, 24 and 28 (it does on API 36, the only other level
  measured).
- **A query or an open can't be stopped once made:** the `CancellationSignal` reaches a query, but
  a read-only open goes through the provider's `openTypedAssetFile`, whose default drops it, so
  only a provider that overrides that one sees it. So `OutsideCalls` makes each on a
  short-lived thread of its own (`luncher-outside-N`), which the job waits for until it answers,
  the job is stopped or the time is up, and then leaves to its call. That thread ends when the app
  answers, closing what comes so late, or when the app's process dies, which makes the call
  throw; meanwhile it costs no CPU and some tens of KB, and nothing in Java can end it sooner.
- **At most 10 such threads are alive at once** (`OutsideCalls.MOST`), whoever started them, the
  one a job waits for and those left to their calls; past that a call fails at once. The bound
  guards against a leak, a caller that asks an app that hangs again and again, and isn't a
  setting to tune. A process of its own, which Luncher could kill with its threads, would cost a
  weak TV more than the threads do.
- **One `OutsideCalls` per process,** which `AppGraph` makes and gives each adapter that asks
  another app, so the bound holds for all of them. It also has the clock and the wait for data
  `OutsideReader` uses: a JVM test makes one with its own, as Robolectric's `Os.poll` doesn't
  wait and its clock stands still, and the time another app takes passes when the test says. In
  the tests, `AnotherApp` is another app's provider; it serves files, as Robolectric's pipes are
  files too, which a reader that catches up finds ended.

## Pictures

The picture library ([`feature/settings/README.md`](../feature/settings/README.md#background)) is
these adapters on files, and a cache of its thumbnails; the wallpaper drawn from it is kept by the
last one:

| Adapter | Port | What it keeps, and how |
|---|---|---|
| `FilesPictures` | `Pictures`, `PictureImport<Uri>` | each picture's original file, unchanged, in `filesDir/pictures/`, named after a hash of its content, so the same picture added twice is stored once; and `index.json`, the pictures' names, sizes as shown and when they were added, written whole to a new file that then replaces the old one. A name is in the index only: every file kept of a picture, here and by the others below, and the background setting, go by its id, so renaming one writes the index and nothing else. Its format is kept across updates, so `FilesPicturesTest` writes it out. |
| `MediaFolderPictures` | `FolderPictures<Uri, Bitmap>` | it lists the drop folders (below); and keeps the previews it draws while Add from folder shows, one file per picture in `cacheDir/folder-previews/` (`PixelFiles`, the picture, its size and the preview's size and framing in the header), deleted as the screen closes and as it opens anew |
| `FilePictureImages` | `PictureImages<Bitmap>` | nothing: it draws a picture of the library from its original at a size, framed by the domain's `frame`, blurred and dimmed as its `PictureStyle` says: the wallpaper, which is stored for good. `DRAWING` is the drawing's version, up by one with each change to it, so what the others kept the old way is drawn again. |
| `WorkingCopyImages` | `PictureImages<Bitmap>`, `PreparedPictures` | each picture's working copy (`WorkingCopies`), in `filesDir/picture-copies/<picture>`: the whole picture, upright, decoded once and scaled to cover half the screen (what the editors' previews show at most), as a JPEG of quality 92, a few hundred KB, after a header with `FilePictureImages.DRAWING` and the size it covers. The screens' thumbnails and previews are drawn from it, framed, blurred and dimmed in milliseconds at each step of an option, where the original takes a decode of the photo each time. One file per picture, replaced when the screen or the drawing changes; in `filesDir`, which Android doesn't delete, so it's made once. Without one that can be written (a full disk), a picture is drawn from its original. |
| `CachedPictureImages` | `PictureImages<Bitmap>` | each picture's thumbnail, as `WorkingCopyImages` drew it at the background editor's row size in pixels, in `cacheDir/thumbnails/<picture>`, its size, style and `FilePictureImages.DRAWING` in the header, so it's read at once with the screen's first frame. One file per picture: another size or style replaces it, so another TV leaves no file behind. |
| `FileStoredWallpaper` | `StoredWallpaper<Bitmap>` | the wallpaper, drawn at the screen's size, in `filesDir/wallpaper`, where Android doesn't delete it as it may a cache; in the `PixelFiles` format, what it was drawn from and `FilePictureImages.DRAWING` in the header, so one drawn by another version reads as none and is drawn again. One file only: a new one is written beside it, then replaces it in one step. |

- **Adding a picture is a job,** of `JobPriority.PICTURE_LIBRARY`, as is everything that changes
  the library's files (moving a new picture's file into place, deleting, writing the index), so
  they run in the order they came, and a picture's file is in place before the library names it.
  A picture named as one of the library isn't added (`nameClash`, in `:domain`): the same name
  and size is the same file, another size another picture, and two of one name would be told
  apart by nothing on the screens. It's refused as soon as the source says its name and size,
  before anything is copied; a source that doesn't say its size, once it's copied; and again
  before it's named, for one of that name added meanwhile. Past those checks, `add` reports that
  the copying starts (`copying`): the real work, which the share window shows as a wait, where a
  picture refused at once shows none.
  Adding streams the source into a new file, stopping past the size limit, hashing it on the way;
  a content the library has already, under any name (a picture renamed since, say), is answered
  `AlreadyThere` then, before it's decoded; else it decodes its size, then all of it, once, into
  its working copy, since a size alone can decode where the picture can't (AVIF on API 31), so a
  picture added is ready for the screens at once;
  and then, on the main thread, either finds it in the index already or has it and its working
  copy moved into place and named. Deleting a picture deletes its file, its working copy and its
  thumbnail. Once per process, a job deletes the files, working copies and thumbnails the index
  doesn't name, which a killed process can leave; an index that can't be read keeps every file.
- **What another app gives is read, never trusted.** A source is opened through `ContentResolver`
  as a stream, its name kept as a label only, never as a path. A `file://` Uri is refused
  (`Unreadable`): only an app with storage permission could read it, as older Android versions
  share files, and Luncher asks for none ([`app/README.md`](../app/README.md#manifest-why-each-part-is-there)).
  The one exception is a file of Luncher's own drop folders, which Add from folder passes. It's
  asked and read through `outside/` ([above](#another-apps-answers)), as the app may never answer:
  one that stops sending gives `Unreadable` after 15 s without an answer.
- **An import can be cancelled wherever it is** (`add`'s handle): its job taken out of the queue
  if it hasn't started, its reading or its decode stopped, its copy deleted if it's made, and on the main
  thread, the steps that follow see it and stop. A file already moved into place but not yet named
  stays for the next start's cleanup: deleting it could delete the same picture, added again
  meanwhile.
- **A decode stops when it isn't wanted any more.** A picture is decoded through a stream whose
  reads fail once the caller says it's stopped (a screen's request dropped, an import cancelled):
  the decoder then ends at its next read, within milliseconds on API 22 to 36, and what it decoded
  so far is dropped. Nothing else stops one: `BitmapFactory.Options.requestCancelDecode` does
  nothing from API 24 on. So the job thread, one for all of Luncher, is free at once for what the
  user asked for next.
- **A picture is drawn into ARGB_8888, at the size it's shown, upright.** `PictureFiles.draw`
  decodes it at no more than the size needed (`inSampleSize`), into sRGB from API 26 on (an iPhone's
  photo is in Display P3), turns it as its orientation tag says, which BitmapFactory doesn't, and
  draws the framed part into a bitmap of exactly that size, over the home screen's background
  color (for a transparent picture); then frees the decoded one. A decoder may give another config
  (16-bit PNG: RGBA_F16 from API 26 on; GIF: none up to 26), which the drawing turns into ARGB_8888.
- **Blur and dim** are drawn into the picture's bitmap, after its framing; dim is a black fill of
  its share. A blurred picture is drawn at half the size: it decodes smaller (a 12 MP photo for a
  1080p screen: 3 MB rather than 12), and blurring and dimming it costs a quarter, which the
  blur's softness hides. It's blurred in place, its strength a share of its width (the domain's
  `Blur.spread`), by three box blurs each way, nearly a Gaussian blur, each a running sum, so a
  stronger blur costs no more: the rows through a line buffer, the columns by a sum per column,
  keeping only the rows the sums still need, so one array of the pixels is all it takes. Then
  it's dimmed and drawn up to the size asked, filtered and dithered, so the dither also covers
  the dim's fill. That takes about half as long as drawing it whole and halving it after, and
  differs from a Gaussian blur at full size by half a level of 255 on average.
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
16 on, a file another app (or adb) put there can't be read; `MediaFolderPictures` lists only what
it can read, so there a picture comes in through a share.

## Manifest

| Element | Reason (don't remove without replacing it) |
|---|---|
| `<queries>` for `MAIN`+`LEANBACK_LAUNCHER` | Package visibility (targetSdk 30+): without it `PackageManagerInstalledApps` can't see other apps. Only TV apps: Luncher lists and draws nothing else, and starting an app needs no visibility, so a query for phone apps (`MAIN`+`LAUNCHER`) would only widen what it can see. |
