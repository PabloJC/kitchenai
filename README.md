# KitchenAI

**Kotlin Multiplatform** app (Android + iOS) built with **Compose Multiplatform** and
**Firebase**.

```
:shared        domain (pure Kotlin) + data (Firebase, cache, mappers)
:composeApp    presentation (ViewModels, UiState, composables) + design system
:androidApp    Android entry point: MainActivity, Application, manifest, resources
iosApp         Xcode wrapper
```

`:androidApp` is a separate module because since AGP 9 the Kotlin Multiplatform plugin
cannot coexist with `com.android.application` in the same module.

Architecture rules, conventions and review criteria live in [`CLAUDE.md`](CLAUDE.md). They
are normative: the implementing agent applies them and the reviewing agent verifies them.

## Getting started

You need the Firebase config files, which are not in the repository:
`androidApp/google-services.json` and `iosApp/GoogleService-Info.plist`. Then:

```bash
./gradlew :shared:check
./gradlew :androidApp:assembleDebug
```

CI, automated review, branch protection and the project board are documented in
[`docs/infra.md`](docs/infra.md), along with the traps worth knowing before touching any
of it.

## Common commands

| What | Command |
|---|---|
| Shared code tests | `./gradlew :shared:check` |
| Link the iOS framework | `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` |
| Debug APK | `./gradlew :androidApp:assembleDebug` |
| Framework for Xcode | `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` |
| Real test task names | `./gradlew :shared:tasks --group verification` |
| Static analysis | `./gradlew detekt ktlintCheck` |
| Auto-format | `./gradlew ktlintFormat` |
| Firebase emulators | `firebase emulators:start` |

## Stack

| Piece | Version |
|---|---|
| Kotlin | 2.4.10 |
| Compose Multiplatform | 1.11.1 |
| Android Gradle Plugin | 9.0.1 |
| Gradle | 9.1.0 |
| Firebase (GitLive KMP) | 2.5.0 &nbsp;<sub>2.6.0 is broken on Maven Central</sub> |
| Koin | 4.1.0 |
| minSdk / compileSdk | 26 / 36 |
| Minimum iOS | 14 (arm64 only) |

> Compose Multiplatform 1.11 dropped the Apple x86_64 targets: do not declare `iosX64()`.

## Workflow

Issue with a development plan → `feat/<n>-slug` branch → PR with `Closes #N` →
green CI + Claude review → squash merge into `main` → issue moves to *Done*.
