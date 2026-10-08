# Translations

Luncher's texts are in English and Greek. This covers where they are, how a string is written so
that it translates well, and what a new string or language needs.

## Where the texts are

| Module | Texts | Languages |
|---|---|---|
| `:feature:home` | the home screen and arrange mode | `res/values/strings.xml` (English), `res/values-el/` (Greek) |
| `:feature:settings` | the settings panels | the same |
| `:app` | the app's name | `translatable="false"`: a name, the same in every language |

Strings stay with the screen that shows them, each module's prefixed with its name (`home_…`,
`settings_…`), and each translation sits next to its English, in `values-<language>/`. A string
both screens show ("Settings", "Hidden") is in each, so either module's translation is complete on
its own.

`:domain` has no text: it returns data (`SettingsEntry.HideApps`, whether every app is hidden),
and the screens turn it into strings. Texts that aren't Luncher's come in the device's language
already: the apps' names, from Android, and the time and date, which `ClockView` formats with the
language's own patterns.

A right-to-left language mirrors the settings panels but not the home screen
([`app/README.md`](../app/README.md#manifest-why-each-part-is-there)).

## Writing a string

- **A comment above each,** in `values/`: where it shows and what it's for. The translations
  don't repeat them.
- **Parts that stay as they are** go in `<xliff:g>` with an example, as the D-pad's arrows in the
  arrange hint.
- **One string for each sentence,** never one built from pieces in code: a translation may need
  the parts in another order. What a sentence takes from elsewhere is a format argument
  (`%1$s`). `home_all_hidden` takes the settings panel's title and its Hide apps entry that way,
  from `:feature:settings`, so the message and the panel always name them alike.
- **Counts** go in `<plurals>`: many languages have more than two forms.
- **Room for longer text.** A translation often runs a third longer than the English, Greek's
  arrange title almost twice. Where text can't grow, the layout says what gives way: the arrange
  title ends in "…" on a narrow screen, never the key hint.
- **In a right-to-left language,** a left-to-right part that should keep its order (the arrows)
  goes between U+2066 and U+2069, a left-to-right isolate.

## A new string, a new language

- **A new string** goes into `values/` and every translation in the same change; a removed one
  leaves them all. Lint fails the build otherwise (`MissingTranslation`, `ExtraTranslation`; every
  lint warning fails it, [`TESTING.md`](TESTING.md#lint-and-compiler-warnings)), which keeps every
  translation complete.
- **A new language** is a `values-<language>/strings.xml` in each module with strings, with every
  string, and a line in `LANGUAGES` (`ui/src/testFixtures/…/testing/TvDevice.kt`), so the layout
  tests check every screen in it.

## Checking a translation

- **The layout tests** (`HomeLayoutTest`, `SettingsLayoutTest`) run every screen in every
  language, with the longest date each has: nothing cut, except where the layout allows it, and
  everything fits ([`TESTING.md`](TESTING.md#layouts-on-other-screens)).
- **On an emulator,** from API 33 on, Luncher's own language can change without the device's:

  ```bash
  adb shell cmd locale set-app-locales com.luncher.launcher.debug --locales el   # Greek
  adb shell cmd locale set-app-locales com.luncher.launcher.debug --locales ""   # the device's again
  ```

  The apps' names and the device's own screens stay in the device's language. On older versions,
  change the device's language in its settings.
