# Releasing test builds

How a build reaches a real phone. Each section belongs to one issue; the order is the order of
the work: signing (#375, #376), backend (#377), distribution (#378, #379).

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
