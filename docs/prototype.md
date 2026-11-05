# Installing the prototype on our own devices

For now KitchenAI is a prototype on two phones, not a published app. This is the short path: no
store, no paid Apple Developer account, debug builds installed from the computer. `docs/release.md`
is the long path, for when the app goes to testers or the stores.

## 1. Firebase, once

1. **Deploy what `main` expects** (`firebase/README.md` › *Deploying*):
   ```bash
   firebase deploy --only firestore:rules,firestore:indexes,storage --project <projectId>
   node tools/seed.mjs --project <projectId>
   firebase deploy --only functions --project <projectId>
   ```
2. **Sign-in providers** (*Authentication › Sign-in method*): **Anonymous** and **Google**. Apple is
   not needed: a free Apple account cannot offer it, and the iPhone build hides its button.
3. **App Check, for the suggestions.** The functions refuse a call App Check has not vouched for,
   even with enforcement off for the rest. A debug build vouches with a debug token, one per device:
   - *App Check › Apps*: one row per platform. Android: Play Integrity, with the SHA-256 of the debug
     keystore (`keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`).
     iOS: App Attest, with the personal team id from step 3 below.
   - Launch the app once on each phone, copy the token it prints, and add it under the app's
     *Manage debug tokens*. Android: `adb logcat -s DebugAppCheckProvider`. iPhone: the Xcode
     console. Details in `docs/infra.md` › *App Check*.

Skip the rest of `docs/release.md` › *Backend for release builds*: API key restrictions and the
Play signing key only matter for release builds.

## 2. Android

The debug keystore's SHA-1 is what Google sign-in checks, and it is already registered for
development, so a debug build signs in as it does on the emulator.

1. On the phone: *Settings › About phone* › tap *Build number* seven times, then *Developer options*
   › *USB debugging* on.
2. Connect it and press *Run* in Android Studio, or run `./gradlew :androidApp:installDebug`.
3. For a phone that is not at hand, `./gradlew :androidApp:assembleDebug` writes
   `androidApp/build/outputs/apk/debug/androidApp-debug.apk`. Send it, and allow installing from
   that app when Android asks.

The second phone needs its own App Check debug token (step 1.3).

### Through Firebase App Distribution

So each phone gets new builds from a notification instead of a cable. The **debug** build, uploaded
from your own computer:

- **Why not `release-android.yml`:** a release build attests with Play Integrity, which only vouches
  for an install from Play. From App Distribution its suggestions would fail App Check.
- **Why not CI:** the debug build is signed with your machine's debug keystore. CI's changes every
  run, and Android refuses an update signed by a different key, so always upload from the same computer.

Once:

1. Firebase console › *App Distribution* › *Get started* for the Android app; *Testers & Groups* ›
   a group with the alias `testers` and both emails.
2. `firebase login` on the computer that builds.

Each new version:

```bash
./gradlew :androidApp:assembleDebug
firebase appdistribution:distribute androidApp/build/outputs/apk/debug/androidApp-debug.apk \
  --app <Firebase Android App ID, 1:…:android:…> --groups testers --release-notes "What changed"
```

Or drag the APK onto *App Distribution › Releases* in the console. Each tester accepts the email
invitation once and installs the *App Tester* app; later builds arrive as a notification, and an
update keeps the app's data.

Each phone's App Check debug token (step 1.3) comes from `adb logcat`, so it needs a cable once. The
token survives updates; it changes only if the app is uninstalled or its data cleared.

## 3. iPhone, with a free Apple account

Needs a Mac with Xcode and each iPhone connected by cable at least once.

1. *Xcode › Settings › Accounts* › add your Apple ID. It appears as *Your Name (Personal Team)*.
   Then *Manage Certificates…* › **+** › *Apple Development*.
2. Find the personal team id. It is the `OU` of that certificate:
   ```bash
   security find-certificate -c "Apple Development" -p | openssl x509 -noout -subject
   ```
3. Copy `iosApp/Configuration/Local.xcconfig.template` to `Local.xcconfig` and set:
   ```
   TEAM_ID = <the OU above>
   PERSONAL_TEAM = YES
   GOOGLE_IOS_CLIENT_ID = <CLIENT_ID from GoogleService-Info.plist>
   GOOGLE_IOS_REVERSED_CLIENT_ID = <REVERSED_CLIENT_ID from the same file>
   ```
   `PERSONAL_TEAM = YES` leaves out Sign in with Apple and App Attest, which a personal team cannot
   sign, and hides the Apple button. Guests and Google sign-in work as before.
4. Put `GoogleService-Info.plist` in `iosApp/iosApp/` (`iosApp/README.md`).
5. On the iPhone: *Settings › Privacy & Security › Developer Mode* on (it shows up after the first
   connection to Xcode, and restarts the phone).
6. Open `iosApp/iosApp.xcodeproj`, pick the iPhone as the destination and press *Run*.
7. The first time, the iPhone refuses to open it: *Settings › General › VPN & Device Management* ›
   your Apple ID › *Trust*.

The app keeps running without the cable. Two limits of a free account:

- **It stops opening after 7 days.** Connect the iPhone and press *Run* again; the data is kept.
- **At most 3 apps** signed this way per device.

Installing without the cable needs the paid account (99 €/year): TestFlight and Firebase App
Distribution both need a build signed for distribution, which a personal team cannot produce. With
it, TestFlight internal testing is the easy one (`docs/release.md` › *iOS test builds*).

If Xcode says the bundle identifier `com.kitchenai.app` is not available, another account has
claimed it. Set `PRODUCT_BUNDLE_IDENTIFIER = com.<something-yours>.kitchenai` in `Local.xcconfig`,
add an iOS app with that id in Firebase, and use its `GoogleService-Info.plist` instead.

## What stays off

Debug builds send no crash reports (`docs/release.md` › *Crash reports*), and nothing here
publishes anything. The release workflows, signing keys and privacy pages wait in the repository
until there is a store to send the app to.
