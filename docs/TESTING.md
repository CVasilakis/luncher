# Testing

Tests protect behavior from regressions. Every test has a fixed place, determined by what it
needs to run, so a change comes with tests in the matching place and nowhere else.
[`ARCHITECTURE.md`](ARCHITECTURE.md) explains the layers these tiers follow.

## Tiers

| Tier | What it checks | Tool | Location | Runs on | Command |
|---|---|---|---|---|---|
| Domain unit | rules and models in `:domain` | JUnit 4 | `domain/src/test/kotlin/` | JVM | `./gradlew :domain:test` |
| App JVM | adapters and screens on a simulated Android (API 36) | Robolectric | `app/src/test/java/` | JVM | `./gradlew :app:testDebugUnitTest` |
| Screenshots | how screens look on a 1080p TV, including D-pad focus states | Roborazzi (on Robolectric) | `app/src/test/java/…/<feature>/*ScreenshotTest.kt`, images in `app/src/test/screenshots/<feature>/` | JVM | `./gradlew :app:verifyRoborazziDebug` |
| In-app | a screen's behavior with real key events: D-pad focus, keys, Back | Espresso | `app/src/androidTest/java/…/<feature>/` | emulator | `./gradlew connectedDebugAndroidTest` |
| System | Luncher as the home screen: Home key, other apps, returning | UI Automator | `app/src/androidTest/java/…/system/` | emulator, API 24+ ([why](#the-system-tier-from-api-24-on)) | `./gradlew connectedDebugAndroidTest` |

Everything at once (with the emulator running for the last two tiers):

```bash
./gradlew :domain:test :app:testDebugUnitTest :app:verifyRoborazziDebug connectedDebugAndroidTest
```

What to install for them (the JDK for the JVM tiers, a device for the others):
[`README.md`](../README.md#requirements). The test libraries download on the first run; their
versions are in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml). They're only in
`testImplementation`/`androidTestImplementation`, so none of them reach the APK.

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
  (`` `reads the apps again when the home screen comes back` ``). `androidTest` uses
  `action_expectedResult` (`backKey_doesNotCloseTheHomeScreen`), because DEX files before API 30
  don't allow spaces in method names and the tests run on API 22.
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
  (`app/src/test/java/com/luncher/launcher/TvDevice.kt`). Tests that check text the language
  formats (the clock) also fix the language, `"en-rUS-$TV_1080P"`, and replace the `Clock` port
  with a `FakeClock`, so the result depends neither on the host's time nor on its time zone.
- **Leave the device as you found it.** Instrumented tests that change system state restore it
  afterwards (e.g. `HomeKeyTest`, which disables other home apps to make Luncher the home), and
  then wait 30 s, only if they changed something, logging why (logcat; Gradle doesn't show a
  test's output): Android saves such changes seconds later, and an emulator stopped right after
  the run would otherwise boot with the test's state
  ([`app/README.md`](../app/README.md#luncher-as-the-home-screen)). No condition to await shows
  when it's saved, so this is the one fixed wait the tests have. Found as it was includes what's
  on screen: `HomeKeyTest` presses Home once the stock launcher is back, so it starts (cold, and
  on API 36 sometimes with a promotion of its own over it) during that wait rather than while
  the next test runs, and ends on the settled home screen.

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
`ANDROID_SERIAL`), typically an emulator booted with android-tv-wsl-dev-tools' `start-emulator.sh`.
The build changes three things about AGP's runs (the reasons are in comments in
[`app/build.gradle.kts`](../app/build.gradle.kts) and `gradle.properties`):

- each run first uninstalls Luncher (`uninstallAll`), so it starts from a clean install;
- the build fails, naming the device, when a device ran no test (`checkConnectedTestsRan`);
- Luncher stays installed afterwards, so a device with the stock launcher disabled still has a
  home screen.

Every instrumented test that opens a screen starts from the device's home screen, settled, so none
depends on what an earlier test or the boot left behind: its `@Before` calls `waitForHomeScreen()`
(`app/src/androidTest/java/…/HomeApp.kt`, which says what settled means and why). The home app
starts whenever a test closes its activities, and some stock launchers then open screens of their
own over whatever a test has started meanwhile. A new test class does the same. On the first
boot of a new emulator, API 23 and 29 can open "USB drive connected" in front of
the home app, which stays until Back ([`app/README.md`](../app/README.md#luncher-as-the-home-screen)):
the helper presses Back on that screen, named, and on no other, since the tests can run on
someone's TV.

On API 22 and 23, AGP's test engine prints `Failed to retrieve additional test outputs from
device` with a long `File name too long` stack trace after the tests. It's harmless: the tests have
run, and Luncher writes no additional test output. Turning that feature off
(`android.enableAdditionalTestOutput=false`) makes AGP 9's `connectedDebugAndroidTest` fail
instead.

Run the instrumented tests with the stock launcher enabled, and on API 22 without having chosen
Luncher as home ("Always"). Otherwise Luncher is the device's home app
([`app/README.md`](../app/README.md#luncher-as-the-home-screen)), and Android starts it again as
soon as an in-app test closes it; the closing activity can stay paused, and the test would time
out although its checks passed. `HomeActivityTest` therefore fails at once, saying so, while
Luncher is the home app. Re-enable the stock launcher first, e.g.
`adb shell pm enable com.google.android.tvlauncher`. The system tier doesn't need it disabled:
`HomeKeyTest` makes Luncher the home app for its own run. While it has the stock launcher
disabled, the device log can show that launcher crashing as Android starts its process anyway (on
the API 34 Android TV emulator, twice per run: `Tried to schedule job for non-existent component
… DailyCheckInService`). That's harmless: the launcher is disabled, and nothing of it is on screen.

On API 22 the "choose home app" dialog shows up during the run and stays on screen afterwards.
That's expected: when a test closes its activity, Android goes Home, which asks there. It doesn't
disturb the tests: each one starts its activity above the dialog, and Espresso sends keys only
once that activity's window has focus. Leave the dialog unanswered (Back closes it).

### The system tier from API 24 on

The system tier runs only on API 24 and newer; on API 22 and 23 the in-app tier still runs.
`SystemTierFilter` (`app/src/androidTest/java/…/SystemTierFilter.kt`, which says why) leaves out
every test in the `system` package on older devices, so a new system test needs nothing of its
own: putting it in `system/` is enough. Luncher as the home screen on API 22 and 23 is checked by
hand ([`app/README.md`](../app/README.md#luncher-as-the-home-screen)).

### On several Android versions

Luncher supports API 22 and newer, and behavior differs between versions, so the instrumented
tests run on six emulators:

| Emulator | Android | Why this one |
|---|---|---|
| `tv_api22` | 5.1 | the oldest supported; the in-app tier only |
| `tv_api24` | 7.0 | the oldest the system tier runs on |
| `tv_api28` | 9 | stock launcher `tvlauncher` instead of `leanbacklauncher` |
| `tv_api30` | 11 | package visibility: other apps are hidden from Luncher unless the manifest's `<queries>` names them |
| `tv_api33` | 13 | `OnBackInvokedCallback` exists, but Back still calls `onBackPressed()`: the path `HomeActivity` relies on up to API 35 |
| `tv_api36` | 16 | the `targetSdk`: Back no longer calls `onBackPressed()` |

With several booted, one run covers them all, in parallel. The first lines use
android-tv-wsl-dev-tools' scripts; any other way to create and boot the emulators works
([`README.md`](../README.md#emulators-and-the-android-tv-wsl-dev-tools-scripts)):

```bash
for api in 22 24 28 30 33 36; do create-avd.sh --api $api; done   # once; needs the system images,
                                                                   # see android-tv-wsl-dev-tools' SETUP.md, step 5
for avd in tv_api22 tv_api24 tv_api28 tv_api30 tv_api33 tv_api36; do start-emulator.sh "$avd"; done
./gradlew connectedDebugAndroidTest                    # runs on every booted emulator
ANDROID_SERIAL=emulator-5558 ./gradlew connectedDebugAndroidTest   # only one (serials: adb devices)
```

Results are per device, in `app/build/outputs/androidTest-results/connected/debug/TEST-<device>.xml`
and `app/build/reports/androidTests/connected/debug/`. How many run at once is up to you: an
emulator takes ~2 GB of RAM on API 22, 24 and 28 and ~3–3.4 GB on 30, 33 and 36, so all six need
~16 GB. With less, boot them in batches (e.g. 22, 24 and 36, then 28, 30 and 33) or one at a time,
stopping each with `stop-emulator.sh <avd>` before the next. The API 36 image also takes 8.2 GB
of disk.

### Without android-tv-wsl-dev-tools

The tests don't need the tools: an emulator from Android Studio, or one started with the SDK's
`emulator` command, or a TV device works. What's up to you:

- **Wait for the boot** before installing or testing: an install started earlier fails. E.g.
  `until adb shell getprop sys.boot_completed 2>/dev/null | grep -q 1; do sleep 2; done`
  (`adb wait-for-device` alone isn't enough: while an emulator boots, adb can list it as `offline`
  for a moment, and a command sent then fails).
- **"offline" after a Quick Boot.** Android Studio resumes an emulator from a snapshot by default,
  and adb can then list it as `offline` for good, so Gradle says the device is offline. Run
  `adb reconnect offline`, or cold boot it (Device Manager → Cold Boot Now, or
  `emulator -avd <name> -no-snapshot-load`).
- **The stock launcher enabled**, and on API 22 no "Always" for Luncher
  ([above](#instrumented-tests-espresso-ui-automator)).

What the tests take care of themselves:

- **The settled home screen**, after the boot and between tests, including Back on "USB drive
  connected" after a first boot (`waitForHomeScreen()`, [above](#instrumented-tests-espresso-ui-automator)).
- **Luncher as the home app**: `HomeKeyTest` disables the other home apps for its run, enables them
  again, and waits 30 s so Android saves that.
- **`tv_user_setup_complete`**, without which API 26 and 27 ignore Home: `HomeKeyTest` sets it for
  its run and puts the old value back.

## In CI

Two GitHub Actions workflows run the tests. The reasons for their individual steps are in comments
in the workflow files.

| Workflow | Runs | When |
|---|---|---|
| [`jvm-tests.yml`](../.github/workflows/jvm-tests.yml) | the JVM tiers, and `assembleRelease` to check that R8 shrinking still works | every push to `main`, every pull request, and by hand |
| [`instrumented-tests.yml`](../.github/workflows/instrumented-tests.yml) | the emulator tiers, one emulator per job | only by hand (Actions → Instrumented tests → Run workflow), then pick the emulators below |

The instrumented workflow creates and boots its emulators with android-tv-wsl-dev-tools, pinned to
a release tag. It boots them with `start-emulator.sh --wait-for-home`, which returns once the home
app is in front with the focus, for a few seconds in a row, rather than as soon as Android has
booted: on API 34, tests started right after the boot once found no window with the focus, ever.
It offers every Android TV and Google TV image that release is tested with, from API 22 on, named
`android_tv_api<level>` and `google_tv_api<level>`:

| Choice | Emulators |
|---|---|
| `required-22-24-28-30-33-36` (default) | the Android TV images of the [six emulators above](#on-several-android-versions) |
| `android-tv-all` | every Android TV image |
| `google-tv-all` | every Google TV image (they start at API 30) |
| `all` | both |
| one name, e.g. `google_tv_api33` | that emulator only |

When a job fails, it uploads its test reports as artifacts, and for an emulator job the device's
log, its windows (`dumpsys window`: `mCurrentFocus` is the focused window, `mFocusedApp` the
focused activity), a screenshot, and adb's server log.

## Rules

- **Every change comes with tests** in the right tier. A bug fix starts with a test that fails
  because of the bug.
- **A new test must be able to fail.** Break the behavior on purpose, see it fail, restore.
- **Before committing**, run the tiers that cover what changed: at least the JVM tiers, and the
  instrumented tiers when a screen's keys or the home behavior changed, on the
  [six emulators](#on-several-android-versions).
- **Tests stay deterministic.** No fixed sleeps where a condition can be awaited, no dependence
  on the host's or emulator's other state, no network.
- **Test libraries are dependencies too.** They never reach the APK, but adding one is as
  deliberate a decision as adding a runtime library ([`ARCHITECTURE.md`](ARCHITECTURE.md#rules)).
