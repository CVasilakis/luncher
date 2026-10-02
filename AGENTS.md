# AGENTS.md

What AI coding agents need on top of the project's documentation. Read that first, from the
general to the specific ([reading order](README.md#documentation)): [`README.md`](README.md) (what
Luncher is, requirements, build and run), [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) (where
code goes and the rules it follows), [`docs/TESTING.md`](docs/TESTING.md) (which tests a change
needs and how to run them), then the README of the folder you're working in. Docs you write
follow [`docs/README.md`](docs/README.md#writing-the-docs).

## Tools outside this repository

The emulator scripts (`create-avd.sh`, `start-emulator.sh`, `remote.sh`) come from
[android-tv-wsl-dev-tools](https://github.com/CVasilakis/android-tv-wsl-dev-tools), a separate
repository that can live anywhere; its `bin/` must be on `PATH`
([`README.md`](README.md#emulators-and-the-android-tv-wsl-dev-tools-scripts)). If the scripts
aren't found, ask the user where that repository is rather than guessing a path.

Its `bin/README.md` is the reference for driving the emulator from a script:
[sending keys](https://github.com/CVasilakis/android-tv-wsl-dev-tools/blob/main/bin/README.md#controlling-the-tv),
[checking what Android received](https://github.com/CVasilakis/android-tv-wsl-dev-tools/blob/main/bin/README.md#checking-what-the-emulator-is-doing),
and [how API levels differ](https://github.com/CVasilakis/android-tv-wsl-dev-tools/blob/main/bin/README.md#differences-between-api-levels).
What Luncher needs to be the home screen on each of them:
[`app/README.md`](app/README.md#luncher-as-the-home-screen).

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
on the emulator:

```bash
./gradlew installDebug
adb shell am start -n com.luncher.launcher/.home.HomeActivity
adb shell dumpsys window | grep mCurrentFocus    # what's in front
adb exec-out screencap -p > screen.png           # screenshot, to look at the UI
adb logcat -b crash                              # crashes
remote.sh --long-press DPAD_CENTER               # long press of OK, any API level (app/README.md#arrange-mode)
stop-emulator.sh                                 # stop it; returns once it has exited (add <avd> if several run)
```

After changing a device setting by hand (e.g. disabling the stock launcher), wait 30 s before
stopping the emulator, or the change is lost ([why](app/README.md#luncher-as-the-home-screen)).

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
| `kotlin-android` declared with `apply false` at the root | `build.gradle.kts` | [`gradle/README.md`](gradle/README.md#how-kotlin-is-set-up-agp-9-built-in-kotlin) |
| `onBackPressed()` without `super`, and an `OnBackInvokedCallback` on API 36+, that only end arrange mode | `app/…/home/HomeActivity.kt` | its comments |
| `java`/`jvmTarget` 17 instead of a toolchain | `domain/build.gradle.kts` | its comment |
| `--add-opens=java.base/jdk.internal.access` for unit tests | `app/build.gradle.kts` | its comment |
| `uninstallAll` before, `checkConnectedTestsRan` after instrumented tests | `app/build.gradle.kts` | its comment |
| `android.injected.androidTest.leaveApksInstalledAfterRun` | `gradle.properties` | its comment |
| `testInstrumentationRunnerArguments["filter"]`, `SystemTierFilter` | `app/build.gradle.kts`, `app/src/androidTest/…` | `SystemTierFilter`'s comment |
| `waitForHomeScreen()` before every instrumented test (and its Back on "USB drive connected"), `pressHome()` in `HomeKeyTest`'s setup (until Luncher has the focus) and cleanup | `app/src/androidTest/…` | their comments |
| `waitForFocus()` right after a screen is seen, before keys (in the system tests and `longPressOk()`) | `app/src/androidTest/…` | `waitForFocus`'s comment, [`docs/TESTING.md`](docs/TESTING.md#instrumented-tests-espresso-ui-automator) |
| `waitForTheSettingsPanel()` between the two Backs of `hidingAnAppInTheSettings_removesItsTile` | `app/src/androidTest/…/home/HomeActivityTest.kt` | its comment, [`docs/TESTING.md`](docs/TESTING.md#instrumented-tests-espresso-ui-automator) |
| `RetryWhenCovered` in the test classes that open screens (a test the stock launcher covered runs twice), and `HideAppsActivityTest`'s `arrangements` made anew at each launch | `app/src/androidTest/…` | its KDoc, [`docs/TESTING.md`](docs/TESTING.md#instrumented-tests-espresso-ui-automator) |
| `start-emulator.sh --wait-for-home` in CI, although the tests wait for the home screen too | `.github/workflows/instrumented-tests.yml` | its comment |
| `open class AppGraph`, settable `LuncherApplication.graph` | `app/src/main/…` | [`docs/TESTING.md`](docs/TESTING.md#organizing-tests) |
| Manifest `<queries>`, `uses-feature`, launcher intent filters | `AndroidManifest.xml` | [`app/README.md`](app/README.md#manifest-why-each-part-is-there) |
| `distributionSha256Sum` | `gradle/wrapper/gradle-wrapper.properties` | [`gradle/README.md`](gradle/README.md#upgrading) |
