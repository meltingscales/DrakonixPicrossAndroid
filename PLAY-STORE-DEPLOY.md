# Publishing Drakonix Picross to the Google Play Store

A start-to-finish walkthrough for taking this project from a local build to a
public Play Store listing. Steps marked **(once)** only need to be done the
first time; everything else repeats for each release.

## 0. Prerequisites

- A Google account you're willing to tie to a developer profile.
- $25 (one-time registration fee).
- This repo building: `just build` must succeed.

## 1. Create a Play developer account (once)

1. Go to <https://play.google.com/console/signup>.
2. Choose a **personal** account type (organization requires a D-U-N-S number).
3. Pay the $25 fee and complete identity verification (government ID;
   usually approved within 48 hours).

**Important for new personal accounts** (created after November 2023): before
you can publish to production, Google requires a **closed test with at least
12 testers opted in for 14 continuous days**. Plan for this — recruit friends
or a testing community early. Accounts predating the rule skip this.

## 2. Create an upload keystore (once)

The app must be signed. Play uses "Play App Signing": you sign with an
*upload key*, Google re-signs with the distribution key it manages.

```sh
keytool -genkeypair \
  -keystore ~/keystores/drakonix-picross-upload.jks \
  -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Pick a strong password and **back this file up somewhere safe** (password
manager attachment, encrypted drive). If you lose it you can request an
upload-key reset from Google, but it's a support ticket and days of delay.
Never commit the keystore to git.

## 3. Wire release signing into the build (once)

Create `keystore.properties` in the repo root (it is **not** committed —
add it to `.gitignore` first):

```properties
storeFile=/home/henrypost/keystores/drakonix-picross-upload.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=upload
keyPassword=YOUR_KEY_PASSWORD
```

Then in `app/build.gradle.kts`, load it and add a signing config:

```kotlin
import java.util.Properties

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // ...existing minify/proguard settings stay as they are
        }
    }
}
```

## 4. Build the release bundle

Play requires an **Android App Bundle (.aab)**, not an APK:

```sh
just bundle
# output: app/build/outputs/bundle/release/app-release.aab
```

Sanity-check the release build on your phone first (bundletool, or just
temporarily install a release APK):

```sh
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Play around — proguard/R8 minification is on for release builds, so verify
levels load and nothing crashes. (This app has no reflection, so problems
are unlikely, but always check the minified build once.)

## 5. Create the app in Play Console (once)

Play Console → **Create app**:

- App name: *Drakonix Picross*
- Default language, App (not game — or Game → Puzzle, your call), **Free**.
- Declarations: not ads-supported, no in-app purchases.

Note: "Free" is permanent — a free app can never become paid (a paid one
can become free). Fine here.

## 6. Store listing (once, then edit as needed)

Under **Grow → Store presence → Main store listing**:

| Asset | Requirement |
|---|---|
| Short description | ≤ 80 chars, e.g. "Chill picross puzzles. No ads, no nonsense — just pixels." |
| Full description | ≤ 4000 chars |
| App icon | 512×512 PNG (export the launcher art) |
| Feature graphic | 1024×500 PNG |
| Phone screenshots | at least 2, 16:9 or 9:16 (`just screenshot`) |

Screenshots of the Downwell/Game Boy themes make the listing pop.

## 7. Required declarations (once)

All under **Monitor and improve → Policy → App content**:

- **Privacy policy URL** — required for every app, even with zero data
  collection. A one-page static site works (GitHub Pages on this repo:
  "Drakonix Picross stores puzzle progress locally on your device. It
  collects, transmits, and shares no data."). 
- **Data safety form** — declare: no data collected, no data shared. True:
  the app has no network permission at all.
- **Content rating questionnaire** — puzzle game, no violence/gambling/etc.
  → rated Everyone.
- **Ads** — declare none.
- **Target audience** — 13+ is simplest (younger brackets add rules).

## 8. Test, then release

1. **Internal testing** (instant, up to 100 testers): upload the .aab,
   add your own Gmail as a tester, install via the opt-in link. Verify.
2. **Closed testing**: required 14-day / 12-tester run for new accounts
   (see step 1). Promote the same build.
3. **Production**: once eligible, promote the release, set rollout to 100%.
   First production review typically takes from a few hours to ~3 days.
   This app is low-risk (no permissions, no network, no ads), which tends
   to mean a fast review.

## 9. Every release after the first

1. Bump `versionCode` (must strictly increase) and `versionName` in
   `app/build.gradle.kts`.
2. `just bundle`
3. Play Console → the chosen track → **Create new release** → upload
   `app-release.aab` → release notes → roll out.

That's it: no keystore juggling after setup, Google handles re-signing.

## Gotchas worth knowing

- **Keystore loss**: upload key can be reset via support; losing the
  `keystore.properties` passwords means regenerating the keystore too.
- **targetSdk policy**: Google requires targeting a recent API level
  (currently within one year of the latest release). We target 36 — fine
  now; expect to bump it roughly yearly.
- **App name collisions**: "Drakonix Picross" is distinctive, but Play
  rejects names impersonating existing apps.
- **The package id `com.drakonix.picross` is permanent** once published.
  Own the domain or at least be comfortable with the id forever.
