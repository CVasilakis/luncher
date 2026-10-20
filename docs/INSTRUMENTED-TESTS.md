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
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.luncher.launcher.settings.BackgroundActivityTest
```

One test class per run, or one method (`…BackgroundActivityTest#addAll_…`): with several classes
separated by commas, only the first one runs.

Results are per device, in `app/build/outputs/androidTest-results/connected/debug/TEST-<device>.xml`
and `app/build/reports/androidTests/connected/debug/`. The build changes five things about AGP's
runs (the reasons are in comments in [`app/build.gradle.kts`](../app/build.gradle.kts) and
`gradle.properties`):

- each run first uninstalls Luncher (`uninstallAll`), so it starts from a clean install;
- the build fails, naming the device, when a device ran no test (`checkConnectedTestsRan`);
- Luncher stays installed afterwards, so a device with the stock launcher disabled still has a
  home screen;
- the runner fails a test method that hasn't returned after 15 minutes and goes on with the next
  (`timeout_msec`), a backstop against a hang: every wait of the tests' own has a shorter limit;
- a test that fails saves the device's state and a screenshot as it fails (`listener`,
  [below](#look-again-until-it-matches-and-keep-what-a-failure-showed)).

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
- A window that takes the focus over a screen (a dialog, the background editor's list of an
  option's values, a `PopupWindow`) is the only one Espresso looks at while it shows: a test
  checks it and sends it keys with `inRoot(isDialog())` or `inRoot(isPlatformPopup())`, and looks
  at the screen under it only once it's closed. A check of the screen meanwhile waits for its
  window to get the focus back, and fails after 10 s (`BackgroundActivityTest`'s
  `waitUntil(…, inList = true)`).
- An activity that closes back to the screen under it keeps its window, on top, until it's
  destroyed, a moment after that screen resumed. An Espresso look that begins meanwhile can pick
  the closing window and wait for it to get the focus, which it never does, and fails after 10 s
  (`RootViewWithoutFocusException`). So after the key that closes it, a test calls
  `waitUntilDestroyed()` (`Closed.kt`) before it looks at the screen it returns to: Add from
  folder in `BackgroundActivityTest`, the appearance editor and the background editor (closed by
  Save) in `AppearanceActivityTest`, Hide apps and the settings panel in `HomeActivityTest`. UI
  Automator's looks don't pick a window, and a screen opened after the closed one is on top of
  it, so neither needs the wait.

### Look again until it matches, and keep what a failure showed

A screen changes asynchronously after a key, so a test doesn't check it once: it looks again
every 50 ms until what it expects shows, up to a limit (`waitUntilMatches()`, `Looks.kt`, which
each test class's `waitUntil` calls with its own limit and window).

- The looks before the last fail quietly: Espresso's own handling of a failed look takes a
  screenshot and the view hierarchy, which would slow each look again and fill the device's log.
  Only the last look fails with it, and so does an error a new look can't change
  (`RootViewWithoutFocusException`).
- A look whose answer may be no (`hasFocus(view)` before a key) uses `matchesNow()`, which never
  counts as a failure.
- When a test fails, `FailureCapture` (`FailureCapture.kt`, whose KDoc lists what it saves)
  saves the device's state while the failing screen is still open: once a test has failed, its
  activity is closed, and the home app is in front. It writes `failure-<n>-<Class>.<test>.txt`
  and a screenshot `….png` in the test storage's output folder, next to Espresso's
  `view-op-error-<n>.png` and `view-hierarchy-<n>.txt`. It's the runner's `listener`
  (`app/build.gradle.kts`), so every test class has it: it handles every Espresso failure, and the
  waits without Espresso (`waitForFocus()`, `waitUntilSeen()`, `waitUntilDestroyed()`) call it
  before they fail. The device's log says where each was saved (tag `FailureCapture`).
- After a local run, AGP copies the files to
  `app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/<device>/`,
  except on API 22 and 23, where its copy fails (below); they stay on the device too, in the
  test storage's folder (`/sdcard/Android/media/com.luncher.launcher.debug/additional_test_output/`
  on API 33, `/sdcard/Android/data/com.luncher.launcher.debug/files/test_data/` on API 22), for
  `adb pull`. The next run deletes both: copy what a failed run saved before running the tests
  again.

### Look with UI Automator while a screen animates

Espresso waits for the app's main thread to be idle before each look. On API 24 a spinning
wheel keeps the main thread busy (from API 28 on it doesn't; 25 to 27 aren't among the
emulators), so while one spins (the window that adds a shared picture), an Espresso check sees
nothing until it stops. A test looks at such a screen with
`waitUntilSeen()` (`Seen.kt`), which asks UI Automator, and uses Espresso again once the wheel is
gone.

### Leave the device as it was, saved

Instrumented tests that change system state restore it afterwards (`HomeKeyTest`, which disables
other home apps to make Luncher the home), and make Android save it before the test ends: an
emulator stopped right after the run would otherwise boot with the test's state
([`EMULATORS.md`](EMULATORS.md#saving-a-change-to-the-device)). `HomeKeyTest`'s `restoreDevice`
has Android write the app states at once, waits for the setting's write, then commits the
filesystem's journal (`sync`); its KDoc says how on each API level. Found as it was includes
what's on screen: it presses Home once the stock launcher is back, so that launcher starts now,
cold, and the test ends on the settled home screen.

### Pictures from another app, and the test's own folders

The picture library's tests add pictures as another app shares them (`PictureShareTest` in
`system/`, `PictureFormatsTest` in `pictures/`), or from a drop folder (`BackgroundActivityTest` in
`settings/`). The tests add into folders of their own (`FilesPictures`, `WorkingCopyImages`,
`CachedPictureImages` and `MediaFolderPictures` take them as parameters): a library of their own,
with its working copies and thumbnails, and a folder of their own inside the drop folder, never
the device's library or the files in its drop folder; they delete them after.

Another app is played by the test package itself, in `testing/`:

| Part | What it is |
|---|---|
| `SamplePictures` | a content provider serving the sample files of `app/src/androidTest/assets/pictures/`, exported |
| `GrantedPictures` | the same samples, from a provider that isn't exported, as a gallery's: only the read access a share gives opens them |
| `StalledPictures` | an app whose picture never comes: it opens a stream it never sends on (`silent`), or never answers the open (`unopened`); exported |
| `ShareSender` | another app's screen sending a `SEND_MULTIPLE` of the pictures it's given, as a gallery does; exported |

They run in the test package's own process, which has no Kotlin standard library (the test APK
leaves out what the app under test has), so they're written in Java.

Most shares are sent from the app's own process, which the instrumentation runs in, to a task of
their own (`NEW_TASK`): that process couldn't grant access to another package's provider, which
is why `SamplePictures` and `StalledPictures` are exported. Sent that way, two alike in a row also
check that the second isn't taken for the first one's task
([`SharePictureActivity`'s affinity](../feature/settings/README.md#manifest)). A share with the
access to pictures only a grant opens comes from `ShareSender`, from the test package's process,
where Android adds the read access to each picture of the stream as it sends it; Luncher must pass
that access on to its window. `ShareSender` makes the share itself, as Android adds the access
only to a share whose extras it can still read as it sends it, not one passed in packed as an
extra; and it stays until it's covered, as a gallery does, because a task emptied while in front
returns to the home app on API 24, over Luncher's window. A share Luncher may read but not pass on
(a picture of the device's media store, which Luncher has no access to) is sent as "Set as"
(`ATTACH_DATA`), to which Android adds no grant of its own.

With `StalledPictures`, Luncher says it can't read a `silent` picture once its time limit has
passed (15 s); Cancel leaves an `unopened` one, and the next share is then added, as nothing waits
behind the stuck call; a share sent while one is stuck joins its session (in its window, or on API
29 in a new window that takes the session over), and the window then says how both went; Home
while one is stuck stops the adding, so a share that joined it isn't added, not even once the
stuck one has given up. While the window copies a picture, its wheel spins, so the tests look at it with UI Automator
([above](#look-with-ui-automator-while-a-screen-animates)).

A share opens Luncher's home screen, in a task of its own, with the window over it, though Luncher
isn't the device's home app during the tests. So `PictureShareTest` ends each test by pressing
Home, which brings the stock launcher in front before the runner closes Luncher's tasks behind it.
Closed in front, they'd leave Android to bring forward the task next in line, which can be another
app's (the device's settings, after `HomeKeyTest`), or, on API 22, to ask which home app; the next
test would then wait for a home screen that can't come. That's also why it's in the system tier.

`PictureFormatsTest` checks which sample decodes on the device's Android version. There's no HEIF
sample: nothing on the development machine can write one, and a phone's photo carries its owner's
data. HEIF is checked by hand: on API 27 and older, adding one must give the message that this TV
can't show it; from API 28 on, it must be added and show upright.

### The system tier from API 24 on

The system tier runs only on API 24 and newer; on API 22 and 23 the in-app tier still runs.
`SystemTierFilter` (which says why) leaves out every test in the `system` package on older
devices, so a new system test needs nothing of its own: putting it in `system/` is enough. Luncher
as the home screen on API 22 and 23 is checked by hand
([`EMULATORS.md`](EMULATORS.md#luncher-as-the-home-screen)).

## Messages and failures that aren't Luncher's

- **API 22 and 23:** AGP's test engine prints `Failed to retrieve additional test outputs from
  device` with a long `File name too long` stack trace after the tests. The tests have run, but
  what a failure saved isn't copied: it's on the device
  ([above](#look-again-until-it-matches-and-keep-what-a-failure-showed)), and CI pulls it itself.
  Turning that feature off (`android.enableAdditionalTestOutput=false`) makes AGP 9's
  `connectedDebugAndroidTest` fail instead.
- **API 22's "choose home app" dialog** shows up during the run and stays on screen afterwards:
  when a test closes its activity, Android goes Home, which asks there. It doesn't disturb the
  tests: each one starts its activity above the dialog. Leave it unanswered (Back closes it). An
  emulator that has run the tests before shows it from its boot, since Luncher stays installed.
- **The stock launcher crashing while `HomeKeyTest` has it disabled:** the device log can show it
  as Android starts its process anyway (on the API 30, 33 and 34 Android TV emulators, twice per
  run: `Tried to schedule job for non-existent component … DailyCheckInService`, or `No such
  service … DailyCheckInService`). That's harmless: the launcher is disabled, and nothing of it is
  on screen.
- **Other crashes in `adb logcat -b crash`, none of them Luncher's,** in every run: on
  `tv_api24`, native crashes (SIGSEGV in a Binder thread) of `cmd`, the shell's command tool,
  which the tests run to ask which app is home: it crashes as it exits, once it has answered; on
  `tv_api28`, the TV app `com.android.tv` (`SQLiteException: no such column: failed_reason`, in
  `AsyncDvrDbTask`); on `tv_api33`, `com.google.android.tv` (a `PendingIntent` made without
  `FLAG_IMMUTABLE`).
- **Every test on `tv_api22` failing after 600 s,** "The home screen didn't settle", with the
  stock launcher focused and an activity of Luncher left `STOPPING` above its task: seen after the
  stock launcher had been disabled and enabled again by hand, or started by hand
  (`am start -n …leanbacklauncher/.MainActivity`). The run then takes hours: stop it, and cold-boot
  the emulator (`stop-emulator.sh`, then `start-emulator.sh`), or force-stop the stock launcher
  and press Home.
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
API 34, tests started right after the boot once found no window with the focus, ever.

To find out why a job failed:

- **Which tests failed:** the run's page lists each one with the first line of its error, as an
  annotation naming the emulator, and so does each job's summary. The job's log has each
  error in full, under "List the failed tests".
- **Which test was running:** the log of "Run the instrumented tests" shows each test as it
  starts, finishes or fails (the test runner's `TestRunner` lines), with the device's time. When
  a job hangs or is cancelled, AGP never writes the results, and these lines are the only record.
- **What the device showed as a test failed:** a failed or cancelled job uploads, in its artifact's
  `failures/` folder, what `FailureCapture` and Espresso saved at each failure
  ([above](#look-again-until-it-matches-and-keep-what-a-failure-showed)): look there first. It
  pulls them from the device, as AGP's copy fails on API 22 and 23 and a cancelled run never
  makes one.
- **What the device was doing:** the artifact also has the test reports, the device's log, its
  windows after the run (`dumpsys window`: `mCurrentFocus` is the focused window, `mFocusedApp`
  the focused activity), a screenshot after the run, and adb's server log. By then the tests
  have closed their screens, and the home app is in front.
- **A download that failed:** the step that builds runs Gradle once more when it failed
  downloading a dependency or a plugin (Google's Maven repository answers 502 now and then), with
  a warning on the run's page; any other failure fails at once.

The reasons for its individual steps are in comments in the workflow file.
