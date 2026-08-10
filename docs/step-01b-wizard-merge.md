# Paso 1 (variante) — Partir del wizard de KMP

El wizard de JetBrains aporta dos cosas que no se pueden escribir a mano de forma fiable:
el **`gradle-wrapper.jar`** y el **`iosApp.xcodeproj`**. Además su matriz de versiones
(Kotlin / AGP / Compose MP) viene probada por JetBrains, que es justo lo que yo no puedo
verificar sin compilar.

Todo lo demás de este repo — `:shared`, Firebase, CI, `CLAUDE.md`, reglas de Firestore —
el wizard no lo genera, así que se conserva tal cual.

---

## Cómo generarlo

<https://kmp.jetbrains.com>

| Campo | Valor | Por qué |
|---|---|---|
| Project name | `KitchenAI` | |
| **Project ID** | **`com.kitchenai.app`** | Debe coincidir exactamente con el `applicationId` y el bundle id de Firebase. Si pones `com.kitchenai` a secas, el `.pbxproj` queda con el bundle id equivocado y `GoogleService-Info.plist` no valida. |
| Android | ✅ | |
| iOS | ✅ **Share UI** | Sin *Share UI* genera SwiftUI nativo y el `iosApp` no sabría cargar Compose. |
| Desktop / Web / Server | ❌ | Fuera del alcance del MVP |

Descarga el ZIP y ejecuta:

```bash
cd ~/Documents/kitchenAI
./scripts/merge-wizard.sh ~/Downloads/KitchenAI.zip
```

---

## Qué hace el merge

| Del wizard | Motivo |
|---|---|
| `gradle/wrapper/` + `gradlew` + `gradlew.bat` | El `.jar` es binario, no puedo generarlo |
| `iosApp/` completo | Contiene el `.pbxproj`, ya con la Run Script Phase que embebe el framework |
| `[versions]` de `libs.versions.toml` (sólo el toolchain) | Combinación Kotlin/AGP/Compose ya validada |
| `composeApp/src/commonMain/composeResources/` | Estructura de recursos multiplataforma |
| `composeApp/src/androidMain/res/` (iconos launcher) | Sin pisar `themes.xml` |

| De este repo | Motivo |
|---|---|
| `shared/` entero | El wizard no crea módulo compartido separado: con *Share UI* lo mete todo en `composeApp` |
| `settings.gradle.kts`, `build.gradle.kts`, ambos `*/build.gradle.kts` | Llevan `:shared`, detekt, ktlint, google-services |
| Las secciones `[libraries]` y `[plugins]` del TOML | Firebase, Koin, coroutines, turbine |
| `.github/`, `CLAUDE.md`, `docs/`, `scripts/`, `config/` | Nada de esto existe en el wizard |
| `firebase/`, `firebase.json`, `.gitignore` | El `.gitignore` del wizard no excluye `google-services.json` |
| `iOSApp.swift`, `ContentView.swift` | Los míos llevan `FirebaseApp.configure()` y el arranque de Koin |

Se descartan `Greeting.kt` y `Platform*.kt` del wizard: sus equivalentes viven en `:shared`
y tener los dos da error de símbolo duplicado.

### Lo que el script deja a propósito sin tocar

- **`androidMinSdk`** se fuerza a **26**. El wizard pone 24, pero el SDK de Firebase y varias
  APIs de Compose se comportan mejor desde 26 y por debajo tendrías que meter desugaring.
- **`[libraries]` y `[plugins]`** no se tocan: las versiones de Firebase, Koin y coroutines
  son independientes del toolchain y las he fijado contra Maven Central.
- **`Info.plist`**: se queda el del wizard, porque el `.pbxproj` lo referencia por ruta.

---

## Cambio que esto obliga en el código

Con el wizard, Xcode enlaza **un solo framework** (`ComposeApp`). Para que Swift vea también
los tipos de `:shared`, hay que exportarlo — ya está aplicado en `composeApp/build.gradle.kts`:

```kotlin
target.binaries.framework {
    baseName = "ComposeApp"
    isStatic = true
    export(projects.shared)      // sin esto, Swift no ve nada de :shared
}
// y la dependencia debe ser api, no implementation:
commonMain.dependencies { api(projects.shared) }
```

Por eso `iOSApp.swift` hace `import ComposeApp` y no `import Shared`.

---

## Gatekeeper: la cuarentena del ZIP

Todo lo que descomprimes de un ZIP descargado lleva el atributo extendido
`com.apple.quarantine`, `gradlew` incluido. Desde terminal no molesta, pero la Run Script
Phase de Xcode falla al ejecutarlo:

```
/bin/sh: ./gradlew: Operation not permitted
```

Fíjate en que es **EPERM** (`Operation not permitted`) y no EACCES (`Permission denied`):
el bit de ejecución está bien, lo que bloquea es Gatekeeper.

`merge-wizard.sh` ya lo limpia. Si montaste el proyecto a mano:

```bash
xattr -dr com.apple.quarantine ~/Documents/kitchenAI
```

---

## Lo que queda a mano en Xcode

El `.pbxproj` del wizard ya trae la Run Script Phase y el sandboxing desactivado, así que
sólo faltan las dos cosas de Firebase:

1. **Deja `GoogleService-Info.plist` en `iosApp/iosApp/` y nada más.** El proyecto del
   wizard usa grupos sincronizados de Xcode 16 (`PBXFileSystemSynchronizedRootGroup`): la
   carpeta se incluye entera de forma automática. Si además lo arrastras al navegador,
   añades una referencia explícita encima y la build falla con
   `duplicate output file … GoogleService-Info.plist`.
2. `File → Add Package Dependencies…` → `https://github.com/firebase/firebase-ios-sdk`
   → *Up to Next Major*. Después, target → **General → Frameworks, Libraries, and Embedded
   Content → +** y añade **sólo** `FirebaseCore`, `FirebaseAuth` y `FirebaseFirestore`.
   Los ~13 paquetes que aparecen resueltos en el navegador son dependencias transitivas de
   Firebase: no se pueden podar y no acaban en el binario salvo que algo enlazado las use.

---

## Verificación

```bash
./gradlew :shared:check
./gradlew :androidApp:assembleDebug
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
open iosApp/iosApp.xcodeproj
```

Si algo falla por versiones, el `libs.versions.toml` anterior está en `.wizard-backup-*/`.
Borra esa carpeta cuando todo compile.

---

## ¿Y si ya ejecuté `finish-setup-macos.sh`?

No hay conflicto. Ese script crea repo, etiquetas y secrets; `merge-wizard.sh` sólo toca
ficheros del proyecto. Ejecuta el merge y vuelve a lanzar la build:

```bash
./scripts/merge-wizard.sh ~/Downloads/KitchenAI.zip
./gradlew :androidApp:assembleDebug
```
