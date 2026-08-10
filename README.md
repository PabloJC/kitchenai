# KitchenAI

App **Kotlin Multiplatform** (Android + iOS) con **Compose Multiplatform** y **Firebase**.

```
:shared        domain (Kotlin puro) + data (Firebase, caché, mappers)
:composeApp    presentation (ViewModels, UiState, composables) + design system
:androidApp    entry point Android: MainActivity, Application, manifest, recursos
iosApp         wrapper Xcode
```

`:androidApp` está separado porque desde AGP 9 el plugin de Kotlin Multiplatform no puede
convivir con `com.android.application` en el mismo módulo.

Las reglas de arquitectura, convenciones y criterios de revisión están en [`CLAUDE.md`](CLAUDE.md).
Son normativas: las aplica el agente que implementa y las verifica el que revisa las PRs.

## Arranque

Necesitas los ficheros de configuración de Firebase, que no están en el repositorio:
`androidApp/google-services.json` y `iosApp/GoogleService-Info.plist`. Después:

```bash
./gradlew :shared:check
./gradlew :androidApp:assembleDebug
```

El montaje de CI, revisión, protección de rama y tablero está en
[`docs/infra.md`](docs/infra.md), junto con las trampas que conviene conocer antes de
tocarlo.

## Comandos habituales

| Qué | Comando |
|---|---|
| Tests del código compartido | `./gradlew :shared:check` |
| Enlazado del framework iOS | `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` |
| APK debug | `./gradlew :androidApp:assembleDebug` |
| Framework para Xcode | `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` |
| Ver nombres reales de tareas de test | `./gradlew :shared:tasks --group verification` |
| Análisis estático | `./gradlew detekt ktlintCheck` |
| Autoformato | `./gradlew ktlintFormat` |
| Emuladores de Firebase | `firebase emulators:start` |

## Stack

| Pieza | Versión |
|---|---|
| Kotlin | 2.4.10 |
| Compose Multiplatform | 1.11.1 |
| Android Gradle Plugin | 9.0.1 |
| Gradle | 9.1.0 |
| Firebase (GitLive KMP) | 2.5.0 &nbsp;<sub>2.6.0 está roto en Maven Central</sub> |
| Koin | 4.1.0 |
| minSdk / compileSdk | 26 / 36 |
| iOS mínimo | 14 (sólo arm64) |

> Compose Multiplatform 1.11 eliminó los targets Apple x86_64: no declares `iosX64()`.

## Flujo de trabajo

Issue con plan de desarrollo → rama `feat/<n>-slug` → PR con `Closes #N` →
CI verde + revisión de Claude → squash merge en `main` → issue a *Done*.
