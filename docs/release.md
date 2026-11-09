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

## Android test builds (#378)

`.github/workflows/release-android.yml` builds a signed AAB and APK and sends them to testers. Run it
from *Actions › Release Android › Run workflow*, choosing **app-distribution** (fastest, by email),
**play-internal** (installed from Play, the build Play Integrity attests) or **both**. A `v*` tag push
(`git tag v0.2.0 && git push origin v0.2.0`) runs it with **both** and names the version after the
tag; a manual run is `0.0.<run>`. `versionCode` is `run × 10 + attempt`, so a re-run of the same
run is still newer than its first attempt.

### One-off setup

1. **The `release` environment.** *Settings › Environments › New environment* `release`, with
   *Required reviewers* set to the owner: every run waits for a click before it can read a secret.
   This is the only gate: without it, anyone who can push a `v*` tag publishes to testers.
2. **Firebase App Distribution.** Firebase console › *App Distribution* › *Get started* for the
   Android app; *Testers & Groups* › a group with the alias `testers`. Then a service account in the
   Google Cloud project with the single role **Firebase App Distribution Admin**, and a JSON key.
3. **Play Console.** The app `com.kitchenai.app` created, *App integrity* › Play App Signing on, and
   one AAB uploaded **by hand** to *Testing › Internal testing* (Play refuses an API upload for an
   app that has never had a release). Then *Users and permissions* › invite the service account of
   step 4 with *Release to testing tracks* on this app only.
4. **Play service account.** Google Cloud › *IAM › Service accounts* › a new one with no project
   role, and a JSON key; Play Console grants it the app permission above.

### Secrets and variables (in the `release` environment)

| Name | Kind | What it is |
|---|---|---|
| `GOOGLE_SERVICES_JSON` | secret | `base64` of `google-services.json`, refreshed after #377 step 2 |
| `ANDROID_UPLOAD_KEYSTORE` | secret | `base64 -i kitchenai-upload.jks` |
| `ANDROID_UPLOAD_STORE_PASSWORD` | secret | the keystore password |
| `ANDROID_UPLOAD_KEY_ALIAS` | secret | `upload`, or whatever alias the key was made with |
| `ANDROID_UPLOAD_KEY_PASSWORD` | secret | the key password |
| `FIREBASE_ANDROID_APP_ID` | secret | Firebase › *Project settings* › the Android app's *App ID* (`1:…:android:…`) |
| `FIREBASE_DISTRIBUTION_SA` | secret | the App Distribution service account's JSON key |
| `PLAY_SERVICE_ACCOUNT_JSON` | secret | the Play service account's JSON key |
| `GOOGLE_WEB_CLIENT_ID` | variable | Google Sign-In's Web client id (`kitchenai.googleWebClientId`) |
| `PRIVACY_POLICY_URL` | variable | the hosted policy (#380); blank keeps the row hidden |

Rotating any of them is a new value in the environment; nothing is stored anywhere else. The run
deletes the keystore and both JSON keys from the runner when it ends, pass or fail.

### Adding a tester

- **App Distribution:** *App Distribution › Testers & Groups* › add the email to `testers`. They get
  an invitation, install the App Tester app once, and every later build arrives as a notification.
- **Play internal testing:** *Testing › Internal testing › Testers* › add the email to the list, then
  send them the *opt-in link* on the same page; after opting in they install from the Play Store.

Each run keeps the AAB, the APK and `mapping.txt` as an artifact for 90 days: the mapping is what
turns an obfuscated stack trace from that build back into names.

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

---

## Privacy policy, account deletion and store forms (#380)

External testers and both stores ask for a privacy policy URL, and Google Play also for a web page
where someone without the app can ask for their account to be deleted. Both live in `hosting/`, as
plain HTML in English and Spanish, served by Firebase Hosting on the project's own domain:
no domain to buy.

| Page | URL |
|---|---|
| Privacy policy | `https://<projectId>.web.app/privacy` (`/privacy-es` in Spanish) |
| Delete your account | `https://<projectId>.web.app/delete-account` (`/delete-account-es`) |

### Publishing

1. Replace every `CONTACT_EMAIL_PLACEHOLDER` in `hosting/*.html` with the address that answers
   privacy and deletion requests, and commit it. `firebase deploy --only hosting:public` refuses to upload
   while one is left (`tools/check-hosting.mjs`, run as the hosting `predeploy`).
2. Once per checkout, map the hosting target to the project's default site (the admin web is a
   second target, `admin/README.md`): `firebase target:apply hosting public <projectId> --project <projectId>`.
   Then `firebase deploy --only hosting:public --project <projectId>`.
3. Set the URL where the apps read it, so *Profile › About › Privacy policy* appears:
   - GitHub › the `release` environment › variable `PRIVACY_POLICY_URL` = `https://<projectId>.web.app/privacy`
     (both release workflows read it; the iOS one writes the `https:/$()/` form itself);
   - locally, `kitchenai.privacyPolicyUrl` in `~/.gradle/gradle.properties` and `PRIVACY_POLICY_URL`
     in `iosApp/Configuration/Local.xcconfig` (written `https:/$()/…`).
4. Paste the same URL in Play Console (*App content › Privacy policy*), App Store Connect (*App
   Privacy › Privacy Policy URL*) and TestFlight's external-testing *Test Information*.

When what the app collects changes, the pages change in the same PR, with the date at their top.

### Google Play › Data safety

| Question | Answer |
|---|---|
| Does the app collect or share user data? | Collects: yes. Shares: no (Google processes it as our service provider, which Play does not count as sharing) |
| Is all data encrypted in transit? | Yes |
| Can users ask for their data to be deleted? | Yes, in the app and at `/delete-account` |
| Personal info › Name | Collected, optional (sign-in provider or the name the user sets); app functionality |
| Personal info › Email address | Collected, optional (Google or Apple sign-in); account management |
| Personal info › User IDs | Collected, required; account management, app functionality |
| Health and fitness › Health info | Collected, optional: food allergies and diets the user avoids; app functionality |
| App activity › Other user-generated content | Collected, required: pantry, shopping lists, saved recipes; app functionality |
| Device or other IDs | Collected, required: App Check attestation; fraud prevention and security |
| App info and performance › Crash logs, Diagnostics | Collected, required, release builds only (#381); app functionality |
| Location, financial, contacts, messages, photos, audio, files, calendar, web browsing | Not collected |

Data processed only on the device (reminder settings, the last ideas) is not "collected" in Play's sense.

### App Store › App Privacy

- **Data used to track you:** none.
- **Data linked to you:** *Contact Info › Name* and *Email Address* (App Functionality); *Identifiers ›
  User ID* (App Functionality); *Health & Fitness › Health* for avoided foods (App Functionality);
  *User Content › Other User Content* for pantry, lists and recipes (App Functionality).
- **Data not linked to you:** *Identifiers › Device ID* through App Check (App Functionality);
  *Diagnostics › Crash Data* through Crashlytics (App Functionality, #381).
- No analytics, advertising or third-party SDK collects anything else.

## Crash reports (#381)

Crashlytics reports from **release builds only**, on both platforms. A debug build sends nothing:
on Android the build type sets `firebase_crashlytics_collection_enabled` in the manifest, and on
iOS `iOSApp.swift` switches collection off under `#if DEBUG`.

### What a report holds

- **Crashes:** the stack trace, device model, OS version and app version. Nothing from the app's
  data: no custom keys, no logs and no user id are set.
- **Non-fatal errors** (today, a failed `suggestRecipes` call): the operation, the error's kind and
  the Firebase status code, for example `suggestRecipes: Unknown (INTERNAL)`. Never a message,
  because an SDK message can quote the request. `NonFatalSummaryTest` pins this, and being offline
  is never reported.

### Symbols

- **Android:** the release workflow sets `kitchenai.crashlyticsUploadMapping=true`, so each build
  uploads its R8 mapping and its stack traces come back deobfuscated. Elsewhere the upload task is
  off, because CI builds against a template `google-services.json`.
- **iOS:** the *Upload Crashlytics dSYMs* build phase uploads the archive's dSYMs in Release. The
  Kotlin framework is static, so its code is in the app binary and symbolicated with it.

### Checking it once per platform

1. Add a temporary crash trigger (for example `throw RuntimeException("Test crash")` behind a
   button) and ship one test build through the release workflow.
2. Press it, relaunch the app (reports are sent on the next launch) and wait a few minutes.
3. In *Firebase console › Crashlytics*, confirm the crash appears with readable file and line
   names, and that nothing in it is a name, email, ingredient, recipe text or location.
4. Remove the trigger.

### Store forms and the policy

Crash data is in the *Data safety* and *App Privacy* answers above, and in the privacy policy's
*Crash reports* section in both languages. Update all three if a report ever carries more.
