# iosApp

`iosApp.xcodeproj` already carries the Run Script Phase that embeds the Kotlin framework,
and `User Script Sandboxing = No`.

## Cloning from scratch

`GoogleService-Info.plist` is not versioned. Download it from the Firebase console and drag
it onto the `iosApp` target → *Copy items if needed* + *Add to target*.

Firebase dependencies come through Swift Package Manager: *File → Add Package
Dependencies…* → `https://github.com/firebase/firebase-ios-sdk` → *Up to Next Major* →
`FirebaseAuth`, `FirebaseFirestore`, `FirebaseStorage`.

If the build fails with `Unable to resolve module dependency: 'FirebaseCore'`, the package
products are not linked to the target even though the package shows up in the navigator.
Check *General → Frameworks, Libraries, and Embedded Content*.

## About the frameworks

Xcode links a single framework, `ComposeApp`, which also exports `:shared`
(`export(projects.shared)` in `composeApp/build.gradle.kts`). That is why the `.swift` files
`import ComposeApp` and never `import Shared`.
