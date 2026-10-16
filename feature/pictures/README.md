# feature/pictures/

The picture library's screens, `:feature:pictures`: Pictures, Add from folder, and the way in for
a picture another app shares. What a feature module holds and may use: [`../README.md`](../README.md);
the rules it follows: [`../../docs/ARCHITECTURE.md`](../../docs/ARCHITECTURE.md).

| | |
|---|---|
| Package | `com.luncher.launcher.pictures` |
| Depends on | `:domain`, `:ui` |

## Layout

```
feature/pictures/
├── build.gradle.kts              luncher.android.library and Roborazzi; its dependencies
└── src/
    ├── main/
    │   ├── AndroidManifest.xml   the screens' activities, and how they behave (below)
    │   ├── java/com/luncher/launcher/pictures/
    │   │   ├── PicturesGraph.kt  the ports the pictures' screens need; Activity.graph
    │   │   ├── PicturesActivity.kt     Pictures: the grid, a picture's actions, Delete unused; adds a picture it's given
    │   │   ├── AddFromFolderActivity.kt  the drop folders' pictures, a preview of the selected one; OK returns it
    │   │   ├── SharePictureActivity.kt   where another app's share arrives: passes the picture to Pictures, no screen
    │   │   ├── PicturesDialog.kt the dialogs over Pictures: a picture's actions, a question, a message
    │   │   ├── PreviewColumn.kt  Add from folder's preview and note, the preview smaller on a short screen
    │   │   └── PictureTexts.kt   what the screens call things: sizes, names, why a picture wasn't added
    │   └── res/                  the theme (Theme.Luncher.Pictures, .Dialog, .NoDisplay), layouts, the tiles'
    │                             frames, pictures_* dimensions, colors and strings, their translations in
    │                             values-el/ and values-ar/ (docs/TRANSLATIONS.md)
    └── test/
        ├── java/…/pictures/      JVM tests; TestPicturesGraph.kt: the ports a test gives the screens, and
        │                         the tests' Application (named in resources/robolectric.properties)
        └── screenshots/pictures/ reference images, committed
```

Everything but the activities and `PicturesGraph` is `internal`.

## Manifest

The intent filters that make `SharePictureActivity` a target of other apps' shares are the app's
to declare ([`app/README.md`](../../app/README.md#manifest-why-each-part-is-there)); the module
declares how the activities behave, and the build merges the two.

| Element | Reason (don't remove without replacing it) |
|---|---|
| `PicturesActivity`, `AddFromFolderActivity`: `exported="false"` | Only Luncher opens them: the settings panel, Pictures, and `SharePictureActivity` with a shared picture. |
| `PicturesActivity`, `AddFromFolderActivity`: `Theme.Luncher.Pictures` | Full-screen and opaque, on the home screen's background, like the appearance editor. |
| `SharePictureActivity`: `exported="true"` | Other apps' shares arrive there. |
| `SharePictureActivity`: `Theme.Luncher.Pictures.NoDisplay`, `noHistory`, `excludeFromRecents` | It shows nothing, and finishes in `onCreate`, before `onResume`, as a `windowNoDisplay` activity must; its backgrounds are still the home screen's dark ones, in case a device shows one ([`ui/README.md`](../../ui/README.md#the-theme)). |
| `supportsRtl="true"` | As the app declares it ([why](../../app/README.md#manifest-why-each-part-is-there)), so the JVM tests mirror the screens in a right-to-left language as the app does. |

## Pictures

Pictures (`PicturesActivity`) is Luncher's own picture library, opened from the settings panel's
Pictures entry: a grid of thumbnails, the newest first, as many a row as fit the width, ending in
an Add from folder tile, with Delete unused below. The whole screen scrolls as the focus moves, so
a short screen shows as much as it can. OK on a picture opens its actions (Delete), and deleting
asks first, Cancel focused, so a repeated OK deletes nothing; Delete unused asks too, saying how
much room the pictures take. While the library is empty, the screen says how to add a picture: a
share from another app, or a drop folder ([`platform/README.md`](../../platform/README.md#the-drop-folders)),
with its path. Back closes it, back to the settings panel. In a right-to-left language it mirrors
like the settings panels; the pictures don't.

| Part | Job |
|---|---|
| `Picture`, `PictureLibrary` (`:domain`) | a picture of the library; the library in the screen's order, the newest first, the room it takes, the pictures nothing uses |
| `Pictures`, `PictureImport` (`:domain`) / `FilesPictures` (`:platform`) | the library, which the screen follows while it shows, and adding to it |
| `PictureImages` (`:domain`) / `CachedPictureImages` (`:platform`) | the thumbnails, drawn at the tile's size in pixels |
| `PicturesActivity` | the grid's tiles, the focus, a picture's actions and the questions; adds the picture it's opened with, or the one Add from folder returns, and selects it, or says why it wasn't added (`ImportResult`, an exhaustive `when` in `whyNotAdded`) |

All thumbnails are read at once when the screen shows, from what `CachedPictureImages` kept, on the
main thread, so the grid shows them with its first frame; one not kept (a picture just added, a
screen of another size) is drawn in the background, a `LatestRequest` each, and shows when it's
done. The screen keeps each picture's tile while the picture is in the library, so its thumbnail is
read or drawn once, and drops the requests in `onStop`.

## Adding a picture

**From another app** (`SharePictureActivity`): a file manager's or a gallery's Share, or "Set as",
of a picture (`SEND` or `ATTACH_DATA`, `image/*`). It takes the picture's Uri out of the intent and
passes it, with the read access the other app gave, to Pictures, and finishes at once; Pictures
adds it, saying so below the title meanwhile, and selects it, or says why it couldn't. A picture
the library has already (the same content) is selected, and nothing is said. An intent with no
picture it can pass on becomes the message that Luncher can't read it. What the Uri points to is
the library's to judge: a `file://` one, as Android up to 9 shares files, can't be read without a
storage permission, and the message says to use a drop folder instead, with its path.

**From a drop folder** (`AddFromFolderActivity`): the pictures a file manager or a computer put in
`Android/media/<applicationId>`, on the TV or a USB drive, listed the TV's first, then the drives',
each by name (`inListOrder`), with a preview of the selected one beside them, drawn in the
background. OK returns the picture to Pictures, which adds it as a shared one. A file in a format
this TV can't show (HEIF before API 28, AVIF before API 33) is listed, marked, and OK on it says so;
anything else that isn't a picture isn't listed. The folders are read again each time the screen
shows, so a USB drive plugged in meanwhile counts. On a screen too short for the preview and its
note, the preview gets smaller (`PreviewColumn`).

To try it on an emulator, put a picture in the debug build's drop folder:

```bash
adb push holiday.jpg /sdcard/Android/media/com.luncher.launcher.debug/
```

## What keeps it light

- **Nothing runs while it's hidden.** The library is followed from `onStart` to `onStop`, and the
  thumbnails and previews being drawn are dropped in `onStop`.
- **Pictures are decoded at the size shown**, one at a time on the background thread, and freed
  once drawn ([`platform/README.md`](../../platform/README.md#pictures)): a 12 MP photo is never
  held whole.
- **A thumbnail is drawn once per screen size**, then read from its file.
