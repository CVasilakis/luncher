# Testing

Tests protect behavior from regressions. Every test has a fixed place, determined by what it
needs to run, so a change comes with tests in the matching place and nowhere else.
[`ARCHITECTURE.md`](ARCHITECTURE.md) explains the layers these tiers follow;
[What each tier needs](#what-each-tier-needs) lists what to install.

## Tiers

| Tier | What it checks | Tool | Location | Runs on | Command |
|---|---|---|---|---|---|
| Domain unit | rules and models in `:domain` | JUnit 4 | `domain/src/test/kotlin/` | JVM | `./gradlew :domain:test` |
| App JVM | adapters and screens on a simulated Android (API 36) | Robolectric | `app/src/test/java/` | JVM | `./gradlew :app:testDebugUnitTest` |
| Screenshots | how screens look on a 1080p TV, including D-pad focus states | Roborazzi (on Robolectric) | `app/src/test/java/…/<feature>/*ScreenshotTest.kt`, images in `app/src/test/screenshots/<feature>/` | JVM | `./gradlew :app:verifyRoborazziDebug` |
| In-app | a screen's behavior with real key events: D-pad focus, keys, Back | Espresso | `app/src/androidTest/java/…/<feature>/` | emulator | `./gradlew connectedDebugAndroidTest` |
| System | Luncher as the home screen: Home key, other apps, returning | UI Automator | `app/src/androidTest/java/…/system/` | emulator | `./gradlew connectedDebugAndroidTest` |

Everything at once (with the emulator running for the last two tiers):

```bash
./gradlew :domain:test :app:testDebugUnitTest :app:verifyRoborazziDebug connectedDebugAndroidTest
```

## What each tier needs

| Tier | Needs |
|---|---|
| Domain unit | JDK 17+ |
| App JVM, screenshots | **JDK 21+**: Robolectric runs the API 36 framework, which needs Java 21 |
| In-app, system | a running Android TV emulator or device on API 25+ (see [`README.md`](../README.md#emulators-and-the-android-tv-wsl-dev-tools-scripts)); before committing, emulators of API 25, 28, 30, 33 and 36 ([below](#on-several-android-versions)) |

The test libraries download automatically on the first test run (sizes in
[`README.md`](../README.md#requirements)); no extra SDK packages are needed. If the Robolectric tests
fail with a Java version error, point `JAVA_HOME` at a JDK 21.

Test libraries are only in `testImplementation`/`androidTestImplementation`, so none of them reach
the APK. Their versions are in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml).

## Which tier for which test

Use the lowest tier that can catch the regression; each step up is slower and more fragile.

- **A decision** (which apps show, their order, hidden apps, banner choice, validating a setting):
  domain unit test. This is where most tests belong.
- **An adapter** (does the PackageManager query or the preferences storage work): app JVM test,
  with Robolectric's shadows standing in for the system (e.g. `shadowOf(packageManager)` to
  install fake apps with intent filters).
- **What a screen shows, and how it reacts to lifecycle changes**: app JVM test
  (`Robolectric.buildActivity(…)`). **How it looks**: screenshot test.
- **Real key events on a screen** (D-pad focus movement, OK, Back): Espresso.
- **Anything across apps or the system** (Home key, launching an app and returning, being the
  default home): UI Automator.

A test has to be able to fail in its tier. For example, Back on the real home screen is
tested with Espresso, not UI Automator: Android restarts a home activity that closes, so from the
outside a broken Back looks the same as a working one.

## Organizing tests

- **Mirror the code.** A test lives in the same package as the code it tests; its class is named
  after that code (`PackageManagerInstalledAppsTest`) or screen (`HomeActivityTest`,
  `HomeScreenshotTest`); system tests after the flow (`HomeKeyTest`).
- **Name tests after behavior.** JVM tests use backtick sentences
  (`` `counts again when the home screen comes back` ``). `androidTest` uses
  `action_expectedResult` (`backKey_doesNotCloseTheHomeScreen`), because DEX files before API 30
  don't allow spaces in method names and the tests run on API 25.
- **One set of fakes.** Fakes of the domain ports (`FakeInstalledApps`, …) live in `:domain`'s
  test fixtures, `domain/src/testFixtures/kotlin/`, and every tier uses them. When a port changes,
  its fake changes in one place. A new port gets a fake there.
- **Replace ports through `AppGraph`**, never by reaching into a screen:
  ```kotlin
  application.graph = object : AppGraph(application) {
      override val installedApps = FakeInstalledApps(app("movies"), app("music"))
  }
  ```
  Under Robolectric each test gets a fresh application. In `androidTest` the process outlives the
  test, so restore it in `@After` with `application.graph = AppGraph(application)`.
- **TV screen configuration.** Robolectric tests of screens use `@Config(qualifiers = TV_1080P)`
  (`app/src/test/java/com/luncher/launcher/TvDevice.kt`): 960×540 dp at xhdpi, landscape, TV UI
  mode, D-pad, no touch.
- **Leave the device as you found it.** Instrumented tests that change system state restore it;
  e.g. `HomeKeyTest` disables other home apps so Luncher is home, and re-enables them afterwards.

## Screenshot tests

```bash
./gradlew :app:recordRoborazziDebug    # write/overwrite the reference images
./gradlew :app:verifyRoborazziDebug    # fail if a screen differs from its reference image
./gradlew :app:compareRoborazziDebug   # write diff images to app/build/outputs/roborazzi/
```

Reference images are committed. When a change alters a screen on purpose, record, look at the new
image, and commit it with the change. A plain `testDebugUnitTest` runs the screenshot tests without
comparing images. Screenshots need Robolectric's native graphics
(`@GraphicsMode(GraphicsMode.Mode.NATIVE)`), available on Linux x86-64, macOS and Windows.

## Instrumented tests (Espresso, UI Automator)

`connectedDebugAndroidTest` runs on every connected device (point it at one with
`ANDROID_SERIAL`), typically an emulator booted with android-tv-wsl-dev-tools' `start-emulator.sh`. The build adjusts
AGP's behavior in three places (reasons in [`app/build.gradle.kts`](../app/build.gradle.kts) and
`gradle.properties`):

- Each run starts with `uninstallAll`: AGP's test engine installs without `-r`, which API 25
  refuses when the app is already installed.
- `checkConnectedTestsRan` fails the build when a device ran no test, naming it; AGP reports
  success when the install fails, even if the other devices ran their tests.
- `android.injected.androidTest.leaveApksInstalledAfterRun=true` keeps Luncher installed afterwards,
  so a device with the stock launcher disabled still has a home screen.

### On several Android versions

Luncher supports API 25 and newer, and behavior differs between versions, so the instrumented
tests run on five emulators:

| Emulator | Android | Why this one |
|---|---|---|
| `tv_api25` | 7.1 | the oldest supported |
| `tv_api28` | 9 | stock launcher `tvlauncher` instead of `leanbacklauncher` |
| `tv_api30` | 11 | package visibility: other apps are hidden from Luncher unless the manifest's `<queries>` names them |
| `tv_api33` | 13 | `OnBackInvokedCallback` exists, but Back still calls `onBackPressed()`: the path `HomeActivity` relies on up to API 35 |
| `tv_api36` | 16 | the `targetSdk`: Back no longer calls `onBackPressed()` |

With several booted, one run covers them all, in parallel. The first lines use
android-tv-wsl-dev-tools' scripts; any other way to create and boot the emulators works
([`README.md`](../README.md#emulators-and-the-android-tv-wsl-dev-tools-scripts)):

```bash
for api in 28 30 33 36; do create-avd.sh --api $api; done   # once; needs the system images, see
                                                             # android-tv-wsl-dev-tools' SETUP.md, step 5
for avd in tv_api25 tv_api28 tv_api30 tv_api33 tv_api36; do start-emulator.sh "$avd"; done
./gradlew connectedDebugAndroidTest                    # runs on every booted emulator
ANDROID_SERIAL=emulator-5558 ./gradlew connectedDebugAndroidTest   # only one (serials: adb devices)
```

Results are per device, in `app/build/outputs/androidTest-results/connected/debug/TEST-<device>.xml`
and `app/build/reports/androidTests/connected/debug/`. How many run at once is up to you: an
emulator takes ~2 GB of RAM on API 25 and 28 and ~3–3.4 GB on 30, 33 and 36, so all five need
~13 GB. With less, boot them in batches (e.g. 25 and 36, then 28, 30 and 33) or one at a time, stopping each with `adb -s <serial> emu kill` before the next.
The API 36 image also takes 8.2 GB of disk.

## Rules

- **Every change comes with tests** in the right tier. A bug fix starts with a test that fails
  because of the bug.
- **A new test must be able to fail.** Break the behavior on purpose, see it fail, restore.
- **Before committing**, run the tiers that cover what changed: at least the JVM tiers, and the
  instrumented tiers when a screen's keys or the home behavior changed, on the API 25, 28, 30, 33
  and 36 emulators.
- **Tests stay deterministic.** No fixed sleeps where a condition can be awaited, no dependence
  on the host's or emulator's other state, no network.
