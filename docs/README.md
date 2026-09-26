# docs/

Guides that cover the whole project rather than one folder. They come after the root README and
before the folder READMEs ([reading order](../README.md#documentation)).

| File | Contents |
|---|---|
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Modules, layers and the rules for where code goes. |
| [`TESTING.md`](TESTING.md) | Test tiers: what goes where, how to run them, on which emulators, and in CI. |

## Writing the docs

These apply to every document in the repository, and to comments that explain code.

- **Each fact in one place.** A fact goes in the one document whose topic it is: the root README
  for what the project is and how to get it running, `docs/` for what spans the whole project, a
  folder's README for what's inside that folder, a comment for why one line of code or
  configuration is there. Everywhere else, link to it instead of repeating it.
- **From the general to the specific.** A document may rely on the ones above it in the
  [reading order](../README.md#documentation) and links down for details, so each one reads on
  its own without restating what comes before it.
- **Every top-level folder has a `README.md`**, except `.github/`: GitHub shows a
  `.github/README.md` on the repository's front page instead of the root one.
- **The current state, not history.** No changelogs, dates or "we tried X" stories.
- **No machine-specific measurements.** How long a boot, build, test run or key press takes
  depends on the host, so describe it relatively ("slower", "faster than a cold boot"). Sizes, RAM
  needs and counts are fine.
- **Paths use `~`**, never an absolute home path, so examples work on any machine.
- **Docs change with the code.** A change to something a document describes updates that
  document in the same change.
