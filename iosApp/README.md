# iosApp

`iosApp.xcodeproj` ya trae la Run Script Phase que embebe el framework de Kotlin y el
`User Script Sandboxing = No`.

## Si clonas el repositorio de cero

`GoogleService-Info.plist` no está versionado. Descárgalo de la consola de Firebase y
arrástralo al target `iosApp` → *Copy items if needed* + *Add to target*.

Las dependencias de Firebase van por Swift Package Manager: *File → Add Package
Dependencies…* → `https://github.com/firebase/firebase-ios-sdk` → *Up to Next Major* →
`FirebaseAuth`, `FirebaseFirestore`, `FirebaseStorage`.

Si el build falla con `Unable to resolve module dependency: 'FirebaseCore'`, es que los
productos del paquete no están enlazados al target, aunque el paquete aparezca en el
navegador: revísalo en *General → Frameworks, Libraries, and Embedded Content*.

## Nota sobre los frameworks

Xcode enlaza un único framework, `ComposeApp`, que exporta también `:shared`
(`export(projects.shared)` en `composeApp/build.gradle.kts`). Por eso los `.swift`
hacen `import ComposeApp` y nunca `import Shared`.
