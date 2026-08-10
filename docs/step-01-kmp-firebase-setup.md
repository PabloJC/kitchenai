# Paso 1 — Setup KMP + Firebase

> Objetivo: repo en GitHub, proyecto KMP compilando en Android e iOS, Firebase conectado desde `shared`.
> **No pases al Paso 2 hasta que el checklist final esté en verde.**

Decisiones tomadas: **Compose Multiplatform** (UI compartida) + **GitLive firebase-kotlin-sdk** (API única en `commonMain`).

---

## 1.0 Prerrequisitos (macOS)

```bash
# Homebrew tooling
brew install gh jenv
brew install --cask temurin@17        # JDK 17 (requerido por AGP)
brew install --cask android-studio     # si no lo tienes
xcode-select --install                 # Command Line Tools

# Verificación
java -version        # -> 17.x
gh --version
xcodebuild -version  # -> Xcode 15+
```

En Android Studio: **Settings → Plugins → "Kotlin Multiplatform"** (instalar) y **SDK Manager → Android SDK 35 + Build Tools**.

Comprobación del entorno KMP (opcional pero muy recomendable):

```bash
brew install kdoctor
kdoctor            # todo debe salir con ✓
```

---

## 1.1 Crear el repositorio en GitHub

```bash
gh auth login          # HTTPS + browser
gh auth status

cd ~/Documents
gh repo create kitchenai \
  --private \
  --description "KitchenAI — Kotlin Multiplatform app (Android/iOS) con Firebase" \
  --clone

cd kitchenai
```

> Si ya tienes la carpeta `kitchenAI` con estos `docs/`, en su lugar:
> ```bash
> cd ~/Documents/kitchenAI
> git init -b main
> gh repo create kitchenai --private --source=. --remote=origin
> ```

Configura de una vez las etiquetas que usará el flujo de issues:

```bash
gh label create "type:feature"   --color 1D76DB --description "Nueva funcionalidad" --force
gh label create "type:chore"     --color C5DEF5 --description "Infra / tooling"     --force
gh label create "type:bug"       --color D73A4A --description "Corrección"          --force
gh label create "layer:domain"   --color 0E8A16 --force
gh label create "layer:data"     --color 5319E7 --force
gh label create "layer:ui"       --color FBCA04 --force
gh label create "platform:android" --color 3DDC84 --force
gh label create "platform:ios"     --color 000000 --force
gh label create "ai-review:approved" --color 0E8A16 --description "La IA aprobó la PR" --force
gh label create "automerge"      --color 6F42C1 --description "Elegible para auto-merge" --force
```

---

## 1.2 Generar el esqueleto KMP (plantilla oficial)

La plantilla oficial de JetBrains garantiza que Kotlin / AGP / Compose MP son versiones compatibles entre sí. **No escribas los ficheros Gradle a mano.**

**Opción A — Web wizard (recomendada, más rápida):**

1. Abre <https://kmp.jetbrains.com/>
2. Rellena:
   - Project name: `KitchenAI`
   - Project ID: `com.kitchenai`
   - Plataformas: **Android** ✅, **iOS** ✅ (marca *Share UI*), resto ❌
3. Descarga el ZIP y descomprímelo dentro del repo:

```bash
cd ~/Documents/kitchenAI
unzip ~/Downloads/KitchenAI.zip -d /tmp/kmp
rsync -a /tmp/kmp/KitchenAI/ ./
chmod +x gradlew
```

**Opción B — Android Studio:** `File → New → New Project → Kotlin Multiplatform App`.

Resultado esperado:

```
kitchenAI/
├── composeApp/          # UI Compose Multiplatform (androidMain, iosMain, commonMain)
├── iosApp/              # proyecto Xcode
├── gradle/libs.versions.toml
├── settings.gradle.kts
└── build.gradle.kts
```

Primer commit antes de tocar nada:

```bash
git add -A && git commit -m "chore: scaffold KMP desde plantilla oficial"
git push -u origin main
```

---

## 1.3 Añadir el módulo `shared` con capas (clean architecture)

El wizard sólo crea `composeApp`. Añadimos `shared`, que contiene domain + data y **no depende de la UI**.

```bash
./scripts/bootstrap-structure.sh
```

Estructura que crea:

```
shared/src/commonMain/kotlin/com/kitchenai/shared/
├── domain/
│   ├── model/          # entidades puras, sin Firebase ni Android
│   ├── repository/     # interfaces (puertos)
│   └── usecase/        # casos de uso, 1 clase = 1 operate()
├── data/
│   ├── remote/firebase/  # implementaciones con GitLive SDK
│   ├── local/            # caché
│   ├── dto/              # modelos de transporte + mappers
│   └── repository/       # implementaciones de los puertos
├── di/                   # módulos Koin
└── core/                 # Result, DispatcherProvider, errores

composeApp/src/commonMain/kotlin/com/kitchenai/app/
├── presentation/<feature>/   # ViewModel + UiState + screen composable
├── navigation/
└── designsystem/
```

**Regla de dependencias (la valida la IA en cada PR):**
`composeApp → shared.domain` ✅ · `shared.data → shared.domain` ✅ · `shared.domain → cualquier cosa` ❌

Registra el módulo en `settings.gradle.kts`:

```kotlin
include(":composeApp")
include(":shared")
```

---

## 1.4 Dependencias: `gradle/libs.versions.toml`

**No sustituyas el fichero**; añade sólo estas entradas a las secciones que ya existen.

```toml
[versions]
# ... deja lo que generó el wizard (kotlin, agp, compose-multiplatform, android-*) ...
gitliveFirebase = "2.6.0"
firebaseBom     = "34.1.0"
googleServices  = "4.4.3"
koin            = "4.1.0"
coroutines      = "1.10.2"
kotlinxDatetime = "0.7.1"
kotlinxSerialization = "1.9.0"
turbine         = "1.2.1"

[libraries]
gitlive-firebase-auth      = { module = "dev.gitlive:firebase-auth",      version.ref = "gitliveFirebase" }
gitlive-firebase-firestore = { module = "dev.gitlive:firebase-firestore", version.ref = "gitliveFirebase" }
gitlive-firebase-storage   = { module = "dev.gitlive:firebase-storage",   version.ref = "gitliveFirebase" }
gitlive-firebase-common    = { module = "dev.gitlive:firebase-common",    version.ref = "gitliveFirebase" }

firebase-bom        = { module = "com.google.firebase:firebase-bom", version.ref = "firebaseBom" }
firebase-analytics  = { module = "com.google.firebase:firebase-analytics" }
firebase-crashlytics= { module = "com.google.firebase:firebase-crashlytics" }

koin-core      = { module = "io.insert-koin:koin-core",       version.ref = "koin" }
koin-android   = { module = "io.insert-koin:koin-android",    version.ref = "koin" }
koin-compose   = { module = "io.insert-koin:koin-compose-viewmodel", version.ref = "koin" }

kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-datetime        = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinxDatetime" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }

[plugins]
googleServices = { id = "com.google.gms.google-services", version.ref = "googleServices" }
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

> Versiones fijadas a estables de agosto 2026. Kotlin y Compose MP los pone el wizard — **no los toques**, ahí es donde se rompe la compatibilidad.

---

## 1.5 `shared/build.gradle.kts`

```kotlin
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    // OJO: Compose Multiplatform 1.11 eliminó los targets Apple x86_64.
    // Nada de iosX64() ni macosX64().
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)

            api(libs.gitlive.firebase.common)
            api(libs.gitlive.firebase.auth)
            api(libs.gitlive.firebase.firestore)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.analytics)
            implementation(libs.firebase.crashlytics)
            implementation(libs.koin.android)
        }
    }
}

android {
    namespace = "com.kitchenai.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
```

Y en `composeApp/build.gradle.kts`, dentro de `commonMain.dependencies`:

```kotlin
implementation(projects.shared)
implementation(libs.koin.compose)
```

---

## 1.6 Firebase — proyecto y apps

```bash
npm install -g firebase-tools
firebase login
firebase projects:create kitchenai-dev --display-name "KitchenAI Dev"
```

En la [consola de Firebase](https://console.firebase.google.com), proyecto `kitchenai-dev`:

| App | Identificador | Fichero a descargar | Destino en el repo |
|---|---|---|---|
| Android | `com.kitchenai.app` | `google-services.json` | `composeApp/google-services.json` |
| iOS | `com.kitchenai.app` | `GoogleService-Info.plist` | `iosApp/iosApp/GoogleService-Info.plist` |

Habilita: **Authentication → Email/Password**, **Firestore Database → modo test**, **Storage**.

### Android

`build.gradle.kts` raíz:

```kotlin
plugins {
    // ...
    alias(libs.plugins.googleServices) apply false
}
```

`composeApp/build.gradle.kts`:

```kotlin
plugins {
    // ...
    alias(libs.plugins.googleServices)
}
```

### iOS

1. Abre `iosApp/iosApp.xcodeproj` en Xcode.
2. Arrastra `GoogleService-Info.plist` al target `iosApp` → marca *Copy items if needed* y *Add to target*.
3. `File → Add Package Dependencies…` → `https://github.com/firebase/firebase-ios-sdk` → **Up to Next Major** → añade `FirebaseAuth`, `FirebaseFirestore`, `FirebaseStorage`.
4. En `iOSApp.swift`:

```swift
import SwiftUI
import FirebaseCore

@main
struct iOSApp: App {
    init() { FirebaseApp.configure() }
    var body: some Scene { WindowGroup { ContentView() } }
}
```

> En Android **no** hace falta llamar a `FirebaseApp.initializeApp`: el plugin `google-services` lo hace por ContentProvider.

### Secretos fuera del repo

```bash
cat >> .gitignore <<'EOF'

# Firebase
composeApp/google-services.json
iosApp/iosApp/GoogleService-Info.plist
**/*.keystore
local.properties
.kotlin/
EOF
```

Para que CI pueda compilar, sube los ficheros como secrets base64:

```bash
gh secret set GOOGLE_SERVICES_JSON < <(base64 -i composeApp/google-services.json)
gh secret set GOOGLE_SERVICE_INFO_PLIST < <(base64 -i iosApp/iosApp/GoogleService-Info.plist)
```

Y guarda plantillas versionables para que el proyecto compile en clones limpios:

```bash
cp composeApp/google-services.json composeApp/google-services.json.template
# edita la plantilla y sustituye claves reales por PLACEHOLDER
git add composeApp/google-services.json.template
```

---

## 1.7 Smoke test

`shared/src/commonMain/kotlin/com/kitchenai/shared/core/FirebaseSmokeTest.kt`:

```kotlin
package com.kitchenai.shared.core

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth

object FirebaseSmoke {
    /** Devuelve true si el SDK de Firebase se inicializó correctamente. */
    fun isReady(): Boolean = runCatching { Firebase.auth }.isSuccess
}
```

Llámalo desde el composable raíz y verifica:

```bash
./gradlew :shared:build
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

Para iOS: abre `iosApp.xcodeproj` y ejecuta en simulador.

---

## ✅ Checklist Paso 1

- [ ] `gh repo view` muestra el repo y `git push` funciona
- [ ] `./gradlew :composeApp:assembleDebug` termina en BUILD SUCCESSFUL
- [ ] La app Android arranca y `FirebaseSmoke.isReady()` devuelve `true`
- [ ] La app iOS arranca en simulador sin crash de `FirebaseApp.configure()`
- [ ] `google-services.json` y `.plist` **no** aparecen en `git status`
- [ ] Los secrets `GOOGLE_SERVICES_JSON` y `GOOGLE_SERVICE_INFO_PLIST` existen (`gh secret list`)

Cuando todo esté marcado, dime **"Paso 1 OK"** y montamos el Paso 2 (GitHub Projects).
