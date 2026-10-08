# Instrumented tests

The two tiers that run on a device ([`TESTING.md`](TESTING.md#tiers)): in-app tests (Espresso), a
screen with real key events, and system tests (UI Automator), Luncher as the home screen across
apps. All of them are in `:app`, `app/src/androidTest/`
([`TESTING.md`](TESTING.md#organizing-tests)). How to run them, on which emulators, what the
device needs, and what a new test must do so that the device can't make it fail. How to create and
boot the emulators: [`EMULATORS.md`](EMULATORS.md).

## Running them

```bash
./gradlew connectedDebugAndroidTest                                # on every connected device
ANDROID_SERIAL=emulator-5558 ./gradlew connectedDebugAndroidTest   # on one (serials: adb devices)
```

Results are per device, in `app/build/outputs/androidTest-results/connected/debug/TEST-<device>.xml`
and `app/build/reports/androidTests/connected/debug/`. The build changes four things about AGP's
runs (the reasons are in comments in [`app/build.gradle.kts`](../app/build.gradle.kts) and
`gradle.properties`):

- each run first uninstalls Luncher (`uninstallAll`), so it starts from a clean install;
- the build fails, naming the device, when a device ran no test (`checkConnectedTestsRan`);
- Luncher stays installed afterwards, so a device with the stock launcher disabled still has a
  home screen;
- the runner fails a test method that hasn't returned after 15 minutes and goes on with the next
  (`timeout_msec`), a backstop against a hang: every wait of the tests' own has a shorter limit.

### The device

Run them with the stock launcher enabled, and on API 22 without having chosen Luncher as home
("Always"). Otherwise Luncher is the device's home app, and Android starts it again as soon as an
in-app test closes it; the closing activity can stay paused, and the test would time out although
its checks passed. `HomeActivityTest` therefore fails at once, saying so, while Luncher is the
home app. Re-enable the stock launcher first
([`EMULATORS.md`](EMULATORS.md#luncher-as-the-home-screen)).

The rest the tests take care of themselves, on any emulator or TV:

- **The settled home screen**, after the boot and between tests, including Back on "USB drive
  connected" after a first boot ([below](#start-from-the-settled-home-screen)).
- **Luncher as the home app**, for the system tier: `HomeKeyTest` disables the other home apps for
  its run, enables them again, and makes Android save that before it ends
  ([below](#leave-the-device-as-it-was-saved)).
- **`tv_user_setup_complete`**, without which API 26 and 27 ignore Home: `HomeKeyTest` sets it for
  its run and puts the old value back.

## On several Android versions

Luncher supports API 22 and newer, and behavior differs between versions, so the instrumented
tests run on six emulators:

| Emulator | Android | Why this one |
|---|---|---|
| `tv_api22` | 5.1 | the oldest supported; the in-app tier only |
| `tv_api24` | 7.0 | the oldest the system tier runs on ([why](#the-system-tier-from-api-24-on)) |
| `tv_api28` | 9 | stock launcher `tvlauncher` instead of `leanbacklauncher` |
| `tv_api30` | 11 | package visibility: other apps are hidden from Luncher unless the manifest's `<queries>` names them |
| `tv_api33` | 13 | `OnBackInvokedCallback` exists, but Back still calls `onBackPressed()`: the path `HomeActivity` relies on up to API 35 |
| `tv_api36` | 16 | the `targetSdk`: Back no longer calls `onBackPressed()` |

With several booted, one run covers them all, in parallel:

```bash
for api in 22 24 28 30 33 36; do create-avd.sh --api $api; done   # once; needs the system images,
                                                                   # see android-tv-wsl-dev-tools' SETUP.md, step 5
for avd in tv_api22 tv_api24 tv_api28 tv_api30 tv_api33 tv_api36; do start-emulator.sh "$avd"; done
./gradlew connectedDebugAndroidTest                    # runs on every booted emulator
```

How many run at once is up to you: an emulator takes ~2 GB of RAM on API 22, 24 and 28 and
~3.2–3.8 GB on 30, 33 and 36, so all six need ~17 GB. With less, boot them in batches (e.g. 22, 24
and 36, then 28, 30 and 33) or one at a time, stopping each with `stop-emulator.sh <avd>` before
the next. The API 36 image also takes 8.2 GB of disk.

## Writing them

A test runs on a device it doesn't control: the stock launcher, the boot and earlier tests leave
screens in front, and Android hands the focus around asynchronously. Each rule below is a helper in
`app/src/androidTest/java/…/testing/`, whose KDoc says what it waits for and why; a new test uses
them as the existing ones do.

### Start from the settled home screen

Every instrumented test that opens a screen starts from the device's home screen, settled: its
`@Before` calls `waitForHomeScreen()` (`HomeApp.kt`). The home app starts whenever a test closes
its activities, and some stock launchers then open screens of their own over whatever a test has
started meanwhile; after a boot, Google TV's launcher first shows a screen of its own, for minutes
on a slow host. So settled is a state the device shows, not a time it has lasted: the home app's
top activity, in its home task, resumed and idle, with the focus. The helper waits up to 10
minutes while the device is on its way there, and fails at once, naming it, once a screen of
another app has kept the focus for 60 s (an app left open on someone's TV, a dialog).
`HomeLookTest` checks both decisions on dumps the emulators produced. The one key it presses is
Back on "USB drive connected", named ([`EMULATORS.md`](EMULATORS.md#what-the-images-do-on-their-own)),
and on no other screen, since the tests can run on someone's TV.

### When the stock launcher covers a test

No wait before a test can foresee the stock launcher coming back over it later, on its own: on
Google TV API 33, Play Store updates Google Play services about 20 s after a boot, and the
launcher dies with it, restarts and brings its home task to the front. A covered test fails with
an error that doesn't say why. So the test classes that open screens have the rule
`RetryWhenCovered`: when a test fails and, while it ran, one of its activities went under a
screen of the stock home app (on API 22, the "choose home app" dialog), the rule logs that, waits
for the settled home screen and runs the test once more, `@Before` and `@After` included. Only a
cover identified that way, and only once: any other failure, and a second one, fails as before.

- A new test class that opens screens uses the rule too.
- The retry runs on the same instance of the test class, so a test class creates what a test
  changes in `@Before` or in the test, not in a field's initializer.
- `RetryWhenCoveredTest` covers its own screen with a HOME intent to check the rule.
- `HomeKeyTest` doesn't use it: it disables the stock launcher, and presses Home until Luncher is
  settled, before each test.

### Send keys only to a focused window

Keys go to the focused window alone, and a window shows before it gets the focus, so a test sends
keys to a screen only once UI Automator sees it and its window has the input focus. With none
focused at all, a key the app under test injects waits up to 60 s and is then dropped without an
error; and Android can give the focus to a window before its first layout.

- Before a key from UI Automator or `Instrumentation.sendKeySync`, a test sees the screen, then
  calls `waitForFocus()` (`Focus.kt`), which fails after 10 s, saying what has the focus.
  `longPressOk()` (`Keys.kt`) waits for a window of Luncher that way too.
- Espresso's actions wait for a focused, laid-out window themselves, but only briefly for the
  activity a Back closed to pause. So after a key that changes the window in front, an in-app test
  waits until the new window shows what it expects, focused, before the next key
  (`HomeActivityTest`'s `waitForTheSettingsPanel()`).

### Leave the device as it was, saved

Instrumented tests that change system state restore it afterwards (`HomeKeyTest`, which disables
other home apps to make Luncher the home), and make Android save it before the test ends: an
emulator stopped right after the run would otherwise boot with the test's state
([`EMULATORS.md`](EMULATORS.md#saving-a-change-to-the-device)). `HomeKeyTest`'s `restoreDevice`
has Android write the app states at once, waits for the setting's write, then commits the
filesystem's journal (`sync`); its KDoc says how on each API level. Found as it was includes
what's on screen: it presses Home once the stock launcher is back, so that launcher starts now,
cold, and the test ends on the settled home screen.

### The system tier from API 24 on

The system tier runs only on API 24 and newer; on API 22 and 23 the in-app tier still runs.
`SystemTierFilter` (which says why) leaves out every test in the `system` package on older
devices, so a new system test needs nothing of its own: putting it in `system/` is enough. Luncher
as the home screen on API 22 and 23 is checked by hand
([`EMULATORS.md`](EMULATORS.md#luncher-as-the-home-screen)).

## Messages and failures that aren't Luncher's

- **API 22 and 23:** AGP's test engine prints `Failed to retrieve additional test outputs from
  device` with a long `File name too long` stack trace after the tests. It's harmless: the tests
  have run, and Luncher writes no additional test output. Turning that feature off
  (`android.enableAdditionalTestOutput=false`) makes AGP 9's `connectedDebugAndroidTest` fail
  instead.
- **API 22's "choose home app" dialog** shows up during the run and stays on screen afterwards:
  when a test closes its activity, Android goes Home, which asks there. It doesn't disturb the
  tests: each one starts its activity above the dialog. Leave it unanswered (Back closes it). An
  emulator that has run the tests before shows it from its boot, since Luncher stays installed.
- **The stock launcher crashing while `HomeKeyTest` has it disabled:** the device log can show it
  as Android starts its process anyway (on the API 33 and 34 Android TV emulators, twice per run:
  `Tried to schedule job for non-existent component … DailyCheckInService`). That's harmless: the
  launcher is disabled, and nothing of it is on screen.
- **Android TV 16's Settings crashing on `tv_api36` starved of CPU,** as it opens (a
  `NullPointerException` in `MainFragment.onSuggestionReady`, on a launch after the first, from
  its cached process), also without Luncher, opened from the shell. `SystemSettingsTest` then
  fails, saying that the device's settings app crashed, with the crash's first line from
  `adb logcat -b crash`. It doesn't run the test again: a retry would hide a real failure too. Run
  the test again, or on an emulator with CPU to spare.

## In CI

[`instrumented-tests.yml`](../.github/workflows/instrumented-tests.yml) runs these tiers, one
emulator per job, only by hand (Actions → Instrumented tests → Run workflow). It creates and boots
its emulators with android-tv-wsl-dev-tools, pinned to a release tag, and offers every Android TV
and Google TV image that release is tested with, from API 22 on, named `android_tv_api<level>` and
`google_tv_api<level>`:

| Choice | Emulators |
|---|---|
| `required-22-24-28-30-33-36` (default) | the Android TV images of the [six emulators above](#on-several-android-versions) |
| `android-tv-all` | every Android TV image |
| `google-tv-all` | every Google TV image (they start at API 30) |
| `all` | both |
| one name, e.g. `google_tv_api33` | that emulator only |

It boots them with `start-emulator.sh --wait-for-home`, which returns once the home app's screen is
in front, has finished starting and has the focus, rather than as soon as Android has booted: on
API 34, tests started right after the boot once found no window with the focus, ever. When a job
fails, it uploads its test reports as artifacts, and the device's log, its windows
(`dumpsys window`: `mCurrentFocus` is the focused window, `mFocusedApp` the focused activity), a
screenshot, and adb's server log. The reasons for its individual steps are in comments in the
workflow file.
