# .github/

GitHub Actions workflows. What each test tier checks: [`../docs/TESTING.md`](../docs/TESTING.md).

| Workflow | Runs | When |
|---|---|---|
| [`jvm-tests.yml`](workflows/jvm-tests.yml) | `:domain:test`, `:app:testDebugUnitTest`, `:app:verifyRoborazziDebug`, `assembleRelease` | every push to `main`, every pull request, and by hand |
| [`instrumented-tests.yml`](workflows/instrumented-tests.yml) | `connectedDebugAndroidTest`, one emulator per job | only by hand: Actions → Instrumented tests → Run workflow, then pick the emulators (below) |

The instrumented tests can run on every Android TV and Google TV image that
android-tv-wsl-dev-tools is tested with, from Luncher's minimum, API 22, on. The emulators are
named `android_tv_api<level>` and `google_tv_api<level>`, and each job's AVD gets that name:

| Choice | Emulators |
|---|---|
| `required-22-24-28-30-33-36` (default) | `android_tv_api22`, `android_tv_api24`, `android_tv_api28`, `android_tv_api30`, `android_tv_api33`, `android_tv_api36`: the six [`TESTING.md`](../docs/TESTING.md#on-several-android-versions) requires |
| `android-tv-all` | `android_tv_api22` to `android_tv_api36`: 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 33, 34 and 36 (there are no images for 32 and 35) |
| `google-tv-all` | `google_tv_api30` to `google_tv_api36`: 30, 31, 33, 34 and 36 (Google TV images start at 30) |
| `all` | both, 18 jobs |
| one name, e.g. `google_tv_api33` | that emulator only |

Why the instrumented tests run only by hand: GitHub-hosted runners are free for public
repositories, but a private repository gets a monthly allowance of Actions minutes (see GitHub's
billing settings). Booting an emulator and running the tests on it takes many times the minutes of
the JVM tiers, and six of them per push would use the allowance up quickly.

Other choices in the workflows:

- **The emulators** are created and booted with
  [android-tv-wsl-dev-tools](https://github.com/CVasilakis/android-tv-wsl-dev-tools), pinned to a
  release tag, following its `CI.md`.
- **Gradle caching** uses `setup-gradle`'s `basic` provider (MIT licensed). The default
  `enhanced` provider is proprietary, and may be charged for on private repositories.
- **Only `jvm-tests.yml` writes the Gradle cache**; the instrumented jobs read it.
  The system images aren't cached: together they would fill most of a repository's
  10 GB Actions cache and evict the Gradle cache.
- **One thing at a time.** The emulator jobs build the app and test APKs before booting the
  emulator, so Gradle's compiling doesn't compete with the freshly booted emulator for the
  runner's 2 cores; the test step then only installs and runs them.
- **Disk space.** Every emulator job first deletes preinstalled toolchains Luncher doesn't use
  (.NET, Haskell, Boost, CodeQL, the NDKs): otherwise the system image and the emulator's data
  partition don't fit on the runner's disk, or only just.
- **On failure**, the emulator jobs print the emulator's log, the free disk space, the devices
  adb sees and the end of adb's server log. The jobs upload their test reports (and for the
  emulators, the device log and adb's server log) as artifacts kept for 7 days, so they don't
  fill the private repository's artifact storage.
