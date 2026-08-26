# iosApp

`iosApp.xcodeproj` already carries the Run Script Phase that embeds the Kotlin framework,
and `User Script Sandboxing = No`.

## Cloning from scratch

`GoogleService-Info.plist` is not versioned. Download it from the Firebase console and put it
at `iosApp/iosApp/GoogleService-Info.plist` — inside the folder the project synchronises into
the target, next to `Info.plist`. Anywhere else and the app builds, launches and dies on
`FirebaseApp.configure()`, which is what happened until this was fixed.

Firebase dependencies come through Swift Package Manager: *File → Add Package
Dependencies…* → `https://github.com/firebase/firebase-ios-sdk` → *Up to Next Major* →
`FirebaseAuth`, `FirebaseFirestore`, `FirebaseStorage`.

If the build fails with `Unable to resolve module dependency: 'FirebaseCore'`, the package
products are not linked to the target even though the package shows up in the navigator.
Check *General → Frameworks, Libraries, and Embedded Content*.

## Running against the live backend

If every suggestion fails with *"This app could not prove who it is, so suggestions are
unavailable"*, or the console logs `[FirebaseAppCheck][I-FAA004002] Failed to exchange debug
token`, the iOS app itself is not registered in App Check yet — a debug token exchanges
against that registration, and it is per platform: a working Android build says nothing about
iOS. See [`docs/infra.md`](../docs/infra.md#register-the-app-before-the-token-means-anything)
for the registration step and how to pin the token so it survives a container wipe.

## About the frameworks

Xcode links a single framework, `ComposeApp`, which also exports `:shared`
(`export(projects.shared)` in `composeApp/build.gradle.kts`). That is why the `.swift` files
`import ComposeApp` and never `import Shared`.
