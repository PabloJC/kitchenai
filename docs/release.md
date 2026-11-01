# Releasing test builds

How a build reaches a real phone. Each section belongs to one issue; the order is the order of
the work: signing (#375, #376), backend (#377), distribution (#378, #379).

---

## Android signing (#375)

### The upload key

Play App Signing holds the key users' devices see; we hold only the **upload key**, which proves
an upload came from us. Losing it is recoverable (Play Console › *App integrity* › *Request
upload key reset*), leaking it is not, so it never enters the repository or a chat.

Generate it once, on the owner's machine:

```sh
keytool -genkeypair -v -keystore kitchenai-upload.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

Keep the `.jks` and both passwords in the owner's password manager. CI receives them as secrets
(#378).

### Building signed

Put these in `~/.gradle/gradle.properties` (never in the project's `gradle.properties`):

```properties
kitchenai.uploadStoreFile=/absolute/path/to/kitchenai-upload.jks
kitchenai.uploadStorePassword=…
kitchenai.uploadKeyAlias=upload
kitchenai.uploadKeyPassword=…
```

Then:

```sh
./gradlew :androidApp:bundleRelease -Pkitchenai.versionCode=2 -Pkitchenai.versionName=0.2.0
# AAB: androidApp/build/outputs/bundle/release/androidApp-release.aab
./gradlew :androidApp:assembleRelease
# APK, for a cable install: androidApp/build/outputs/apk/release/androidApp-release.apk
```

Without the four `upload*` properties the same tasks build an **unsigned** release: it cannot be
installed or uploaded, but R8 has run, which is what CI's *Build Android (release, unsigned)* job
checks on every PR.

`versionCode` must grow with every upload. Locally it defaults to 1; the upload workflow (#378)
will pass its run number. The PR check above builds with the default.

### Fingerprints for Firebase

Google Sign-In and Play Integrity check the certificate's fingerprints, so the upload key's go to
Firebase (#377):

```sh
keytool -list -v -keystore kitchenai-upload.jks -alias upload | grep -E 'SHA1|SHA256'
```

Once the app is on Play, Play re-signs it with the **app signing key**; its fingerprints (Play
Console › *App integrity* › *App signing key certificate*) are registered as well, or a build
installed from Play fails Google Sign-In while the same build installed by cable works.

### Smoke path on a real device

Install the signed APK (`adb install -r androidApp-release.apk`) and, with R8 on, walk:

1. Continue as guest, then add a pantry item from the list and one typed by hand.
2. Generate ideas; open one; save it; cook it and confirm the pantry deduction.
3. Add a recipe's missing items to the list; tick one and move it to the pantry.
4. Profile: change a preference, set your name, open About › Photo credits and Open-source licences.
5. Turn reminders on and allow notifications; start a cooking timer and lock the phone.

A crash or a `ClassNotFoundException` / `MissingFieldException` in `adb logcat` is an R8 rule
missing from `androidApp/proguard-rules.pro`; add it with a one-line reason.

---

## iOS signing (#376)

### Once, in the Apple Developer account

1. *Certificates, Identifiers & Profiles › Identifiers*: register the App ID `com.kitchenai.app`
   (it must match `PRODUCT_BUNDLE_IDENTIFIER` in `iosApp/Configuration/Config.xcconfig` and the
   app registered in Firebase) with the **Sign in with Apple** and **App Attest** capabilities.
2. *App Store Connect › Apps › +*: create the app with the same bundle id. TestFlight lives there.
3. Note the **Team ID** (*Membership details*).

### Once, on the Mac that archives

```sh
cp iosApp/Configuration/Local.xcconfig.template iosApp/Configuration/Local.xcconfig
# set TEAM_ID=… in it; Local.xcconfig is in .gitignore
```

Signing stays **Automatic**: Xcode creates the development and distribution certificates and
profiles for that team the first time it needs them. Sign in with the Apple ID under *Xcode ›
Settings › Accounts*.

### What the configurations differ in

| | Debug | Release |
|---|---|---|
| App Check provider | debug token (`#if DEBUG` in `iOSApp.swift`) | App Attest, DeviceCheck fallback |
| `appattest-environment` entitlement | `development` | `production` (`APP_ATTEST_ENVIRONMENT` in `Config.xcconfig`) |
| Kotlin framework | debug | release, optimised (`embedAndSignAppleFrameworkForXcode` follows the configuration) |

CI links the release device framework on every PR (`linkReleaseFrameworkIosArm64` in *Build iOS
framework*), so a Kotlin/Native release-only break shows up there rather than at archive time.

### Archiving

1. Put `GoogleService-Info.plist` in `iosApp/iosApp/` (see `iosApp/README.md`).
2. Fill `GOOGLE_IOS_CLIENT_ID` and `GOOGLE_IOS_REVERSED_CLIENT_ID` in `Config.xcconfig` (or
   `Local.xcconfig`), and `PRIVACY_POLICY_URL` once it is hosted (#380), written `https:/$()/…`.
3. Set a build number that grows with every upload: `CURRENT_PROJECT_VERSION` in `Local.xcconfig`.
4. Select *Any iOS Device (arm64)*, then *Product › Archive*. In the Organizer, *Distribute App ›
   TestFlight & App Store* uploads it; *Release Testing* or *Debugging* installs it on registered
   iPhones directly.

### Smoke path on a real iPhone

1. Sign in with Apple, then sign out and sign in with Google.
2. Add pantry items; generate ideas; open, save and cook a recipe.
3. Start a cooking timer and lock the phone; it rings.
4. Allow notifications and turn reminders on; with something expiring tomorrow, one arrives.
5. In the Firebase console, *App Check › Apps* shows verified requests from the iOS app once the
   build came through TestFlight (#377 registers it).

---

## Backend for release builds (#377)

A test build talks to the same Firebase project as development. Before the first one goes out,
the project must run the code on `main` and recognise the **release** apps, which are signed with
different keys from the debug builds. Everything here is done by whoever holds the project's
credentials; nothing in CI deploys or changes it.

### 1. Deploy what `main` expects

Follow `firebase/README.md` › *Deploying*, in its order, naming the project every time:

1. `firebase deploy --only firestore:rules,firestore:indexes,storage --project <projectId>`
   (rules changed in #351 for the ownership hand-over; storage with them, so the two never drift).
2. `node tools/seed.mjs --project <projectId>` (the seed changed in #345: `food-groups` now declares
   `SHOPPING_SECTIONS`, which is what groups the shopping list by section).
3. `firebase deploy --only functions --project <projectId>`, then `firebase functions:list` shows
   every callable in `functions/src/index.ts`.
4. The smoke check (`tools/smoke-rules.mjs`), which must end without an unexpected result.

Then record the deployment in the table at the end of this section.

### 2. Register the release signing certificates (Android)

Firebase › *Project settings* › the Android app › *SHA certificate fingerprints*. Add **SHA-1 and
SHA-256** of:

| Certificate | Where it comes from | Why |
|---|---|---|
| Debug keystore | `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android` | Already there for development; keep it |
| Upload key | `docs/release.md` › *Android signing* › *Fingerprints* | A release APK installed by cable or from App Distribution |
| Play app signing key | Play Console › the app › *Test and release › App integrity* › *App signing key certificate* | The build Play installs, re-signed by Google |

Then download `google-services.json` again (the OAuth clients for Google Sign-In change with the
fingerprints), replace the local copy and update the `GOOGLE_SERVICES_JSON` secret
(`base64 -i google-services.json | pbcopy`). A Google Sign-In that works from Android Studio and
fails in a Play build is a missing Play signing fingerprint, every time.

### 3. Register both apps in App Check (enforcement stays off)

*App Check › Apps*, one row per platform (`docs/infra.md` › *Register the app before the token
means anything*):

- **Android, Play Integrity.** Play Console › *Test and release › App integrity* › *Play Integrity
  API* › link the Google Cloud project that holds Firebase. Then register the provider in Firebase
  with the SHA-256 of the **Play app signing** key. Only a build installed from Play attests;
  a cable or App Distribution install sends requests App Check counts as unverified.
- **iOS, App Attest.** Register with the Team ID (`docs/release.md` › *iOS signing*). Only a build
  from TestFlight or the App Store attests; the DeviceCheck fallback covers older devices.

**Do not turn enforcement on.** The gate and its order are in `docs/infra.md` › *Enforcement*, and
it needs builds attesting from **both** platforms first (*Enforcement is off on purpose: iOS cannot
attest yet*). Until then enforcement would lock out every cable and App Distribution install, with
an error that never names App Check.

### 4. Restrict the API keys

Google Cloud console › *APIs & Services › Credentials*:

- **Android key:** *Android apps* restriction with package `com.kitchenai.app` and the SHA-1 of
  each certificate in step 2.
- **iOS key:** *iOS apps* restriction with bundle id `com.kitchenai.app`.
- Both: *API restrictions* to the Firebase APIs the app calls (Identity Toolkit, Token Service,
  Firestore, Firebase Storage, Cloud Functions, Firebase App Check, Firebase Installations).

The smoke check then needs the fingerprint it presents (`--cert-sha1`, see `firebase/README.md`).

### 5. Sign-in providers

*Authentication › Sign-in method*:

- **Google:** enabled; the Web client id is `kitchenai.googleWebClientId` (Android) and the iOS
  client id is `GOOGLE_IOS_CLIENT_ID` (`Config.xcconfig`).
- **Apple:** enabled, with the Services ID, Team ID, Key ID and private key from the Apple
  Developer account (*Keys* › a key with *Sign in with Apple*). A mismatch fails Apple sign-in only,
  with an `invalid-credential` error.
- **Anonymous:** enabled; every guest starts there.

### Deployments

| Date | Commit on `main` | Rules | Seed | Functions | Smoke check | By |
|---|---|---|---|---|---|---|
| | | | | | | |

---

## iOS test builds (#379)

`.github/workflows/release-ios.yml` archives Release on a macOS runner and uploads it to App Store
Connect, where it appears under *TestFlight* after processing (usually 10–30 minutes). Run it from
*Actions › Release iOS › Run workflow*, or push a `v*` tag, which also names the version
(`v0.2.0` → `0.2.0`); a manual run is `0.0.<run>`. The build number is `<run>.<attempt>`, so a
re-run is still a new build.

Signing is **cloud-managed**: `xcodebuild -allowProvisioningUpdates` with an App Store Connect API
key asks Apple for the distribution certificate and profile during the run, so neither is stored
in the repository, in secrets or on the runner. `ITSAppUsesNonExemptEncryption = NO` in `Info.plist`
answers the export-compliance question, so builds are not held at *Missing Compliance*.

### One-off setup

1. The account work in *iOS signing* (App ID with Sign in with Apple and App Attest, the app in
   App Store Connect).
2. **API key.** App Store Connect › *Users and Access › Integrations › App Store Connect API* ›
   *Team keys* › generate one with the **Admin** role (cloud-managed signing creates certificates,
   which *App Manager* cannot). Download the `.p8` once; note the *Key ID* and *Issuer ID*.
3. The `release` environment from *Android test builds*, if not created yet.

### Secrets and variables (in the `release` environment)

| Name | Kind | What it is |
|---|---|---|
| `GOOGLE_SERVICE_INFO_PLIST` | secret | `base64` of `GoogleService-Info.plist` |
| `APPLE_TEAM_ID` | secret | *Membership details › Team ID* (not secret in itself; kept with the rest) |
| `APP_STORE_CONNECT_KEY_ID` | secret | the API key's *Key ID* |
| `APP_STORE_CONNECT_ISSUER_ID` | secret | the *Issuer ID* above the keys list |
| `APP_STORE_CONNECT_KEY_P8` | secret | the contents of `AuthKey_<KeyID>.p8` |
| `GOOGLE_IOS_CLIENT_ID` | variable | Google Sign-In's iOS client id (`CLIENT_ID` in `GoogleService-Info.plist`) |
| `GOOGLE_IOS_REVERSED_CLIENT_ID` | variable | the same id reversed (`REVERSED_CLIENT_ID`) |
| `PRIVACY_POLICY_URL` | variable | shared with Android; the workflow writes it in the `https:/$()/` form an xcconfig needs |

The workflow writes these into `Local.xcconfig` for the run and deletes it, the key and
`GoogleService-Info.plist` when it ends, pass or fail.

### Testers

- **Internal** (up to 100 people with an App Store Connect role): *TestFlight › Internal Testing* ›
  a group with *automatic distribution* on; each new build reaches them without review.
- **External** (by email or a public link): *TestFlight › External Testing*. The first build of a
  version goes through a short Beta App Review, which asks for a privacy policy URL (#380), a beta
  description and a contact email.

Each run keeps the archive's dSYMs as an artifact for 90 days, for symbolicating a crash report.
