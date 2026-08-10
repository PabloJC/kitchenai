# iosApp

## Si has usado el wizard (recomendado)

`iosApp.xcodeproj` viene generado y ya trae la Run Script Phase que embebe el framework
de Kotlin y el `User Script Sandboxing = No`. Sólo falta Firebase:

1. Arrastra `GoogleService-Info.plist` al target `iosApp` → *Copy items if needed* + *Add to target*.
2. `File → Add Package Dependencies…` → `https://github.com/firebase/firebase-ios-sdk`
   → *Up to Next Major* → añade `FirebaseAuth`, `FirebaseFirestore`, `FirebaseStorage`.

Ver [`../docs/step-01b-wizard-merge.md`](../docs/step-01b-wizard-merge.md).

## Si creas el proyecto Xcode a mano

Los `.swift` y el `Info.plist` están versionados aquí, pero el `.pbxproj` no se puede
generar fuera de macOS. Xcode → *New Project → iOS → App* (Interface: SwiftUI,
product name `iosApp`, bundle id `com.kitchenai.app`), guardado en `iosApp/`, y después:

1. Sustituye los ficheros generados por los de este directorio.
2. Añade `GoogleService-Info.plist` y el paquete `firebase-ios-sdk` (pasos 1 y 2 de arriba).
3. Target `iosApp` → *Build Phases* → `+` → *New Run Script Phase*, colócala **antes** de
   *Compile Sources* y pega:

   ```bash
   cd "$SRCROOT/.."
   ./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
   ```

4. *Build Settings* → `User Script Sandboxing` → **No**. Si no, el script falla sin decir por qué.
5. *Build Settings* → `Other Linker Flags` → añade `$(inherited)`.

## Nota sobre los frameworks

Xcode enlaza un único framework, `ComposeApp`, que exporta también `:shared`
(`export(projects.shared)` en `composeApp/build.gradle.kts`). Por eso los `.swift`
hacen `import ComposeApp` y nunca `import Shared`.
