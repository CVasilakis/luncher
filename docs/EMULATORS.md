# Emulators and devices

How to run Luncher on an Android TV emulator or device, make it the home screen, and drive it from
the command line; and what the emulator images do on their own that gets in the way. What to
install first: [`README.md`](../README.md#requirements). How the instrumented tests use the
emulators: [`INSTRUMENTED-TESTS.md`](INSTRUMENTED-TESTS.md).

## The android-tv-wsl-dev-tools scripts

`create-avd.sh`, `start-emulator.sh`, `stop-emulator.sh` and `remote.sh` in these docs are scripts
from [**android-tv-wsl-dev-tools**](https://github.com/CVasilakis/android-tv-wsl-dev-tools), a
separate repository of command-line tools for Android TV development under WSL2, which also has
`SETUP.md`, a setup of the whole toolchain (JDK, SDK, emulator) without Android Studio. The
scripts are a convenience, not a requirement. To use them, clone that repository anywhere and put
its `bin/` folder on your `PATH` (its README explains how), or call them by their path. Without
them, anything that does the same job works, e.g. Android Studio's Device Manager:

| Script | What it does | Without it |
|---|---|---|
| `create-avd.sh --api <level>` | Creates the `tv_api<level>` Android TV emulator. | Create an emulator from the Android TV system image of that API level with the TV 1080p device profile, in landscape, with hardware keyboard and D-pad input enabled. |
| `start-emulator.sh [avd]` | Boots it and returns once Android has fully booted. | Start the emulator, and wait for the boot ([below](#without-the-scripts)). |
| `stop-emulator.sh [avd]` | Has Android save its pending changes, stops the emulator, and returns once it has exited. | Run `adb emu kill`, after [waiting for Android to save](#saving-a-change-to-the-device), and wait until `adb devices` no longer lists the emulator before starting the same one again. |
| `remote.sh` | A TV remote in the terminal; `remote.sh --long-press <key>` holds a key as a long press. | The emulator window's keyboard, or adb ([Keys](#keys)). |

The docs name emulators `tv_api<level>`, as `create-avd.sh` does; with emulators of your own, use
their names instead. A physical Android TV device on API 22+ works too.

### Without the scripts

- **Wait for the boot** before installing or testing: an install started earlier fails. E.g.
  `until adb shell getprop sys.boot_completed 2>/dev/null | grep -q 1; do sleep 2; done`
  (`adb wait-for-device` alone isn't enough: while an emulator boots, adb can list it as `offline`
  for a moment, and a command sent then fails).
- **"offline" after a Quick Boot.** Android Studio resumes an emulator from a snapshot by default,
  and adb can then list it as `offline` for good, so Gradle says the device is offline. Run
  `adb reconnect offline`, or cold boot it (Device Manager → Cold Boot Now, or
  `emulator -avd <name> -no-snapshot-load`).
- **API 26 and 27 ignore Home** until the TV setup wizard has run
  ([below](#what-the-images-do-on-their-own)); `start-emulator.sh` takes care of that.

## Luncher as the home screen

On the Android TV and Google TV emulator images from API 23 on, pressing Home never shows a
"choose home app" prompt, and `adb shell cmd package set-home-activity …` has no effect. The stock
launcher is a system app whose HOME intent filter has priority 2, third-party apps are capped at
priority 0, and Android picks the highest priority without asking. Disable the stock launcher
instead (this persists across reboots, once [saved](#saving-a-change-to-the-device)). Its package
depends on the Android version:

| Emulator | Stock launcher |
|---|---|
| API 22 to 25 (Android 5.1 to 7.1) | `com.google.android.leanbacklauncher` |
| API 26 (Android 8.0) and newer | `com.google.android.tvlauncher` |
| Google TV, every level (API 30–36) | `com.google.android.apps.tv.launcherx` |

```bash
adb shell pm disable-user --user 0 com.google.android.leanbacklauncher   # Home opens Luncher (API 23-25)
adb shell pm enable com.google.android.leanbacklauncher                  # back to the stock launcher
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME   # who is home (API 24+)
```

API 22 (Android 5.1) is the exception: its stock launcher's HOME filter has no priority, so with
Luncher installed, Home asks which home app to use. Pick Luncher there ("Always"); disabling the
stock launcher isn't needed.

Don't uninstall Luncher while the stock launcher is disabled, or Home has nowhere to go. Re-enable
the stock launcher first, or wipe the emulator's data (`start-emulator.sh -wipe-data`, or "Wipe
Data" in Android Studio's Device Manager). While the stock launcher is enabled, Luncher appears in
its app row (with its banner) and can be opened like any app. Home seems to do nothing while
Luncher is already in front, because Luncher is the home screen.

A debug build is an app of its own, so its activities' full name is e.g.
`com.luncher.launcher.debug/com.luncher.launcher.home.HomeActivity`
([`app/README.md`](../app/README.md#builds)):

```bash
adb shell am start -n com.luncher.launcher.debug/com.luncher.launcher.home.HomeActivity
```

## Saving a change to the device

**Wait 30 s before stopping the emulator after disabling or enabling an app**, or stop it with
`stop-emulator.sh`, which has Android save its pending changes first. `adb emu kill` doesn't shut
Android down, and Android saves such a change only a while after it's made, so the next cold boot
starts with the app as it was before. Measured on these emulators:

- Android writes an app's enabled state 10 s after the first unsaved change (10.35 s measured; on
  an emulator starved of CPU, 10 s after the `pm` command returned, which itself took 3–4 s
  there). A setting (`settings put`) it writes about 0.2 s after the change (0.34 s at most when
  starved). API 22 writes both at once.
- Then the file needs up to 5 s more: Android keeps the old one as a backup until the new one is
  complete, and until the filesystem's journal has recorded that (every 5 s), a boot reads the
  backup.

So a change is safe 15 s after it at the latest, and 30 s leaves twice that. Stopped earlier, a
change survived only by chance.

## What the images do on their own

- **A promotion after the stock launcher's cold start (API 36).** Re-enabled, the stock launcher
  starts cold at the next Home. The API 36 one (`tvlauncher`) can then open a promotion over
  itself a few seconds later, "Buy and rent movies on your TV" (`.dialog.ShowDialogsActivity`, at
  most once per boot in the runs seen), which stays until dismissed. An app opened in those
  seconds ends up behind it. Google TV's launcher, coming back, opens its profile chooser over
  itself the same way.
- **"USB drive connected" on a first boot (API 23 and 29).** The first boot of a new emulator with
  an SD card opens `com.android.tv.settings/.device.storage.NewStorageActivity` in front of the
  home app, and it stays until Back; later boots don't. It's Android TV's Settings announcing the
  SD card, which Android mounts as a removable drive from API 23 on. `create-avd.sh` gives every
  emulator an SD card, so each of them, CI's included, shows the screen once. `avdmanager create
  avd` without `--sdcard` writes an SD card size into the emulator's `config.ini` but creates no
  SD card image, so its emulators have no SD card and don't show it. Why the other API levels
  don't show it isn't known. `start-emulator.sh --wait-for-home` presses Back on it.
- **Home does nothing on API 26 and 27** (Android 8.0 and 8.1), whoever the home app is: Android
  ignores it until the TV setup wizard has set `tv_user_setup_complete`, and these images never
  run that wizard (logcat: "Not starting activity because user setup is in progress").
  `start-emulator.sh` sets it after the boot. On an emulator started another way, set it by hand:

  ```bash
  adb shell settings put secure tv_user_setup_complete 1
  ```

## Keys

The emulator window's keyboard is a remote: arrows, Enter for OK, Ctrl+Backspace for Back; hold a
key for a long press. From the shell, `adb shell input keyevent <KEY>` sends one, and
`remote.sh` is a remote in the terminal.

A long press (OK held, which starts [arrange mode](../feature/home/README.md#arrange-mode)) has to
hold the key down: `remote.sh --long-press DPAD_CENTER` does on every API level
(android-tv-wsl-dev-tools v1.4.0 on; in the interactive `remote.sh`, `l` then Enter), and so does
holding Enter in the emulator window. `adb shell input keyevent --longpress` holds the key only
from API 30 on; before, it's a short press, which opens the focused app.
