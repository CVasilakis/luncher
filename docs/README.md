# docs/

The documentation for developers: guides that cover the whole project rather than one folder, and
where to start. The root [`README.md`](../README.md) shows Luncher to its users: what it is, what
it does, and how to install it.

## Reading order

Read from the general to the specific; each document builds on the ones before it:

1. **[`BUILDING.md`](BUILDING.md):** what to install, how to build and run Luncher.
2. **[`ARCHITECTURE.md`](ARCHITECTURE.md):** modules, layers and the rules for where code goes.
3. **[`TESTING.md`](TESTING.md):** test tiers, how to run them, and in CI.
4. **The other guides [below](#guides)**, as a task needs them: emulators and devices, the
   instrumented tests, translations, releasing.
5. **The folder READMEs** ([the repository](#the-repository)): what each folder holds, in
   detail.
6. **Comments in the code and build files:** why a particular line is there.

How the docs are written and kept up to date: [below](#writing-the-docs). Coding agents also read
[`AGENTS.md`](../AGENTS.md).

## Guides

| File | Contents |
|---|---|
| [`BUILDING.md`](BUILDING.md) | Building and running: what to install, and a quick start on an emulator. |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Modules, layers and the rules for where code goes. |
| [`TESTING.md`](TESTING.md) | Test tiers: what goes where, how to run them, and in CI. |
| [`INSTRUMENTED-TESTS.md`](INSTRUMENTED-TESTS.md) | The emulator tiers: running them, on which emulators, and writing them so that the device can't make them fail. |
| [`EMULATORS.md`](EMULATORS.md) | Emulators and devices: the scripts that create and drive them, Luncher as the home screen on each Android version, and what the images do on their own. |
| [`TRANSLATIONS.md`](TRANSLATIONS.md) | Translations: where the texts are, writing a string that translates well, adding a string or a language, checking a translation. |
| [`RELEASING.md`](RELEASING.md) | Making a release: versions, the signing key, the release workflow, F-Droid. |
| [`archive/`](archive/README.md) | How the current design was reached, not part of the build: the layout review on nine TV screens, and the removed launch screen, its final drawables and what it taught. |

## The repository

| Path | Contents |
|---|---|
| [`app/`](../app/README.md) | The application module: the composition root that joins the others, the manifest, icon and version, and the instrumented tests. |
| [`feature/`](../feature/README.md) | The screens, one module per feature: the home screen, the settings panel. |
| [`platform/`](../platform/README.md) | The adapters: the domain's ports on Android APIs. |
| [`ui/`](../ui/README.md) | What every screen shares: the theme, colors and UI helpers. |
| [`domain/`](../domain/README.md) | Pure Kotlin module: the launcher's models, rules and ports (no Android). |
| [`build-logic/`](../build-logic/README.md) | Gradle convention plugins: the build settings every Android module shares. |
| [`gradle/`](../gradle/README.md) | Version catalog and Gradle wrapper. |
| [`docs/`](README.md) | Project-wide guides: building, architecture, testing, emulators, releasing; and an archive of how the design was reached. |
| [`fastlane/`](../fastlane/README.md) | The store listing F-Droid and Google Play show: name, descriptions, changes per version, screenshots (not part of the build). |
| `.github/workflows/` | GitHub Actions workflows that run the tests ([`TESTING.md`](TESTING.md#in-ci)) and make releases ([`RELEASING.md`](RELEASING.md)). |
| [`AGENTS.md`](../AGENTS.md) | What coding agents need on top of these docs. |
| `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradlew*` | Standard Gradle project files. |
| `lint.xml` | Lint's exceptions for every module ([`TESTING.md`](TESTING.md#lint-and-compiler-warnings)). |

## Writing the docs

These apply to every document in the repository, and to comments that explain code.

- **Each fact in one place.** A fact goes in the one document whose topic it is: the root README
  for what Luncher is to its users and how to install it, `docs/` for what spans the whole
  project, a folder's README for what's inside that folder, a comment for why one line of code or
  configuration is there. Everywhere else, link to it instead of repeating it.
- **One topic per document.** A topic that spans several folders, or is too narrow for the
  document above it (the emulators, the instrumented tests), gets a document of its own in
  `docs/`, listed above, rather than a long section in a document about something else.
- **From the general to the specific.** A document may rely on the ones above it in the
  [reading order](#reading-order) and links down for details, so each one reads on its own
  without restating what comes before it.
- **Every top-level folder has a `README.md`**, except `.github/`: GitHub shows a
  `.github/README.md` on the repository's front page instead of the root one.
- **The current state, not history.** No changelogs, dates or "we tried X" stories, except in
  `archive/`, whose documents keep how the design was reached and what removed features taught;
  elsewhere, link to them where the reason for the current state is there.
- **No machine-specific measurements.** How long a boot, build, test run or key press takes
  depends on the host, so describe it relatively ("slower", "faster than a cold boot"). Sizes, RAM
  needs and counts are fine.
- **Paths use `~`**, never an absolute home path, so examples work on any machine.
- **Docs change with the code.** A change to something a document describes updates that
  document in the same change.
