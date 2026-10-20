## What's new

### Wallpapers

Set one of your own pictures behind the home screen. Open the **Settings**, then **Appearance**,
then **Background**:

![The background editor](https://github.com/CVasilakis/luncher/raw/v0.3.0/fastlane/metadata/android/en-US/images/tvScreenshots/5.png)

- **Fit:** Fill the screen, cutting what doesn't fit, or Stretch the picture to it.
- **Position:** which part of a filled picture shows: the Center, or a side (top, bottom, left, right).
- **Dim:** darken the picture, so the apps and the time stand out: Off, Light, Medium or Strong.
- **Blur:** soften the picture: Off, Light, Medium or Strong.

Hold OK on a picture to rename or delete it, and **Delete all** removes every picture at once.

### Adding pictures

Luncher still asks for **no permissions**, so it can't browse your storage. A picture comes in one
of two ways:

- **Share it:** in a file manager or a gallery on the TV, choose **Share** on a picture (or
  several, up to 100 at once), then select Luncher.
- **Copy it into Luncher's folder,** `Android/media/com.luncher.launcher`, on the TV or on a USB
  drive, then choose **Add from folder** at the end of the pictures list. Up to Android 14,
  Luncher can read a picture copied there by another app, without any special permissions.

> [!NOTE]
> From Android 16 on (Android TV 15 was skipped entirely), Luncher can't read pictures copied into
> the `Android/media/com.luncher.launcher` folder by another app. Share the picture instead in this
> case.

![Add from folder](https://github.com/CVasilakis/luncher/raw/v0.3.0/fastlane/metadata/android/en-US/images/tvScreenshots/6.png)

Once a picture is added (either by sharing or by copying it into the folder above), it is copied
into Luncher's own data folder, where the app has normal read/write access and no special
permissions are required. Clearing the app's data or uninstalling Luncher will delete all pictures
that were previously added. Since Luncher keeps its own copy of each picture, it stays after the
original is deleted or the USB drive is removed. This also means that deleting a picture from
Luncher will not affect the original on the TV or USB drive.
