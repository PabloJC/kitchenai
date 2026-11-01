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

## Android test builds (#378)

`.github/workflows/release-android.yml` builds a signed AAB and APK and sends them to testers. Run it
from *Actions › Release Android › Run workflow*, choosing **app-distribution** (fastest, by email),
**play-internal** (installed from Play, the build Play Integrity attests) or **both**. A `v*` tag push
(`git tag v0.2.0 && git push origin v0.2.0`) runs it with **both** and names the version after the
tag; a manual run is `0.0.<run>`. `versionCode` is the run number, so every upload is newer.

### One-off setup

1. **The `release` environment.** *Settings › Environments › New environment* `release`, with
   *Required reviewers* set to the owner: every run waits for a click before it can read a secret.
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
