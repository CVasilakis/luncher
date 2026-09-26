# AGENTS.md

Guidance for AI coding agents (and humans) working on this repository.

## Project in one paragraph

Luncher is a minimal Android TV launcher (home screen app) meant to run well on very weak devices:
Kotlin, platform Views, plain `android.app.Activity`, no AndroidX/Leanback/Compose, `minSdk 25`,
`compileSdk`/`targetSdk 36`, package `com.luncher.launcher`. Scope: hide apps, change app banners,
set wallpapers, reorder apps, a few settings. Two Gradle modules: `:domain` (pure Kotlin rules)
and `:app` (Android). Start with [`README.md`](README.md), then [`ARCHITECTURE.md`](docs/ARCHITECTURE.md)
and [`TESTING.md`](docs/TESTING.md). Every folder has its own `README.md`. The emulator scripts and the
SDK setup are in the separate [**android-tv-wsl-dev-tools**](https://github.com/CVasilakis/android-tv-wsl-dev-tools) repository, which can live anywhere; this
repository holds only the app. Its scripts (`create-avd.sh`, `start-emulator.sh`, `remote.sh`) are
called by name below, so its `bin/` must be on `PATH`. They're a convenience: what each one does,
and how to do it without them, is in [`README.md`](README.md#emulators-and-the-android-tv-wsl-dev-tools-scripts). If they
aren't found, ask the user where the repository is rather than guessing a path.

## Commands

```bash
./gradlew assembleDebug                 # build
./gradlew assembleRelease               # R8-minified, unsigned (checks shrinking still works)
./gradlew :domain:test :app:testDebugUnitTest :app:verifyRoborazziDebug   # JVM tiers
./gradlew :app:recordRoborazziDebug     # accept intended screen changes (commit the images)
start-emulator.sh                       # android-tv-wsl-dev-tools: boot the TV emulator (returns when booted)
for avd in tv_api25 tv_api28 tv_api30 tv_api33 tv_api36; do start-emulator.sh "$avd"; done   # all five (~13 GB RAM)
./gradlew connectedDebugAndroidTest     # Espresso + UI Automator on every booted emulator
./gradlew installDebug                  # install on it
adb shell am start -n com.luncher.launcher/.home.HomeActivity
adb shell dumpsys window | grep mCurrentFocus    # what's in front
adb exec-out screencap -p > screen.png           # screenshot, to look at the UI
adb emu kill                            # stop the emulator (with several devices: adb -s <serial> emu kill)
```

Verify app changes with the test tiers in [`TESTING.md`](docs/TESTING.md), and by installing and checking
the result on the emulator (focus, screenshot, `adb logcat -b crash`).

## Architecture (details in [`ARCHITECTURE.md`](docs/ARCHITECTURE.md))

- **`:domain` holds the decisions, `:app` the Android side.** Which apps show, their order, hiding,
  banner choice and setting validation are rules in `:domain`: pure Kotlin, no Android, no
  libraries (the build enforces it). `:app` holds UI, adapters and the composition root.
- **Activities and views only render and forward input.** Android data APIs (PackageManager,
  SharedPreferences, WallpaperManager, files) are used only in adapters implementing a `:domain`
  port. Only `AppGraph` creates adapters; screens get ports through `graph`.
- **Packages by feature in `:app`** (`home/`, `apps/`, …); features don't import each other.
  Resource names start with their feature (`home_…`).
- **Zero-cost structure.** Interfaces and constructors only: no reflection, no DI framework, no
  runtime libraries. Adapters are created lazily. `private` by default.
- **Where does it go?** Check the table in `docs/ARCHITECTURE.md` before adding a file; if nothing
  fits, ask rather than inventing a new layer.

## Testing (details in [`TESTING.md`](docs/TESTING.md))

- **Every change comes with tests** in the tier `docs/TESTING.md` assigns to it; a bug fix starts with a
  failing test. Use the lowest tier that can catch the regression (most tests: `:domain` unit tests).
  Tiers: `:domain` JUnit → `:app` Robolectric (adapters, screens) → Roborazzi screenshots →
  Espresso (real keys on a screen) → UI Automator (Home key, other apps).
- **Fakes of ports live in `domain/src/testFixtures/`**; tests swap them in through `AppGraph`
  (`application.graph = object : AppGraph(application) { override … }`), restoring it in
  `androidTest`.
- **Screen changes update the reference screenshots** (`recordRoborazziDebug`); review the new
  image before committing it.
- **Tests follow the code**: same package, named after the class, screen or flow; test names
  describe behavior. No spaces in `androidTest` method names (DEX on API < 30).
- **Check that a new test can fail** by breaking the behavior once.
- **New test libraries** need the user's agreement like any dependency, even though test
  libraries never reach the APK.

## Rules

- **Stay lightweight.** Every byte and allocation counts on weak devices. Don't add dependencies
  (AndroidX, Leanback, image loaders, coroutines, test libraries, …) without the user agreeing
  to it. Everything must run on API 25: guard newer APIs with `Build.VERSION.SDK_INT`.
- **D-pad first.** Every screen must work with arrows, OK, Back and Home only; there's no touch.
  Don't make features reachable only through the Menu key (many remotes lack it).
- **Docs describe the current state, not history.** No changelogs, dates or "we tried X" stories
  in docs. When you change something a README describes, update that README in the same change.
- **No machine-specific measurements in docs.** How long a boot, build, test run or key press
  takes depends on the host, so describe it relatively ("slower", "faster than a cold boot").
  Sizes, RAM needs and counts are fine.
- **No personal information in tracked files:** no names, e-mail addresses, usernames or absolute
  home paths (write `~` or `/home/<user>`), no machine-specific config. The one exception is the
  link to the android-tv-wsl-dev-tools repository, whose address contains the owner's username. `local.properties` stays git-ignored.
- **Never commit** build outputs, `.gradle/`, `.kotlin/`, signing keys or keystores.
- Downloads can be large (the emulator is ~354 MB, a system image ~700 MB). Ask before
  triggering big SDK downloads.

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

Each of these fixes a real problem. The reasons are in the linked file; read them before changing anything.

| What | Where | Why |
|---|---|---|
| `kotlin-android` declared with `apply false` at the root | `build.gradle.kts` | Pins the Kotlin version AGP's built-in Kotlin uses ([`gradle/README.md`](gradle/README.md)). Also: never apply it in `app/`, AGP 9 fails the build. |
| Empty `onBackPressed()`, and an empty `OnBackInvokedCallback` on API 36+ | `app/…/home/HomeActivity.kt` | A home screen must not close on Back; Android 16 no longer calls `onBackPressed()` ([`app/README.md`](app/README.md)). |
| `java`/`jvmTarget` 17 instead of a toolchain | `domain/build.gradle.kts` | Works with any JDK 17+ without downloading another JDK. |
| `--add-opens=java.base/jdk.internal.access` for unit tests | `app/build.gradle.kts` | Robolectric's API 36 framework fails every test without it. |
| `uninstallAll` before, `checkConnectedTestsRan` after instrumented tests | `app/build.gradle.kts` | AGP installs without `-r` (API 25 refuses), then reports success with zero tests on that device. |
| `android.injected.androidTest.leaveApksInstalledAfterRun` | `gradle.properties` | Otherwise a test run uninstalls Luncher, leaving no home screen. |
| `open class AppGraph`, settable `LuncherApplication.graph` | `app/src/main/…` | How tests swap in fakes ([`TESTING.md`](docs/TESTING.md#organizing-tests)). |
| Manifest `<queries>`, `uses-feature`, launcher intent filters | `AndroidManifest.xml` | Explained line by line in [`app/README.md`](app/README.md). |
| `distributionSha256Sum` | `gradle/wrapper/gradle-wrapper.properties` | Verifies the downloaded Gradle distribution. |

## Emulator facts that save time

(Details in android-tv-wsl-dev-tools' `bin/README.md`, and [`app/README.md`](app/README.md#luncher-as-the-home-screen).)

- The TV emulator has **no touchscreen**: clicks on the screen do nothing by design.
- Sending keys from a script: `adb shell input keyevent DPAD_DOWN` (any device, any API level;
  what `remote.sh` uses). Host-level simulated input (xdotool/XTest, XSetInputFocus) does **not**
  reach the emulator under WSLg.
- To check which keys Android received: `adb shell dumpsys input | sed -n '/RecentQueue/,/PendingEvent/p'`.
  `adb shell getevent > file` doesn't work for this (output is buffered without a terminal).
- In the emulator window, Esc and F1 don't reach Android; Back is Ctrl+Backspace, Home Ctrl+H, Menu Ctrl+M.
  `remote.sh` (android-tv-wsl-dev-tools) is a TV remote in the terminal.
- Luncher is only the home screen while the stock launcher is disabled
  (`adb shell pm disable-user --user 0 com.google.android.leanbacklauncher`; on API 28, 30, 33 and
  36 it's `com.google.android.tvlauncher`, on the Google TV images
  `com.google.android.apps.tv.launcherx`). `set-home-activity` and the home chooser don't work on
  these images: the stock launcher's HOME filter has a higher priority.
- Instrumented tests run on the `tv_api25`, `tv_api28`, `tv_api30`, `tv_api33` and `tv_api36` emulators
  ([`TESTING.md`](docs/TESTING.md#on-several-android-versions)). From API 30 on, `dumpsys input` shows
  no key codes.
- **Agents boot one emulator at a time** for their test runs, to spare the host's resources: boot
  one, run the tests, stop it, then start the next. (People running the tests themselves boot as
  many at once as their machine allows.)

  ```bash
  for avd in tv_api25 tv_api28 tv_api30 tv_api33 tv_api36; do
    start-emulator.sh "$avd" && ./gradlew connectedDebugAndroidTest; adb emu kill; adb wait-for-disconnect
  done
  ```
- `sudo` needs a password and there's no terminal to type it into, so ask the user to run sudo
  commands themselves in a regular terminal.
