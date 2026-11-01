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
