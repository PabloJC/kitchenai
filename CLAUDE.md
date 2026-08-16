# KitchenAI — context for agents

**Kotlin Multiplatform** application (Android + iOS) using **Compose Multiplatform** and
**Firebase**. This file is normative: it is read both by the agent implementing issues and
by the agent reviewing pull requests.

---

## Modules

| Module | Android plugin | Contains | May depend on |
|---|---|---|---|
| `:shared` | `com.android.kotlin.multiplatform.library` | `domain` (models, ports, use cases) and `data` (Firebase, cache, mappers) | nothing in the project |
| `:composeApp` | `com.android.kotlin.multiplatform.library` | `presentation` (ViewModels, UiState, composables), navigation, design system. Package `com.kitchenai.ui` | `:shared` |
| `:androidApp` | `com.android.application` | `MainActivity`, `Application`, manifest, resources and icons. Package `com.kitchenai.app`. **No logic** | `:composeApp`, `:shared` |
| `iosApp` | — | Xcode wrapper, `FirebaseApp.configure()` | `ComposeApp` framework |
| `functions` | — | TypeScript. The `suggestRecipes` callable: the only thing that talks to a model, and the only thing holding a credential to do it | nothing in the project |

Since AGP 9 the KMP plugin is incompatible with `com.android.application` in the same
module: that is why `:androidApp` exists and holds nothing but the entry point. Any PR that
puts business logic or composables there is a rejection.

### Dependency rule (blocking)

```
androidApp               →  composeApp, shared   ✅
composeApp.presentation  →  shared.domain        ✅
shared.data              →  shared.domain        ✅
shared.domain            →  anything else        ❌
composeApp               →  shared.data          ❌
composeApp / shared      →  androidApp           ❌
```

`shared/domain` is **pure Kotlin**: no Firebase, no Android, no iOS, no Compose, no Ktor.
If a file under `domain/` imports anything from `dev.gitlive`, `android.`, `androidx.`,
`platform.` or `kotlinx.coroutines.Dispatchers`, that is an automatic rejection.

### Source sets

- `commonMain` — multiplatform code. `java.*`, `android.*` and `platform.*` are forbidden.
- `androidMain` / `iosMain` — `actual` implementations and nothing else.
- The public API of `:shared` towards iOS must avoid complex generics, deeply nested
  `sealed` hierarchies and unwrapped `Flow`: Objective-C interop translates them poorly.

---

## Conventions

**Errors.** No exception crosses a layer boundary. Everything leaving `data` and `domain`
travels in `AppResult<T>` (`core/AppResult.kt`). `catch` blocks map to `AppError`; they
never swallow.

**Concurrency.** No hardcoded `Dispatchers.IO`: inject `DispatcherProvider`. `GlobalScope`
and `runBlocking` are forbidden outside tests.

**Use cases.** One class = one operation, with `operator fun invoke`. Imperative names:
`GetRecipeById`, `SaveShoppingList`.

**Repositories and data sources.** The layering is `UseCases -> Repositories -> DataSources
(Local and Remote)`. Domain declares a `<Entity>RepositoryContract`; a single `<Entity>Repository`
in `data/repository/` implements it and is the only thing a use case may depend on for that
entity. A repository owns the domain mapping and coordinates one or more data sources — it never
touches a database driver, an HTTP client or the filesystem itself. A data source wraps exactly
one backend and stays in that backend's own shape (`<Entity>Entity` for Room, a DTO for
Firestore), never a domain type: `<Entity>LocalDataSource` lives in `shared/data/local/` next to
the storage it wraps; `<Entity>RemoteDataSource` would live in `shared/data/remote/`.
`RecipeRepositoryContract` / `RecipeRepository` (#137, #139) is the first pair of these, backed by
both a `RecipeLocalDataSource` (Room, the local generation cache) and a `RecipeRemoteDataSource`
(Firestore, the read-only catalogue and saved recipes) — one contract, one repository, coordinating
two data sources rather than each backend getting its own seam. Every other repository-backed
domain interface follows the same `*RepositoryContract` naming (#138).

**ViewModels.** They expose a single `StateFlow<XxxUiState>`. No business logic: they
orchestrate use cases. One-shot events go through a `Channel`, not through state.

**Injection.** Koin. Each layer declares its module under `di/`; no hand-rolled singletons
and no `object` holding mutable state.

**File names.** One public type per file, named after it.

**Language.** Everything written in the repository is in English: code, comments, KDoc, test
names, commit messages, documentation, workflow step names and anything a script prints.

**Comments.** One or two lines. Say what the code cannot — a constraint, a forced choice, a
trap. Never paraphrase the code. If it needs paragraphs it belongs in `docs/` or the pull
request body.

---

## Tests

- Every new or modified use case needs a test in `shared/src/commonTest`.
- Repositories: tested against fakes of the ports, never against real Firebase.
- Flows: `app.cash.turbine`.
- ViewModels are tested with `kotlinx-coroutines-test` and a test `DispatcherProvider`.
- Coverage is not a goal; uncovered error branches are a finding.

---

## Security

- `androidApp/google-services.json` and `iosApp/GoogleService-Info.plist` are **never**
  committed (they are in `.gitignore`). CI restores them from base64 secrets.
- No keys, tokens or endpoints hardcoded in the source. The model call lives in `functions/`
  precisely so no client ever holds one; a prompt or a provider name appearing in `:shared` or
  `:composeApp` is a rejection.
- Firestore rules live in `firebase/firestore.rules`, versioned and tested. No rule may be
  left as `allow read, write: if true`.
- No PII (emails, locations, user content) in `println` or in Crashlytics.

---

## Workflow (spec-driven)

1. Every unit of work is an **issue** carrying: context, a step-by-step development plan,
   the files it will touch, acceptance criteria and dependencies (`Blocked by #N`).
2. One branch per issue: `feat/<n>-slug`, `fix/<n>-slug`, `chore/<n>-slug`.
3. Commits follow [Conventional Commits](https://www.conventionalcommits.org):
   `feat(domain): ...`.
4. The PR references the issue with `Closes #N` and touches nothing outside its scope.
5. CI and Claude's review must both be green before merging.
6. Squash merge into `main`; the issue moves to **Done** automatically.

Issues with no dependencies between them can be developed in parallel: that is why each
issue declares the files it will touch, so collisions surface before any work starts.

---

## Review criteria (for the reviewing agent)

Blocking:

- Any violation of the dependency rule, or a platform leak in `commonMain`.
- A new use case without a test.
- A secret, key or Firebase config file committed.
- An exception escaping `data`/`domain` without being wrapped in `AppResult`.
- A permissive Firestore rule.
- The PR does not meet the acceptance criteria of the issue it claims to close.

Non-blocking (comment, do not block): minor duplication, naming that could be better,
TODOs with an associated issue.

Never comment on: formatting (ktlint covers it), style preferences, generic praise.
