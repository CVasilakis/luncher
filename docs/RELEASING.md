# Releasing

A release is a GitHub release tagged `vX.Y.Z`, with the signed APK `luncher-vX.Y.Z.apk` and its
SHA-256 as downloads. The [release workflow](../.github/workflows/release.yml) makes it as a
draft, from `main`; its notes are written and it is published by hand.

## Versions

A version is `X.Y.Z`, each part 0 to 999, and each release's is higher than the last one's:
Android updates an app only to a higher `versionCode`. The version is kept in
`app/build.gradle.kts`, as two values that are both changed by hand before a release:

```kotlin
versionCode = 1_002_003
versionName = "1.2.3"
```

`versionCode` is X × 1,000,000 + Y × 1,000 + Z, so a higher version always has a higher code;
Gradle fails every build whose two values don't agree, and says which `versionCode` the
`versionName` needs. Both are written out as numbers and text, never computed, because F-Droid
finds a release's version by reading them from the file at the release's tag
([F-Droid](#f-droid)). Between releases, `main` holds the last release's version.

To build a release locally, check out its tag:

```bash
git checkout v1.2.3
./gradlew assembleRelease   # unsigned
```

## The signing key

Every release must be signed with the same key: Android installs an update only over an app
signed with the key it was installed with. If the key is lost, a new release can't update the
installed one, and users have to uninstall Luncher, losing its settings, to install it.

It is made once, as a PKCS #12 keystore holding the key under the alias `luncher`. `keytool` asks
for the keystore's password:

```bash
keytool -genkeypair -keystore ~/luncher-release.p12 -storetype PKCS12 -alias luncher \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Luncher"
```

Keep the keystore and its password in two places outside the repository, e.g. a password manager
and an offline drive. `.gitignore` excludes `*.p12`, `*.jks` and `*.keystore`.

The workflow reads it from two repository secrets (Settings → Secrets and variables → Actions →
New repository secret):

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | the keystore, as one line of base64: `base64 -w0 ~/luncher-release.p12` |
| `RELEASE_KEYSTORE_PASSWORD` | its password |

GitHub hands a secret to any workflow run that names it, on any branch, so anyone who can push
to the repository can read the key. In the release workflow, only the job that signs gets it: it
runs `apksigner` on the APK the build job made, never Gradle and the build's plugins and
dependencies, and it has no token that can change the repository.

## Making a release

1. **Raise the version:** change `versionName` and `versionCode` in `app/build.gradle.kts`
   ([Versions](#versions)), add the version's changes to the store listing as
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
   ([`fastlane/`](../fastlane/README.md)), and push the change to `main`.
2. **Run the workflow:** Actions → Release → Run workflow, on `main`. It releases the version
   `app/build.gradle.kts` holds, and refuses another branch, a version that isn't higher than the
   last release or already has a release, and a missing signing secret. A tag `v1.2.3` made by
   hand beforehand is kept if it's on the commit the run releases, and refused on any other
   commit: F-Droid builds the commit the tag names. Before tagging, the run also fails if F-Droid
   couldn't publish the signed APK ([F-Droid](#f-droid)).
3. **What it makes:** the tag `v1.2.3` on the commit it built, unless it's there already, and a
   draft release "Luncher 1.2.3" with `luncher-v1.2.3.apk`, `luncher-v1.2.3.apk.sha256`, and
   notes from a template that already holds the install command and both checksums (the APK's,
   and the signing certificate's that `apksigner verify --print-certs` shows).
4. **Finish it:** on the Releases page, edit the draft, write the notes, and publish it.

To drop a draft, delete it on the Releases page and delete its tag:
`git push origin --delete v1.2.3`. A run that failed in its last step, after tagging, can be run
again as it is, as long as `main` hasn't moved on: its tag is on the commit the new run releases.

## F-Droid

F-Droid builds every app it publishes from source. It can publish a release's own APK, signed
with the release key, instead of its build signed with F-Droid's key, so that an install from
F-Droid and one from GitHub update each other. Before it does, it checks that its build is the
same APK: it copies the release's signature onto its build and publishes the release's APK only
if the signature verifies there. A release that fails this isn't published on F-Droid at all
([F-Droid's reproducible builds](https://f-droid.org/en/docs/Reproducible_Builds/)).

The release workflow's `reproduce` job makes the same check before anything is tagged, in
F-Droid's build server image, and the run fails if either step fails:

1. The signature must carry over to the APK the build job made. This fails when signing changed
   more than the signature: from build-tools 35 on, `apksigner` redoes the APK's alignment
   padding unless it's given `--alignment-preserved true`.
2. It must carry over to an APK built again the way F-Droid builds: on Debian, with the image's
   JDK, at the path F-Droid builds in. When this fails, the run's `reproduce-diff` artifact holds
   both unsigned APKs; [diffoscope](https://diffoscope.org/) shows what differs.

What keeps the APK reproducible:

- **Nothing in it depends on when, where or by whom it was built:** no build times, absolute
  paths, host or user names. A version shown in the app comes from `versionName`, not from the
  build.
- **It's built from a clean checkout of the tagged commit,** as the workflow does: the Android
  Gradle plugin writes the commit into the APK (`META-INF/version-control-info.textproto`).
- **The same JDK major version as F-Droid's:** different major versions compile to different
  bytecode. The build job's `java-version` stays at the image's default JDK (21) unless F-Droid's
  recipe for Luncher names another.
