# Paso 1 (addendum) — Estructura para AGP 9

## Qué pasó

El wizard trajo **AGP 9.0.1**. Desde AGP 9, `com.android.application` y `com.android.library`
son incompatibles con `org.jetbrains.kotlin.multiplatform` **en el mismo módulo**:

```
> Failed to apply plugin 'com.android.internal.application'.
  The 'com.android.library' (or 'com.android.application') plugin is not compatible
  with the 'org.jetbrains.kotlin.multiplatform' plugin since AGP 9.
```

Había tres salidas. Descartamos las dos rápidas:

- `android.builtInKotlin=false` + `android.newDsl=false` — compila hoy, deja de existir en
  AGP 10 (segundo semestre de 2026). Habría que hacer esta misma migración más tarde y con
  más código encima.
- Bajar a AGP 8.x — funciona, pero congela el toolchain justo al empezar el proyecto.

Hicimos la migración real, que además encaja mejor con la arquitectura por capas.

> **Nota**: el error se disparó porque mi script de merge hacía copia de seguridad de
> `gradle.properties` pero nunca fusionaba el del wizard. Ya está corregido en
> `scripts/merge-wizard.sh` (paso 4/7).

---

## Estructura resultante

```
:shared        com.android.kotlin.multiplatform.library   domain + data       com.kitchenai.shared
:composeApp    com.android.kotlin.multiplatform.library   UI compartida       com.kitchenai.ui
:androidApp    com.android.application                    entry point Android com.kitchenai.app
iosApp         —                                          entry point iOS
```

`:androidApp` contiene **sólo** `MainActivity`, `KitchenAiApplication`, el manifest y los
recursos. Ni composables ni lógica: eso vive en `:composeApp` y `:shared`. Esa regla está
en `CLAUDE.md` y la verifica el revisor de PRs.

Los namespaces tuvieron que separarse: AGP 9 activa `android.uniquePackageNames` por
defecto y dos módulos no pueden compartirlo. Por eso la UI compartida pasó de
`com.kitchenai.app` a `com.kitchenai.ui`.

---

## Cambios en los build scripts

**`:shared` y `:composeApp`** — el bloque `android {}` de nivel superior desaparece y su
configuración se mueve dentro de `kotlin { androidLibrary { } }`:

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)   // era androidLibrary
}

kotlin {
    androidLibrary {                        // era androidTarget {}
        namespace = "com.kitchenai.shared"
        compileSdk = 36
        minSdk = 26
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        withHostTest { }                    // los tests unitarios ya no se activan solos
    }
}
```

En `:composeApp` hace falta además:

```kotlin
androidResources { enable = true }
```

Sin esa línea los recursos de Compose no se empaquetan en el APK y la app crashea al
arrancar ([CMP-9547](https://youtrack.jetbrains.com/issue/CMP-9547)). Es el fallo más
silencioso de toda la migración.

**`:androidApp`** — Gradle de Android clásico, sin plugin de KMP y **sin `kotlin-android`**:
AGP 9 trae soporte de Kotlin integrado y aplicar el plugin da conflicto.

```kotlin
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
}

dependencies {
    implementation(projects.composeApp)
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.firebase.bom))
    // ...
}
```

---

## Otros efectos

| Antes | Ahora |
|---|---|
| `composeApp/google-services.json` | `androidApp/google-services.json` |
| `./gradlew :composeApp:assembleDebug` | `./gradlew :androidApp:assembleDebug` |
| `./gradlew :shared:testDebugUnitTest` | `./gradlew :shared:check` |
| `:shared:linkDebugFrameworkIosSimulatorArm64` | `:composeApp:linkDebugFrameworkIosSimulatorArm64` |

Actualizados: `.gitignore`, `.github/workflows/ci.yml`, `CLAUDE.md`, `README.md` y los tres
scripts de `scripts/`.

**Sobre los nombres de las tareas de test.** El plugin nuevo usa un único variant, así que
`testDebugUnitTest` ya no existe. Uso `check` en CI porque resuelve las tareas que existan
sin que yo tenga que adivinar el nombre. Para fijarlo de forma precisa:

```bash
./gradlew :shared:tasks --group verification
```

Pásame la salida y sustituyo `check` por la tarea concreta en el workflow.

---

## Verificación

```bash
./gradlew --stop
rm -rf .gradle build             # la configuration cache guarda el grafo viejo
./gradlew :shared:check
./gradlew :androidApp:assembleDebug
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

En Xcode, la Run Script Phase del wizard apunta a `:composeApp:embedAndSignAppleFrameworkForXcode`.
Si el wizard la generó apuntando a otro módulo, corrígela.

Y en la consola de Firebase, la app Android debe estar registrada con el package
**`com.kitchenai.app`** — que sigue siendo el `applicationId`, ahora declarado en
`androidApp/build.gradle.kts`.


---

## Apéndice — otros dos fallos de la primera build

### `Could not find dev.gitlive:firebase-app:2.6.0`

La release **2.6.0** de GitLive está incompleta en Maven Central: `firebase-auth` y
`firebase-firestore` sí se publicaron, pero el módulo raíz `firebase-app` **no**. Sus
propios POM declaran `firebase-app-android:2.6.0` como dependencia, así que la resolución
falla siempre, no es un problema de configuración local.

Comprobado directamente contra el repositorio:

```
dev.gitlive:firebase-auth       … 2.4.0, 2.5.0, 2.6.0, 3.0.0-alpha01
dev.gitlive:firebase-firestore  … 2.4.0, 2.5.0, 2.6.0, 3.0.0-alpha01
dev.gitlive:firebase-app        … 2.4.0, 2.5.0,        3.0.0-alpha01   ← falta 2.6.0
```

Fijado `gitliveFirebase = "2.5.0"`, donde el conjunto está completo y coherente
(sus POM apuntan a `firebase-app-android:2.5.0`).

Cuando salga 2.6.1 o 3.0.0 estable, actualizar. Antes de subir la versión, conviene
verificar que `firebase-app` existe en esa versión:

```bash
curl -s https://repo1.maven.org/maven2/dev/gitlive/firebase-app/maven-metadata.xml | grep '<version>'
```

### `Could not find com.google.firebase:firebase-common:` (versión vacía)

Fíjate en el `:` final sin número: Gradle no tenía **ninguna** versión que aplicar.

Los POM de GitLive declaran `com.google.firebase:firebase-auth`, `firebase-firestore` y
`firebase-common` **sin versión**, y las resuelven importando `firebase-bom` en su
`<dependencyManagement>`. Gradle, al consumir los metadatos de módulo, no hereda ese import:
el BOM lo tiene que aportar el consumidor.

Regresión introducida en la migración a AGP 9: al mover Firebase a `:androidApp` me llevé
también el BOM, pero quien depende de GitLive es `:shared`. Sin BOM en ese módulo, nada
resolvía las versiones.

Corregido en `shared/build.gradle.kts`:

```kotlin
androidMain.dependencies {
    api(project.dependencies.platform(libs.firebase.bom))
    // ...
}
```

`api` y no `implementation`: la restricción de versiones tiene que propagarse al
`androidCompileClasspath` de `:composeApp` y `:androidApp`, no quedarse en `:shared`.

El BOM vive sólo en `:shared`. `:androidApp` no consume nada de Firebase directamente
(ver el apartado de Crashlytics más abajo), así que no lo declara.

BOM subido a **34.9.0**. GitLive 2.5.0 se construyó contra 33.15.0, pero sus POM piden
`com.google.firebase:firebase-auth` y no las variantes `-ktx` que desaparecieron en el
BOM 34.0.0, así que 34.x es seguro.

### `No matching client found for package name 'com.kitchenai.app.debug'`

Yo había puesto `applicationIdSuffix = ".debug"` en el build type de debug, lo que cambia
el package a `com.kitchenai.app.debug`. El plugin de google-services busca ese package
dentro de `google-services.json` y sólo está registrado `com.kitchenai.app`.

Sufijo eliminado. Servía para tener debug y release instalados a la vez, algo que ahora
mismo no aporta: hay un único proyecto de Firebase, así que no estás comparando datos de
dev contra los de producción.

Cuando existan `kitchenai-dev` y `kitchenai-prod` como proyectos separados, la forma
correcta no es el sufijo sino **product flavors**, cada uno con su propio
`google-services.json` en `androidApp/src/<flavor>/`:

```
androidApp/src/dev/google-services.json    -> proyecto kitchenai-dev
androidApp/src/prod/google-services.json   -> proyecto kitchenai-prod
```

La alternativa rápida, si quieres el sufijo ya, es registrar una segunda app Android con
package `com.kitchenai.app.debug` en el mismo proyecto de Firebase y volver a descargar
el JSON: contendrá los dos clientes.

### `kotlin.native.cacheKind` deprecado

Era una propiedad que yo había puesto sin necesidad real. Se eliminó en Kotlin 2.3.20.
Borrada de `gradle.properties`.

### Si vuelve el error de configuration cache

```
Task ':androidApp:processDebugNavigationResources': error writing value of type
'DefaultConfigurableFileCollection'
```

Era una consecuencia del fallo de resolución: Gradle no podía serializar un classpath que
no había conseguido resolver. Debería desaparecer al arreglar la dependencia. Si persiste,
es fricción entre AGP 9 y la configuration cache; desactívala temporalmente con
`org.gradle.configuration-cache=false` y dímelo, porque entonces hay que investigarlo
aparte y no conviene renunciar a ella de forma permanente.


---

## `ld: framework 'FirebaseCore' not found` al enlazar los tests de iOS

Los artefactos de GitLive para Kotlin/Native declaran linker options hacia los frameworks
del SDK nativo de Firebase. Cuando Xcode compila la app, SPM los tiene resueltos; pero
`linkDebugTestIosSimulatorArm64` lo enlaza Gradle por su cuenta, fuera de Xcode, y no
encuentra `FirebaseCore`.

Es una limitación conocida de la combinación KMP + SDK nativo distribuido por SPM, no un
error de configuración de este proyecto.

**Ahora:** tests de Kotlin/Native desactivados en `:shared` y `:composeApp`. Los de
`commonTest` siguen ejecutándose en JVM/Android, que es donde vive toda la lógica de
dominio; el código es el mismo, sólo cambia la plataforma que lo ejecuta.

**Arreglo de fondo, cuando toque:** partir `:shared` en dos módulos.

```
:domain    Kotlin puro. Sin Firebase, sin Android, sin iOS. Testeable en TODOS los targets.
:data      GitLive, Firestore, caché, mappers. Sólo aquí entra Firebase.
```

`CLAUDE.md` ya exige que `shared/domain` no importe nada de `dev.gitlive`, así que la
separación física en módulos es la consecuencia natural de una regla que ya existe: hoy la
vigila el revisor de PRs, mañana la haría cumplir el propio grafo de dependencias de Gradle.
Y de paso los tests de dominio volverían a correr en iOS.

No lo hago ahora porque es otra reestructuración y el objetivo inmediato es cerrar el
Paso 1. Merece una issue propia.

**Lo que esto NO cubre:** si algún día metes código en `iosMain` con lógica propia, no
tendrá tests hasta que se haga esa separación. Tenlo presente al escribir implementaciones
`actual`: cuanto más finas sean, menos se echa de menos.


---

## `The Crashlytics build ID is missing` — crash al arrancar en Android

Yo había añadido `firebase-crashlytics` como **dependencia** pero no su **plugin de Gradle**,
que es quien inyecta el build ID en el APK. Sin ese ID, Crashlytics lanza al inicializarse.
Y como se inicializa dentro de `FirebaseInitProvider`, se lleva por delante todo Firebase:

```
java.lang.RuntimeException: Unable to get provider
  com.google.firebase.provider.FirebaseInitProvider:
  java.lang.IllegalStateException: The Crashlytics build ID is missing.
```

La app crashea en el arranque, antes de pintar nada, aunque tu código no toque Crashlytics.

**Decisión: fuera Analytics y Crashlytics por ahora.** Ningún código las usaba. Crashlytics
bien montado necesita además la subida del mapping de R8 para que los stack traces de release
sean legibles, y eso merece su propia issue cuando haya testers.

Cuando toque añadirlas, la issue debe cubrir:

1. Plugin `com.google.firebase.crashlytics` en `androidApp/build.gradle.kts`.
2. `firebase-crashlytics` y `firebase-analytics` de vuelta, más el BOM en ese módulo.
3. Subida automática del mapping de R8 en los builds de release.
4. Verificación real: forzar un crash y comprobar que llega a la consola de Firebase
   con el stack trace desofuscado. Sin ese último paso no sabes si funciona.
