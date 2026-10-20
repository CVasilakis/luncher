# Translations

Luncher's texts are in English, Greek and Arabic. This covers where they are, how they're worded,
how a string is written so that it translates well, and what a new string or language needs.

- **Greek** is kept by the project's maintainer.
- **Arabic** is there so a right-to-left translation is tested on every change, and for users who
  want it. No native speaker has reviewed it yet; corrections are welcome.

## Where the texts are

| Module | Texts | Languages |
|---|---|---|
| `:feature:home` | the home screen and arrange mode | `res/values/strings.xml` (English), `res/values-el/` (Greek), `res/values-ar/` (Arabic) |
| `:feature:settings` | the settings panels, the appearance and background editors, Add from folder, the window after a share | the same |
| `:app` | the app's name | `translatable="false"`: a name, the same in every language |

Strings stay with the screen that shows them, each module's prefixed with its name (`home_…`,
`settings_…`), and each translation sits next to its English, in
`values-<language>/`. A string two modules show ("Settings", "Hidden", "Pictures") is in each, so
each module's translation is complete on its own.

`:domain` has no text: it returns data (`SettingsEntry.HideApps`, whether every app is hidden),
and the screens turn it into strings. Texts that aren't Luncher's come in the device's language
already: the apps' names, from Android, which `:domain` sorts as that language sorts words
(`LabelOrder`: "Άρης" after "Αθήνα" in Greek), the time and date, which `ClockView` formats
with the language's own patterns, a file's size, which Android formats (`Formatter`), and the
pictures' names, which are their files'.

A right-to-left language mirrors the settings panels, the picture library's screens and the
window over the home screen while its wallpaper is drawn, but not the home screen, nor the
editors' preview of it, nor a picture
([`app/README.md`](../app/README.md#manifest-why-each-part-is-there)).

## Wording

Luncher's texts are short, plain and neutral: they say what happened and what to do, never chat.
The English follows these rules; a translation keeps them as its language allows.

- **A title is a short status, not a sentence:** "Picture not added", "Pictures not added", "No
  TV apps installed".
- **The thing is the subject, not Luncher, "it" or "you":** "The file can't be read", "The
  picture is deleted from Luncher's pictures only", not "Luncher can't read it". What to do is said as a command:
  "Share fewer, then try again".
- **The reason first, the detail after a colon:** "Too large: over 50 MB.", "Too many pictures:
  up to 100 can be added at a time."
- **Only the limit that was crossed, with its number:** "Too large: over 64 megapixels.", not
  every limit a picture has.
- **A problem ends with what to do, as "do this, then try again":** "Free up space, then try
  again.", "Save it as JPEG or PNG, then add it again."
- **What isn't a failure isn't worded as one:** a picture the library has already is "Already in
  Luncher's pictures", with OK, not an error.
- **Each reason on its own:** pictures refused are counted by reason, a line each ("2 already in
  Luncher's pictures"), never lumped as "4 pictures weren't added".
- **One name for one thing, everywhere:** "Already in Luncher's pictures" in the message, the
  count lines and Add from folder; "Name already used in Luncher's pictures" in Add from folder and
  below the rename field; "Adding the picture…" for a share and for Add from folder;
  "background", never "wallpaper", for the picture behind the tiles; "added", the opposite of
  "not added", rather than "saved". Options alike read alike: Dim's levels are Blur's words (Off,
  Light, Medium, Strong).
- **What really happens:** "Processing background…", not "Applying effects…", as no effect may
  have been chosen.
- **No semicolons:** two sentences, or a colon when the second part explains the first.
- **Periods end a message's sentences only:** titles, entries, values, count lines and a text in
  place of a list have none ("No pictures in the folder yet").

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
- **In a right-to-left language,** a left-to-right part that should keep its order goes between
  U+2066 and U+2069, a left-to-right isolate, written `\u2066` and `\u2069` in the string: the
  arrange hint's arrows in Arabic would otherwise show in reverse order. An arrow that means
  "then" points the way the text reads (`←` in Arabic's `home_all_hidden`).

## A new string, a new language

- **A new string** goes into `values/` and every translation in the same change; a removed one
  leaves them all. Lint fails the build otherwise (`MissingTranslation`, `ExtraTranslation`; every
  lint warning fails it, [`TESTING.md`](TESTING.md#lint-and-compiler-warnings)), which keeps every
  translation complete.
- **A new language** is a `values-<language>/strings.xml` in each module with strings, with every
  string, and a line in `LANGUAGES` (`ui/src/testFixtures/…/testing/TvDevice.kt`), so the layout
  tests check every screen in it.

## Checking a translation

- **The layout tests** (`HomeLayoutTest`, `SettingsLayoutTest`, `BackgroundLayoutTest`) run every
  screen in every language, with the longest date each has: nothing cut, except where the layout allows it, and
  everything fits ([`TESTING.md`](TESTING.md#layouts-on-other-screens)). In Arabic, that's the
  mirrored settings panels too.
- **On an emulator,** from API 33 on, Luncher's own language can change without the device's:

  ```bash
  adb shell cmd locale set-app-locales com.luncher.launcher.debug --locales el   # Greek
  adb shell cmd locale set-app-locales com.luncher.launcher.debug --locales ar   # Arabic
  adb shell cmd locale set-app-locales com.luncher.launcher.debug --locales ""   # the device's again
  ```

  The apps' names and the device's own screens stay in the device's language. On older versions,
  change the device's language in its settings.
