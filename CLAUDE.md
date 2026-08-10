# KitchenAI — Contexto para agentes

Aplicación **Kotlin Multiplatform** (Android + iOS) con **Compose Multiplatform** y **Firebase**.
Este fichero es normativo: lo leen tanto el agente que implementa las issues como el que revisa las PRs.

---

## Módulos

| Módulo | Plugin Android | Contiene | Puede depender de |
|---|---|---|---|
| `:shared` | `com.android.kotlin.multiplatform.library` | `domain` (modelos, puertos, casos de uso) y `data` (Firebase, caché, mappers) | nada del proyecto |
| `:composeApp` | `com.android.kotlin.multiplatform.library` | `presentation` (ViewModels, UiState, composables), navegación, design system. Paquete `com.kitchenai.ui` | `:shared` |
| `:androidApp` | `com.android.application` | `MainActivity`, `Application`, manifest, recursos e iconos. Paquete `com.kitchenai.app`. **Sin lógica** | `:composeApp`, `:shared` |
| `iosApp` | — | Xcode wrapper, `FirebaseApp.configure()` | framework `ComposeApp` |

Desde AGP 9 el plugin de KMP es incompatible con `com.android.application` en el mismo
módulo: por eso `:androidApp` existe y sólo contiene el punto de entrada. Cualquier PR que
meta lógica de negocio o composables ahí es un rechazo.

### Regla de dependencias (bloqueante)

```
androidApp               →  composeApp, shared   ✅
composeApp.presentation  →  shared.domain        ✅
shared.data              →  shared.domain        ✅
shared.domain            →  cualquier otra cosa  ❌
composeApp               →  shared.data          ❌
composeApp / shared      →  androidApp           ❌
```

`shared/domain` es **Kotlin puro**: sin Firebase, sin Android, sin iOS, sin Compose, sin Ktor.
Si un fichero bajo `domain/` importa algo de `dev.gitlive`, `android.`, `androidx.`, `platform.` o
`kotlinx.coroutines.Dispatchers`, es un rechazo automático.

### Source sets

- `commonMain` — código multiplataforma. Prohibido `java.*`, `android.*`, `platform.*`.
- `androidMain` / `iosMain` — implementaciones `actual` y sólo eso.
- La API pública de `:shared` hacia iOS debe evitar genéricos complejos, `sealed` anidados
  profundos y `Flow` sin envolver: Objective-C interop no los traduce bien.

---

## Convenciones

**Errores.** Ninguna excepción cruza una frontera de capa. Todo lo que sale de `data` y `domain`
va en `AppResult<T>` (`core/AppResult.kt`). Los `catch` mapean a `AppError`, nunca se tragan.

**Concurrencia.** Nada de `Dispatchers.IO` hardcodeado: se inyecta `DispatcherProvider`.
Prohibido `GlobalScope` y `runBlocking` fuera de tests.

**Casos de uso.** Una clase = una operación, con `operator fun invoke`. Nombre en imperativo:
`GetRecipeById`, `SaveShoppingList`.

**ViewModels.** Exponen un único `StateFlow<XxxUiState>`. Sin lógica de negocio: orquestan
casos de uso. Los eventos de un solo disparo van por `Channel`, no por estado.

**Inyección.** Koin. Cada capa declara su módulo en `di/`; nada de singletons a mano ni
`object` con estado mutable.

**Nombres de ficheros.** Un tipo público por fichero, con el mismo nombre.

---

## Tests

- Todo caso de uso nuevo o modificado necesita test en `shared/src/commonTest`.
- Repositorios: test contra fakes de los puertos, nunca contra Firebase real.
- Flows: `app.cash.turbine`.
- Los ViewModels se testean con `kotlinx-coroutines-test` y `DispatcherProvider` de test.
- Cobertura no es un objetivo; ramas de error sin cubrir sí son un hallazgo.

---

## Seguridad

- `androidApp/google-services.json` y `GoogleService-Info.plist` **nunca** se commitean (están en `.gitignore`).
  En CI se restauran desde secrets base64.
- Sin claves, tokens ni endpoints hardcodeados en el código.
- Reglas de Firestore en `firebase/firestore.rules`, versionadas y con tests.
  Ninguna regla puede quedar como `allow read, write: if true`.
- Nada de PII (emails, ubicaciones, contenido de usuario) en `println` ni en Crashlytics.

---

## Flujo de trabajo (spec-driven)

1. Cada unidad de trabajo es una **issue** con: contexto, plan de desarrollo paso a paso,
   ficheros afectados, criterios de aceptación y dependencias (`Blocked by #N`).
2. Rama por issue: `feat/<n>-slug`, `fix/<n>-slug`, `chore/<n>-slug`.
3. Commits en [Conventional Commits](https://www.conventionalcommits.org): `feat(domain): ...`.
4. La PR referencia la issue con `Closes #N` y no toca nada fuera de su alcance.
5. CI + revisión de Claude deben estar en verde antes del merge.
6. Merge en `main` con squash; la issue pasa a **Done** automáticamente.

Las issues sin dependencias entre sí pueden desarrollarse en paralelo: por eso cada issue
declara explícitamente los ficheros que va a tocar, para detectar colisiones antes de empezar.

---

## Criterios de revisión (para el agente revisor)

Bloqueante:

- Violación de la regla de dependencias o fuga de plataforma en `commonMain`.
- Caso de uso nuevo sin test.
- Secreto, clave o fichero de configuración de Firebase commiteado.
- Excepción propagándose fuera de `data`/`domain` sin envolver en `AppResult`.
- Regla de Firestore permisiva.
- La PR no cumple los criterios de aceptación de la issue que dice cerrar.

No bloqueante (comentar, no bloquear): duplicación menor, naming mejorable, TODOs con issue asociada.

No comentar nunca: formato (lo cubre ktlint), preferencias de estilo, elogios genéricos.
