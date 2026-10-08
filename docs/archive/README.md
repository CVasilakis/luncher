# docs/archive/

How the current design was reached, and what removed features taught: the notes below, and the
one removed feature's final drawables. Nothing here is part of the build.

| Path | Contents |
|---|---|
| `launch-screen/res/` | The removed launch screen's drawables, to copy back into the app ([below](#bringing-it-back)): `drawable/launch_screen_art.xml` (the drawing, a 960×540 dp vector drawable), `drawable-v23/launch_screen.xml` (it centred on the home screen's colour), `drawable/launch_screen.xml` (API 22: it stretched over the window). |
| `launch-screen/previews/` | The launch screen as Robolectric drew it, at 1080p and on a 4:3 screen: the former screenshot tests' reference images. |

## Screen layouts on nine TV screens

A review of the home screen, the settings panel and Hide apps as Robolectric drew them on screens
other than 1080p, made when every test still ran at 1080p only: 720p at tvdpi, 4:3, 16:10, 21:9,
1080p at 240 and at 160 dpi, Android TV's largest text, and Arabic, right to left. What it showed
shaped the current layouts and their tests:

- **Five fixed columns:** the tiles grew and shrank with the screen's width in dp while the top
  bar's text kept its size, huge on 1080p at 160 dpi, small on 4:3. The home screen now fits as
  many tiles in a row as the width holds
  ([`feature/home/README.md`](../../feature/home/README.md#the-home-screen)).
- **Right-to-left:** the top bar mirrored while the tiles still filled from the left. Every screen
  now stays left to right (`supportsRtl="false"`,
  [`app/README.md`](../../app/README.md#manifest-why-each-part-is-there)).
- **The other screens** fitted, which `HomeLayoutTest` and `SettingsLayoutTest` now check on each
  of them ([`docs/TESTING.md`](../TESTING.md#layouts-on-other-screens)).

## The launch screen (removed)

Up to Android 11, Luncher's home screen had a launch screen: Android showed a drawing, the TV and
the name "Luncher" in a sky-blue glow, while Luncher's process started. It was removed. On a Fire
TV Stick the drawing and the home screen alternated for a few frames as the launch screen went
away. Luncher now asks for no starting window on any Android version, so the previous screen stays
until the home screen has drawn, as Android 12 and later do anyway
([`ui/README.md`](../../ui/README.md#while-luncher-starts), which also has what Android shows while an
app starts, on each version).

### Why it was removed

Up to Android 11 (API 30), once the app's window has drawn, Android doesn't remove the starting
window at once: it fades it out over the app's first frame, linearly over 150 ms
(`app_starting_exit`, chosen by AOSP `PhoneWindowManager.selectAnimationLw` up to Android 9,
`DisplayPolicy.selectAnimationLw` on 10 and `DisplayPolicy.selectAnimation` on 11). Android 11
skips the fade for an activity of type home, not for one opened as an app.

- With the plain dark starting window Luncher had before, the fade was invisible: it went from
  the home screen's background colour to the home screen, so only the tiles faded in.
- With the drawing, the fade blends two different pictures. A weak device, busy with a starting
  app and blending two full-screen layers, draws only two or three of its frames, each a
  different mix of the two, which looks like the launch screen and the home screen flickering.
- On a Fire TV Stick, Luncher always takes that path: Fire OS keeps its own launcher as the home
  app, so Luncher runs as an app there, on Fire OS 7 (Android 9) and 8 (Android 11) alike.
- An emulator on a fast host draws every frame of the fade, which is why it went unnoticed there.
  To see the fade, slow it down: `adb shell settings put global window_animation_scale 10`, start
  Luncher cold (`am force-stop` first), and take screenshots on the device in a loop
  (`screencap -p /sdcard/fNN.png`). The launch screen comes up, the home screen appears under it,
  and the drawing fades away over it. `window_animation_scale 0` removes the fade altogether.

An app can't choose or turn off that animation. The ways around it that were weighed:

1. **The app draws the launch screen in its first frame, and the home screen after the fade.** Keep
   the drawing as the window's background and the views invisible, so Android fades the drawing
   into the same drawing; then, after 150 ms × the window animation scale, show the views and set
   the plain background. Android doesn't report the end of the fade (`onEnterAnimationComplete` is
   the end of the window's opening animation, not of this fade), so it takes a timer. It has to
   run only when a starting window was shown: Android 11 and older, a new activity rather than
   one recreated or brought back with `onNewIntent`, and not at boot, where a home activity gets
   no starting window. The two pictures must match to the pixel, which they don't on API 22 with
   its density overridden (below). Instrumented tests would need to wait for the reveal.
2. **A darker drawing**, close to the home screen's colour: the fade is still there, harder to see.
3. **The plain dark starting window again.**
4. **No starting window (`windowDisablePreview`)**, the one chosen: the previous screen stays until
   Luncher has drawn, the same on every Android version, since from 12 on a home activity gets no
   splash screen anyway. The price is that a cold start shows nothing until Luncher has drawn.

### Switching to the app's own theme

The starting window takes the theme the manifest gives the activity, and the activity's window the
theme in force when its decor is made. With the launch theme in the manifest,
`HomeActivity.onCreate` called `setTheme(R.style.Theme_Luncher)` before `super.onCreate`; without
it, the drawing stayed as the window's background, drawn behind the tiles on every frame.

### Drawing one picture for every TV screen

- **960×540 dp** is a 16:9 TV's whole screen at its usual density: 720p at tvdpi, 1080p at xhdpi,
  4K at xxxhdpi. So one vector drawable of that size fits most TVs, though not every one
  ([the other screens](../TESTING.md#layouts-on-other-screens)).
- **Keeping its shape:** `res/drawable-v23/launch_screen.xml` is a layer-list with the background
  colour and the drawing at `android:gravity="center"`, 960×540 dp. On other shapes it's cut at
  the edges or surrounded by the background, into which the glow fades; on a screen larger in dp
  it's smaller than the screen. A layer-list item takes gravity, width and height only from API 23
  on, so API 22 got `res/drawable/launch_screen.xml`, the drawing stretched over the window.
- **A full-screen vector drawable is a full-screen bitmap.** Android draws a vector drawable into a
  bitmap of the size it's shown at (lint's `VectorRaster` warning, which needed an exception), so
  the launch screen cost a screen-sized bitmap while it showed.
- **No gradients before API 24:** vector drawables take gradients only from Android 7.0 on. A soft
  glow made of many faint transparent discs vanished on API 22, which draws vector drawables in
  software one shape at a time in 8-bit steps, so faint layers round to nothing. The glow became
  32 opaque discs from the outside in, each in the colour faint layers would have added up to.
  API 22 and 24 then drew it the same.
- **Flat paths,** like the icon and banner, for API 22 and 23
  ([`app/README.md`](../../app/README.md#icon-and-banner)).
- **An Android 5.1 bug:** on the API 22 emulator, with the display density overridden
  (`wm density`, as Developer options do) to differ from the device's own, the starting window
  drew the drawing zoomed in and shifted, even after a reboot. With the device's own density at
  240 dpi, it was right, and Luncher's own code drew the same drawable right either way.

### Size and tests

- **APK size:** the drawing added about 7 KB to the release APK (about 69 KB to 76 KB), and the
  centring about 1 KB more. Removing it brought the APK to about 70 KB.
- **Screenshots:** Robolectric doesn't show starting windows, so a test drew the launch theme's
  `windowBackground` into a screen-sized bitmap and Roborazzi compared it
  (`launch-screen/previews/`): one at 1080p, and one at 720×540 dp (4:3) that failed if the
  drawing was stretched again.
- **The switch in `onCreate`:** a JVM test checked that the manifest gave `HomeActivity` the
  launch theme and that its window's background ended up the plain colour; it failed without
  `setTheme`.
- **Never too bright:** `ThemesTest`, which draws every screen's backgrounds over black, kept
  passing: the launch screen's average brightness was about 0.14, under its limit of 0.2.
- **Check on the slowest device early:** the emulators showed the launch screen right on API 22 to
  30, centred at 16:9, 4:3, 16:10 and 21:9 and at 240 dpi, but never the flicker, which only a
  real weak device showed.

### Bringing it back

With `windowDisablePreview` taken out of `Theme.Luncher` (`ui/src/main/res/values/themes.xml`):

1. Copy `launch-screen/res/` into `ui/src/main/res/`, next to the theme and the `background`
   colour the drawable uses.
2. Add a theme that only sets the window background, and give it to `HomeActivity` in
   `feature/home/src/main/AndroidManifest.xml`:

   ```xml
   <style name="Theme.Luncher.Launch">
       <item name="android:windowBackground">@drawable/launch_screen</item>
   </style>
   ```

3. Call `setTheme(R.style.Theme_Luncher)` first in `HomeActivity.onCreate`, before
   `super.onCreate` ([why](#switching-to-the-apps-own-theme)).
4. Add `src/main/res/drawable/launch_screen_art.xml` to the `VectorRaster` exceptions in the
   module's `lint.xml` (as `app/lint.xml` has for the banner), and the drawable to `app/NOTICE`.
5. Deal with the fade ([option 1 above](#why-it-was-removed)), or the flicker comes back on slow
   TVs.

## License

The drawables in `launch-screen/res/` and the images in `launch-screen/previews/` draw the same
AOSP desserts as the app's icon and banner, and are under the Apache License 2.0 like them
([`app/README.md`](../../app/README.md#license)). The drawables keep the AOSP header.
