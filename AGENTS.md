# AGENTS.md

What AI coding agents need on top of the project's documentation. Read that first, from the
general to the specific ([reading order](docs/README.md#reading-order)): [`README.md`](README.md)
(what Luncher is), [`docs/BUILDING.md`](docs/BUILDING.md) (requirements, build and run),
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) (where code goes and the rules it follows),
[`docs/TESTING.md`](docs/TESTING.md) (which tests a change needs and how to run them), then the
README of the folder you're working in. Docs you write follow
[`docs/README.md`](docs/README.md#writing-the-docs).

## Tools outside this repository

The emulator scripts (`create-avd.sh`, `start-emulator.sh`, `stop-emulator.sh`, `remote.sh`) come
from [android-tv-wsl-dev-tools](https://github.com/CVasilakis/android-tv-wsl-dev-tools), a separate
repository that can live anywhere; its `bin/` must be on `PATH`
([`docs/EMULATORS.md`](docs/EMULATORS.md)). If the scripts aren't found, ask the user where that
repository is rather than guessing a path.

Its `bin/README.md` is the reference for driving the emulator from a script:
[sending keys](https://github.com/CVasilakis/android-tv-wsl-dev-tools/blob/main/bin/README.md#controlling-the-tv),
[checking what Android received](https://github.com/CVasilakis/android-tv-wsl-dev-tools/blob/main/bin/README.md#checking-what-the-emulator-is-doing),
and [how API levels differ](https://github.com/CVasilakis/android-tv-wsl-dev-tools/blob/main/bin/README.md#differences-between-api-levels).

Two things of the host that can stop the work:
- **"No access to /dev/kvm"** from `start-emulator.sh`, although it worked before: under WSL,
  booting another WSL distro can hand `/dev/kvm` to that distro's `kvm` group
  ([the tools' `SETUP.md`](https://github.com/CVasilakis/android-tv-wsl-dev-tools/blob/main/SETUP.md#make-devkvm-writable)).
  The fix needs `sudo`: stop and ask the user.
- **The user's desktop:** don't run anything that opens and closes many windows on the real
  display (`$DISPLAY`, e.g. the tools' X11 tests with `SCRIPT_TESTS_DISPLAY=:0`). It has crashed
  WSLg's compositor, which closes every Linux GUI app. Boot emulators for tests with
  `-no-window`.

## Verifying a change

Beyond the tests [`docs/TESTING.md`](docs/TESTING.md) asks for, install the change and look at it
on the emulator: `./gradlew installDebug`, then start it, or make it the home screen
([`docs/EMULATORS.md`](docs/EMULATORS.md#luncher-as-the-home-screen)), and drive it with keys
([Keys](docs/EMULATORS.md#keys)). To see what happened:

```bash
adb shell dumpsys window | grep mCurrentFocus    # what's in front
adb exec-out screencap -p > screen.png           # screenshot, to look at the UI
adb logcat -b crash                              # crashes
```

Stop an emulator with `stop-emulator.sh`, which has Android save a device setting changed by hand
first; any other way loses a change made less than 30 s before
([why](docs/EMULATORS.md#saving-a-change-to-the-device)).

**Boot one emulator at a time** for test runs, to spare the host's resources: boot one, run the
tests, stop it, then start the next.

```bash
for avd in tv_api22 tv_api24 tv_api28 tv_api30 tv_api33 tv_api36; do
  start-emulator.sh "$avd" && ./gradlew connectedDebugAndroidTest; stop-emulator.sh "$avd"
done
```

**Waiting in commands.** A command that waits must end on its own, or it holds up the session
until the tool's time limit:

- Stop emulators with `stop-emulator.sh`. It waits until the emulator has exited, with a time
  limit, so don't wait for the emulator's process yourself. (A checkout of the tools older than
  v1.3.0 lacks it: `adb emu kill; adb wait-for-disconnect`.)
- Never wait on `pgrep -f` or `pkill -f`: your command runs as `bash -c '<command>'`, whose
  command line holds the pattern too, so they always find it and the loop never ends
  ([details](https://github.com/CVasilakis/android-tv-wsl-dev-tools/blob/main/bin/README.md#stop-emulatorsh)).
- Start any wait loop you write with `timeout <seconds>`, and `sleep` in its body, so a mistake
  ends in seconds and doesn't keep a CPU core busy.

## Ask the user first

- **Before adding any dependency**, runtime or test.
- **When nothing in ARCHITECTURE.md's [Where things go](docs/ARCHITECTURE.md#where-things-go)
  fits**, rather than inventing a new layer.
- **Before triggering big SDK downloads** (sizes in android-tv-wsl-dev-tools' `SETUP.md`).
- **For `sudo`:** it needs a password and there's no terminal to type it into, so ask the user to
  run sudo commands themselves in a regular terminal.

Never add build outputs, `.gradle/`, `.kotlin/`, `local.properties`, signing keys or keystores
to git.

## Commit messages

Agents never create commits. When asked for a commit message, read recent history (`git log`)
first and match its style and level of detail.

- **Subject: the outcome, not the activity** ("classify lock storage failures as storage errors",
  not "update locking code"). Use a conventional prefix (`fix(locking): …`) only if recent
  history does.
- **Body: understandable without the diff.** Say what changed, why, which module or layer owns
  it, and which behavior was deliberately kept (invariants, compatibility). Explain non-obvious
  decisions, such as why a port or boundary was added or why responsibilities stay in separate
  layers.
- Describe behavior, not file inventories, symbol lists or the order things were implemented in.
- Left-aligned `-` bullets, one purpose each; don't repeat the subject or another bullet.
- No filler, promotional wording or vague phrases ("improve robustness", "various fixes")
  unless the concrete behavior follows right away.
- Mention tests only if they ran, with the exact passing count when known. Never claim a run
  passed if it failed or didn't run.
- No trailers or sign-offs of any kind (`Co-Authored-By`, "Generated with …", …).
- Make it detailed enough to keep the design intent, and no longer than that.

## Things that look removable but aren't

Each of these fixes a real problem. Read the reason before changing anything.

| What | Where | Reason |
|---|---|---|
| `kotlin-android` (and the other plugins) declared with `apply false` at the root; AGP and KGP `compileOnly` in `build-logic/` | `build.gradle.kts`, `build-logic/build.gradle.kts` | [`gradle/README.md`](gradle/README.md#how-kotlin-is-set-up-agp-9-built-in-kotlin) |
| `onBackPressed()` without `super`, and an `OnBackInvokedCallback` on API 36+, that only end moving an app | `feature/home/…/HomeActivity.kt` | its comments |
| `java`/`jvmTarget` 17 instead of a toolchain | `domain/build.gradle.kts` | its comment |
| `--add-opens=java.base/jdk.internal.access` for unit tests | `build-logic/…/AndroidConventions.kt` | its comment |
| A library's `testOptions.targetSdk`, and its device tests disabled | `build-logic/…/AndroidLibraryConventionPlugin.kt` | its KDoc: without the first, Robolectric runs a library's tests on minSdk |
| `checkDependencies` in `:app`'s lint | `app/build.gradle.kts` | its comment, [`docs/TESTING.md`](docs/TESTING.md#lint-and-compiler-warnings) |
| `uninstallAll` before, `checkConnectedTestsRan` after instrumented tests | `app/build.gradle.kts` | its comment |
| `android.injected.androidTest.leaveApksInstalledAfterRun` | `gradle.properties` | its comment |
| `testInstrumentationRunnerArguments["filter"]`, `SystemTierFilter` | `app/build.gradle.kts`, `app/src/androidTest/…/testing/` | `SystemTierFilter`'s comment |
| `testInstrumentationRunnerArguments["timeout_msec"]` (15 minutes per test method) | `app/build.gradle.kts` | its comment |
| `waitForHomeScreen()` before every instrumented test (and its Back on "USB drive connected"), with its two limits (10 minutes on the way to the home screen, 60 s for another app's screen), `pressHome()` in `HomeKeyTest`'s setup (until Luncher has the focus) and cleanup | `app/src/androidTest/…` | their comments |
| `HomeKeyTest`'s cleanup: `dumpsys package write` (up to API 31) or the wait for a `commit_sys_config_file` event, the wait for `/data/system/users/0` to change, then `sync` | `app/src/androidTest/…/system/HomeKeyTest.kt` | `restoreDevice`'s KDoc, [`docs/INSTRUMENTED-TESTS.md`](docs/INSTRUMENTED-TESTS.md#leave-the-device-as-it-was-saved) |
| From API 33 on, `HomeKeyTest`'s cleanup setting Luncher's own `SettingsActivity` to ENABLED and back to DEFAULT with `PackageManager.SYNCHRONOUS` | `app/src/androidTest/…/system/HomeKeyTest.kt` | `writeHomeAppsNow`'s KDoc: it makes Android write the restored stock launcher's state at once, which nothing else can from API 33 on |
| `waitForFocus()` right after a screen is seen, before keys (in the system tests and `longPressOk()`) | `app/src/androidTest/…` | `waitForFocus`'s comment, [`docs/INSTRUMENTED-TESTS.md`](docs/INSTRUMENTED-TESTS.md#writing-them) |
| `testInstrumentationRunnerArguments["listener"]` (`FailureCapture`), and the waits without Espresso calling `FailureCapture.dump` before they fail | `app/build.gradle.kts`, `app/src/androidTest/…/testing/` | `FailureCapture`'s KDoc, [`docs/INSTRUMENTED-TESTS.md`](docs/INSTRUMENTED-TESTS.md#look-again-until-it-matches-and-keep-what-a-failure-showed): it saves the device's state while the failing screen is still open; after the test, its activity is closed |
| `waitUntilMatches()`'s quiet failure handler (`Retrying`) for every look but the last, and `matchesNow()` for a look that may answer no | `app/src/androidTest/…/testing/Looks.kt` | its KDoc: Espresso's own handling takes a screenshot and the view hierarchy at every failed look, which would slow each look again |
| CI pulling what the tests saved as they failed from the device (`adb pull`), not uploading AGP's copy | `.github/workflows/instrumented-tests.yml` | its comment: AGP's copy fails on API 22 and 23, and a cancelled run never makes one |
| The build steps running Gradle once more after a failed download | `.github/workflows/instrumented-tests.yml`, `jvm-tests.yml` | their comment: Google's Maven repository answers 502 now and then |
| `waitUntilDestroyed()` after the key that closes a screen, before Espresso looks at the one under it (Add from folder, the appearance editor, Hide apps, the settings panel, the tile banner picker) | `app/src/androidTest/…`: `BackgroundActivityTest`, `AppearanceActivityTest`, `HomeActivityTest`, `TileBannerActivityTest` | its KDoc in `testing/Closed.kt`, [`docs/INSTRUMENTED-TESTS.md`](docs/INSTRUMENTED-TESTS.md#send-keys-only-to-a-focused-window): Espresso can pick the closing window, still on top, and wait 10 s for a focus it never gets |
| `waitForTheSettingsPanel()` between the two Backs of `hidingAnAppInTheSettings_removesItsTile` | `app/src/androidTest/…/home/HomeActivityTest.kt` | its comment, [`docs/INSTRUMENTED-TESTS.md`](docs/INSTRUMENTED-TESTS.md#writing-them) |
| The wait, before `coverWithTheStockLauncher()`'s HOME intent, until no "choose home app" dialog is left that isn't finishing (`homeChooserNotFinishing`), rather than re-sending the intent | `app/src/androidTest/…/testing/RetryWhenCoveredTest.kt` | `coverWithTheStockLauncher`'s KDoc, [`docs/INSTRUMENTED-TESTS.md`](docs/INSTRUMENTED-TESTS.md#writing-them) |
| `RetryWhenCovered` in the test classes that open screens (a test the stock launcher covered runs twice), judging each look by the state when its read began; and `HideAppsActivityTest`'s `arrangements` made anew at each launch | `app/src/androidTest/…` | its KDoc, [`docs/INSTRUMENTED-TESTS.md`](docs/INSTRUMENTED-TESTS.md#writing-them) |
| `start-emulator.sh --wait-for-home` in CI, although the tests wait for the home screen too | `.github/workflows/instrumented-tests.yml` | its comment |
| `ListEntry`'s `onMeasure` `final`, a subclass measuring through `measureContent` | `ui/…/ListEntry.kt` | its comment, [`ui/README.md`](ui/README.md#the-design-system): a list gives its rows one height so its window is whole rows; `StepperRow` measuring itself left its rows out, and the window drifted by 2 px a row |
| `FocusSlide` drawing the rows' focused looks once, apart from the rows, as a `Picture` from API 23 on and into kept bitmaps before, and the list redrawn as a slide starts and ends; `ListEntry.drawChild` cutting a child along its outline while it draws into them | `ui/…/FocusSlide.kt`, `ui/…/ListEntry.kt` | their KDoc: the GPU keeps one drawing of a view a frame, so a row can't look focused inside the bar and at rest outside it any other way; bitmaps sent to the GPU made a slide's frames 6 times slower (tv_api28), and API 22's GPU can't play a `Picture`; the GPU keeps the list's drawing, the bar in it, until the list itself changes; those canvases leave outlines (a thumbnail's rounded corners) to the GPU |
| `LookAheadListView` selecting through `selectFromTop`, its padding less `listPaddingTop`, not `setSelectionFromTop` or `ListView`'s `setSelection` | `ui/…/LookAheadListView.kt` | `selectFromTop`'s KDoc: before its first measure `ListView` counts no padding, and Add from folder's first row sat on the list's top edge, the margin lost until it scrolled |
| `LookAheadListView` laying itself out (`layoutChildren()`) right after an arrow selects | `ui/…/LookAheadListView.kt` | `select`'s KDoc: else OK right after Down acts on the row selected before |
| `AppearanceEditorLayout` measuring the options' container for the room below the title in `onMeasure`, from the start column's measures | `feature/settings/…/AppearanceEditorLayout.kt` | its KDoc: measured for more room first, the list fits its rows to that room and loses its scroll at each layout |
| `CachedBannerImages` queuing its writes only once the main thread is idle (an `IdleHandler`), not as each image is drawn | `platform/…/apps/CachedBannerImages.kt` | its KDoc: writing while the start still draws tiles slowed the first start |
| The tiles' prepared pictures in `filesDir`, not `cacheDir` like the banner cache; their shape's masks at half the tile's scale | `platform/…/apps/FilePreparedTiles.kt` | its KDoc, [`platform/README.md`](platform/README.md#pictures): Android may delete a cache, and a tile found missing at a start brings the home screen's window back; the masks are blurred, so half the scale doesn't show, for a quarter of the memory |
| A light card that comes with the focus under a see-through picture without a backdrop, lifted with Focus Tile, and the outline's alpha at 0.3 under one (1 under a Frosted one while the screen has a frost: `TilePainter.outlineAlpha`); a tile whose focus follows the picture's shape zoomed only (`home_tile_focus`, outline alpha 0), its picture casting its own shadow | `ui/…/TilePainter.kt`, `feature/home/…/AppTileView.kt` | their KDoc, [`feature/home/README.md`](feature/home/README.md#appearance): Android's shadow of a tile's outline shows through a see-through picture's empty parts as a dark box behind nothing |
| A lifted tile's outline 1 px inside the tile, its radius 1 px less (`TilePainter.outline`, `SHADOW_INSET`), although it looks like an off-by-one | `ui/…/TilePainter.kt`, `feature/home/…/TilePaints.kt` | `SHADOW_INSET`'s KDoc, [`feature/home/README.md`](feature/home/README.md#appearance): Android leaves out the shadow under an opaque outline up to its edge, so with the tile's own bounds its anti-aliased bottom edge and corners showed bare wallpaper; an alpha just under 1 instead shows the whole shadow through an app banner's clear parts |
| A see-through tile at rest drawn in one pass, a `ComposeShader` of a one-color `LinearGradient` (its glass) under the picture's `BitmapShader`, and that shader's `FILTER_MODE_LINEAR` from API 33 | `ui/…/TilePainter.kt` | `TilePainter`'s and `TileParts.restingWithGlass`' KDoc, [`ui/README.md`](ui/README.md#layout): the glass drawn apart is a second fill of the whole tile at every frame, for every see-through tile; below API 28 a bitmap's shader composes only with another kind, hence a gradient; on API 36 a bitmap inside it isn't filtered by the paint |
| A Frosted tile's frost drawn by moving and scaling the canvas, through a shader made once per frosted copy (`TileFrost`), not by the shader's matrix; `AppTilesView` keeping its tiles' origin in the window (`origin`, from `onLayout` and `onScrollChanged`); `AppTileView` overriding `setScaleX`/`setScaleY` and `onLayout`, and its `animate()` update listener, to draw a Frosted tile again | `ui/…/TilePainter.kt`, `feature/home/…/AppTilesView.kt`, `feature/home/…/AppTileView.kt` | their KDoc, [`feature/home/README.md`](feature/home/README.md#appearance): a matrix changed at each draw makes Android build the shader again, which made scrolling 2 to 3 times slower on API 36; Android doesn't draw a view again when it only moves or zooms, and the frost must follow what's behind the tile |
| The home screen's padding at the sides in `:ui` (`screen_padding_horizontal`), not in `:feature:home`'s dimens | `ui/src/main/res/values/dimens.xml`, `ui/…/HomeTiles.kt` | [`feature/home/README.md`](feature/home/README.md#a-tiles-picture): the tile banner picker prepares a tile's picture at the home tile's size, which it computes from it; a size off by a pixel prepares the tile again behind the home screen's window (`HomeLayoutTest` checks the two agree) |
| `windowDisablePreview` in `Theme.Luncher` | `ui/src/main/res/values/themes.xml` | [`ui/README.md`](ui/README.md#while-luncher-starts): no starting window up to Android 11, as from 12 on; a launch screen there flickers on slow TVs ([`docs/archive/`](docs/archive/README.md#the-launch-screen-removed)) |
| `layoutDirection="ltr"` on the home screen's root; `textAlignment` `viewStart` in every theme; `supportsRtl="true"` in the feature modules' manifests too; `paddingStart="0dp"` on the appearance editor's options and the picture editors' pictures (the background editor's and the tile banner picker's) | `feature/home/…/home_activity.xml`, `feature/settings/…/settings_appearance_activity.xml`, `feature/settings/…/settings_pictures_column.xml`, `themes.xml` of `:ui` and `:feature:settings`, the features' `AndroidManifest.xml` | [`app/README.md`](app/README.md#manifest-why-each-part-is-there): in a right-to-left language only the settings panels, the picture library's screens and the windows over the home screen mirror, and texts line up with the screen's direction; the features' JVM tests have no app manifest; the editors' overscan margin is at their options' or pictures' end only (lint's `RtlSymmetry`) |
| `open class AppGraph`, settable `LuncherApplication.graph`; the features' `Test…Graph` and test Applications, named in `robolectric.properties` | `app/src/main/…`, `feature/*/src/test/…` | [`docs/TESTING.md`](docs/TESTING.md#organizing-tests) |
| `android:theme` on `HomeActivity`, although the application has the same | `feature/home/src/main/AndroidManifest.xml` | [`feature/home/README.md`](feature/home/README.md#manifest): the JVM tests have no app manifest |
| Manifest `<queries>`, `uses-feature`, launcher intent filters | `AndroidManifest.xml` of `:app` and `:platform` | [`app/README.md`](app/README.md#manifest-why-each-part-is-there), [`platform/README.md`](platform/README.md#manifest) |
| `distributionSha256Sum` | `gradle/wrapper/gradle-wrapper.properties` | [`gradle/README.md`](gradle/README.md#upgrading) |
| `versionCode` and `versionName` written out as literals, although the check after the `android` block could compute one from the other | `app/build.gradle.kts` | their comment, [`docs/RELEASING.md`](docs/RELEASING.md#versions): F-Droid reads them from the file |
| `--alignment-preserved true` on `apksigner sign`, and the `reproduce` job | `.github/workflows/release.yml` | [`docs/RELEASING.md`](docs/RELEASING.md#f-droid): without them F-Droid may silently stop publishing releases |
| `SharePictureActivity`'s intent filters `SEND`, `SEND_MULTIPLE` and `ATTACH_DATA` only, no `VIEW` | `app/src/main/AndroidManifest.xml` | [`app/README.md`](app/README.md#manifest-why-each-part-is-there): with `VIEW`, Luncher would be offered whenever any picture is opened |
| `PreparedPictures.isPrepared` an extension function beside the port, not a default method; no port with a default method | `domain/…/pictures/PreparedPictures.kt` | its KDoc, [`domain/README.md`](domain/README.md#writing-models-rules-and-ports): the app's code is desugared for API 22, and a fake in the instrumented tests' APK, built apart, calls a default in the interface (`AbstractMethodError` on every API level); the JVM tiers can't see it |
| `PictureFiles` decoding through `StoppableInput` with `decodeStream`, not `decodeFile` | `platform/…/pictures/PictureFiles.kt` | `decode`'s KDoc, [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md#background-work-and-changes-while-shown) point 4: a stream whose reads fail is the only way to stop a running decode (`requestCancelDecode` does nothing from API 24 on), so a dropped preview frees the one job thread at once |
| The wallpaper drawn by `FilePictureImages`, from the original, while the screens draw from working copies (`WorkingCopyImages`) | `app/…/AppGraph.kt` | the wallpaper's KDoc there, [`platform/README.md`](platform/README.md#pictures): it's stored for good, and drawn sharp from a working copy it's softer (differences of up to half the range on fine edges, measured) |
| A `file://` source refused as `Unreadable`, except in the drop folders; a shared picture read only as a stream | `platform/…/pictures/FilesPictures.kt` | [`platform/README.md`](platform/README.md#pictures): reading a path would need a storage permission, and any app can share anything |
| A JPEG's orientation read by `jpegOrientation`, `ExifInterface` only for HEIF | `platform/…/pictures/PictureFiles.kt`, `JpegOrientation.kt` | [`platform/README.md`](platform/README.md#pictures): the platform's `ExifInterface` parses in native code with known security bugs up to Android 6 |
| `PictureFiles.whole` read before a picture is added or Add from folder previews it, although a decode follows | `platform/…/pictures/FilesPictures.kt`, `MediaFolderPictures.kt`, `WholeFiles.kt` | [`platform/README.md`](platform/README.md#pictures): decoders draw a picture cut short or damaged without an error on most versions, and a damaged PNG can keep API 22 and 23's decoder running for good |
| `OutsideCalls` leaving a call's thread to it when the job gives up, never ending it, and its bound of 10 alive threads | `platform/…/outside/OutsideCalls.kt` | its KDoc, [`platform/README.md`](platform/README.md#another-apps-answers): another app's query or open can't be stopped once made, the job must go on (rule 6 of [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md#background-work-and-changes-while-shown)); the bound guards against a leak; a process of its own would cost more |
| `OutsideReader` reading only once `Os.poll` says data came, in 250 ms slices, rather than a plain read stopped by closing the stream | `platform/…/outside/OutsideReader.kt` | its KDoc, [`platform/README.md`](platform/README.md#another-apps-answers): closing a stream doesn't end a read that waits on another thread before API 36 |
| `OutsideCalls`' clock and wait for data, passed in; `AnotherApp` serving files, not pipes | `platform/…/outside/OutsideCalls.kt`, `platform/src/test/…/outside/AnotherApp.kt` | [`platform/README.md`](platform/README.md#another-apps-answers): Robolectric's `Os.poll` doesn't wait, its clock stands still, and its pipes are files |
| `PictureReceivedActivity` cancelling the import in `onDestroy` | `feature/settings/…/PictureReceivedActivity.kt` | its KDoc, [`feature/settings/README.md`](feature/settings/README.md#adding-a-picture): closing the window stops the adding through it (Back and Cancel before a picture was saved, Home, OK), and no answer comes to a window that's gone |
| `SharePictureActivity` falling back to the "can't read" message on a `SecurityException` | `feature/settings/…/SharePictureActivity.kt` | its comment, [`feature/settings/README.md`](feature/settings/README.md#adding-a-picture): a guard: the clip carries only the Uris Luncher holds a grant for, which Android passes on, but a refused grant would crash the exported activity |
| `SharePictureActivity` opening the home screen with a `HOME` intent to Luncher's package, naming no component, only while a `HOME` intent resolves to Luncher, else with `getLeanbackLaunchIntentForPackage`; the `<queries>` for `MAIN`+`HOME` | `feature/settings/…/SharePictureActivity.kt`, `feature/settings/src/main/AndroidManifest.xml` | its KDoc, [`feature/settings/README.md`](feature/settings/README.md#adding-a-picture): on API 24 to 27 a home screen opened any other way, a component named too, is a second one; a `HOME` intent while another app is the home app makes the task a home screen's, and closing it asks which home app on API 22; from API 30 on, without the query, the stock launcher is hidden and Luncher seems the default; `MAIN` + `LEANBACK_LAUNCHER` itself can't be started implicitly (no `DEFAULT`) |
| `PictureReceivedActivity` closing itself in `onStop` after `onUserLeaveHint`, not in `onUserLeaveHint`, nor in any `onStop` | `feature/settings/…/PictureReceivedActivity.kt` | its comments, [`feature/settings/README.md`](feature/settings/README.md#adding-a-picture): Home leaves the window alive on API 24 to 27 as the home app, and whenever another app is the home app, so nothing else would stop its pictures; Android gives the same hint as a share arrives over the window, which stays in sight and gets it; the TV sleeping stops the window without the user leaving it |
| `PictureReceivedActivity`'s `singleTop`, and `SharePictureActivity` sending the window alone while a session of `ReceivedPictures` is open and a task of Luncher's has the window on top (`getAppTasks`), else holding the session and opening a window that takes it over; the window ending its session in `finish`, not only `onDestroy`, and only while it holds it (`ReceivedPictures.holder`); `SharePictureActivity` passing the access to the pictures still to add | `feature/settings/src/main/AndroidManifest.xml`, `feature/settings/…/SharePictureActivity.kt`, `PictureReceivedActivity.kt` | their KDoc, [`feature/settings/README.md`](feature/settings/README.md#adding-a-picture): the home screen, `singleTask`, would close the window and stop the pictures it adds; a share that came right after OK would join a session that's closing; on API 29 Android closes Luncher's task when another app's opens, and a share then comes to a new window before the old one is destroyed: the old one must not end the session the new one took over, nor take the pictures' access with it |
| `TileMenu.placeAt` setting the window's animation at each opening, by the side the menu is on, although the theme has one (`Animation.Luncher.Home.Menu.*`) | `feature/home/…/TileMenu.kt`, `feature/home/src/main/res/values/themes.xml` | [`feature/home/README.md`](feature/home/README.md#how-the-menu-comes-and-goes): the menu grows out of the edge that faces its tile; the theme's one style would grow every menu from the same edge |
| On API 22 and 23, `WindowEntrance` playing the entrance in the window (`TileMenu`'s, `PanelDialog`'s), the window's style with the exit only, the entrance started at the first pre-draw; the dialogs' exit-only style with an entrance that changes nothing (`anim/panel_none`), not `@null` | `ui/…/WindowEntrance.kt`, `ui/…/Dialogs.kt`, `feature/home/…/TileMenu.kt`, `ui/src/main/res/values/themes.xml` | their KDoc, [`ui/README.md`](ui/README.md#the-design-system), [`feature/home/README.md`](feature/home/README.md#how-the-menu-comes-and-goes): Android 5.1 and 6.0 play no app dialog's window entrance, so the menu and the dialogs popped in there; started at `show`, it could end before a slow TV's first frame; with `@null`, Android 5.1 played no exit either for a dialog that dims the screen |
| `Animation.Luncher.Panel` with activity items (`activityOpenEnterAnimation`, `activityCloseExitAnimation`) beside its window items | `ui/src/main/res/values/themes.xml` | its comment, [`ui/README.md`](ui/README.md#the-design-system): the settings panel, the panels in its place and the window after a share are activities, whose windows play only those; the window items alone left them Android's animations, none at all on Android 16 |
| `Handover.from` showing the next window first, the old one closing only once the next has drawn (`PanelDialog.takeOver`), with no animation, no key, its own `dismiss()` ignored meanwhile, and the next's dim waiting for it to go; the working window's Cancel not a handover | `ui/…/Handover.kt`, `Dialogs.kt`, `WorkingDialog.kt` | their KDoc, [`ui/README.md`](ui/README.md#the-design-system): closing the old one first dropped the dim for a moment and left the screen bare while the first dialog of a process was made; both dimming made the two darker than one (from Android 11; before it the window handed over to plays its entrance in itself and keeps its dim, since a dim faded with the window animation left the screen bare for the time it took to ramp up; and the old window waits for the new one's first rendered frame, `FrameMetrics`, not its first draw); a screen replacing its dialog (`PictureActions.show`) closed the old one before the next showed; a window whose action finishes its screen is left to the screen's exit (closed first, the screen showed bright for about 50 ms); Cancel closes at once, before what it does |
| `Animation.Luncher.Screen` set in the full-screen themes, with `screen_hold_*` for the screen under, never in `Theme.Luncher` | `feature/settings/src/main/res/values/themes.xml`, `ui/…/themes.xml` | its comment, [`ui/README.md`](ui/README.md#the-design-system): `Theme.Luncher` is also the home screen's, which opens apps and comes back from them with Android's animation |
| `SharePictureActivity` starting its window with `makeCustomAnimation` only while `Foreground.inFront`, none from another app | `feature/settings/…/SharePictureActivity.kt`, `ui/…/Foreground.kt` | their KDoc, [`ui/README.md`](ui/README.md#the-design-system): Android's task cross-fade, kept from another app, is replaced by any custom animation; with Luncher in front there is none, and the window popped in |
| `AppTileView`'s own hold calling `cancelLongPress()` before it long-clicks, and consuming OK's release after it (`holdOpened`) | `feature/home/…/AppTileView.kt` | their comments, [`feature/home/README.md`](feature/home/README.md#a-tiles-menu): without the first, Android's own long press, still timed from OK down, opens the menu a second time; without the second, a release that reaches the tile (from Android 12 on, before the menu is drawn and takes the focus: a release 30 ms after the menu opened did on tv_api36) clicks it, and opens the app as the menu shows |
| `windowSoftInputMode="adjustNothing"` on `HomeActivity`, `BackgroundActivity` and `TileBannerActivity`, which have no field; `NameDialog` moving its window to the top while `WindowInsets.isVisible(ime())` (API 30+), not by the keyboard's height, and its window's `fitInsetsTypes` set without `ime()` | `feature/home/src/main/AndroidManifest.xml`, `feature/settings/src/main/AndroidManifest.xml`, `ui/…/Dialogs.kt` | their comments, [`ui/README.md`](ui/README.md#the-design-system): the screen under a rename dialog would slide up to keep its own focused tile or picture clear of the keyboard; the keyboard's height reads 0 on some versions, and the window changed before the keyboard is up keeps it down; on API 30 a dialog with a field shrinks to the room above the keyboard unless its window says which insets it fits, and on 720p at 320 dpi that cut the field's text in half |
| The appearance editor reading the appearance again in `onResume`, not `onRestart` | `feature/settings/…/AppearanceActivity.kt` | its comment: the chooser can close before the editor was stopped |
| `SamplePictures`, `StalledPictures`, `GrantedPictures` and `ShareSender` in Java; all but `GrantedPictures` exported | `app/src/androidTest/…/testing/` | [`docs/INSTRUMENTED-TESTS.md`](docs/INSTRUMENTED-TESTS.md#pictures-from-another-app-and-the-tests-own-folders): it runs in the test package's process, which has no Kotlin standard library |
| `waitUntilSeen()` (UI Automator), not Espresso, for the share window while it adds a picture | `app/src/androidTest/…/testing/Seen.kt`, `…/system/PictureShareTest.kt` | its KDoc, [`docs/INSTRUMENTED-TESTS.md`](docs/INSTRUMENTED-TESTS.md#look-with-ui-automator-while-a-screen-animates): on API 24 the spinning wheel keeps the main thread busy, which Espresso waits out |
| `taskAffinity=""` on `SharePictureActivity`, and its window sent with `NEW_TASK` | `feature/settings/src/main/AndroidManifest.xml`, `feature/settings/…/SharePictureActivity.kt` | their comments, [`feature/settings/README.md`](feature/settings/README.md#manifest): with Luncher's affinity, a second share alike sent with `NEW_TASK` only brought back the task the first one made, and its picture never arrived; without the flag the window joins the share's task, where the home screen's, brought back, can come over it (API 28) |
| `PictureShareTest` pressing Home after each test (`goHome`), and being in the system tier | `app/src/androidTest/…/system/PictureShareTest.kt` | its KDoc, [`docs/INSTRUMENTED-TESTS.md`](docs/INSTRUMENTED-TESTS.md#pictures-from-another-app-and-the-tests-own-folders): closed in front, the share's tasks leave another app's screen, or API 22's "choose home app", in front of the next test |
