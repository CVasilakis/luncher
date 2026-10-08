# Testing

Tests protect behavior from regressions. Every test has a fixed place, determined by what it
needs to run, so a change comes with tests in the matching place and nowhere else.
[`ARCHITECTURE.md`](ARCHITECTURE.md) explains the layers these tiers follow.

## Tiers

| Tier | What it checks | Tool | Location | Runs on | Command |
|---|---|---|---|---|---|
| Domain unit | rules and models in `:domain` | JUnit 4 | `domain/src/test/kotlin/` | JVM | `./gradlew :domain:test` |
| Android JVM | adapters and screens on a simulated Android (API 36; the layout checks also API 33) | Robolectric | `src/test/java/` of `:platform`, each feature and `:app` | JVM | `./gradlew testDebugUnitTest` |
| Screenshots | how screens look on a 1080p TV, including D-pad focus states, and on a few other screens | Roborazzi (on Robolectric) | `feature/<name>/src/test/java/…/*ScreenshotTest.kt`, images in `feature/<name>/src/test/screenshots/<name>/` | JVM | `./gradlew verifyRoborazziDebug` |
| In-app | a screen's behavior with real key events: D-pad focus, keys, Back | Espresso | `app/src/androidTest/java/…/<feature>/` | emulator | `./gradlew connectedDebugAndroidTest` |
| System | Luncher as the home screen: Home key, other apps, returning | UI Automator | `app/src/androidTest/java/…/system/` | emulator, API 24+ ([why](INSTRUMENTED-TESTS.md#the-system-tier-from-api-24-on)) | `./gradlew connectedDebugAndroidTest` |

Everything at once, [lint](#lint-and-compiler-warnings) included (with the emulator running for
the last two tiers):

```bash
./gradlew :domain:test testDebugUnitTest verifyRoborazziDebug :app:lintDebug connectedDebugAndroidTest
```

What to install for them (the JDK for the JVM tiers, a device for the others):
[`README.md`](../README.md#requirements). The test libraries download on the first run; their
versions are in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml). They're only in
`testImplementation`/`androidTestImplementation`, so none of them reach the APK.

The two emulator tiers have a document of their own,
[`INSTRUMENTED-TESTS.md`](INSTRUMENTED-TESTS.md): running them, on which emulators, and what a
test must do so that the device can't make it fail.

## Which tier for which test

Use the lowest tier that can catch the regression; each step up is slower and more fragile.

- **A decision** (which apps show, their order, hidden apps, banner choice, validating a setting):
  domain unit test. This is where most tests belong.
- **An adapter** (does the PackageManager query or the preferences storage work): Android JVM test,
  with Robolectric's shadows standing in for the system (e.g. `shadowOf(packageManager)` to
  install fake apps with intent filters).
- **What a screen shows, and how it reacts to lifecycle changes**: Android JVM test
  (`Robolectric.buildActivity(…)`). **How it looks**: screenshot test.
- **Real key events on a screen** (D-pad focus movement, OK, Back): Espresso.
- **Anything across apps or the system** (Home key, launching an app and returning, being the
  default home): UI Automator.

A test has to be able to fail in its tier. For example, Back on the real home screen is
tested with Espresso, not UI Automator: Android restarts a home activity that closes, so from the
outside a broken Back looks the same as a working one.

## Organizing tests

- **Mirror the code.** A JVM test lives in the module and package of the code it tests; its class
  is named after that code (`PackageManagerInstalledAppsTest`) or screen (`HomeActivityTest`,
  `HomeScreenshotTest`); system tests after the flow (`HomeKeyTest`). The instrumented tests of
  every module are in `:app` (`app/src/androidTest/`), in the package of the screen they test:
  they need the installed app as the device's home screen. The library modules have none
  ([`build-logic/`](../build-logic/README.md)).
- **Test infrastructure in `testing/`.** What the tests of several features share is in a package
  `com.luncher.launcher.testing`, with the tests of that infrastructure itself: for the JVM tests,
  `:ui`'s test fixtures (the TV screens, layout checks), which every module's tests can use; for
  the instrumented tests, `app/src/androidTest/…/testing/` (the waits for the home screen and the
  focus, keys, `RetryWhenCovered`, `SystemTierFilter`). A feature's test package holds only that
  feature's tests.
- **Name tests after behavior.** JVM tests use backtick sentences
  (`` `reads the apps again when the home screen comes back` ``). `androidTest` uses
  `action_expectedResult` (`backKey_doesNotCloseTheHomeScreen`), because DEX files before API 30
  don't allow spaces in method names and the tests run on API 22.
- **One set of fakes.** Fakes of the domain ports (`FakeInstalledApps`, …) live in `:domain`'s
  test fixtures, `domain/src/testFixtures/kotlin/`, and every tier uses them. When a port changes,
  its fake changes in one place. A new port gets a fake there.
- **Replace ports through the graph**, never by reaching into a screen. A feature's JVM tests run
  with an Application of their own (`HomeTestApplication`, named in the module's
  `src/test/resources/robolectric.properties`), which holds the feature's graph; a test gives it
  the feature's test graph, which has fakes, with its own ports in their place:
  ```kotlin
  application.graph = object : TestHomeGraph(application) {
      override val installedApps = FakeInstalledApps(app("movies"), app("music"))
  }
  ```
  The instrumented tests run on the real app, and replace ports in `AppGraph`, the same way:
  `object : AppGraph(application) { override … }`. Under Robolectric each test gets a fresh
  application. In `androidTest` the process outlives the test, so restore it in `@After` with
  `application.graph = AppGraph(application)`.
- **TV screen configuration.** Robolectric tests of screens use `@Config(qualifiers = TV_1080P)`
  (`ui/src/testFixtures/java/com/luncher/launcher/testing/TvDevice.kt`). Tests that check text
  the language formats (the clock) also fix the language, `"en-rUS-$TV_1080P"`, and replace the
  `Clock` port with a `FakeClock`, so the result depends neither on the host's time nor on its
  time zone.
  Other screens: [Layouts on other screens](#layouts-on-other-screens).
- **A configuration change** (another resolution over HDMI, another language) recreates the
  activity: `RuntimeEnvironment.setQualifiers("+tvdpi")`, then
  `controller.configurationChange().visible()`. Robolectric attaches the new activity's window
  only on `visible()`; without it, nothing in the new activity is laid out.

## Screenshot tests

```bash
./gradlew recordRoborazziDebug    # write/overwrite the reference images, in every feature
./gradlew verifyRoborazziDebug    # fail if a screen differs from its reference image
./gradlew compareRoborazziDebug   # write diff images to feature/<name>/build/outputs/roborazzi/
```

Reference images are committed. When a change alters a screen on purpose, record, look at the new
image, and commit it with the change. A plain `testDebugUnitTest` runs the screenshot tests without
comparing images. Screenshots need Robolectric's native graphics
(`@GraphicsMode(GraphicsMode.Mode.NATIVE)`), available on Linux x86-64, macOS and Windows.

## Layouts on other screens

TVs aren't all 960×540 dp: 720p at the 1080p density is 640×360 dp, some TV boxes run 1080p at
240 or 160 dpi (1280 or 1920 dp wide), screens can be 4:3, 16:10 or 21:9, and the user can make
text larger. `HomeLayoutTest` and `SettingsLayoutTest` run on each of these screens, `TV_SCREENS` in
`ui/src/testFixtures/…/testing/TvDevice.kt`, and check rules rather than pixels (`LayoutChecks.kt`):
everything inside the TV's overscan margin, tiles clear of each other and of the top bar even
zoomed, tiles of about the size meant, no text cut, panels a margin from the screen's edges. A
new element of a screen gets its check there, a new screen a `*LayoutTest` of its own, and a
screen to support a line in `TV_SCREENS`. Their text checks need native graphics, like the
screenshots: without them, Robolectric measures every character as 1 px wide. A few of these
screens also have reference images (`home_screen_4by3.png`, …).

The layout tests run on two Android versions, API 33 and 36 (`@Config(sdk = …)`), since they
scale large text differently: from API 34 on, Android grows large text less than small, so at the
largest text size the 32 sp clock stays about 32 dp, where API 22 to 33 make it 41.6 dp. API 33
stands for all of 22 to 33, which scale text alike; every screen runs on both, so a difference
between the versions other than the text shows too. The other JVM tests run on API 36 only.

## Lint and compiler warnings

```bash
./gradlew :app:lintDebug    # every module; report: app/build/reports/lint-results-debug.html
```

Lint finds what no JVM test can: the JVM tiers run on API 36's framework (the layout tests on
33's too), so an API used below the level it exists on (`NewApi`) passes them, and the emulators
catch it only on a path a test takes. Lint runs from `:app` over every module, with the app's
merged manifest; a library's own `lintDebug` lacks it, and reports e.g. a missing TV banner. Every
lint warning fails it, and every Kotlin compiler warning fails the build, in every module. A
warning is fixed, or, where it doesn't apply, made an exception with its reason: for lint in the
root [`lint.xml`](../lint.xml), or a module's own `lint.xml` for its files; in Kotlin with
`@Suppress` and a comment (as in `ui`'s `Colors.kt`). Lint's checks that a newer SDK or library
exists are off, since they would fail the build the day one comes out, without a change in the
repository.

## In CI

Three GitHub Actions workflows; the reasons for their individual steps are in comments in the
workflow files.

| Workflow | Runs | When |
|---|---|---|
| [`jvm-tests.yml`](../.github/workflows/jvm-tests.yml) | the JVM tiers, lint, and `assembleRelease` to check that R8 shrinking still works | every push to `main`, every pull request, and by hand |
| [`instrumented-tests.yml`](../.github/workflows/instrumented-tests.yml) | the emulator tiers, one emulator per job ([`INSTRUMENTED-TESTS.md`](INSTRUMENTED-TESTS.md#in-ci)) | only by hand |
| [`release.yml`](../.github/workflows/release.yml) | no tests: makes a release ([`RELEASING.md`](RELEASING.md)) | by hand |

## Rules

- **Every change comes with tests** in the right tier. A bug fix starts with a test that fails
  because of the bug.
- **A new test must be able to fail.** Break the behavior on purpose, see it fail, restore.
- **Before committing**, run the tiers that cover what changed: at least the JVM tiers and lint,
  and the instrumented tiers when a screen's keys or the home behavior changed, on the
  [six emulators](INSTRUMENTED-TESTS.md#on-several-android-versions).
- **Tests stay deterministic.** No fixed sleeps where a condition can be awaited, no dependence
  on the host's or emulator's other state, no network.
- **Test libraries are dependencies too.** They never reach the APK, but adding one is as
  deliberate a decision as adding a runtime library ([`ARCHITECTURE.md`](ARCHITECTURE.md#rules)).
